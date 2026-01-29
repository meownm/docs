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

        NfcReadResult result = NfcReadResult.success(data, "PACE", "0.4.0.127.0.7.2.2.4.2.2");

        assertTrue(result.isSuccess());
        assertTrue(result.allowsBackendCall());
        assertEquals(NfcReadStatus.SUCCESS, result.status);
        assertNotNull(result.data);
        assertEquals(data, result.data);
        assertNull(result.errorStage);
        assertNull(result.swCode);
        assertNull(result.technicalMessage);
        assertEquals("PACE", result.authMethod);
        assertEquals("0.4.0.127.0.7.2.2.4.2.2", result.paceOid);
    }

    @Test
    public void success_withBacAuth() {
        Models.NfcRawResult data = new Models.NfcRawResult();
        data.dg1Raw = new byte[100];
        data.dg2Raw = new byte[2000];

        NfcReadResult result = NfcReadResult.success(data, "BAC", null);

        assertTrue(result.isSuccess());
        assertEquals("BAC", result.authMethod);
        assertNull(result.paceOid);
    }

    @Test
    public void success_legacyMethod_defaultsToBac() {
        Models.NfcRawResult data = new Models.NfcRawResult();
        data.dg1Raw = new byte[100];
        data.dg2Raw = new byte[2000];

        @SuppressWarnings("deprecation")
        NfcReadResult result = NfcReadResult.success(data);

        assertTrue(result.isSuccess());
        assertEquals("BAC", result.authMethod);
        assertNull(result.paceOid);
    }

    @Test
    public void error_createsFailedResult() {
        NfcReadResult result = NfcReadResult.error(
                NfcReadStatus.PACE_FAILED,
                "pace_authentication",
                "6985",
                "PACE authentication failed"
        );

        assertFalse(result.isSuccess());
        assertFalse(result.allowsBackendCall());
        assertEquals(NfcReadStatus.PACE_FAILED, result.status);
        assertNull(result.data);
        assertEquals("pace_authentication", result.errorStage);
        assertEquals("6985", result.swCode);
        assertEquals("PACE authentication failed", result.technicalMessage);
        assertNull(result.authMethod);
        assertNull(result.paceOid);
    }

    @Test
    public void paceError_includesPaceOid() {
        NfcReadResult result = NfcReadResult.paceError(
                NfcReadStatus.PACE_FAILED,
                "pace_authentication",
                "6300",
                "PACE protocol error",
                "0.4.0.127.0.7.2.2.4.2.2"
        );

        assertFalse(result.isSuccess());
        assertEquals(NfcReadStatus.PACE_FAILED, result.status);
        assertEquals("pace_authentication", result.errorStage);
        assertEquals("6300", result.swCode);
        assertEquals("PACE protocol error", result.technicalMessage);
        assertEquals("PACE", result.authMethod);
        assertEquals("0.4.0.127.0.7.2.2.4.2.2", result.paceOid);
    }

    @Test
    public void error_withStatusOnly() {
        NfcReadResult result = NfcReadResult.error(NfcReadStatus.NFC_NOT_AVAILABLE);

        assertFalse(result.isSuccess());
        assertEquals(NfcReadStatus.NFC_NOT_AVAILABLE, result.status);
        assertNull(result.errorStage);
        assertNull(result.swCode);
        assertNull(result.technicalMessage);
        assertNull(result.authMethod);
        assertNull(result.paceOid);
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
    public void paceError_throwsForSuccessStatus() {
        assertThrows(IllegalArgumentException.class, () ->
                NfcReadResult.paceError(NfcReadStatus.SUCCESS, "stage", "sw", "message", "oid")
        );
    }

    @Test
    public void getUserMessage_delegatesToStatus() {
        NfcReadResult result = NfcReadResult.error(NfcReadStatus.PACE_FAILED);
        assertEquals(NfcReadStatus.PACE_FAILED.getUserMessage(), result.getUserMessage());
    }

    @Test
    public void toString_includesAllFields() {
        NfcReadResult result = NfcReadResult.paceError(
                NfcReadStatus.PACE_FAILED,
                "pace_authentication",
                "6985",
                "PACE failed",
                "0.4.0.127.0.7.2.2.4.2.2"
        );

        String str = result.toString();
        assertTrue(str.contains("PACE_FAILED"));
        assertTrue(str.contains("PACE"));
        assertTrue(str.contains("0.4.0.127.0.7.2.2.4.2.2"));
        assertTrue(str.contains("pace_authentication"));
        assertTrue(str.contains("6985"));
        assertTrue(str.contains("PACE failed"));
    }

    @Test
    public void toString_includesAuthMethod() {
        Models.NfcRawResult data = new Models.NfcRawResult();
        NfcReadResult result = NfcReadResult.success(data, "PACE", "0.4.0.127.0.7.2.2.4.2.2");

        String str = result.toString();
        assertTrue(str.contains("authMethod=PACE"));
        assertTrue(str.contains("paceOid=0.4.0.127.0.7.2.2.4.2.2"));
    }

    @Test
    public void toString_omitsNullFields() {
        NfcReadResult result = NfcReadResult.error(NfcReadStatus.UNKNOWN_ERROR);

        String str = result.toString();
        assertTrue(str.contains("UNKNOWN_ERROR"));
        assertFalse(str.contains("authMethod="));
        assertFalse(str.contains("paceOid="));
        assertFalse(str.contains("stage="));
        assertFalse(str.contains("sw="));
        assertFalse(str.contains("message="));
    }

    @Test
    public void allowsBackendCall_onlyForSuccess() {
        Models.NfcRawResult data = new Models.NfcRawResult();
        assertTrue(NfcReadResult.success(data, "PACE", null).allowsBackendCall());
        assertFalse(NfcReadResult.error(NfcReadStatus.PACE_FAILED).allowsBackendCall());
        assertFalse(NfcReadResult.error(NfcReadStatus.BAC_FAILED).allowsBackendCall());
        assertFalse(NfcReadResult.error(NfcReadStatus.UNKNOWN_ERROR).allowsBackendCall());
    }
}
