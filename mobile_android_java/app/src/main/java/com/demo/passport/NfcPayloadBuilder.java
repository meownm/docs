package com.demo.passport;

import com.google.gson.JsonObject;

import java.util.Base64;
import java.util.Map;

final class NfcPayloadBuilder {
    static final int MIN_FACE_IMAGE_BYTES = 1024;

    static JsonObject build(Models.NfcResult result) {
        if (result == null) {
            throw new IllegalArgumentException("NFC result is null");
        }
        if (result.passport == null || result.passport.isEmpty()) {
            throw new IllegalArgumentException("Passport data is missing");
        }
        if (result.faceImageJpeg == null || result.faceImageJpeg.length < MIN_FACE_IMAGE_BYTES) {
            throw new IllegalArgumentException("Face image is missing or too small");
        }
        JsonObject passportJson = new JsonObject();
        int passportCount = 0;
        for (Map.Entry<String, String> entry : result.passport.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String value = entry.getValue() == null ? "" : entry.getValue();
            passportJson.addProperty(entry.getKey(), value);
            passportCount++;
        }
        if (passportCount == 0) {
            throw new IllegalArgumentException("Passport data is missing");
        }
        String faceBase64 = Base64.getEncoder().encodeToString(result.faceImageJpeg);
        JsonObject payload = new JsonObject();
        payload.add("passport", passportJson);
        payload.addProperty("face_image_b64", faceBase64);
        return payload;
    }

    private NfcPayloadBuilder() {}
}
