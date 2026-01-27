package com.demo.passport;

import android.nfc.Tag;
import android.nfc.tech.IsoDep;

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
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) {
            throw new IllegalStateException("IsoDep technology is required for passport NFC reading.");
        }

        PassportService service = null;
        try {
            isoDep.connect();
            isoDep.setTimeout(10000);
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
                    throw new IllegalStateException("Unsupported face image format: " + mimeType);
                }
                faceBytes = readAllBytes(faceImageInfo.getImageInputStream());
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

    private static boolean isSupportedFaceMimeType(String mimeType) {
        if (mimeType == null || mimeType.trim().isEmpty()) {
            return true;
        }
        String normalized = mimeType.toLowerCase();
        return normalized.contains("jpeg")
                || normalized.contains("jp2")
                || normalized.contains("jpeg2000")
                || normalized.contains("j2k");
    }

    private NfcPassportReader() {}
}
