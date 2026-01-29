package com.demo.passport;

import android.nfc.Tag;
import android.nfc.tech.IsoDep;

import net.sf.scuba.smartcards.CardService;
import net.sf.scuba.smartcards.CardServiceException;

import org.jmrtd.BACKey;
import org.jmrtd.PACEKeySpec;
import org.jmrtd.PassportService;
import org.jmrtd.lds.CardAccessFile;
import org.jmrtd.lds.PACEInfo;
import org.jmrtd.lds.SecurityInfo;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NFC eMRTD reader with PACE and BAC authentication support.
 *
 * Authentication strategy:
 * 1. Read EF.CardAccess to detect PACE support
 * 2. If PACE is supported - attempt PACE authentication first
 * 3. If PACE is not supported or fails with "not supported" - fallback to BAC
 * 4. If PACE fails with protocol/crypto error - return PACE_FAILED
 *
 * Key improvements:
 * - PACE as primary authentication method
 * - BAC as fallback for older documents
 * - Canonical classification of all error scenarios
 * - Structured logging with auth method tracking
 * - Backend calls only allowed on SUCCESS
 */
public final class NfcPassportReader {
    static final int NFC_TIMEOUT_MS = 45000;

    /** Authentication method constants */
    public static final String AUTH_METHOD_PACE = "PACE";
    public static final String AUTH_METHOD_BAC = "BAC";

    /**
     * Pattern to extract SW code from error messages.
     * Matches formats like "SW = 0x6985", "SW=6985", "sw: 0x6985"
     */
    private static final Pattern SW_CODE_PATTERN = Pattern.compile(
            "SW\\s*[=:]\\s*(0x)?([0-9A-Fa-f]{4})",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * SW codes indicating PACE is not supported (fallback to BAC allowed).
     */
    private static final String SW_FILE_NOT_FOUND = "6A82";
    private static final String SW_FUNCTION_NOT_SUPPORTED = "6A81";
    private static final String SW_WRONG_P1P2 = "6A86";
    private static final String SW_INS_NOT_SUPPORTED = "6D00";

    /**
     * SW code indicating CONDITIONS NOT SATISFIED (PACE protocol error).
     */
    private static final String SW_CONDITIONS_NOT_SATISFIED = "6985";

    /**
     * Result of authentication attempt.
     */
    private static final class AuthResult {
        final boolean success;
        final String method;
        final String paceOid;
        final NfcReadResult errorResult;

        private AuthResult(boolean success, String method, String paceOid, NfcReadResult errorResult) {
            this.success = success;
            this.method = method;
            this.paceOid = paceOid;
            this.errorResult = errorResult;
        }

        static AuthResult paceSuccess(String paceOid) {
            return new AuthResult(true, AUTH_METHOD_PACE, paceOid, null);
        }

        static AuthResult bacSuccess() {
            return new AuthResult(true, AUTH_METHOD_BAC, null, null);
        }

        static AuthResult failure(NfcReadResult errorResult) {
            return new AuthResult(false, null, null, errorResult);
        }
    }

    /**
     * Reads raw DG1 and DG2 bytes from the passport chip.
     * Uses PACE as primary authentication, BAC as fallback.
     *
     * @param tag NFC tag from the chip
     * @param mrz MRZ keys for authentication
     * @return NfcReadResult with status and data (if successful)
     */
    public static NfcReadResult readPassportRaw(Tag tag, Models.MRZKeys mrz) {
        NfcLogger.logSessionStart();

        // Validate inputs
        if (mrz == null) {
            NfcLogger.logError(NfcReadStatus.BAC_FAILED, "input_validation", null, null);
            return NfcReadResult.error(
                    NfcReadStatus.BAC_FAILED,
                    "input_validation",
                    null,
                    "MRZ keys are required before reading NFC passport data"
            );
        }
        if (tag == null) {
            NfcLogger.logError(NfcReadStatus.NFC_NOT_AVAILABLE, "input_validation", null, null);
            return NfcReadResult.error(
                    NfcReadStatus.NFC_NOT_AVAILABLE,
                    "input_validation",
                    null,
                    "NFC tag is required for reading passport data"
            );
        }

        NfcLogger.logStage("isodep_check");
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) {
            NfcLogger.logError(NfcReadStatus.NFC_NOT_ISODEP, "isodep_check", null, null);
            return NfcReadResult.error(
                    NfcReadStatus.NFC_NOT_ISODEP,
                    "isodep_check",
                    null,
                    "IsoDep technology not available for this tag"
            );
        }

        PassportService service = null;
        try {
            NfcLogger.logStage("connection");
            isoDep.connect();
            isoDep.setTimeout(NFC_TIMEOUT_MS);
            CardService cardService = CardService.getInstance(isoDep);
            service = new PassportService(
                    cardService,
                    PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                    PassportService.DEFAULT_MAX_BLOCKSIZE,
                    false,
                    false
            );
            service.open();

            // Select passport applet
            NfcLogger.logStage("applet_selection");
            try {
                service.sendSelectApplet(false);
            } catch (Exception e) {
                String swCode = extractSwCode(e);
                NfcLogger.logError(NfcReadStatus.APPLET_SELECTION_FAILED, "applet_selection", swCode, e);
                return NfcReadResult.error(
                        NfcReadStatus.APPLET_SELECTION_FAILED,
                        "applet_selection",
                        swCode,
                        "Failed to select passport applet: " + e.getMessage()
                );
            }

            // Perform authentication (PACE first, then BAC fallback)
            NfcLogger.logStage("authentication");
            AuthResult authResult = performAuthentication(service, mrz);

            if (!authResult.success) {
                return authResult.errorResult;
            }

            NfcLogger.logAuthSuccess(authResult.method, authResult.paceOid);

            // Read DG1 (MRZ data)
            NfcLogger.logStage("dg1_read");
            byte[] dg1Raw;
            try (InputStream dg1Input = service.getInputStream(PassportService.EF_DG1)) {
                dg1Raw = readAllBytes(dg1Input);
            } catch (Exception e) {
                String swCode = extractSwCode(e);
                NfcLogger.logError(NfcReadStatus.DG_READ_ERROR, "dg1_read", swCode, e);
                return NfcReadResult.error(
                        NfcReadStatus.DG_READ_ERROR,
                        "dg1_read",
                        swCode,
                        "DG1 read failed: " + e.getMessage()
                );
            }

            // Read DG2 (face image)
            NfcLogger.logStage("dg2_read");
            byte[] dg2Raw;
            try (InputStream dg2Input = service.getInputStream(PassportService.EF_DG2)) {
                dg2Raw = readAllBytes(dg2Input);
            } catch (Exception e) {
                String swCode = extractSwCode(e);
                NfcLogger.logError(NfcReadStatus.DG_READ_ERROR, "dg2_read", swCode, e);
                return NfcReadResult.error(
                        NfcReadStatus.DG_READ_ERROR,
                        "dg2_read",
                        swCode,
                        "DG2 read failed: " + e.getMessage()
                );
            }

            // Validate minimum sizes
            NfcLogger.logStage("validation");
            NfcLogger.logDataRead(dg1Raw.length, dg2Raw.length);

            if (dg1Raw.length < 10) {
                NfcLogger.logError(NfcReadStatus.PARTIAL_READ, "validation", null, null);
                return NfcReadResult.error(
                        NfcReadStatus.PARTIAL_READ,
                        "validation",
                        null,
                        "DG1 data is too small (expected MRZ data, got " + dg1Raw.length + " bytes)"
                );
            }
            if (dg2Raw.length < NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES) {
                NfcLogger.logError(NfcReadStatus.PARTIAL_READ, "validation", null, null);
                return NfcReadResult.error(
                        NfcReadStatus.PARTIAL_READ,
                        "validation",
                        null,
                        "DG2 data is too small (expected face image, got " + dg2Raw.length + " bytes)"
                );
            }

            // Build successful result
            Models.NfcRawResult data = new Models.NfcRawResult();
            data.dg1Raw = dg1Raw;
            data.dg2Raw = dg2Raw;
            data.mrzKeys = mrz;

            NfcReadResult result = NfcReadResult.success(data, authResult.method, authResult.paceOid);
            NfcLogger.logResult(result);
            return result;

        } catch (Exception e) {
            String swCode = extractSwCode(e);
            NfcLogger.logError(NfcReadStatus.UNKNOWN_ERROR, "unknown", swCode, e);
            return NfcReadResult.error(
                    NfcReadStatus.UNKNOWN_ERROR,
                    "unknown",
                    swCode,
                    "NFC read failed: " + e.getMessage()
            );
        } finally {
            closeQuietly(service);
            closeQuietly(isoDep);
        }
    }

    /**
     * Performs authentication using PACE (preferred) or BAC (fallback).
     *
     * Strategy:
     * 1. Try to read EF.CardAccess to detect PACE support
     * 2. If PACE info found - attempt PACE authentication
     * 3. If PACE succeeds - return success
     * 4. If PACE is not supported (file not found, etc.) - fallback to BAC
     * 5. If PACE fails with protocol error - return PACE_FAILED (no fallback)
     */
    private static AuthResult performAuthentication(PassportService service, Models.MRZKeys mrz) {
        // Try to read EF.CardAccess for PACE info
        PACEInfo paceInfo = null;
        String paceOid = null;

        NfcLogger.logStage("card_access_read");
        try {
            CardAccessFile cardAccessFile = new CardAccessFile(
                    service.getInputStream(PassportService.EF_CARD_ACCESS)
            );
            Collection<SecurityInfo> securityInfos = cardAccessFile.getSecurityInfos();

            // Find first PACE info
            for (SecurityInfo info : securityInfos) {
                if (info instanceof PACEInfo) {
                    paceInfo = (PACEInfo) info;
                    paceOid = paceInfo.getObjectIdentifier();
                    break;
                }
            }

            int paceInfoCount = 0;
            for (SecurityInfo info : securityInfos) {
                if (info instanceof PACEInfo) {
                    paceInfoCount++;
                }
            }
            NfcLogger.logCardAccess(paceInfoCount, paceOid);

        } catch (Exception e) {
            // EF.CardAccess not found or not readable - PACE not supported
            // This is normal for older documents, fallback to BAC
            String swCode = extractSwCode(e);
            NfcLogger.logAuthAttempt(AUTH_METHOD_PACE, null, "not_supported");

            // Only fallback to BAC if it's a "file not found" type error
            if (isPaceNotSupportedError(swCode, e.getMessage())) {
                return performBacAuthentication(service, mrz);
            }

            // For other errors during CardAccess read, still try BAC
            return performBacAuthentication(service, mrz);
        }

        // If PACE info found, attempt PACE authentication
        if (paceInfo != null) {
            NfcLogger.logStage("pace_authentication");
            NfcLogger.logAuthAttempt(AUTH_METHOD_PACE, paceOid, "attempting");

            try {
                // Create PACE key from MRZ data
                PACEKeySpec paceKey = PACEKeySpec.createMRZKey(
                        mrz.document_number,
                        mrz.date_of_birth,
                        mrz.date_of_expiry
                );

                // Perform PACE authentication
                service.doPACE(
                        paceKey,
                        paceInfo.getObjectIdentifier(),
                        PACEInfo.toParameterSpec(paceInfo.getParameterId()),
                        paceInfo.getParameterId()
                );

                NfcLogger.logAuthAttempt(AUTH_METHOD_PACE, paceOid, "success");
                return AuthResult.paceSuccess(paceOid);

            } catch (CardServiceException e) {
                String swCode = extractSwCode(e);
                String errorMessage = e.getMessage();

                // Check if this is a "PACE not supported" error (fallback to BAC)
                if (isPaceNotSupportedError(swCode, errorMessage)) {
                    NfcLogger.logAuthAttempt(AUTH_METHOD_PACE, paceOid, "fallback_to_bac");
                    return performBacAuthentication(service, mrz);
                }

                // PACE failed with protocol/crypto error - no fallback
                NfcLogger.logAuthAttempt(AUTH_METHOD_PACE, paceOid, "failed");
                NfcLogger.logError(NfcReadStatus.PACE_FAILED, "pace_authentication", swCode, e);

                return AuthResult.failure(NfcReadResult.paceError(
                        NfcReadStatus.PACE_FAILED,
                        "pace_authentication",
                        swCode,
                        "PACE authentication failed: " + errorMessage,
                        paceOid
                ));

            } catch (Exception e) {
                String swCode = extractSwCode(e);

                // For generic exceptions, check if it's a "not supported" type
                if (isPaceNotSupportedError(swCode, e.getMessage())) {
                    NfcLogger.logAuthAttempt(AUTH_METHOD_PACE, paceOid, "fallback_to_bac");
                    return performBacAuthentication(service, mrz);
                }

                // PACE failed - return error
                NfcLogger.logAuthAttempt(AUTH_METHOD_PACE, paceOid, "failed");
                NfcLogger.logError(NfcReadStatus.PACE_FAILED, "pace_authentication", swCode, e);

                return AuthResult.failure(NfcReadResult.paceError(
                        NfcReadStatus.PACE_FAILED,
                        "pace_authentication",
                        swCode,
                        "PACE authentication failed: " + e.getMessage(),
                        paceOid
                ));
            }
        }

        // No PACE info found - use BAC
        NfcLogger.logAuthAttempt(AUTH_METHOD_PACE, null, "not_available");
        return performBacAuthentication(service, mrz);
    }

    /**
     * Performs BAC (Basic Access Control) authentication.
     */
    private static AuthResult performBacAuthentication(PassportService service, Models.MRZKeys mrz) {
        NfcLogger.logStage("bac_authentication");
        NfcLogger.logAuthAttempt(AUTH_METHOD_BAC, null, "attempting");

        BACKey bacKey = new BACKey(
                mrz.document_number,
                mrz.date_of_birth,
                mrz.date_of_expiry
        );

        try {
            service.doBAC(bacKey);
            NfcLogger.logAuthAttempt(AUTH_METHOD_BAC, null, "success");
            return AuthResult.bacSuccess();

        } catch (Exception e) {
            String swCode = extractSwCode(e);
            String errorMessage = e.getMessage();

            // Mask document number for logging
            String docNumMasked = mrz.document_number != null && mrz.document_number.length() > 3
                    ? mrz.document_number.substring(0, 3) + "***"
                    : "***";

            NfcLogger.logAuthAttempt(AUTH_METHOD_BAC, null, "failed");
            NfcLogger.logError(NfcReadStatus.BAC_FAILED, "bac_authentication", swCode, e);

            return AuthResult.failure(NfcReadResult.error(
                    NfcReadStatus.BAC_FAILED,
                    "bac_authentication",
                    swCode,
                    "BAC authentication failed [doc=" + docNumMasked + "]: " + errorMessage
            ));
        }
    }

    /**
     * Determines if the error indicates PACE is not supported.
     * These errors allow fallback to BAC.
     *
     * @return true if PACE is not supported and BAC fallback should be attempted
     */
    private static boolean isPaceNotSupportedError(String swCode, String errorMessage) {
        if (swCode != null) {
            // File not found, function not supported, wrong parameters, instruction not supported
            if (swCode.equalsIgnoreCase(SW_FILE_NOT_FOUND) ||
                swCode.equalsIgnoreCase(SW_FUNCTION_NOT_SUPPORTED) ||
                swCode.equalsIgnoreCase(SW_WRONG_P1P2) ||
                swCode.equalsIgnoreCase(SW_INS_NOT_SUPPORTED)) {
                return true;
            }
        }

        if (errorMessage != null) {
            String msg = errorMessage.toLowerCase();
            // Check for common "not supported" indicators
            if (msg.contains("file not found") ||
                msg.contains("not supported") ||
                msg.contains("not available") ||
                msg.contains("no such file")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Extracts SW code from exception message.
     * Returns null if no SW code is found.
     */
    static String extractSwCode(Throwable e) {
        if (e == null) {
            return null;
        }

        String message = e.getMessage();
        if (message == null) {
            // Check cause
            Throwable cause = e.getCause();
            if (cause != null && cause.getMessage() != null) {
                message = cause.getMessage();
            } else {
                return null;
            }
        }

        Matcher matcher = SW_CODE_PATTERN.matcher(message);
        if (matcher.find()) {
            // Return just the hex digits without 0x prefix
            return matcher.group(2).toUpperCase();
        }

        return null;
    }

    private static byte[] readAllBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void closeQuietly(PassportService service) {
        if (service != null) {
            try {
                service.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void closeQuietly(IsoDep isoDep) {
        if (isoDep != null) {
            try {
                isoDep.close();
            } catch (Exception ignored) {
            }
        }
    }

    private NfcPassportReader() {}
}
