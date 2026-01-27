package com.demo.passport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UiStateModelTest {
    @Test
    public void from_cameraState_resetsUi() {
        UiStateModel model = UiStateModel.from(MainActivity.State.CAMERA, null, null);

        assertEquals("Готово к съемке документа", model.statusText);
        assertFalse(model.showResult);
        assertTrue(model.takePhotoEnabled);
        assertEquals("Номер документа: -", model.documentNumber);
        assertEquals("Дата рождения: -", model.birthDate);
        assertEquals("Срок действия: -", model.expiryDate);
        assertNull(model.toastMessage);
    }

    @Test
    public void from_resultState_populatesMrz() {
        Models.MRZKeys mrz = new Models.MRZKeys();
        mrz.document_number = "AB123";
        mrz.date_of_birth = "1990-01-01";
        mrz.date_of_expiry = "2030-01-01";

        UiStateModel model = UiStateModel.from(MainActivity.State.RESULT, mrz, null);

        assertEquals("Документ распознан", model.statusText);
        assertTrue(model.showResult);
        assertTrue(model.takePhotoEnabled);
        assertEquals("Номер документа: AB123", model.documentNumber);
        assertEquals("Дата рождения: 1990-01-01", model.birthDate);
        assertEquals("Срок действия: 2030-01-01", model.expiryDate);
    }

    @Test
    public void from_nfcWait_enablesNfcAndShowsPrompt() {
        UiStateModel model = UiStateModel.from(MainActivity.State.NFC_WAIT, null, null);

        assertEquals("Ожидаем NFC-сканирование", model.statusText);
        assertFalse(model.showResult);
        assertFalse(model.takePhotoEnabled);
        assertEquals("Номер документа: -", model.documentNumber);
        assertEquals("Дата рождения: -", model.birthDate);
        assertEquals("Срок действия: -", model.expiryDate);
        assertEquals("Приложите паспорт к NFC", model.toastMessage);
    }

    @Test
    public void from_errorState_exposesMessage() {
        UiStateModel model = UiStateModel.from(MainActivity.State.ERROR, null, "boom");

        assertEquals("Ошибка: boom", model.statusText);
        assertFalse(model.showResult);
        assertTrue(model.takePhotoEnabled);
        assertEquals("Номер документа: -", model.documentNumber);
        assertEquals("Дата рождения: -", model.birthDate);
        assertEquals("Срок действия: -", model.expiryDate);
        assertEquals("boom", model.toastMessage);
    }
}
