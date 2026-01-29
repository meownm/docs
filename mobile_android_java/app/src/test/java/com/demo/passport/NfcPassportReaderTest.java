package com.demo.passport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NfcPassportReaderTest {

    @Test
    public void readPassportRaw_returnsErrorWhenMrzMissing() {
        NfcReadResult result = NfcPassportReader.readPassportRaw(null, null);

        assertNotNull(result);
        assertEquals(NfcReadStatus.BAC_FAILED, result.status);
        assertFalse(result.isSuccess());
        assertFalse(result.allowsBackendCall());
        assertNull(result.data);
        assertEquals("input_validation", result.errorStage);
    }

    @Test
    public void readPassportRaw_returnsErrorWhenTagMissing() {
        Models.MRZKeys keys = new Models.MRZKeys();
        keys.document_number = "1234567";
        keys.date_of_birth = "900101";
        keys.date_of_expiry = "300101";

        NfcReadResult result = NfcPassportReader.readPassportRaw(null, keys);

        assertNotNull(result);
        assertEquals(NfcReadStatus.NFC_NOT_AVAILABLE, result.status);
        assertFalse(result.isSuccess());
        assertFalse(result.allowsBackendCall());
        assertNull(result.data);
        assertEquals("input_validation", result.errorStage);
    }

    // Authentication method constants tests

    @Test
    public void authMethodConstants_areCorrect() {
        assertEquals("PACE", NfcPassportReader.AUTH_METHOD_PACE);
        assertEquals("BAC", NfcPassportReader.AUTH_METHOD_BAC);
    }

    // SW code extraction tests

    @Test
    public void extractSwCode_extractsFromStandardFormat() {
        Exception e = new Exception("BAC failed: SW = 0x6985");
        assertEquals("6985", NfcPassportReader.extractSwCode(e));
    }

    @Test
    public void extractSwCode_extractsWithoutPrefix() {
        Exception e = new Exception("BAC failed: SW=6985 (CONDITIONS NOT SATISFIED)");
        assertEquals("6985", NfcPassportReader.extractSwCode(e));
    }

    @Test
    public void extractSwCode_extractsWithColon() {
        Exception e = new Exception("Error SW: 0x6300");
        assertEquals("6300", NfcPassportReader.extractSwCode(e));
    }

    @Test
    public void extractSwCode_returnsUpperCase() {
        Exception e = new Exception("SW = 0xabcd");
        assertEquals("ABCD", NfcPassportReader.extractSwCode(e));
    }

    @Test
    public void extractSwCode_returnsNullForNoSwCode() {
        Exception e = new Exception("Generic error without SW code");
        assertNull(NfcPassportReader.extractSwCode(e));
    }

    @Test
    public void extractSwCode_returnsNullForNullException() {
        assertNull(NfcPassportReader.extractSwCode(null));
    }

    @Test
    public void extractSwCode_checksExceptionCause() {
        Exception cause = new Exception("SW = 0x6985");
        Exception e = new Exception("Wrapper exception", cause);
        // When main message is null-like but cause has SW code
        Exception wrapperWithNullMsg = new Exception(null, cause);
        assertEquals("6985", NfcPassportReader.extractSwCode(wrapperWithNullMsg));
    }

    // Tests for PACE-related SW codes

    @Test
    public void extractSwCode_extractsPaceRelatedCodes() {
        // 6A82 - File not found (PACE not supported)
        assertEquals("6A82", NfcPassportReader.extractSwCode(new Exception("SW = 0x6A82")));

        // 6A81 - Function not supported
        assertEquals("6A81", NfcPassportReader.extractSwCode(new Exception("SW = 0x6A81")));

        // 6D00 - Instruction not supported
        assertEquals("6D00", NfcPassportReader.extractSwCode(new Exception("SW = 0x6D00")));

        // 6300 - Authentication failed
        assertEquals("6300", NfcPassportReader.extractSwCode(new Exception("SW = 0x6300")));
    }
}
