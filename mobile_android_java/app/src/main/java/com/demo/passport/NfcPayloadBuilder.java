package com.demo.passport;

import com.google.gson.JsonObject;

import java.util.Base64;
import java.util.Map;

final class NfcPayloadBuilder {

    static JsonObject build(Models.NfcResult result) {
        if (result == null) {
            throw new IllegalArgumentException("NFC result is null");
        }
        if (result.passport == null) {
            throw new IllegalArgumentException("Passport data is missing");
        }
        JsonObject passportJson = new JsonObject();
        for (Map.Entry<String, String> entry : result.passport.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String value = entry.getValue() == null ? "" : entry.getValue();
            passportJson.addProperty(entry.getKey(), value);
        }
        byte[] faceBytes = result.faceImageJpeg == null ? new byte[0] : result.faceImageJpeg;
        String faceBase64 = Base64.getEncoder().encodeToString(faceBytes);
        JsonObject payload = new JsonObject();
        payload.add("passport", passportJson);
        payload.addProperty("face_image_b64", faceBase64);
        return payload;
    }

    private NfcPayloadBuilder() {}
}
