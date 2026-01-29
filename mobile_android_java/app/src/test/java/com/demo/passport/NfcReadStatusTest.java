package com.demo.passport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NfcReadStatusTest {

    @Test
    public void success_allowsBackendCall() {
        assertTrue(NfcReadStatus.SUCCESS.allowsBackendCall());
    }

    @Test
    public void allNonSuccessStatuses_disallowBackendCall() {
        assertFalse(NfcReadStatus.NFC_NOT_AVAILABLE.allowsBackendCall());
        assertFalse(NfcReadStatus.NFC_NOT_ISODEP.allowsBackendCall());
        assertFalse(NfcReadStatus.APPLET_SELECTION_FAILED.allowsBackendCall());
        assertFalse(NfcReadStatus.BAC_FAILED.allowsBackendCall());
        assertFalse(NfcReadStatus.PACE_REQUIRED.allowsBackendCall());
        assertFalse(NfcReadStatus.DG_READ_ERROR.allowsBackendCall());
        assertFalse(NfcReadStatus.PARTIAL_READ.allowsBackendCall());
        assertFalse(NfcReadStatus.UNKNOWN_ERROR.allowsBackendCall());
    }

    @Test
    public void success_isNotClientError() {
        assertFalse(NfcReadStatus.SUCCESS.isClientError());
    }

    @Test
    public void allNonSuccessStatuses_areClientErrors() {
        assertTrue(NfcReadStatus.NFC_NOT_AVAILABLE.isClientError());
        assertTrue(NfcReadStatus.NFC_NOT_ISODEP.isClientError());
        assertTrue(NfcReadStatus.APPLET_SELECTION_FAILED.isClientError());
        assertTrue(NfcReadStatus.BAC_FAILED.isClientError());
        assertTrue(NfcReadStatus.PACE_REQUIRED.isClientError());
        assertTrue(NfcReadStatus.DG_READ_ERROR.isClientError());
        assertTrue(NfcReadStatus.PARTIAL_READ.isClientError());
        assertTrue(NfcReadStatus.UNKNOWN_ERROR.isClientError());
    }

    @Test
    public void paceRequired_hasCorrectUserMessage() {
        String message = NfcReadStatus.PACE_REQUIRED.getUserMessage();
        assertTrue(message.contains("PACE"));
        assertTrue(message.contains("современн"));
        // Verify NO mention of "unsupported chip"
        assertFalse(message.toLowerCase().contains("неподдерживаем"));
        assertFalse(message.toLowerCase().contains("unsupported"));
    }

    @Test
    public void allStatuses_haveNonEmptyUserMessages() {
        for (NfcReadStatus status : NfcReadStatus.values()) {
            String message = status.getUserMessage();
            assertTrue("Status " + status + " should have non-empty message",
                    message != null && !message.isEmpty());
        }
    }

    @Test
    public void noStatusMessage_containsUnsupportedChip() {
        for (NfcReadStatus status : NfcReadStatus.values()) {
            String message = status.getUserMessage().toLowerCase();
            assertFalse("Status " + status + " should not mention 'unsupported chip'",
                    message.contains("неподдерживаемый чип") || message.contains("unsupported chip"));
        }
    }
}
