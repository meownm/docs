package com.demo.passport;

import android.nfc.Tag;
import android.nfc.tech.IsoDep;

import net.sf.scuba.smartcards.CardService;

import org.jmrtd.BACKey;
import org.jmrtd.PassportService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NFC eMRTD reader with structured error handling.
 * Returns NfcReadResult instead of throwing exceptions.
 *
 * Key improvements:
 * - Canonical classification of all error scenarios
 * - Detection of PACE requirement (SW=0x6985)
 * - Structured logging without MRZ exposure
 * - Backend calls only allowed on SUCCESS
 */
public final class NfcPassportReader {
    static final int NFC_TIMEOUT_MS = 45000;

    /**
     * Pattern to extract SW code from error messages.
     * Matches formats like "SW = 0x6985", "SW=6985", "sw: 0x6985"
     */
    private static final Pattern SW_CODE_PATTERN = Pattern.compile(
            "SW\\s*[=:]\\s*(0x)?([0-9A-Fa-f]{4})",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Pattern to detect PACE requirement in error messages.
     */
    private static final Pattern PACE_INDICATOR_PATTERN = Pattern.compile(
            "CONDITIONS\\s+NOT\\s+SATISFIED|" +
            "expected\\s+length:\\s*40\\s*\\+\\s*2,\\s*actual\\s+length:\\s*2",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * SW code indicating CONDITIONS NOT SATISFIED (PACE required).
     */
    private static final String SW_PACE_REQUIRED = "6985";

    /**
     * Reads raw DG1 and DG2 bytes from the passport chip.
     * Returns a structured result with status and optional data.
     *
     * @param tag NFC tag from the chip
     * @param mrz MRZ keys for BAC authentication
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

            // Perform BAC authentication
            NfcLogger.logStage("bac_authentication");
            BACKey bacKey = new BACKey(
                    mrz.document_number,
                    mrz.date_of_birth,
                    mrz.date_of_expiry
            );
            try {
                service.doBAC(bacKey);
            } catch (Exception e) {
                return handleBacError(e, mrz);
            }

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

            NfcReadResult result = NfcReadResult.success(data);
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
     * Handles BAC authentication errors with proper PACE detection.
     *
     * CRITICAL: SW=0x6985 (CONDITIONS NOT SATISFIED) indicates PACE is required.
     * This must be classified as PACE_REQUIRED, NOT as BAC_FAILED or UNKNOWN_ERROR.
     */
    private static NfcReadResult handleBacError(Exception e, Models.MRZKeys mrz) {
        String swCode = extractSwCode(e);
        String errorMessage = e.getMessage();

        // Check for PACE requirement indicators
        boolean isPaceRequired = isPaceRequiredError(swCode, errorMessage);

        if (isPaceRequired) {
            NfcLogger.logError(NfcReadStatus.PACE_REQUIRED, "bac_authentication", swCode, e);
            return NfcReadResult.error(
                    NfcReadStatus.PACE_REQUIRED,
                    "bac_authentication",
                    swCode,
                    "Document requires PACE authentication (SW=" + (swCode != null ? swCode : "unknown") + ")"
            );
        }

        // Regular BAC failure (wrong MRZ data, etc.)
        // Include masked document number for debugging (first 3 chars only)
        String docNumMasked = mrz.document_number != null && mrz.document_number.length() > 3
                ? mrz.document_number.substring(0, 3) + "***"
                : "***";

        NfcLogger.logError(NfcReadStatus.BAC_FAILED, "bac_authentication", swCode, e);
        return NfcReadResult.error(
                NfcReadStatus.BAC_FAILED,
                "bac_authentication",
                swCode,
                "BAC authentication failed [doc=" + docNumMasked + "]: " + errorMessage
        );
    }

    /**
     * Determines if the error indicates PACE is required.
     *
     * PACE requirement is indicated by:
     * 1. SW = 0x6985 (CONDITIONS NOT SATISFIED)
     * 2. Error message containing "CONDITIONS NOT SATISFIED"
     * 3. Error message containing "expected length: 40 + 2, actual length: 2"
     */
    static boolean isPaceRequiredError(String swCode, String errorMessage) {
        // Check SW code
        if (swCode != null && swCode.equalsIgnoreCase(SW_PACE_REQUIRED)) {
            return true;
        }

        // Check error message patterns
        if (errorMessage != null) {
            Matcher matcher = PACE_INDICATOR_PATTERN.matcher(errorMessage);
            return matcher.find();
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
