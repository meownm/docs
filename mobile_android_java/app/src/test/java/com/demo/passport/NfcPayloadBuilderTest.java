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

    // Tests for buildRaw (server-side decoding)

    @Test
    public void buildRaw_includesDg1AndDg2Base64() {
        Models.NfcRawResult result = new Models.NfcRawResult();
        result.dg1Raw = new byte[NfcPayloadBuilder.MIN_DG1_BYTES];
        result.dg2Raw = new byte[NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES];
        result.mrzKeys = new Models.MRZKeys();
        result.mrzKeys.document_number = "AB123456";
        result.mrzKeys.date_of_birth = "900101";
        result.mrzKeys.date_of_expiry = "300101";

        JsonObject payload = NfcPayloadBuilder.buildRaw(result);

        assertEquals(Base64.getEncoder().encodeToString(result.dg1Raw),
                payload.get("dg1_raw_b64").getAsString());
        assertEquals(Base64.getEncoder().encodeToString(result.dg2Raw),
                payload.get("dg2_raw_b64").getAsString());
        assertEquals("raw", payload.get("format").getAsString());
    }

    @Test
    public void buildRaw_includesMrzKeys() {
        Models.NfcRawResult result = new Models.NfcRawResult();
        result.dg1Raw = new byte[NfcPayloadBuilder.MIN_DG1_BYTES];
        result.dg2Raw = new byte[NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES];
        result.mrzKeys = new Models.MRZKeys();
        result.mrzKeys.document_number = "AB123456";
        result.mrzKeys.date_of_birth = "900101";
        result.mrzKeys.date_of_expiry = "300101";

        JsonObject payload = NfcPayloadBuilder.buildRaw(result);

        JsonObject mrzKeys = payload.getAsJsonObject("mrz_keys");
        assertEquals("AB123456", mrzKeys.get("document_number").getAsString());
        assertEquals("900101", mrzKeys.get("date_of_birth").getAsString());
        assertEquals("300101", mrzKeys.get("date_of_expiry").getAsString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildRaw_throwsWhenResultMissing() {
        NfcPayloadBuilder.buildRaw(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildRaw_throwsWhenDg1Missing() {
        Models.NfcRawResult result = new Models.NfcRawResult();
        result.dg2Raw = new byte[NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES];
        result.mrzKeys = new Models.MRZKeys();
        result.mrzKeys.document_number = "AB123456";
        result.mrzKeys.date_of_birth = "900101";
        result.mrzKeys.date_of_expiry = "300101";
        NfcPayloadBuilder.buildRaw(result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildRaw_throwsWhenDg1TooSmall() {
        Models.NfcRawResult result = new Models.NfcRawResult();
        result.dg1Raw = new byte[NfcPayloadBuilder.MIN_DG1_BYTES - 1];
        result.dg2Raw = new byte[NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES];
        result.mrzKeys = new Models.MRZKeys();
        result.mrzKeys.document_number = "AB123456";
        result.mrzKeys.date_of_birth = "900101";
        result.mrzKeys.date_of_expiry = "300101";
        NfcPayloadBuilder.buildRaw(result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildRaw_throwsWhenDg2Missing() {
        Models.NfcRawResult result = new Models.NfcRawResult();
        result.dg1Raw = new byte[NfcPayloadBuilder.MIN_DG1_BYTES];
        result.mrzKeys = new Models.MRZKeys();
        result.mrzKeys.document_number = "AB123456";
        result.mrzKeys.date_of_birth = "900101";
        result.mrzKeys.date_of_expiry = "300101";
        NfcPayloadBuilder.buildRaw(result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildRaw_throwsWhenDg2TooSmall() {
        Models.NfcRawResult result = new Models.NfcRawResult();
        result.dg1Raw = new byte[NfcPayloadBuilder.MIN_DG1_BYTES];
        result.dg2Raw = new byte[NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES - 1];
        result.mrzKeys = new Models.MRZKeys();
        result.mrzKeys.document_number = "AB123456";
        result.mrzKeys.date_of_birth = "900101";
        result.mrzKeys.date_of_expiry = "300101";
        NfcPayloadBuilder.buildRaw(result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildRaw_throwsWhenMrzKeysMissing() {
        Models.NfcRawResult result = new Models.NfcRawResult();
        result.dg1Raw = new byte[NfcPayloadBuilder.MIN_DG1_BYTES];
        result.dg2Raw = new byte[NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES];
        NfcPayloadBuilder.buildRaw(result);
    }
}
