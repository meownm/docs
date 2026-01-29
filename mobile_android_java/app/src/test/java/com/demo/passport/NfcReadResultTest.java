package com.demo.passport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NfcReadResultTest {

    @Test
    public void success_createsSuccessfulResult() {
        Models.NfcRawResult data = new Models.NfcRawResult();
        data.dg1Raw = new byte[100];
        data.dg2Raw = new byte[2000];

        NfcReadResult result = NfcReadResult.success(data);

        assertTrue(result.isSuccess());
        assertTrue(result.allowsBackendCall());
        assertEquals(NfcReadStatus.SUCCESS, result.status);
        assertNotNull(result.data);
        assertEquals(data, result.data);
        assertNull(result.errorStage);
        assertNull(result.swCode);
        assertNull(result.technicalMessage);
    }

    @Test
    public void error_createsFailedResult() {
        NfcReadResult result = NfcReadResult.error(
                NfcReadStatus.PACE_REQUIRED,
                "bac_authentication",
                "6985",
                "Document requires PACE"
        );

        assertFalse(result.isSuccess());
        assertFalse(result.allowsBackendCall());
        assertEquals(NfcReadStatus.PACE_REQUIRED, result.status);
        assertNull(result.data);
        assertEquals("bac_authentication", result.errorStage);
        assertEquals("6985", result.swCode);
        assertEquals("Document requires PACE", result.technicalMessage);
    }

    @Test
    public void error_withStatusOnly() {
        NfcReadResult result = NfcReadResult.error(NfcReadStatus.NFC_NOT_AVAILABLE);

        assertFalse(result.isSuccess());
        assertEquals(NfcReadStatus.NFC_NOT_AVAILABLE, result.status);
        assertNull(result.errorStage);
        assertNull(result.swCode);
        assertNull(result.technicalMessage);
    }

    @Test
    public void error_withStatusAndMessage() {
        NfcReadResult result = NfcReadResult.error(NfcReadStatus.BAC_FAILED, "MRZ data incorrect");

        assertFalse(result.isSuccess());
        assertEquals(NfcReadStatus.BAC_FAILED, result.status);
        assertEquals("MRZ data incorrect", result.technicalMessage);
    }

    @Test
    public void error_throwsForSuccessStatus() {
        assertThrows(IllegalArgumentException.class, () ->
                NfcReadResult.error(NfcReadStatus.SUCCESS, "stage", "sw", "message")
        );
    }

    @Test
    public void getUserMessage_delegatesToStatus() {
        NfcReadResult result = NfcReadResult.error(NfcReadStatus.PACE_REQUIRED);
        assertEquals(NfcReadStatus.PACE_REQUIRED.getUserMessage(), result.getUserMessage());
    }

    @Test
    public void toString_includesAllFields() {
        NfcReadResult result = NfcReadResult.error(
                NfcReadStatus.PACE_REQUIRED,
                "bac_authentication",
                "6985",
                "Document requires PACE"
        );

        String str = result.toString();
        assertTrue(str.contains("PACE_REQUIRED"));
        assertTrue(str.contains("bac_authentication"));
        assertTrue(str.contains("6985"));
        assertTrue(str.contains("Document requires PACE"));
    }

    @Test
    public void toString_omitsNullFields() {
        NfcReadResult result = NfcReadResult.error(NfcReadStatus.UNKNOWN_ERROR);

        String str = result.toString();
        assertTrue(str.contains("UNKNOWN_ERROR"));
        assertFalse(str.contains("stage="));
        assertFalse(str.contains("sw="));
        assertFalse(str.contains("message="));
    }

    @Test
    public void allowsBackendCall_onlyForSuccess() {
        assertTrue(NfcReadResult.success(new Models.NfcRawResult()).allowsBackendCall());
        assertFalse(NfcReadResult.error(NfcReadStatus.PACE_REQUIRED).allowsBackendCall());
        assertFalse(NfcReadResult.error(NfcReadStatus.BAC_FAILED).allowsBackendCall());
        assertFalse(NfcReadResult.error(NfcReadStatus.UNKNOWN_ERROR).allowsBackendCall());
    }
}
