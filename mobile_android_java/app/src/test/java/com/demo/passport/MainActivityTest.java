package com.demo.passport;

import static org.junit.Assert.assertFalse;
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
}
