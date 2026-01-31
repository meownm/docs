package com.demo.passport;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Diagnostic data model for NFC reading results.
 * Contains all raw data without normalization or transformation.
 *
 * Sections order:
 * 1. NFC Session
 * 2. Access & MRZ Keys
 * 3. DG1 (MRZ Data)
 * 4. DG2 (Face Image)
 * 5. Other Data Groups
 * 6. Errors & Warnings
 */
public final class NfcDiagnosticData implements Serializable {
    private static final long serialVersionUID = 1L;

    // === NFC Session ===
    @NonNull
    public String status = "";
    @Nullable
    public String accessMethodUsed;
    public boolean paceSupported;
    public boolean bacSupported;
    @Nullable
    public String documentType;
    @Nullable
    public String issuingCountry;
    @Nullable
    public String chipInfo;
    public long readTimeMs;
    @Nullable
    public String ldsVersion;

    // === Access & MRZ Keys ===
    @Nullable
    public String documentNumberMasked;
    @Nullable
    public String dateOfBirth;
    @Nullable
    public String dateOfExpiry;
    @Nullable
    public String mrzKeyHash;

    // === DG1 (MRZ Data) ===
    @Nullable
    public String dg1DocumentNumber;
    @Nullable
    public String dg1IssuingState;
    @Nullable
    public String dg1Nationality;
    @Nullable
    public String dg1Surname;
    @Nullable
    public String dg1GivenNames;
    @Nullable
    public String dg1DateOfBirth;
    @Nullable
    public String dg1Sex;
    @Nullable
    public String dg1DateOfExpiry;
    @Nullable
    public String dg1OptionalDataRaw;
    public int dg1RawSize;

    // === DG2 (Face Image) ===
    public boolean dg2Present;
    @Nullable
    public String dg2ImageFormat;
    public int dg2WidthPx;
    public int dg2HeightPx;
    public int dg2SizeBytes;
    @Nullable
    public transient byte[] dg2RawBytes;  // Not serialized, passed via Intent extras

    // === Other Data Groups (presence only) ===
    public boolean dg3Present;  // Fingerprint - never read
    public boolean dg11Present;
    public boolean dg12Present;
    public boolean dg14Present;

    // === Errors & Warnings ===
    @NonNull
    public List<DiagnosticError> errors = new ArrayList<>();

    /**
     * Represents an error or warning that occurred during NFC reading.
     */
    public static final class DiagnosticError implements Serializable {
        private static final long serialVersionUID = 1L;

        @NonNull
        public String stage = "";
        @Nullable
        public String errorCode;
        @Nullable
        public String errorMessage;
        @Nullable
        public String sw;

        public DiagnosticError() {}

        public DiagnosticError(@NonNull String stage, @Nullable String errorCode,
                               @Nullable String errorMessage, @Nullable String sw) {
            this.stage = stage;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.sw = sw;
        }
    }

    /**
     * Creates diagnostic data from an NfcReadResult.
     * This is the primary factory method after NFC reading completes.
     */
    public static NfcDiagnosticData fromNfcReadResult(
            @NonNull NfcReadResult result,
            @Nullable Models.MRZKeys mrzKeys,
            long readTimeMs
    ) {
        NfcDiagnosticData data = new NfcDiagnosticData();
        data.readTimeMs = readTimeMs;

        // NFC Session
        data.status = result.status.name();
        data.accessMethodUsed = "BAC";  // Current implementation only supports BAC
        data.paceSupported = false;     // PACE not implemented
        data.bacSupported = true;

        // Access & MRZ Keys
        if (mrzKeys != null) {
            data.documentNumberMasked = maskDocumentNumber(mrzKeys.document_number);
            data.dateOfBirth = mrzKeys.date_of_birth;
            data.dateOfExpiry = mrzKeys.date_of_expiry;
            data.mrzKeyHash = computeMrzKeyHash(mrzKeys);
        }

        // Handle successful read with data
        if (result.isSuccess() && result.data != null) {
            Models.NfcRawResult rawResult = result.data;

            // DG1 processing
            if (rawResult.dg1Raw != null && rawResult.dg1Raw.length > 0) {
                data.dg1RawSize = rawResult.dg1Raw.length;
                parseDg1(data, rawResult.dg1Raw);
            }

            // DG2 processing
            if (rawResult.dg2Raw != null && rawResult.dg2Raw.length > 0) {
                data.dg2Present = true;
                data.dg2SizeBytes = rawResult.dg2Raw.length;
                data.dg2RawBytes = rawResult.dg2Raw;
                parseDg2Metadata(data, rawResult.dg2Raw);
            }
        }

        // Handle errors
        if (!result.isSuccess()) {
            DiagnosticError error = new DiagnosticError();
            error.stage = result.errorStage != null ? result.errorStage : "unknown";
            error.errorCode = result.status.name();
            error.errorMessage = result.technicalMessage;
            error.sw = result.swCode;
            data.errors.add(error);
        }

        return data;
    }

    /**
     * Mask document number for display (show first 3 chars, mask the rest).
     */
    private static String maskDocumentNumber(@Nullable String documentNumber) {
        if (documentNumber == null || documentNumber.length() <= 3) {
            return documentNumber != null ? documentNumber : "";
        }
        StringBuilder masked = new StringBuilder(documentNumber.substring(0, 3));
        for (int i = 3; i < documentNumber.length(); i++) {
            masked.append('*');
        }
        return masked.toString();
    }

    /**
     * Compute a short hash of MRZ keys for verification purposes.
     */
    private static String computeMrzKeyHash(@NonNull Models.MRZKeys keys) {
        try {
            String combined = String.format("%s|%s|%s",
                    keys.document_number != null ? keys.document_number : "",
                    keys.date_of_birth != null ? keys.date_of_birth : "",
                    keys.date_of_expiry != null ? keys.date_of_expiry : "");
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(combined.getBytes(StandardCharsets.UTF_8));
            // Return first 8 bytes as hex
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < hash.length; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "error";
        }
    }

    /**
     * Parse DG1 (MRZ) data according to ICAO Doc 9303 format.
     * Extracts fields without normalization.
     */
    private static void parseDg1(@NonNull NfcDiagnosticData data, @NonNull byte[] dg1Raw) {
        try {
            // DG1 format: TAG (0x61) + LEN + TAG (0x5F1F) + LEN + MRZ_DATA
            // Skip ASN.1 headers to get to the raw MRZ string
            int offset = 0;

            // Find the MRZ content
            if (dg1Raw.length < 5) {
                return;
            }

            // Skip outer tag 0x61
            if (dg1Raw[offset] == 0x61) {
                offset++;
                offset += getLengthBytes(dg1Raw, offset);
            }

            // Skip inner tag 0x5F1F
            if (offset + 2 < dg1Raw.length &&
                dg1Raw[offset] == 0x5F && dg1Raw[offset + 1] == 0x1F) {
                offset += 2;
                offset += getLengthBytes(dg1Raw, offset);
            }

            // Remaining bytes are the MRZ
            if (offset >= dg1Raw.length) {
                return;
            }

            byte[] mrzBytes = new byte[dg1Raw.length - offset];
            System.arraycopy(dg1Raw, offset, mrzBytes, 0, mrzBytes.length);
            String mrz = new String(mrzBytes, StandardCharsets.US_ASCII).trim();

            // Determine MRZ format (TD1, TD2, or TD3/MRP)
            String[] lines = mrz.split("\\n");
            if (lines.length == 0) {
                // Try to parse as continuous string
                if (mrz.length() == 90) {
                    // TD3: 2 lines of 44 chars each (+ CR/LF = 90)
                    parseTd3Mrz(data, mrz.substring(0, 44), mrz.substring(44));
                } else if (mrz.length() == 88) {
                    // TD3 without separators
                    parseTd3Mrz(data, mrz.substring(0, 44), mrz.substring(44, 88));
                }
                return;
            }

            if (lines.length >= 3 && lines[0].length() == 30) {
                // TD1 (ID cards): 3 lines of 30 chars
                parseTd1Mrz(data, lines);
            } else if (lines.length >= 2 && lines[0].length() == 36) {
                // TD2: 2 lines of 36 chars
                parseTd2Mrz(data, lines);
            } else if (lines.length >= 2 && lines[0].length() >= 44) {
                // TD3 (Passports): 2 lines of 44 chars
                parseTd3Mrz(data, lines[0], lines[1]);
            }
        } catch (Exception e) {
            // Parsing failed, leave fields null
            DiagnosticError error = new DiagnosticError();
            error.stage = "dg1_parse";
            error.errorMessage = e.getMessage();
            data.errors.add(error);
        }
    }

    /**
     * Parse TD3 format MRZ (passport, 2 lines of 44 characters).
     */
    private static void parseTd3Mrz(@NonNull NfcDiagnosticData data,
                                     @NonNull String line1, @NonNull String line2) {
        // Line 1: P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<
        // Position: 0-1 = doc type, 2-4 = issuing state, 5-43 = name (surname<<given names)
        if (line1.length() >= 44) {
            data.documentType = line1.substring(0, 2).replace('<', ' ').trim();
            data.dg1IssuingState = line1.substring(2, 5).replace('<', ' ').trim();
            String name = line1.substring(5).replace('<', ' ');
            String[] nameParts = name.split("  +");  // Split on multiple spaces
            if (nameParts.length >= 1) {
                data.dg1Surname = nameParts[0].trim();
            }
            if (nameParts.length >= 2) {
                data.dg1GivenNames = nameParts[1].trim();
            }
        }

        // Line 2: L898902C36UTO7408122F1204159ZE184226B<<<<<10
        // Position: 0-8 = doc number, 9 = check, 10-12 = nationality, 13-18 = DOB, 19 = check,
        // 20 = sex, 21-26 = expiry, 27 = check, 28-41 = optional, 42 = check, 43 = overall check
        if (line2.length() >= 44) {
            data.dg1DocumentNumber = line2.substring(0, 9).replace('<', ' ').trim();
            data.dg1Nationality = line2.substring(10, 13).replace('<', ' ').trim();
            data.dg1DateOfBirth = line2.substring(13, 19);
            data.dg1Sex = line2.substring(20, 21);
            data.dg1DateOfExpiry = line2.substring(21, 27);
            data.dg1OptionalDataRaw = line2.substring(28, 42).replace('<', ' ').trim();

            // Also update issuing country from nationality if not set
            if (data.issuingCountry == null || data.issuingCountry.isEmpty()) {
                data.issuingCountry = data.dg1Nationality;
            }
        }
    }

    /**
     * Parse TD1 format MRZ (ID cards, 3 lines of 30 characters).
     */
    private static void parseTd1Mrz(@NonNull NfcDiagnosticData data, @NonNull String[] lines) {
        // Line 1: I<UTOD231458907<<<<<<<<<<<<<<<
        if (lines[0].length() >= 30) {
            data.documentType = lines[0].substring(0, 2).replace('<', ' ').trim();
            data.dg1IssuingState = lines[0].substring(2, 5).replace('<', ' ').trim();
            data.dg1DocumentNumber = lines[0].substring(5, 14).replace('<', ' ').trim();
            data.dg1OptionalDataRaw = lines[0].substring(15, 30).replace('<', ' ').trim();
        }

        // Line 2: 7408122F1204159UTO<<<<<<<<<<<1
        if (lines.length >= 2 && lines[1].length() >= 30) {
            data.dg1DateOfBirth = lines[1].substring(0, 6);
            data.dg1Sex = lines[1].substring(7, 8);
            data.dg1DateOfExpiry = lines[1].substring(8, 14);
            data.dg1Nationality = lines[1].substring(15, 18).replace('<', ' ').trim();
        }

        // Line 3: ERIKSSON<<ANNA<MARIA<<<<<<<<<<
        if (lines.length >= 3 && lines[2].length() >= 30) {
            String name = lines[2].replace('<', ' ');
            String[] nameParts = name.split("  +");
            if (nameParts.length >= 1) {
                data.dg1Surname = nameParts[0].trim();
            }
            if (nameParts.length >= 2) {
                data.dg1GivenNames = nameParts[1].trim();
            }
        }

        if (data.issuingCountry == null || data.issuingCountry.isEmpty()) {
            data.issuingCountry = data.dg1IssuingState;
        }
    }

    /**
     * Parse TD2 format MRZ (travel documents, 2 lines of 36 characters).
     */
    private static void parseTd2Mrz(@NonNull NfcDiagnosticData data, @NonNull String[] lines) {
        // Line 1: I<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<
        if (lines[0].length() >= 36) {
            data.documentType = lines[0].substring(0, 2).replace('<', ' ').trim();
            data.dg1IssuingState = lines[0].substring(2, 5).replace('<', ' ').trim();
            String name = lines[0].substring(5).replace('<', ' ');
            String[] nameParts = name.split("  +");
            if (nameParts.length >= 1) {
                data.dg1Surname = nameParts[0].trim();
            }
            if (nameParts.length >= 2) {
                data.dg1GivenNames = nameParts[1].trim();
            }
        }

        // Line 2: D231458907UTO7408122F1204159<<<<<<1
        if (lines.length >= 2 && lines[1].length() >= 36) {
            data.dg1DocumentNumber = lines[1].substring(0, 9).replace('<', ' ').trim();
            data.dg1Nationality = lines[1].substring(10, 13).replace('<', ' ').trim();
            data.dg1DateOfBirth = lines[1].substring(13, 19);
            data.dg1Sex = lines[1].substring(19, 20);
            data.dg1DateOfExpiry = lines[1].substring(20, 26);
            data.dg1OptionalDataRaw = lines[1].substring(27, 35).replace('<', ' ').trim();
        }

        if (data.issuingCountry == null || data.issuingCountry.isEmpty()) {
            data.issuingCountry = data.dg1IssuingState;
        }
    }

    /**
     * Get the number of bytes used by ASN.1 length encoding.
     */
    private static int getLengthBytes(byte[] data, int offset) {
        if (offset >= data.length) {
            return 0;
        }
        int first = data[offset] & 0xFF;
        if (first < 0x80) {
            return 1;  // Short form
        }
        int numBytes = first & 0x7F;
        return 1 + numBytes;  // Long form
    }

    /**
     * Parse DG2 metadata (image dimensions, format).
     * Attempts to detect JPEG/JPEG2000 format and extract dimensions.
     */
    private static void parseDg2Metadata(@NonNull NfcDiagnosticData data, @NonNull byte[] dg2Raw) {
        // Try to find JPEG or JPEG2000 image data within DG2
        // DG2 contains biometric template with face image

        // Look for JPEG SOI marker (0xFF 0xD8)
        int jpegOffset = findBytes(dg2Raw, new byte[]{(byte) 0xFF, (byte) 0xD8});
        if (jpegOffset >= 0) {
            data.dg2ImageFormat = "JPEG";
            // Find JPEG dimensions from SOF0/SOF2 markers
            parseJpegDimensions(data, dg2Raw, jpegOffset);
            return;
        }

        // Look for JPEG2000 signature (0x00 0x00 0x00 0x0C 0x6A 0x50)
        int jp2Offset = findBytes(dg2Raw, new byte[]{0x00, 0x00, 0x00, 0x0C, 0x6A, 0x50});
        if (jp2Offset >= 0) {
            data.dg2ImageFormat = "JPEG2000";
            // JPEG2000 dimension parsing is complex, skip for now
            return;
        }

        // Unknown format
        data.dg2ImageFormat = "Unknown";
    }

    /**
     * Parse JPEG dimensions from SOF marker.
     */
    private static void parseJpegDimensions(@NonNull NfcDiagnosticData data,
                                            @NonNull byte[] raw, int offset) {
        // Search for SOF0 (0xFF 0xC0) or SOF2 (0xFF 0xC2) markers
        for (int i = offset; i < raw.length - 9; i++) {
            if (raw[i] == (byte) 0xFF) {
                int marker = raw[i + 1] & 0xFF;
                // SOF0, SOF1, SOF2 markers contain dimensions
                if (marker == 0xC0 || marker == 0xC1 || marker == 0xC2) {
                    // SOF format: FF CX LEN PRECISION HEIGHT WIDTH ...
                    int height = ((raw[i + 5] & 0xFF) << 8) | (raw[i + 6] & 0xFF);
                    int width = ((raw[i + 7] & 0xFF) << 8) | (raw[i + 8] & 0xFF);
                    data.dg2HeightPx = height;
                    data.dg2WidthPx = width;
                    return;
                }
            }
        }
    }

    /**
     * Find byte sequence in array.
     */
    private static int findBytes(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    /**
     * Try to decode face image from DG2 raw bytes.
     * Returns null if decoding fails.
     */
    @Nullable
    public Bitmap decodeFaceImage() {
        if (dg2RawBytes == null || dg2RawBytes.length == 0) {
            return null;
        }

        // Find JPEG data within DG2
        int jpegOffset = findBytes(dg2RawBytes, new byte[]{(byte) 0xFF, (byte) 0xD8});
        if (jpegOffset >= 0) {
            // Find JPEG EOI marker (0xFF 0xD9)
            int jpegEnd = dg2RawBytes.length;
            for (int i = jpegOffset + 2; i < dg2RawBytes.length - 1; i++) {
                if (dg2RawBytes[i] == (byte) 0xFF && dg2RawBytes[i + 1] == (byte) 0xD9) {
                    jpegEnd = i + 2;
                    break;
                }
            }

            try {
                return BitmapFactory.decodeByteArray(
                        dg2RawBytes, jpegOffset, jpegEnd - jpegOffset);
            } catch (Exception e) {
                return null;
            }
        }

        // Try decoding entire DG2 as image (fallback)
        try {
            return BitmapFactory.decodeByteArray(dg2RawBytes, 0, dg2RawBytes.length);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Add an error to the diagnostic data.
     */
    public void addError(@NonNull String stage, @Nullable String errorCode,
                         @Nullable String errorMessage, @Nullable String sw) {
        errors.add(new DiagnosticError(stage, errorCode, errorMessage, sw));
    }

    /**
     * Check if there are any errors.
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Format read time for display.
     */
    @NonNull
    public String getFormattedReadTime() {
        if (readTimeMs < 1000) {
            return readTimeMs + " ms";
        }
        return String.format(Locale.US, "%.2f s", readTimeMs / 1000.0);
    }
}
