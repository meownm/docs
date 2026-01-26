package com.demo.passport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.Test;

public class BackendApiTest {
    private final Gson gson = new Gson();

    @Test
    public void buildNfcPayloadJsonEncodesPassportAndImage() throws Exception {
        MRZKeys mrz = new MRZKeys();
        mrz.document_number = "123456789";
        mrz.date_of_birth = "1990-01-01";
        mrz.date_of_expiry = "2030-01-01";

        NfcResult result = NfcPassportReader.readPassport(null, mrz);
        result.faceImageJpeg = new byte[] {1, 2, 3};

        String json = BackendApi.buildNfcPayloadJson(result);
        JsonObject payload = gson.fromJson(json, JsonObject.class);

        assertNotNull(payload.getAsJsonObject("passport"));
        assertEquals("123456789", payload.getAsJsonObject("passport").get("document_number").getAsString());
        assertNotNull(payload.get("face_image_b64"));
    }

    @Test
    public void buildNfcPayloadJsonRejectsMissingImage() {
        NfcResult result = new NfcResult();
        result.passport = new java.util.HashMap<>();
        result.passport.put("document_number", "123");

        assertThrows(IllegalArgumentException.class, () -> BackendApi.buildNfcPayloadJson(result));
    }

    @Test
    public void buildNfcPayloadJsonRejectsMissingPassport() {
        NfcResult result = new NfcResult();
        result.faceImageJpeg = new byte[] {1};

        assertThrows(IllegalArgumentException.class, () -> BackendApi.buildNfcPayloadJson(result));
    }
}
