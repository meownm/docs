package com.demo.passport;

import android.nfc.Tag;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Демо-ридер eMRTD. Реальная интеграция требует:
 * - IsoDep
 * - BAC (на основе MRZ keys)
 * - чтение DG1 (персональные данные), DG2 (фото лица)
 *
 * Здесь оставлен минимальный каркас и поясняющие места.
 */
public final class NfcPassportReader {

    public static NfcResult readPassport(Tag tag, MRZKeys mrz) throws Exception {
        if (mrz == null) {
            throw new IllegalArgumentException("MRZ keys are required");
        }
        requireField(mrz.document_number, "document_number");
        requireField(mrz.date_of_birth, "date_of_birth");
        requireField(mrz.date_of_expiry, "date_of_expiry");

        // В демонстрации не делаем полный BAC/DG чтение в рантайме без устройства/чипа.
        // Каркас оставлен, чтобы в Android Studio подключить jmrtd и реализовать чтение.
        //
        // Минимально ожидается:
        // - заполнить Map passport
        // - положить faceImageJpeg (DG2)
        NfcResult out = new NfcResult();
        out.passport = new HashMap<>();

        out.passport.put("document_number", mrz.document_number);
        out.passport.put("date_of_birth", mrz.date_of_birth);
        out.passport.put("date_of_expiry", mrz.date_of_expiry);

        // TODO: заменить реальным изображением из DG2
        out.faceImageJpeg = new byte[0];

        return out;
    }

    private static void requireField(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing " + fieldName);
        }
    }

    private NfcPassportReader() {}
}
