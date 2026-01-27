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

    public static final class NfcScanResponse {
        public String scan_id;
        public String face_image_url;
        public com.google.gson.JsonObject passport;
    }
    private Models() {}
}
