package com.demo.passport;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.HashMap;

public class MainActivityTest {

    @Test
    public void buildFileProviderAuthority_buildsExpectedAuthority() {
        String authority = MainActivity.buildFileProviderAuthority("com.demo.passport");
        assertTrue(MainActivity.isFileProviderAuthorityValid("com.demo.passport", authority));
    }

    @Test
    public void buildFileProviderAuthority_rejectsMissingPackageName() {
        boolean threw = false;
        try {
            MainActivity.buildFileProviderAuthority("");
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        assertTrue(threw);
    }

    @Test
    public void buildFileProviderAuthority_rejectsNullPackageName() {
        boolean threw = false;
        try {
            MainActivity.buildFileProviderAuthority(null);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        assertTrue(threw);
    }

    @Test
    public void isFileProviderAuthorityValid_returnsFalseForMismatch() {
        assertFalse(MainActivity.isFileProviderAuthorityValid("com.demo.passport", "com.demo.other.fileprovider"));
    }

    @Test
    public void buildManualMrzKeys_returnsKeysWhenAllFieldsPresent() {
        Models.MRZKeys keys = MainActivity.buildManualMrzKeys("ab 123", "900101", "300101");
        assertNotNull(keys);
        assertTrue("AB123".equals(keys.document_number));
        assertTrue("900101".equals(keys.date_of_birth));
        assertTrue("300101".equals(keys.date_of_expiry));
    }

    @Test
    public void buildManualMrzKeys_returnsNullWhenAnyFieldMissing() {
        assertNull(MainActivity.buildManualMrzKeys("", "1990-01-01", "2030-01-01"));
        assertNull(MainActivity.buildManualMrzKeys("AB123", "", "2030-01-01"));
        assertNull(MainActivity.buildManualMrzKeys("AB123", "1990-01-01", ""));
    }

    @Test
    public void validateMrzInputs_returnsNullWhenValuesAreValid() {
        assertNull(MainActivity.validateMrzInputs("AB123", "900101", "300101"));
    }

    @Test
    public void normalizeDocumentNumber_removesSpacesAndUppercases() {
        assertTrue("AB123".equals(MainActivity.normalizeDocumentNumber(" ab 123 ")));
        assertNull(MainActivity.normalizeDocumentNumber("   "));
    }

    @Test
    public void validateMrzInputs_returnsErrorWhenValuesMissing() {
        assertNotNull(MainActivity.validateMrzInputs("", "900101", "300101"));
    }

    @Test
    public void validateMrzInputs_returnsErrorWhenBirthDateInvalid() {
        assertNotNull(MainActivity.validateMrzInputs("AB123", "1990-01-01", "300101"));
    }

    @Test
    public void validateMrzInputs_returnsErrorWhenExpiryDateInvalid() {
        assertNotNull(MainActivity.validateMrzInputs("AB123", "900101", "2030-01-01"));
    }

    @Test
    public void validateMrzInputs_returnsCombinedErrorWhenBothDatesInvalid() {
        assertNotNull(MainActivity.validateMrzInputs("AB123", "1990-01-01", "2030-01-01"));
    }

    @Test
    public void validateMrzKeys_returnsErrorWhenKeysMissing() {
        assertNotNull(MainActivity.validateMrzKeys(null));
    }

    @Test
    public void tryBuildNfcPayload_returnsPayloadForValidResult() {
        Models.NfcResult result = new Models.NfcResult();
        result.passport = new HashMap<>();
        result.passport.put("doc", "123");
        result.faceImageJpeg = new byte[NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES];

        StringBuilder error = new StringBuilder();
        JsonObject payload = MainActivity.tryBuildNfcPayload(result, error);

        assertNotNull(payload);
        assertTrue(error.length() == 0);
    }

    @Test
    public void validateNfcResult_returnsErrorWhenResultMissing() {
        assertNotNull(MainActivity.validateNfcResult(null));
    }

    @Test
    public void validateNfcResult_returnsErrorWhenPassportMissing() {
        Models.NfcResult result = new Models.NfcResult();
        assertNotNull(MainActivity.validateNfcResult(result));
    }

    @Test
    public void validateNfcResult_returnsErrorWhenPassportEmpty() {
        Models.NfcResult result = new Models.NfcResult();
        result.passport = new HashMap<>();
        assertNotNull(MainActivity.validateNfcResult(result));
    }

    @Test
    public void validateNfcResult_returnsErrorWhenFaceImageMissing() {
        Models.NfcResult result = new Models.NfcResult();
        result.passport = new HashMap<>();
        result.passport.put("doc", "123");
        assertNotNull(MainActivity.validateNfcResult(result));
    }

    @Test
    public void validateNfcResult_returnsErrorWhenFaceImageTooSmall() {
        Models.NfcResult result = new Models.NfcResult();
        result.passport = new HashMap<>();
        result.passport.put("doc", "123");
        result.faceImageJpeg = new byte[NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES - 1];
        assertNotNull(MainActivity.validateNfcResult(result));
    }

    @Test
    public void validateNfcResult_returnsNullWhenPassportPresent() {
        Models.NfcResult result = new Models.NfcResult();
        result.passport = new HashMap<>();
        result.passport.put("doc", "123");
        result.faceImageJpeg = new byte[NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES];
        assertNull(MainActivity.validateNfcResult(result));
    }

    @Test
    public void tryBuildNfcPayload_returnsNullAndErrorForMissingPassport() {
        Models.NfcResult result = new Models.NfcResult();
        result.faceImageJpeg = new byte[NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES];

        StringBuilder error = new StringBuilder();
        JsonObject payload = MainActivity.tryBuildNfcPayload(result, error);

        assertNull(payload);
        assertTrue(error.toString().contains("Passport data is missing"));
    }

    // Tests for server-side decoding (NfcRawResult)

    @Test
    public void validateNfcRawResult_returnsErrorWhenResultMissing() {
        assertNotNull(MainActivity.validateNfcRawResult(null));
    }

    @Test
    public void validateNfcRawResult_returnsErrorWhenDg1Missing() {
        Models.NfcRawResult result = new Models.NfcRawResult();
        result.dg2Raw = new byte[NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES];
        assertNotNull(MainActivity.validateNfcRawResult(result));
    }

    @Test
    public void validateNfcRawResult_returnsErrorWhenDg1TooSmall() {
        Models.NfcRawResult result = new Models.NfcRawResult();
        result.dg1Raw = new byte[NfcPayloadBuilder.MIN_DG1_BYTES - 1];
        result.dg2Raw = new byte[NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES];
        assertNotNull(MainActivity.validateNfcRawResult(result));
    }

    @Test
    public void validateNfcRawResult_returnsErrorWhenDg2Missing() {
        Models.NfcRawResult result = new Models.NfcRawResult();
        result.dg1Raw = new byte[NfcPayloadBuilder.MIN_DG1_BYTES];
        assertNotNull(MainActivity.validateNfcRawResult(result));
    }

    @Test
    public void validateNfcRawResult_returnsErrorWhenDg2TooSmall() {
        Models.NfcRawResult result = new Models.NfcRawResult();
        result.dg1Raw = new byte[NfcPayloadBuilder.MIN_DG1_BYTES];
        result.dg2Raw = new byte[NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES - 1];
        assertNotNull(MainActivity.validateNfcRawResult(result));
    }

    @Test
    public void validateNfcRawResult_returnsNullWhenDataValid() {
        Models.NfcRawResult result = new Models.NfcRawResult();
        result.dg1Raw = new byte[NfcPayloadBuilder.MIN_DG1_BYTES];
        result.dg2Raw = new byte[NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES];
        assertNull(MainActivity.validateNfcRawResult(result));
    }

    @Test
    public void tryBuildNfcRawPayload_returnsPayloadForValidResult() {
        Models.NfcRawResult result = new Models.NfcRawResult();
        result.dg1Raw = new byte[NfcPayloadBuilder.MIN_DG1_BYTES];
        result.dg2Raw = new byte[NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES];
        result.mrzKeys = new Models.MRZKeys();
        result.mrzKeys.document_number = "AB123456";
        result.mrzKeys.date_of_birth = "900101";
        result.mrzKeys.date_of_expiry = "300101";

        StringBuilder error = new StringBuilder();
        JsonObject payload = MainActivity.tryBuildNfcRawPayload(result, error);

        assertNotNull(payload);
        assertTrue(error.length() == 0);
        assertTrue(payload.has("dg1_raw_b64"));
        assertTrue(payload.has("dg2_raw_b64"));
        assertTrue(payload.has("format"));
    }

    @Test
    public void tryBuildNfcRawPayload_returnsNullAndErrorForMissingDg1() {
        Models.NfcRawResult result = new Models.NfcRawResult();
        result.dg2Raw = new byte[NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES];
        result.mrzKeys = new Models.MRZKeys();
        result.mrzKeys.document_number = "AB123456";
        result.mrzKeys.date_of_birth = "900101";
        result.mrzKeys.date_of_expiry = "300101";

        StringBuilder error = new StringBuilder();
        JsonObject payload = MainActivity.tryBuildNfcRawPayload(result, error);

        assertNull(payload);
        assertTrue(error.toString().contains("DG1"));
    }
}
