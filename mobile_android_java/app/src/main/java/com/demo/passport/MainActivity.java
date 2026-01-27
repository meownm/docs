package com.demo.passport;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;

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
    private static final int REQUEST_TAKE_PHOTO = 1001;

    private NfcAdapter nfcAdapter;
    private Button btnTakePhoto;
    private TextView textStatus;
    private LinearLayout resultContainer;
    private TextView textDocumentNumber;
    private TextView textBirthDate;
    private TextView textExpiryDate;
    private State currentState = State.CAMERA;
    private Models.MRZKeys mrzKeys;
    private String lastErrorMessage;
    private String pendingPhotoPath;
    private Uri pendingPhotoUri;

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
            launchCameraCapture();
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
        if (mrzKeys == null) {
            lastErrorMessage = "Нет данных MRZ для чтения NFC";
            setState(State.ERROR);
            return;
        }
        setState(State.RESULT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_TAKE_PHOTO) {
            return;
        }
        if (resultCode != RESULT_OK) {
            lastErrorMessage = "Съемка отменена";
            setState(State.CAMERA);
            return;
        }
        handleCapturedPhoto();
    }

    private void launchCameraCapture() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) == null) {
            lastErrorMessage = "Камера недоступна";
            setState(State.ERROR);
            return;
        }
        try {
            File tempFile = File.createTempFile("passport_", ".jpg", getCacheDir());
            pendingPhotoPath = tempFile.getAbsolutePath();
            pendingPhotoUri = FileProvider.getUriForFile(
                    this,
                    BuildConfig.APPLICATION_ID + ".fileprovider",
                    tempFile
            );
        } catch (IOException e) {
            lastErrorMessage = "Не удалось подготовить файл для фото";
            setState(State.ERROR);
            return;
        }
        intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingPhotoUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_TAKE_PHOTO);
    }

    private void handleCapturedPhoto() {
        if (pendingPhotoPath == null) {
            lastErrorMessage = "Фото не сохранено";
            setState(State.ERROR);
            return;
        }
        File photoFile = new File(pendingPhotoPath);
        byte[] bytes;
        try {
            bytes = PhotoCaptureUtils.readImageBytes(photoFile);
        } catch (IllegalArgumentException e) {
            lastErrorMessage = "Фото слишком маленькое (нужно > 500KB)";
            setState(State.ERROR);
            return;
        } catch (IOException e) {
            lastErrorMessage = "Не удалось прочитать фото";
            setState(State.ERROR);
            return;
        }
        sendPhotoForRecognition(bytes);
    }

    private void sendPhotoForRecognition(byte[] jpegBytes) {
        lastErrorMessage = null;
        setState(State.PHOTO_SENDING);
        BackendApi.recognizePassport(jpegBytes, new BackendApi.Callback<Models.MRZKeys>() {
            @Override
            public void onSuccess(Models.MRZKeys value) {
                runOnUiThread(() -> {
                    mrzKeys = value;
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
        State previousState = currentState;
        if ((newState == State.NFC_WAIT || newState == State.NFC_READING) && nfcAdapter == null) {
            lastErrorMessage = "NFC не поддерживается";
            newState = State.ERROR;
        }
        currentState = newState;
        UiStateModel uiState = UiStateModel.from(newState, mrzKeys, lastErrorMessage);
        textStatus.setText(uiState.statusText);
        resultContainer.setVisibility(uiState.showResult ? LinearLayout.VISIBLE : LinearLayout.GONE);
        btnTakePhoto.setEnabled(uiState.takePhotoEnabled);
        textDocumentNumber.setText(uiState.documentNumber);
        textBirthDate.setText(uiState.birthDate);
        textExpiryDate.setText(uiState.expiryDate);
        updateNfcDispatch(NfcDispatchTransition.from(previousState, newState));
        if (uiState.toastMessage != null) {
            Toast.makeText(this, uiState.toastMessage, Toast.LENGTH_LONG).show();
        }
    }

    private void updateNfcDispatch(NfcDispatchTransition.Action action) {
        if (nfcAdapter == null) {
            return;
        }
        if (action == NfcDispatchTransition.Action.ENABLE) {
            PendingIntent pendingIntent =
                    PendingIntent.getActivity(
                            this,
                            0,
                            new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                            PendingIntent.FLAG_MUTABLE
                    );
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        } else if (action == NfcDispatchTransition.Action.DISABLE) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }
}
