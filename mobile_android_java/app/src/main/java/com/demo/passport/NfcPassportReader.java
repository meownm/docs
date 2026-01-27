package com.demo.passport;

import android.nfc.Tag;
import java.util.HashMap;

/**
 * Каркас чтения eMRTD.
 * Для демо: возвращаем паспортные поля (из MRZ) и пустое фото.
 * Здесь остается заглушка без внешних зависимостей.
 */
public final class NfcPassportReader {

    public static Models.NfcResult readPassport(Tag tag, Models.MRZKeys mrz) {
        if (mrz == null) {
            throw new IllegalStateException("MRZ keys are required before reading NFC passport data.");
        }
        Models.NfcResult out = new Models.NfcResult();
        out.passport = new HashMap<>();
        out.passport.put("document_number", mrz.document_number);
        out.passport.put("date_of_birth", mrz.date_of_birth);
        out.passport.put("date_of_expiry", mrz.date_of_expiry);
        out.faceImageJpeg = new byte[0];
        return out;
    }

    private NfcPassportReader() {}
}
