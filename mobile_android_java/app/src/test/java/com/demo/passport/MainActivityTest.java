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
    public void shouldBindCamera_returnsTrueForCameraState() {
        assertTrue(MainActivity.shouldBindCamera(MainActivity.State.CAMERA));
    }

    @Test
    public void shouldBindCamera_returnsFalseForOtherStates() {
        assertFalse(MainActivity.shouldBindCamera(MainActivity.State.PHOTO_SENDING));
        assertFalse(MainActivity.shouldBindCamera(MainActivity.State.NFC_WAIT));
        assertFalse(MainActivity.shouldBindCamera(MainActivity.State.NFC_READING));
        assertFalse(MainActivity.shouldBindCamera(MainActivity.State.RESULT));
        assertFalse(MainActivity.shouldBindCamera(MainActivity.State.ERROR));
    }

    @Test
    public void shouldBindCamera_returnsFalseForNullState() {
        assertFalse(MainActivity.shouldBindCamera(null));
    }

    @Test
    public void shouldUpdateCameraPreview_returnsTrueOnCameraTransition() {
        assertTrue(MainActivity.shouldUpdateCameraPreview(MainActivity.State.CAMERA, MainActivity.State.PHOTO_SENDING));
        assertTrue(MainActivity.shouldUpdateCameraPreview(MainActivity.State.ERROR, MainActivity.State.CAMERA));
    }

    @Test
    public void shouldUpdateCameraPreview_returnsFalseWhenCameraStateUnchanged() {
        assertFalse(MainActivity.shouldUpdateCameraPreview(MainActivity.State.CAMERA, MainActivity.State.CAMERA));
        assertFalse(MainActivity.shouldUpdateCameraPreview(MainActivity.State.ERROR, MainActivity.State.RESULT));
    }

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
}
