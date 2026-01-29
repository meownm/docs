package com.demo.passport;

/**
 * Canonical classification of NFC reading statuses.
 * Used to provide structured diagnostics and control backend API call flow.
 */
public enum NfcReadStatus {
    /**
     * Reading completed successfully, data is available.
     * Backend calls are allowed ONLY with this status.
     */
    SUCCESS,

    /**
     * NFC hardware is not available or disabled on the device.
     */
    NFC_NOT_AVAILABLE,

    /**
     * NFC tag does not support IsoDep technology required for eMRTD.
     */
    NFC_NOT_ISODEP,

    /**
     * Failed to select the passport applet on the chip.
     * Document may not be positioned correctly or may not be an eMRTD.
     */
    APPLET_SELECTION_FAILED,

    /**
     * Basic Access Control (BAC) authentication failed.
     * MRZ data may be incorrect or the document may be damaged.
     */
    BAC_FAILED,

    /**
     * Document requires PACE (Password Authenticated Connection Establishment)
     * which is not supported by this application version.
     *
     * This status is set when BAC fails with SW=0x6985 (CONDITIONS NOT SATISFIED)
     * or when the error indicates PACE is required instead of BAC.
     */
    PACE_REQUIRED,

    /**
     * Error reading Data Group files (DG1/DG2) from the chip.
     */
    DG_READ_ERROR,

    /**
     * Partial read: some data groups were read but validation failed
     * (e.g., DG2 is too small to contain a valid face image).
     */
    PARTIAL_READ,

    /**
     * Unknown or unclassified error during NFC reading.
     */
    UNKNOWN_ERROR;

    /**
     * Returns true if this status represents a client-side error
     * that should NOT be sent to the backend.
     */
    public boolean isClientError() {
        return this != SUCCESS;
    }

    /**
     * Returns true if backend API calls are allowed with this status.
     */
    public boolean allowsBackendCall() {
        return this == SUCCESS;
    }

    /**
     * Returns a user-friendly message in Russian for this status.
     */
    public String getUserMessage() {
        switch (this) {
            case SUCCESS:
                return "Данные успешно считаны";
            case NFC_NOT_AVAILABLE:
                return "NFC недоступен. Включите NFC в настройках устройства.";
            case NFC_NOT_ISODEP:
                return "Документ не поддерживает требуемый протокол NFC. Убедитесь, что это электронный паспорт (eMRTD).";
            case APPLET_SELECTION_FAILED:
                return "Не удалось установить связь с чипом документа. Убедитесь, что документ правильно расположен на NFC-датчике.";
            case BAC_FAILED:
                return "Ошибка аутентификации с чипом. Проверьте корректность данных MRZ (номер документа, дата рождения, срок действия).";
            case PACE_REQUIRED:
                return "Документ использует современную защиту (PACE). Чтение не поддерживается текущей версией приложения.";
            case DG_READ_ERROR:
                return "Ошибка чтения данных с чипа. Попробуйте повторить сканирование, удерживая документ неподвижно.";
            case PARTIAL_READ:
                return "Данные считаны частично. Фото лица отсутствует или повреждено.";
            case UNKNOWN_ERROR:
            default:
                return "Произошла ошибка при чтении NFC. Попробуйте ещё раз.";
        }
    }
}
