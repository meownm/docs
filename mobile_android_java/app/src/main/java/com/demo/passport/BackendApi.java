package com.demo.passport;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import okhttp3.*;

import java.io.IOException;

public final class BackendApi {
    private static final OkHttpClient client = new OkHttpClient();
    private static final Gson gson = new Gson();

    public interface Callback<T> {
        void onSuccess(T value);
        void onError(String message);
    }

    public static void recognizePassport(byte[] jpegBytes, Callback<Models.MRZKeys> cb) {
        RequestBody fileBody = RequestBody.create(jpegBytes, MediaType.parse("image/jpeg"));

        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "passport.jpg", fileBody)
                .build();

        Request req = new Request.Builder()
                .url(BackendConfig.BASE_URL + "/api/passport/recognize")
                .post(body)
                .build();

        client.newCall(req).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                cb.onError("HTTP failure: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response resp) throws IOException {
                String s = resp.body() != null ? resp.body().string() : "";
                if (!resp.isSuccessful()) {
                    cb.onError("HTTP " + resp.code() + ": " + s);
                    return;
                }
                JsonObject obj = gson.fromJson(s, JsonObject.class);

                // Ожидаем либо поля, либо error
                if (obj.has("error")) {
                    cb.onError("RECOGNIZE_ERROR: " + obj.get("error").toString());
                    return;
                }

                Models.MRZKeys mrz = new Models.MRZKeys();
                mrz.document_number = obj.get("document_number").getAsString();
                mrz.date_of_birth = obj.get("date_of_birth").getAsString();
                mrz.date_of_expiry = obj.get("date_of_expiry").getAsString();

                cb.onSuccess(mrz);
            }
        });
    }

    public static void sendNfcRaw(JsonObject payload, Callback<Void> cb) {
        String json = gson.toJson(payload);
        Request req = new Request.Builder()
                .url(BackendConfig.BASE_URL + "/api/passport/nfc")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

        client.newCall(req).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                cb.onError("HTTP failure: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response resp) throws IOException {
                String s = resp.body() != null ? resp.body().string() : "";
                if (!resp.isSuccessful()) {
                    cb.onError("HTTP " + resp.code() + ": " + s);
                    return;
                }
                cb.onSuccess(null);
            }
        });
    }

    private BackendApi() {}
}
