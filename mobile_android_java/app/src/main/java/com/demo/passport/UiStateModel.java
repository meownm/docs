package com.demo.passport;

class UiStateModel {
    final String statusText;
    final boolean showResult;
    final boolean takePhotoEnabled;
    final String documentNumber;
    final String birthDate;
    final String expiryDate;
    final String toastMessage;

    private UiStateModel(
            String statusText,
            boolean showResult,
            boolean takePhotoEnabled,
            String documentNumber,
            String birthDate,
            String expiryDate,
            String toastMessage
    ) {
        this.statusText = statusText;
        this.showResult = showResult;
        this.takePhotoEnabled = takePhotoEnabled;
        this.documentNumber = documentNumber;
        this.birthDate = birthDate;
        this.expiryDate = expiryDate;
        this.toastMessage = toastMessage;
    }

    static UiStateModel from(MainActivity.State state, Models.MRZKeys mrz, String errorMessage) {
        String emptyDocNumber = "Номер документа: -";
        String emptyBirthDate = "Дата рождения: -";
        String emptyExpiryDate = "Срок действия: -";
        switch (state) {
            case CAMERA:
                return new UiStateModel(
                        "Готово к съемке документа",
                        false,
                        true,
                        emptyDocNumber,
                        emptyBirthDate,
                        emptyExpiryDate,
                        null
                );
            case PHOTO_SENDING:
                return new UiStateModel(
                        "Отправляем фото на распознавание...",
                        false,
                        false,
                        emptyDocNumber,
                        emptyBirthDate,
                        emptyExpiryDate,
                        null
                );
            case NFC_WAIT:
                return new UiStateModel(
                        "Ожидаем NFC-сканирование",
                        false,
                        false,
                        emptyDocNumber,
                        emptyBirthDate,
                        emptyExpiryDate,
                        "Приложите паспорт к NFC"
                );
            case NFC_READING:
                return new UiStateModel(
                        "Считываем данные с NFC...",
                        false,
                        false,
                        emptyDocNumber,
                        emptyBirthDate,
                        emptyExpiryDate,
                        null
                );
            case RESULT:
                String documentNumber = mrz == null ? emptyDocNumber : "Номер документа: " + mrz.document_number;
                String birthDate = mrz == null ? emptyBirthDate : "Дата рождения: " + mrz.date_of_birth;
                String expiryDate = mrz == null ? emptyExpiryDate : "Срок действия: " + mrz.date_of_expiry;
                return new UiStateModel(
                        "Документ распознан",
                        true,
                        true,
                        documentNumber,
                        birthDate,
                        expiryDate,
                        null
                );
            case ERROR:
            default:
                String statusText = errorMessage == null || errorMessage.isEmpty()
                        ? "Произошла ошибка"
                        : "Ошибка: " + errorMessage;
                return new UiStateModel(
                        statusText,
                        false,
                        true,
                        emptyDocNumber,
                        emptyBirthDate,
                        emptyExpiryDate,
                        errorMessage
                );
        }
    }
}
