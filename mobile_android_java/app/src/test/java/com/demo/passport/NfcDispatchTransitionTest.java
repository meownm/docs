package com.demo.passport;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NfcDispatchTransitionTest {
    @Test
    public void from_entersNfcWait_enablesDispatch() {
        NfcDispatchTransition.Action action =
                NfcDispatchTransition.from(MainActivity.State.CAMERA, MainActivity.State.NFC_WAIT);

        assertEquals(NfcDispatchTransition.Action.ENABLE, action);
    }

    @Test
    public void from_leavesNfcWait_disablesDispatch() {
        NfcDispatchTransition.Action action =
                NfcDispatchTransition.from(MainActivity.State.NFC_WAIT, MainActivity.State.RESULT);

        assertEquals(NfcDispatchTransition.Action.DISABLE, action);
    }

    @Test
    public void from_otherTransitions_doNothing() {
        NfcDispatchTransition.Action action =
                NfcDispatchTransition.from(MainActivity.State.NFC_READING, MainActivity.State.RESULT);

        assertEquals(NfcDispatchTransition.Action.NONE, action);
    }

    @Test
    public void from_flowSequence_emitsEnableThenDisable() {
        NfcDispatchTransition.Action enterAction =
                NfcDispatchTransition.from(MainActivity.State.PHOTO_SENDING, MainActivity.State.NFC_WAIT);
        NfcDispatchTransition.Action stayAction =
                NfcDispatchTransition.from(MainActivity.State.NFC_WAIT, MainActivity.State.NFC_READING);
        NfcDispatchTransition.Action exitAction =
                NfcDispatchTransition.from(MainActivity.State.NFC_READING, MainActivity.State.RESULT);

        assertEquals(NfcDispatchTransition.Action.ENABLE, enterAction);
        assertEquals(NfcDispatchTransition.Action.DISABLE, stayAction);
        assertEquals(NfcDispatchTransition.Action.NONE, exitAction);
    }
}
