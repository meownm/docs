package com.demo.passport;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Structured logging utility for NFC operations.
 * Provides consistent logging format with diagnostic information.
 *
 * IMPORTANT: Never logs MRZ data in plain text for security reasons.
 */
public final class NfcLogger {
    private static final String TAG = "NFC";
    private static final Gson gson = new Gson();

    /**
     * Logs the start of an NFC reading session.
     */
    public static void logSessionStart() {
        JsonObject log = new JsonObject();
        log.addProperty("event", "nfc_session_start");
        log.addProperty("timestamp", System.currentTimeMillis());
        Log.i(TAG, gson.toJson(log));
    }

    /**
     * Logs a stage transition during NFC reading.
     */
    public static void logStage(@NonNull String stage) {
        JsonObject log = new JsonObject();
        log.addProperty("event", "nfc_stage");
        log.addProperty("nfc_stage", stage);
        log.addProperty("timestamp", System.currentTimeMillis());
        Log.d(TAG, gson.toJson(log));
    }

    /**
     * Logs the result of an NFC reading operation.
     */
    public static void logResult(@NonNull NfcReadResult result) {
        JsonObject log = new JsonObject();
        log.addProperty("event", "nfc_result");
        log.addProperty("nfc_status", result.status.name());

        if (result.errorStage != null) {
            log.addProperty("nfc_stage", result.errorStage);
        }
        if (result.swCode != null) {
            log.addProperty("sw_code", result.swCode);
        }
        if (result.technicalMessage != null) {
            // Sanitize message to remove any potential MRZ data
            log.addProperty("message", sanitizeMessage(result.technicalMessage));
        }

        log.addProperty("timestamp", System.currentTimeMillis());

        if (result.isSuccess()) {
            Log.i(TAG, gson.toJson(log));
        } else {
            Log.w(TAG, gson.toJson(log));
        }
    }

    /**
     * Logs an error during NFC reading with detailed diagnostics.
     */
    public static void logError(
            @NonNull NfcReadStatus status,
            @NonNull String stage,
            @Nullable String swCode,
            @Nullable Throwable error
    ) {
        JsonObject log = new JsonObject();
        log.addProperty("event", "nfc_error");
        log.addProperty("nfc_status", status.name());
        log.addProperty("nfc_stage", stage);

        if (swCode != null) {
            log.addProperty("sw_code", swCode);
        }
        if (error != null) {
            log.addProperty("error_class", error.getClass().getSimpleName());
            String message = error.getMessage();
            if (message != null) {
                log.addProperty("error_message", sanitizeMessage(message));
            }
        }

        log.addProperty("timestamp", System.currentTimeMillis());
        Log.e(TAG, gson.toJson(log));
    }

    /**
     * Logs successful data read with sizes (no actual data).
     */
    public static void logDataRead(int dg1Size, int dg2Size) {
        JsonObject log = new JsonObject();
        log.addProperty("event", "nfc_data_read");
        log.addProperty("dg1_size", dg1Size);
        log.addProperty("dg2_size", dg2Size);
        log.addProperty("timestamp", System.currentTimeMillis());
        Log.i(TAG, gson.toJson(log));
    }

    /**
     * Sanitizes a message to remove potential MRZ data.
     * Masks sequences that look like document numbers or dates.
     */
    private static String sanitizeMessage(String message) {
        if (message == null) {
            return null;
        }
        // Mask document number patterns (alphanumeric sequences 6-12 chars)
        // Mask date patterns (YYMMDD, YYYYMMDD)
        // Note: This is a basic sanitization, real implementation may need more sophisticated patterns
        return message
                .replaceAll("doc=[A-Z0-9]{3,}\\*{0,3}", "doc=***")
                .replaceAll("dob=\\d{6}", "dob=******")
                .replaceAll("exp=\\d{6}", "exp=******")
                .replaceAll("document_number[\":]\\s*[\"']?[A-Z0-9]+[\"']?", "document_number=***")
                .replaceAll("date_of_birth[\":]\\s*[\"']?\\d+[\"']?", "date_of_birth=***")
                .replaceAll("date_of_expiry[\":]\\s*[\"']?\\d+[\"']?", "date_of_expiry=***");
    }

    private NfcLogger() {}
}
