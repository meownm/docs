package com.demo.passport;

import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "APP";
    public enum State {
        CAMERA,
        PHOTO_SENDING,
        NFC_WAIT,
        NFC_READING,
        RESULT,
        ERROR
    }
    private static final byte[] PLACEHOLDER_JPEG = new byte[] {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46,
            0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            (byte) 0xFF, (byte) 0xDB, 0x00, 0x43, 0x00, 0x03, 0x02, 0x02, 0x03, 0x02,
            0x02, 0x03, 0x03, 0x03, 0x03, 0x04, 0x03, 0x03, 0x04, 0x05, 0x08, 0x05,
            0x05, 0x04, 0x04, 0x05, 0x0A, 0x07, 0x07, 0x06, 0x08, 0x0C, 0x0A, 0x0C,
            0x0C, 0x0B, 0x0A, 0x0B, 0x0B, 0x0D, 0x0E, 0x12, 0x10, 0x0D, 0x0E, 0x11,
            0x0E, 0x0B, 0x0B, 0x10, 0x16, 0x10, 0x11, 0x13, 0x14, 0x15, 0x15, 0x15,
            0x0C, 0x0F, 0x17, 0x18, 0x16, 0x14, 0x18, 0x12, 0x14, 0x15, 0x14,
            (byte) 0xFF, (byte) 0xC0, 0x00, 0x11, 0x08, 0x00, 0x01, 0x00, 0x01, 0x03,
            0x01, 0x11, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01, (byte) 0xFF,
            (byte) 0xC4, 0x00, 0x14, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xFF,
            (byte) 0xC4, 0x00, 0x14, 0x10, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xFF,
            (byte) 0xDA, 0x00, 0x0C, 0x03, 0x01, 0x00, 0x02, 0x11, 0x03, 0x11, 0x00,
            0x3F, 0x00, 0x00, (byte) 0xFF, (byte) 0xD9
    };

    private NfcAdapter nfcAdapter;
    private Button btnTakePhoto;
    private TextView textStatus;
    private LinearLayout resultContainer;
    private TextView textDocumentNumber;
    private TextView textBirthDate;
    private TextView textExpiryDate;
    private State currentState = State.CAMERA;
    private Models.MRZKeys lastMrz;
    private String lastErrorMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Log.d(TAG, "UI attached");

        btnTakePhoto = findViewById(R.id.btnTakePhoto);
        textStatus = findViewById(R.id.textStatus);
        resultContainer = findViewById(R.id.resultContainer);
        textDocumentNumber = findViewById(R.id.textDocumentNumber);
        textBirthDate = findViewById(R.id.textBirthDate);
        textExpiryDate = findViewById(R.id.textExpiryDate);

        btnTakePhoto.setOnClickListener(v -> {
            Log.d(TAG, "Take photo clicked");
            sendPhotoForRecognition();
        });

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        setState(State.CAMERA);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (currentState != State.NFC_WAIT) {
            return;
        }
        setState(State.NFC_READING);
        if (lastMrz == null) {
            lastErrorMessage = "Нет данных MRZ для чтения NFC";
            setState(State.ERROR);
            return;
        }
        setState(State.RESULT);
    }

    private void sendPhotoForRecognition() {
        lastMrz = null;
        lastErrorMessage = null;
        setState(State.PHOTO_SENDING);
        BackendApi.recognizePassport(PLACEHOLDER_JPEG, new BackendApi.Callback<Models.MRZKeys>() {
            @Override
            public void onSuccess(Models.MRZKeys value) {
                runOnUiThread(() -> {
                    lastMrz = value;
                    lastErrorMessage = null;
                    setState(State.NFC_WAIT);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    lastErrorMessage = message;
                    setState(State.ERROR);
                });
            }
        });
    }

    private void setState(State newState) {
        if ((newState == State.NFC_WAIT || newState == State.NFC_READING) && nfcAdapter == null) {
            lastErrorMessage = "NFC не поддерживается";
            newState = State.ERROR;
        }
        currentState = newState;
        UiStateModel uiState = UiStateModel.from(newState, lastMrz, lastErrorMessage);
        textStatus.setText(uiState.statusText);
        resultContainer.setVisibility(uiState.showResult ? LinearLayout.VISIBLE : LinearLayout.GONE);
        btnTakePhoto.setEnabled(uiState.takePhotoEnabled);
        textDocumentNumber.setText(uiState.documentNumber);
        textBirthDate.setText(uiState.birthDate);
        textExpiryDate.setText(uiState.expiryDate);
        updateNfcDispatch(uiState.enableNfc);
        if (uiState.toastMessage != null) {
            Toast.makeText(this, uiState.toastMessage, Toast.LENGTH_LONG).show();
        }
    }

    private void updateNfcDispatch(boolean enable) {
        if (nfcAdapter == null) {
            return;
        }
        if (enable) {
            PendingIntent pendingIntent =
                    PendingIntent.getActivity(
                            this,
                            0,
                            new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                            PendingIntent.FLAG_MUTABLE
                    );
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        } else {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }
}
