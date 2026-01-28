package com.demo.passport;

import com.google.gson.JsonObject;

import java.util.Base64;
import java.util.Map;

final class NfcPayloadBuilder {
    static final int MIN_FACE_IMAGE_BYTES = 1024;
    static final int MIN_DG1_BYTES = 10;

    /**
     * Build payload with raw DG1/DG2 bytes for server-side decoding.
     * This is the preferred method for server-side parsing architecture.
     *
     * @param result Raw NFC data from the chip
     * @return JSON payload with base64-encoded raw bytes
     */
    static JsonObject buildRaw(Models.NfcRawResult result) {
        if (result == null) {
            throw new IllegalArgumentException("NFC raw result is null");
        }
        if (result.dg1Raw == null || result.dg1Raw.length < MIN_DG1_BYTES) {
            throw new IllegalArgumentException("DG1 raw data is missing or too small");
        }
        if (result.dg2Raw == null || result.dg2Raw.length < MIN_FACE_IMAGE_BYTES) {
            throw new IllegalArgumentException("DG2 raw data is missing or too small");
        }
        if (result.mrzKeys == null) {
            throw new IllegalArgumentException("MRZ keys are required for verification");
        }

        JsonObject payload = new JsonObject();

        // Raw data groups as base64
        String dg1Base64 = Base64.getEncoder().encodeToString(result.dg1Raw);
        String dg2Base64 = Base64.getEncoder().encodeToString(result.dg2Raw);

        payload.addProperty("dg1_raw_b64", dg1Base64);
        payload.addProperty("dg2_raw_b64", dg2Base64);

        // Include MRZ keys for server-side verification
        JsonObject mrzJson = new JsonObject();
        mrzJson.addProperty("document_number", result.mrzKeys.document_number);
        mrzJson.addProperty("date_of_birth", result.mrzKeys.date_of_birth);
        mrzJson.addProperty("date_of_expiry", result.mrzKeys.date_of_expiry);
        payload.add("mrz_keys", mrzJson);

        // Mark as raw format for server routing
        payload.addProperty("format", "raw");

        return payload;
    }

    /**
     * Build payload with pre-parsed passport data (legacy method).
     * @deprecated Use {@link #buildRaw(Models.NfcRawResult)} for server-side decoding.
     */
    @Deprecated
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
