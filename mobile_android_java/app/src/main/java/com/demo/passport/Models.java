package com.demo.passport;

import java.util.Map;

public final class Models {

    public static final class ErrorInfo {
        public String error_code;
        public String message;
    }

    public static final class MRZKeys {
        public String document_number;
        public String date_of_birth;
        public String date_of_expiry;
    }

    public static final class RecognizeResponse {
        public String request_id;
        public MRZKeys mrz;
        public ErrorInfo error;
        public Object raw;
    }

    public static final class NFCPayload {
        public Map<String, Object> passport;
        public String face_image_b64;
    }

    public static final class NfcResult {
        public Map<String, Object> passport;
        public byte[] faceImageJpeg;
    }

    private Models() {}
}
