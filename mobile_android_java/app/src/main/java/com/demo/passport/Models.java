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
    private Models() {}
}
