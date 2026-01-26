package com.demo.passport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class NfcPassportReaderTest {

    @Test
    public void readPassportPopulatesPassportFields() throws Exception {
        Models.MRZKeys mrz = new Models.MRZKeys();
        mrz.document_number = "123456789";
        mrz.date_of_birth = "1990-01-01";
        mrz.date_of_expiry = "2030-01-01";

        Models.NfcResult result = NfcPassportReader.readPassport(null, mrz);

        assertNotNull(result);
        assertNotNull(result.passport);
        assertEquals("123456789", result.passport.get("document_number"));
        assertEquals("1990-01-01", result.passport.get("date_of_birth"));
        assertEquals("2030-01-01", result.passport.get("date_of_expiry"));
    }

    @Test
    public void readPassportRejectsMissingMrz() {
        assertThrows(IllegalArgumentException.class, () -> NfcPassportReader.readPassport(null, null));
    }

    @Test
    public void readPassportRejectsBlankField() {
        Models.MRZKeys mrz = new Models.MRZKeys();
        mrz.document_number = "";
        mrz.date_of_birth = "1990-01-01";
        mrz.date_of_expiry = "2030-01-01";

        assertThrows(IllegalArgumentException.class, () -> NfcPassportReader.readPassport(null, mrz));
    }
}
