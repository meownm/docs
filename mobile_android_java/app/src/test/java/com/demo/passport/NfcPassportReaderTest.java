package com.demo.passport;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NfcPassportReaderTest {

    @Test(expected = IllegalStateException.class)
    public void readPassport_throwsWhenMrzMissing() {
        NfcPassportReader.readPassport(null, null);
    }

    @Test
    public void readPassport_throwsWhenTagMissing() {
        Models.MRZKeys mrz = new Models.MRZKeys();
        mrz.document_number = "B456";
        mrz.date_of_birth = "880202";
        mrz.date_of_expiry = "280202";

        try {
            NfcPassportReader.readPassport(null, mrz);
        } catch (IllegalStateException e) {
            assertEquals("NFC tag is required for reading passport data.", e.getMessage());
            return;
        }
        throw new AssertionError("Expected IllegalStateException when tag is missing");
    }
}
