package com.demo.passport;

import android.nfc.Tag;
import java.util.HashMap;

/**
 * Каркас чтения eMRTD.
 * Для демо: возвращаем паспортные поля (из MRZ) и пустое фото.
 * При желании здесь реализуется реальное чтение DG1/DG2 через jmrtd + IsoDep.
 */
public final class NfcPassportReader {

    public static Models.NfcResult readPassport(Tag tag, Models.MRZKeys mrz) {
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
