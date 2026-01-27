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
}
