package com.demo.passport;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NfcPassportReaderTest {

    @Test
    public void readPassport_throwsWhenMrzMissing() {
        assertThrows(IllegalStateException.class, () -> NfcPassportReader.readPassport(null, null));
    }

    @Test
    public void readPassport_throwsWhenTagMissing() {
        Models.MRZKeys keys = new Models.MRZKeys();
        keys.document_number = "1234567";
        keys.date_of_birth = "900101";
        keys.date_of_expiry = "300101";
        assertThrows(IllegalStateException.class, () -> NfcPassportReader.readPassport(null, keys));
    }

    @Test
    public void isSupportedFaceMimeType_acceptsJpegVariants() {
        assertTrue(NfcPassportReader.isSupportedFaceMimeType("image/jpeg"));
        assertTrue(NfcPassportReader.isSupportedFaceMimeType("image/jp2"));
        assertTrue(NfcPassportReader.isSupportedFaceMimeType("image/jpeg2000"));
    }

    @Test
    public void isSupportedFaceMimeType_rejectsUnknownOrEmpty() {
        assertFalse(NfcPassportReader.isSupportedFaceMimeType("image/png"));
        assertFalse(NfcPassportReader.isSupportedFaceMimeType("image/jpeg; charset=binary"));
        assertFalse(NfcPassportReader.isSupportedFaceMimeType(""));
        assertFalse(NfcPassportReader.isSupportedFaceMimeType(null));
    }
}
