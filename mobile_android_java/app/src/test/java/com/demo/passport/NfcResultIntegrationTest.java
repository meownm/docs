package com.demo.passport;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.HashMap;

public class NfcResultIntegrationTest {

    @Test
    public void validateThenBuildPayload_succeedsForChipData() {
        Models.NfcResult result = new Models.NfcResult();
        result.passport = new HashMap<>();
        result.passport.put("doc", "123");
        result.faceImageJpeg = new byte[NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES];

        String validationError = MainActivity.validateNfcResult(result);
        JsonObject payload = MainActivity.tryBuildNfcPayload(result, new StringBuilder());

        assertNull(validationError);
        assertNotNull(payload);
    }

    @Test
    public void validateThenBuildPayload_failsWhenPassportMissing() {
        Models.NfcResult result = new Models.NfcResult();
        result.faceImageJpeg = new byte[NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES];

        String validationError = MainActivity.validateNfcResult(result);
        JsonObject payload = MainActivity.tryBuildNfcPayload(result, new StringBuilder());

        assertNotNull(validationError);
        assertNull(payload);
    }

    @Test
    public void validateThenBuildPayload_failsWhenFaceMissing() {
        Models.NfcResult result = new Models.NfcResult();
        result.passport = new HashMap<>();
        result.passport.put("doc", "123");

        String validationError = MainActivity.validateNfcResult(result);
        JsonObject payload = MainActivity.tryBuildNfcPayload(result, new StringBuilder());

        assertNotNull(validationError);
        assertNull(payload);
    }
}
