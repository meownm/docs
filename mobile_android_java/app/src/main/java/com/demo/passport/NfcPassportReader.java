package com.demo.passport;

import android.nfc.Tag;
/**
 * Каркас чтения eMRTD.
 * Требует реального NFC-чтения из чипа; MRZ используется только для BAC.
 */
public final class NfcPassportReader {

    public static Models.NfcResult readPassport(Tag tag, Models.MRZKeys mrz) {
        if (mrz == null) {
            throw new IllegalStateException("MRZ keys are required before reading NFC passport data.");
        }
        if (tag == null) {
            throw new IllegalStateException("NFC tag is required for reading passport data.");
        }
        throw new UnsupportedOperationException(
                "NFC reading is not implemented; read data from chip and do not fill passport from MRZ keys."
        );
    }

    private NfcPassportReader() {}
}
