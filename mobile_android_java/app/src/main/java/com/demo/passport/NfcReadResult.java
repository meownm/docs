package com.demo.passport;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Result of NFC passport reading operation.
 * Encapsulates the status, optional data, and diagnostic information.
 *
 * This is a structured result that replaces exception-based error handling.
 * Backend calls should ONLY be made when status == SUCCESS.
 */
public final class NfcReadResult {
    /**
     * Canonical status of the NFC reading operation.
     */
    @NonNull
    public final NfcReadStatus status;

    /**
     * Raw NFC data if reading was successful, null otherwise.
     */
    @Nullable
    public final Models.NfcRawResult data;

    /**
     * Stage at which the error occurred (for diagnostics).
     * Examples: "applet_selection", "bac_authentication", "dg1_read", "dg2_read"
     */
    @Nullable
    public final String errorStage;

    /**
     * SW (Status Word) code from the smart card, if available.
     * Format: "0x6985" or similar.
     */
    @Nullable
    public final String swCode;

    /**
     * Technical error message for logging (not for user display).
     * May contain exception details.
     */
    @Nullable
    public final String technicalMessage;

    /**
     * Authentication method used (PACE or BAC).
     * Only set on successful reads.
     */
    @Nullable
    public final String authMethod;

    /**
     * PACE OID used for authentication (if PACE was used).
     * Example: "0.4.0.127.0.7.2.2.4.2.2" for ECDH-GM-AES-CBC-CMAC-128
     */
    @Nullable
    public final String paceOid;

    private NfcReadResult(
            @NonNull NfcReadStatus status,
            @Nullable Models.NfcRawResult data,
            @Nullable String errorStage,
            @Nullable String swCode,
            @Nullable String technicalMessage,
            @Nullable String authMethod,
            @Nullable String paceOid
    ) {
        this.status = status;
        this.data = data;
        this.errorStage = errorStage;
        this.swCode = swCode;
        this.technicalMessage = technicalMessage;
        this.authMethod = authMethod;
        this.paceOid = paceOid;
    }

    /**
     * Creates a successful result with data and authentication method info.
     *
     * @param data The raw NFC data
     * @param authMethod The authentication method used (PACE or BAC)
     * @param paceOid The PACE OID if PACE was used, null for BAC
     */
    public static NfcReadResult success(
            @NonNull Models.NfcRawResult data,
            @NonNull String authMethod,
            @Nullable String paceOid
    ) {
        return new NfcReadResult(NfcReadStatus.SUCCESS, data, null, null, null, authMethod, paceOid);
    }

    /**
     * Creates a successful result with data (legacy method for BAC).
     * @deprecated Use {@link #success(Models.NfcRawResult, String, String)} instead
     */
    @Deprecated
    public static NfcReadResult success(@NonNull Models.NfcRawResult data) {
        return new NfcReadResult(NfcReadStatus.SUCCESS, data, null, null, null, "BAC", null);
    }

    /**
     * Creates an error result with full diagnostic information.
     */
    public static NfcReadResult error(
            @NonNull NfcReadStatus status,
            @Nullable String errorStage,
            @Nullable String swCode,
            @Nullable String technicalMessage
    ) {
        if (status == NfcReadStatus.SUCCESS) {
            throw new IllegalArgumentException("Cannot create error result with SUCCESS status");
        }
        return new NfcReadResult(status, null, errorStage, swCode, technicalMessage, null, null);
    }

    /**
     * Creates an error result with status and message only.
     */
    public static NfcReadResult error(@NonNull NfcReadStatus status, @Nullable String technicalMessage) {
        return error(status, null, null, technicalMessage);
    }

    /**
     * Creates an error result with status only.
     */
    public static NfcReadResult error(@NonNull NfcReadStatus status) {
        return error(status, null, null, null);
    }

    /**
     * Creates an error result with PACE-specific diagnostic information.
     */
    public static NfcReadResult paceError(
            @NonNull NfcReadStatus status,
            @Nullable String errorStage,
            @Nullable String swCode,
            @Nullable String technicalMessage,
            @Nullable String paceOid
    ) {
        if (status == NfcReadStatus.SUCCESS) {
            throw new IllegalArgumentException("Cannot create error result with SUCCESS status");
        }
        return new NfcReadResult(status, null, errorStage, swCode, technicalMessage, "PACE", paceOid);
    }

    /**
     * Returns true if the operation was successful and data is available.
     */
    public boolean isSuccess() {
        return status == NfcReadStatus.SUCCESS && data != null;
    }

    /**
     * Returns the user-friendly message for this result.
     */
    public String getUserMessage() {
        return status.getUserMessage();
    }

    /**
     * Returns true if backend API calls are allowed for this result.
     */
    public boolean allowsBackendCall() {
        return status.allowsBackendCall();
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("NfcReadResult{status=").append(status);
        if (authMethod != null) {
            sb.append(", authMethod=").append(authMethod);
        }
        if (paceOid != null) {
            sb.append(", paceOid=").append(paceOid);
        }
        if (errorStage != null) {
            sb.append(", stage=").append(errorStage);
        }
        if (swCode != null) {
            sb.append(", sw=").append(swCode);
        }
        if (technicalMessage != null) {
            sb.append(", message=").append(technicalMessage);
        }
        sb.append("}");
        return sb.toString();
    }
}
