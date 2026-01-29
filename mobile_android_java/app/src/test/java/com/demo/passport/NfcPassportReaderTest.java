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

    // PACE detection tests

    @Test
    public void isPaceRequiredError_detectsSwCode6985() {
        assertTrue(NfcPassportReader.isPaceRequiredError("6985", null));
        assertTrue(NfcPassportReader.isPaceRequiredError("6985", "some error"));
    }

    @Test
    public void isPaceRequiredError_detectsSwCode6985CaseInsensitive() {
        assertTrue(NfcPassportReader.isPaceRequiredError("6985", null));
        assertTrue(NfcPassportReader.isPaceRequiredError("6985", null));
    }

    @Test
    public void isPaceRequiredError_detectsConditionsNotSatisfied() {
        assertTrue(NfcPassportReader.isPaceRequiredError(null, "CONDITIONS NOT SATISFIED"));
        assertTrue(NfcPassportReader.isPaceRequiredError(null, "Error: CONDITIONS NOT SATISFIED at offset 0"));
        assertTrue(NfcPassportReader.isPaceRequiredError(null, "conditions not satisfied"));
        assertTrue(NfcPassportReader.isPaceRequiredError(null, "Conditions   Not   Satisfied"));
    }

    @Test
    public void isPaceRequiredError_detectsExpectedLengthPattern() {
        assertTrue(NfcPassportReader.isPaceRequiredError(null, "expected length: 40 + 2, actual length: 2"));
        assertTrue(NfcPassportReader.isPaceRequiredError(null, "Error: expected length: 40 + 2, actual length: 2 at BAC"));
    }

    @Test
    public void isPaceRequiredError_returnsFalseForOtherErrors() {
        assertFalse(NfcPassportReader.isPaceRequiredError(null, null));
        assertFalse(NfcPassportReader.isPaceRequiredError(null, ""));
        assertFalse(NfcPassportReader.isPaceRequiredError(null, "Generic BAC error"));
        assertFalse(NfcPassportReader.isPaceRequiredError("6300", "Authentication failed"));
        assertFalse(NfcPassportReader.isPaceRequiredError("6982", "Security status not satisfied"));
    }

    @Test
    public void isPaceRequiredError_prioritizesSwCodeOver6985() {
        // Even if message doesn't mention PACE, SW=6985 is definitive
        assertTrue(NfcPassportReader.isPaceRequiredError("6985", "Unknown error"));
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
}
