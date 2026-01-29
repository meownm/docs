package com.demo.passport;

import android.nfc.Tag;
import android.nfc.tech.IsoDep;

import net.sf.scuba.smartcards.CardService;

import org.jmrtd.BACKey;
import org.jmrtd.PassportService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
/**
 * Каркас чтения eMRTD.
 * Требует реального NFC-чтения из чипа; MRZ используется только для BAC.
 */
public final class NfcPassportReader {
    static final int NFC_TIMEOUT_MS = 45000;

    /**
     * Reads raw DG1 and DG2 bytes from the passport chip without parsing.
     * Server-side decoding mode: all ASN.1/TLV parsing is delegated to the server.
     *
     * @param tag NFC tag from the chip
     * @param mrz MRZ keys for BAC authentication
     * @return NfcRawResult containing raw DG1/DG2 bytes
     */
    public static Models.NfcRawResult readPassportRaw(Tag tag, Models.MRZKeys mrz) {
        if (mrz == null) {
            throw new IllegalStateException("MRZ keys are required before reading NFC passport data.");
        }
        if (tag == null) {
            throw new IllegalStateException("NFC tag is required for reading passport data.");
        }
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) {
            throw new IllegalStateException("IsoDep technology is required for passport NFC reading.");
        }

        PassportService service = null;
        try {
            isoDep.connect();
            isoDep.setTimeout(NFC_TIMEOUT_MS);
            CardService cardService = CardService.getInstance(isoDep);
            service = new PassportService(
                    cardService,
                    PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                    PassportService.DEFAULT_MAX_BLOCKSIZE,
                    false,
                    false
            );
            service.open();
            // Select the passport applet before BAC authentication
            // Without this, doBAC() fails with SW=0x6985 (CONDITIONS NOT SATISFIED)
            try {
                service.sendSelectApplet(false);
            } catch (Exception e) {
                throw new IllegalStateException(
                    "Failed to select passport applet. Ensure the document is positioned correctly on the NFC reader.", e);
            }
            BACKey bacKey = new BACKey(
                    mrz.document_number,
                    mrz.date_of_birth,
                    mrz.date_of_expiry
            );
            try {
                service.doBAC(bacKey);
            } catch (Exception e) {
                // Include MRZ info for debugging (document number partially masked)
                String docNumMasked = mrz.document_number != null && mrz.document_number.length() > 3
                    ? mrz.document_number.substring(0, 3) + "***"
                    : "null";
                throw new IllegalStateException(
                    "BAC failed: " + e.getMessage() +
                    " [doc=" + docNumMasked +
                    ", dob=" + mrz.date_of_birth +
                    ", exp=" + mrz.date_of_expiry + "]", e);
            }

            // Read raw DG1 bytes without parsing
            byte[] dg1Raw;
            try (InputStream dg1Input = service.getInputStream(PassportService.EF_DG1)) {
                dg1Raw = readAllBytes(dg1Input);
            } catch (Exception e) {
                throw new IllegalStateException("DG1 read failed: " + e.getMessage(), e);
            }

            // Read raw DG2 bytes without parsing
            byte[] dg2Raw;
            try (InputStream dg2Input = service.getInputStream(PassportService.EF_DG2)) {
                dg2Raw = readAllBytes(dg2Input);
            } catch (Exception e) {
                throw new IllegalStateException("DG2 read failed: " + e.getMessage(), e);
            }

            // Validate minimum sizes
            if (dg1Raw.length < 10) {
                throw new IllegalStateException("DG1 data is too small (expected MRZ data).");
            }
            if (dg2Raw.length < NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES) {
                throw new IllegalStateException("DG2 data is too small (expected face image).");
            }

            Models.NfcRawResult result = new Models.NfcRawResult();
            result.dg1Raw = dg1Raw;
            result.dg2Raw = dg2Raw;
            result.mrzKeys = mrz;
            return result;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("NFC read failed: " + e.getMessage(), e);
        } finally {
            if (service != null) {
                try {
                    service.close();
                } catch (Exception ignored) {
                }
            }
            try {
                isoDep.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static byte[] readAllBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private NfcPassportReader() {}
}
