package com.demo.passport;

import com.google.gson.Gson;

import java.util.Base64;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class BackendApi {
    private static final OkHttpClient client = new OkHttpClient();
    private static final Gson gson = new Gson();

    public static RecognizeResponse recognizePassport(byte[] jpegBytes) throws Exception {
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "passport.jpg",
                        RequestBody.create(jpegBytes, MediaType.parse("image/jpeg")))
                .build();

        Request req = new Request.Builder()
                .url(BackendConfig.BASE_URL + "/api/passport/recognize")
                .post(body)
                .build();

        try (Response resp = client.newCall(req).execute()) {
            String s = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) throw new RuntimeException("HTTP " + resp.code() + ": " + s);
            return gson.fromJson(s, RecognizeResponse.class);
        }
    }

    public static void sendNfcScan(NfcResult nfc) throws Exception {
        String imgB64 = Base64.getEncoder().encodeToString(nfc.faceImageJpeg);

        NFCPayload payload = new NFCPayload();
        payload.passport = nfc.passport;
        payload.face_image_b64 = imgB64;

        String json = gson.toJson(payload);
        Request req = new Request.Builder()
                .url(BackendConfig.BASE_URL + "/api/passport/nfc")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

        try (Response resp = client.newCall(req).execute()) {
            String s = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) throw new RuntimeException("HTTP " + resp.code() + ": " + s);
        }
    }

    private BackendApi() {}
}
