package com.demo.passport;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonObject;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;

public class NfcPayloadBuilderTest {

    @Test
    public void build_includesPassportAndFaceImageBase64() {
        Models.NfcResult result = new Models.NfcResult();
        result.passport = new HashMap<>();
        result.passport.put("doc", "123");
        result.faceImageJpeg = new byte[NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES];

        JsonObject payload = NfcPayloadBuilder.build(result);

        JsonObject passport = payload.getAsJsonObject("passport");
        assertEquals("123", passport.get("doc").getAsString());
        assertEquals(Base64.getEncoder().encodeToString(result.faceImageJpeg),
                payload.get("face_image_b64").getAsString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void build_throwsWhenResultMissing() {
        NfcPayloadBuilder.build(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void build_throwsWhenPassportMissing() {
        Models.NfcResult result = new Models.NfcResult();
        result.faceImageJpeg = new byte[NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES];
        NfcPayloadBuilder.build(result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void build_throwsWhenFaceImageMissing() {
        Models.NfcResult result = new Models.NfcResult();
        result.passport = new HashMap<>();
        result.passport.put("doc", "123");
        NfcPayloadBuilder.build(result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void build_throwsWhenFaceImageEmpty() {
        Models.NfcResult result = new Models.NfcResult();
        result.passport = new HashMap<>();
        result.passport.put("doc", "123");
        result.faceImageJpeg = new byte[0];
        NfcPayloadBuilder.build(result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void build_throwsWhenFaceImageTooSmall() {
        Models.NfcResult result = new Models.NfcResult();
        result.passport = new HashMap<>();
        result.passport.put("doc", "123");
        result.faceImageJpeg = "tiny".getBytes(StandardCharsets.UTF_8);
        NfcPayloadBuilder.build(result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void build_throwsWhenPassportEmpty() {
        Models.NfcResult result = new Models.NfcResult();
        result.passport = new HashMap<>();
        result.faceImageJpeg = new byte[NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES];
        NfcPayloadBuilder.build(result);
    }
}
