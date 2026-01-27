package com.demo.passport;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.FileProvider;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "APP";
    private static final String FILE_PROVIDER_SUFFIX = ".fileprovider";
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
    private PreviewView cameraPreview;
    private State currentState = State.CAMERA;
    private Models.MRZKeys mrzKeys;
    private String lastErrorMessage;
    private String pendingPhotoPath;
    private Uri pendingPhotoUri;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ProcessCameraProvider cameraProvider;

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
        cameraPreview = findViewById(R.id.cameraPreview);
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

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
        if (mrzKeys == null) {
            lastErrorMessage = "Нет данных MRZ для чтения NFC";
            setState(State.ERROR);
            return;
        }
        android.nfc.Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag == null) {
            lastErrorMessage = "NFC-тег не найден";
            setState(State.ERROR);
            return;
        }
        lastErrorMessage = null;
        setState(State.NFC_READING);
        Models.NfcResult result;
        try {
            result = NfcPassportReader.readPassport(tag, mrzKeys);
        } catch (Exception e) {
            lastErrorMessage = "Ошибка чтения NFC: " + e.getMessage();
            setState(State.ERROR);
            return;
        }
        BackendApi.sendNfcRaw(NfcPayloadBuilder.build(result), new BackendApi.Callback<Void>() {
            @Override
            public void onSuccess(Void value) {
                runOnUiThread(() -> {
                    lastErrorMessage = null;
                    setState(State.RESULT);
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
                    buildFileProviderAuthority(getPackageName()),
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

    private void updateCameraPreview(State previousState, State newState) {
        boolean wasCameraState = shouldBindCamera(previousState);
        boolean isCameraState = shouldBindCamera(newState);
        if (isCameraState && !wasCameraState) {
            bindCameraPreview();
        } else if (!isCameraState && wasCameraState) {
            unbindCameraPreview();
        }
    }

    private void bindCameraPreview() {
        if (cameraPreview == null) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Camera permission not granted; preview disabled");
            return;
        }
        ListenableFuture<ProcessCameraProvider> future = cameraProviderFuture;
        if (future == null) {
            cameraProviderFuture = ProcessCameraProvider.getInstance(this);
            future = cameraProviderFuture;
        }
        final ListenableFuture<ProcessCameraProvider> cameraFuture = future;
        cameraFuture.addListener(() -> {
            try {
                cameraProvider = cameraFuture.get();
                bindUseCases(cameraProvider);
            } catch (Exception e) {
                Log.e(TAG, "Failed to bind camera preview", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindUseCases(ProcessCameraProvider provider) {
        provider.unbindAll();
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
        CameraSelector selector =
                new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
        provider.bindToLifecycle(this, selector, preview);
    }

    private void unbindCameraPreview() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    static boolean shouldBindCamera(State state) {
        return state == State.CAMERA;
    }

    static String buildFileProviderAuthority(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            throw new IllegalArgumentException("packageName is required");
        }
        return packageName + FILE_PROVIDER_SUFFIX;
    }

    static boolean isFileProviderAuthorityValid(String packageName, String authority) {
        if (authority == null || authority.isEmpty()) {
            return false;
        }
        return authority.equals(buildFileProviderAuthority(packageName));
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
