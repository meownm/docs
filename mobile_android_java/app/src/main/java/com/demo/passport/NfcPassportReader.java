package com.demo.passport;

import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.util.Log;

import org.jmrtd.BACKey;
import org.jmrtd.PassportService;
import org.jmrtd.lds.DG1File;
import org.jmrtd.lds.DG2File;
import org.jmrtd.lds.LDS;
import org.jmrtd.lds.icao.MRZInfo;
import org.jmrtd.lds.iso19794.FaceImageInfo;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
/**
 * Каркас чтения eMRTD.
 * Требует реального NFC-чтения из чипа; MRZ используется только для BAC.
 */
public final class NfcPassportReader {
    private static final String TAG = "NfcPassportReader";
    static final int NFC_TIMEOUT_MS = 45000;

    public static Models.NfcResult readPassport(Tag tag, Models.MRZKeys mrz) {
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
            service = new PassportService(
                    isoDep,
                    PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                    PassportService.DEFAULT_MAX_BLOCKSIZE,
                    false,
                    false
            );
            service.open();
            BACKey bacKey = new BACKey(
                    mrz.document_number,
                    mrz.date_of_birth,
                    mrz.date_of_expiry
            );
            try {
                service.doBAC(bacKey);
            } catch (Exception e) {
                throw new IllegalStateException("BAC failed: " + e.getMessage(), e);
            }

            LDS lds = new LDS();
            MRZInfo mrzInfo;
            try (InputStream dg1Input = service.getInputStream(PassportService.EF_DG1)) {
                DG1File dg1 = new DG1File(dg1Input);
                mrzInfo = dg1.getMRZInfo();
                lds.add(dg1);
            } catch (Exception e) {
                throw new IllegalStateException("DG1 read failed: " + e.getMessage(), e);
            }

            byte[] faceBytes;
            try (InputStream dg2Input = service.getInputStream(PassportService.EF_DG2)) {
                DG2File dg2 = new DG2File(dg2Input);
                lds.add(dg2);
                if (dg2.getFaceInfos() == null || dg2.getFaceInfos().isEmpty()) {
                    throw new IllegalStateException("DG2 does not contain face info.");
                }
                List<FaceImageInfo> faceImages = dg2.getFaceInfos()
                        .get(0)
                        .getFaceImageInfos();
                if (faceImages == null || faceImages.isEmpty()) {
                    throw new IllegalStateException("DG2 does not contain face images.");
                }
                FaceImageInfo faceImageInfo = faceImages.get(0);
                String mimeType = faceImageInfo.getMimeType();
                if (!isSupportedFaceMimeType(mimeType)) {
                    throw new IllegalStateException(
                            "Unsupported face image format (expected image/jpeg, image/jp2, image/jpeg2000): "
                                    + mimeType
                    );
                }
                faceBytes = readAllBytes(faceImageInfo.getImageInputStream());
                if (faceBytes.length < NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES) {
                    throw new IllegalStateException("Face image is missing or too small.");
                }
            } catch (Exception e) {
                throw new IllegalStateException("DG2 read failed: " + e.getMessage(), e);
            }

            HashMap<String, String> passport = new HashMap<>();
            if (mrzInfo != null) {
                passport.put("document_number", mrzInfo.getDocumentNumber());
                passport.put("date_of_birth", mrzInfo.getDateOfBirth());
                passport.put("date_of_expiry", mrzInfo.getDateOfExpiry());
                passport.put("surname", mrzInfo.getPrimaryIdentifier());
                passport.put("given_names", mrzInfo.getSecondaryIdentifier());
                passport.put("nationality", mrzInfo.getNationality());
                passport.put("sex", mrzInfo.getGender());
            }

            Models.NfcResult result = new Models.NfcResult();
            result.passport = passport;
            result.faceImageJpeg = faceBytes;
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

    static boolean isSupportedFaceMimeType(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return false;
        }
        String normalized = mimeType.toLowerCase(Locale.US);
        return "image/jpeg".equals(normalized)
                || "image/jp2".equals(normalized)
                || "image/jpeg2000".equals(normalized);
    }

    private NfcPassportReader() {}
}
