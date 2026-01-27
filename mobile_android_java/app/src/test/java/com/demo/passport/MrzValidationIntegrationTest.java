package com.demo.passport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MrzValidationIntegrationTest {

    @Test
    public void invalidMrzInput_producesToastMessage() {
        String validationError = MainActivity.validateMrzInputs("AB123", "1990-01-01", "2030-01-01");
        assertNotNull(validationError);

        UiStateModel model = UiStateModel.from(MainActivity.State.ERROR, null, validationError);
        assertEquals(validationError, model.toastMessage);
    }

    @Test
    public void validMrzInput_populatesUiFromManualKeys() {
        String validationError = MainActivity.validateMrzInputs("AB123", "900101", "300101");
        assertNull(validationError);

        Models.MRZKeys keys = MainActivity.buildManualMrzKeys("AB123", "900101", "300101");
        UiStateModel model = UiStateModel.from(MainActivity.State.RESULT, keys, null);

        assertEquals("Номер документа: AB123", model.documentNumber);
        assertEquals("Дата рождения: 900101", model.birthDate);
        assertEquals("Срок действия: 300101", model.expiryDate);
    }
}
