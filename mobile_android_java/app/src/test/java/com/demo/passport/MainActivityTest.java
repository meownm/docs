package com.demo.passport;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
        Models.MRZKeys keys = MainActivity.buildManualMrzKeys("AB123", "1990-01-01", "2030-01-01");
        assertNotNull(keys);
        assertTrue("AB123".equals(keys.document_number));
        assertTrue("1990-01-01".equals(keys.date_of_birth));
        assertTrue("2030-01-01".equals(keys.date_of_expiry));
    }

    @Test
    public void buildManualMrzKeys_returnsNullWhenAnyFieldMissing() {
        assertNull(MainActivity.buildManualMrzKeys("", "1990-01-01", "2030-01-01"));
        assertNull(MainActivity.buildManualMrzKeys("AB123", "", "2030-01-01"));
        assertNull(MainActivity.buildManualMrzKeys("AB123", "1990-01-01", ""));
    }
}
