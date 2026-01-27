package com.demo.passport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gson.JsonObject;

import org.junit.Test;

public class NfcPassportReaderTest {

    @Test(expected = IllegalStateException.class)
    public void readPassport_throwsWhenMrzMissing() {
        NfcPassportReader.readPassport(null, null);
    }

    @Test
    public void readPassport_returnsPassportFields() {
        Models.MRZKeys mrz = new Models.MRZKeys();
        mrz.document_number = "A123";
        mrz.date_of_birth = "1990-01-01";
        mrz.date_of_expiry = "2030-01-01";

        Models.NfcResult result = NfcPassportReader.readPassport(null, mrz);

        assertNotNull(result);
        assertEquals("A123", result.passport.get("document_number"));
        assertEquals("1990-01-01", result.passport.get("date_of_birth"));
        assertEquals("2030-01-01", result.passport.get("date_of_expiry"));
        assertEquals(0, result.faceImageJpeg.length);
    }

    @Test
    public void readPassport_buildsPayloadIntegration() {
        Models.MRZKeys mrz = new Models.MRZKeys();
        mrz.document_number = "B456";
        mrz.date_of_birth = "1988-02-02";
        mrz.date_of_expiry = "2028-02-02";

        Models.NfcResult result = NfcPassportReader.readPassport(null, mrz);
        JsonObject payload = NfcPayloadBuilder.build(result);

        JsonObject passport = payload.getAsJsonObject("passport");
        assertEquals("B456", passport.get("document_number").getAsString());
        assertEquals("1988-02-02", passport.get("date_of_birth").getAsString());
        assertEquals("2028-02-02", passport.get("date_of_expiry").getAsString());
        assertEquals("", payload.get("face_image_b64").getAsString());
    }
}
