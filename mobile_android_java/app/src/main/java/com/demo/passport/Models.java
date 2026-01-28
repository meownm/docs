package com.demo.passport;

public final class Models {
    public static final class MRZKeys {
        public String document_number;
        public String date_of_birth;
        public String date_of_expiry;
    }

    public static final class NfcResult {
        public java.util.Map<String, String> passport;
        public byte[] faceImageJpeg;
    }

    /**
     * Raw NFC data for server-side decoding.
     * Contains raw DG1/DG2 bytes without client-side parsing.
     */
    public static final class NfcRawResult {
        /** Raw DG1 (MRZ) bytes from the chip */
        public byte[] dg1Raw;
        /** Raw DG2 (Face image) bytes from the chip */
        public byte[] dg2Raw;
        /** MRZ keys used for BAC authentication (needed for verification) */
        public MRZKeys mrzKeys;
    }

    public static final class NfcScanResponse {
        public String scan_id;
        public String face_image_url;
        public com.google.gson.JsonObject passport;
    }
    private Models() {}
}
