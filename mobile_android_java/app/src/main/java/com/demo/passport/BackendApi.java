package com.demo.passport;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

import java.io.IOException;

public final class BackendApi {
    private static final long DEFAULT_ERROR_REPORT_INTERVAL_MS = 5000;
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build();
    private static final Gson gson = new Gson();
    private static long errorReportIntervalMs = DEFAULT_ERROR_REPORT_INTERVAL_MS;
    private static long lastErrorReportAtMs = 0;
    private static DebugListener debugListener;

    public interface Callback<T> {
        void onSuccess(T value);
        void onError(String message);
    }

    public interface DebugListener {
        void onDebugResponse(String rawResponse);
    }

    public static void setDebugListener(DebugListener listener) {
        debugListener = listener;
    }

    public static void recognizePassport(byte[] jpegBytes, Callback<Models.MRZKeys> cb) {
        RequestBody fileBody = RequestBody.create(jpegBytes, MediaType.parse("image/jpeg"));

        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)

                .addFormDataPart("image", "passport.jpg", fileBody)
                .build();

        Request req = new Request.Builder()

                .url(BackendConfig.getBaseUrl() + "/recognize")
                .post(body)
                .build();

        client.newCall(req).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String message = "HTTP failure: " + e.getMessage();
                emitDebugResponse(message);
                reportError(
                        message,
                        stackTraceToString(e),
                        buildRequestContext(req, null, null),
                        null
                );
                cb.onError(message);
            }

            @Override
            public void onResponse(Call call, Response resp) throws IOException {
                String s = resp.body() != null ? resp.body().string() : "";
                emitDebugResponse(s);
                if (!resp.isSuccessful()) {
                    String message = "HTTP " + resp.code() + ": " + s;
                    reportError(
                            message,
                            null,
                            buildRequestContext(req, resp.code(), s),
                            null
                    );
                    cb.onError(message);
                    return;
                }
                JsonObject obj;
                try {
                    obj = gson.fromJson(s, JsonObject.class);
                } catch (Exception e) {
                    String message = "RECOGNIZE_ERROR: invalid JSON: " + e.getMessage();
                    reportError(
                            message,
                            stackTraceToString(e),
                            buildRequestContext(req, resp.code(), s),
                            null
                    );
                    cb.onError(message);
                    return;
                }

                // Ожидаем либо поля, либо error
                if (obj.has("error")) {
                    String message = "RECOGNIZE_ERROR: " + obj.get("error").toString();
                    reportError(
                            message,
                            null,
                            buildRequestContext(req, resp.code(), s),
                            null
                    );
                    cb.onError(message);
                    return;
                }

                Models.MRZKeys mrz = parseMrz(obj);
                if (mrz == null) {
                    String message = "RECOGNIZE_ERROR: missing MRZ fields";
                    reportError(
                            message,
                            null,
                            buildRequestContext(req, resp.code(), s),
                            null
                    );
                    cb.onError(message);
                    return;
                }
                cb.onSuccess(mrz);
            }
        });
    }

    public static void sendNfcRaw(JsonObject payload, Callback<Void> cb) {
        String json = gson.toJson(payload);
        Request req = new Request.Builder()
                .url(BackendConfig.getBaseUrl() + "/nfc")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

        client.newCall(req).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String message = "HTTP failure: " + e.getMessage();
                emitDebugResponse(message);
                reportError(
                        message,
                        stackTraceToString(e),
                        buildRequestContext(req, null, null),
                        null
                );
                cb.onError(message);
            }

            @Override
            public void onResponse(Call call, Response resp) throws IOException {
                String s = resp.body() != null ? resp.body().string() : "";
                emitDebugResponse(s);
                if (!resp.isSuccessful()) {
                    String message = "HTTP " + resp.code() + ": " + s;
                    reportError(
                            message,
                            null,
                            buildRequestContext(req, resp.code(), s),
                            null
                    );
                    cb.onError(message);
                    return;
                }
                cb.onSuccess(null);
            }
        });
    }

    public static void reportError(
            String errorMessage,
            String stacktrace,
            JsonObject contextJson,
            Callback<Void> cb
    ) {
        if (!shouldReportError()) {
            if (cb != null) {
                cb.onSuccess(null);
            }
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("platform", "android");
        payload.addProperty("error_message", errorMessage);
        if (stacktrace != null) {
            payload.addProperty("stacktrace", stacktrace);
        }
        if (contextJson != null) {
            payload.add("context_json", contextJson);
        }

        String json = gson.toJson(payload);
        Request req = new Request.Builder()
                .url(BackendConfig.getBaseUrl() + "/errors")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

        client.newCall(req).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (cb != null) {
                    cb.onError("HTTP failure: " + e.getMessage());
                }
            }

            @Override
            public void onResponse(Call call, Response resp) {
                if (cb == null) {
                    resp.close();
                    return;
                }
                if (!resp.isSuccessful()) {
                    cb.onError("HTTP " + resp.code());
                } else {
                    cb.onSuccess(null);
                }
                resp.close();
            }
        });
    }

    static void setErrorReportIntervalMsForTesting(long intervalMs) {
        errorReportIntervalMs = intervalMs;
    }

    static void resetErrorReportDebounceForTesting() {
        lastErrorReportAtMs = 0;
        errorReportIntervalMs = DEFAULT_ERROR_REPORT_INTERVAL_MS;
    }

    private static boolean shouldReportError() {
        long now = System.currentTimeMillis();
        if (now - lastErrorReportAtMs < errorReportIntervalMs) {
            return false;
        }
        lastErrorReportAtMs = now;
        return true;
    }

    private static void emitDebugResponse(String response) {
        DebugListener listener = debugListener;
        if (listener != null) {
            listener.onDebugResponse(response);
        }
    }

    private static Models.MRZKeys parseMrz(JsonObject obj) {
        JsonObject mrzObj = obj;
        if (obj.has("mrz") && obj.get("mrz").isJsonObject()) {
            mrzObj = obj.getAsJsonObject("mrz");
        }
        if (!mrzObj.has("document_number") || !mrzObj.has("date_of_birth") || !mrzObj.has("date_of_expiry")) {
            return null;
        }
        Models.MRZKeys mrz = new Models.MRZKeys();
        mrz.document_number = mrzObj.get("document_number").getAsString();
        mrz.date_of_birth = mrzObj.get("date_of_birth").getAsString();
        mrz.date_of_expiry = mrzObj.get("date_of_expiry").getAsString();
        return mrz;
    }

    private static JsonObject buildRequestContext(Request req, Integer httpStatus, String responseBody) {
        JsonObject context = new JsonObject();
        context.addProperty("request_url", req.url().toString());
        context.addProperty("method", req.method());
        if (httpStatus != null) {
            context.addProperty("http_status", httpStatus);
        }
        if (responseBody != null) {
            context.addProperty("response_body", responseBody);
        }
        return context;
    }

    private static String stackTraceToString(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private BackendApi() {}
}
