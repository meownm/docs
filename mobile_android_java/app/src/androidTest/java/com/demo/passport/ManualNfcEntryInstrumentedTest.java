package com.demo.passport;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.widget.Button;
import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ManualNfcEntryInstrumentedTest {

    @Test
    public void manualNfcButton_disabledWhenInputsIncomplete() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                EditText doc = activity.findViewById(R.id.inputDocumentNumber);
                EditText birth = activity.findViewById(R.id.inputBirthDate);
                Button button = activity.findViewById(R.id.btnStartNfcManual);

                doc.setText("AB123");
                birth.setText("");

                assertFalse(button.isEnabled());
            });
        }
    }

    @Test
    public void manualNfcButton_enabledWhenInputsComplete() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                EditText doc = activity.findViewById(R.id.inputDocumentNumber);
                EditText birth = activity.findViewById(R.id.inputBirthDate);
                EditText expiry = activity.findViewById(R.id.inputExpiryDate);
                Button button = activity.findViewById(R.id.btnStartNfcManual);

                doc.setText("AB123");
                birth.setText("1990-01-01");
                expiry.setText("2030-01-01");

                assertTrue(button.isEnabled());
            });
        }
    }
}
