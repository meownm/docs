package com.demo.passport;

import static org.junit.Assert.assertThrows;

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
}
