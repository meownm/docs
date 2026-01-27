package com.demo.passport;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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

import com.google.gson.JsonObject;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "APP";
    private static final String FILE_PROVIDER_SUFFIX = ".fileprovider";
    private static final String MRZ_DATE_REGEX = "\\d{6}";
    private static final String MRZ_DATE_YYYYMMDD_REGEX = "\\d{8}";
    private static final String MRZ_DATE_YYYY_MM_DD_REGEX = "\\d{4}-\\d{2}-\\d{2}";
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
    private Button btnStartNfcManual;
    private EditText inputDocumentNumber;
    private EditText inputBirthDate;
    private EditText inputExpiryDate;
    private TextView textStatus;
    private LinearLayout resultContainer;
    private TextView textDocumentNumber;
    private TextView textBirthDate;
    private TextView textExpiryDate;
    private ImageView imageFace;
    private TextView textDebugRecognize;
    private TextView textDebugNfc;
    private TextView textDebugFace;
    private PreviewView cameraPreview;
    private State currentState = State.CAMERA;
    private Models.MRZKeys mrzKeys;
    private String lastErrorMessage;
    private String lastRecognizeResponse;
    private String lastNfcResponse;
    private String lastFaceResponse;
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
        btnStartNfcManual = findViewById(R.id.btnStartNfcManual);
        inputDocumentNumber = findViewById(R.id.inputDocumentNumber);
        inputBirthDate = findViewById(R.id.inputBirthDate);
        inputExpiryDate = findViewById(R.id.inputExpiryDate);
        textStatus = findViewById(R.id.textStatus);
        resultContainer = findViewById(R.id.resultContainer);
        textDocumentNumber = findViewById(R.id.textDocumentNumber);
        textBirthDate = findViewById(R.id.textBirthDate);
        textExpiryDate = findViewById(R.id.textExpiryDate);
        imageFace = findViewById(R.id.imageFace);
        textDebugRecognize = findViewById(R.id.textDebugRecognize);
        textDebugNfc = findViewById(R.id.textDebugNfc);
        textDebugFace = findViewById(R.id.textDebugFace);
        cameraPreview = findViewById(R.id.cameraPreview);
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        btnTakePhoto.setOnClickListener(v -> {
            Log.d(TAG, "Take photo clicked");
            launchCameraCapture();
        });
        btnStartNfcManual.setOnClickListener(v -> {
            Log.d(TAG, "Manual NFC clicked");
            handleManualNfcStart();
        });
        ManualInputWatcher inputWatcher = new ManualInputWatcher();
        inputDocumentNumber.addTextChangedListener(inputWatcher);
        inputBirthDate.addTextChangedListener(inputWatcher);
        inputExpiryDate.addTextChangedListener(inputWatcher);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        setState(State.CAMERA);
    }

    @Override
    protected void onStart() {
        super.onStart();
        BackendApi.setDebugListener(this::handleDebugResponse);
        updateDebugPanel();
    }

    @Override
    protected void onStop() {
        super.onStop();
        BackendApi.setDebugListener(null);
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
        String validationError = validateNfcResult(result);
        if (validationError != null) {
            lastErrorMessage = validationError;
            setState(State.ERROR);
            return;
        }
        StringBuilder payloadError = new StringBuilder();
        JsonObject payload = tryBuildNfcPayload(result, payloadError);
        if (payload == null) {
            lastErrorMessage = "Ошибка подготовки NFC: " + payloadError;
            setState(State.ERROR);
            return;
        }
        BackendApi.sendNfcRawAndParse(payload, new BackendApi.Callback<Models.NfcScanResponse>() {
            @Override
            public void onSuccess(Models.NfcScanResponse value) {
                String faceUrl = ensureAbsoluteUrl(value.face_image_url);
                if (faceUrl == null || faceUrl.trim().isEmpty()) {
                    runOnUiThread(() -> {
                        lastErrorMessage = "Не удалось получить URL фото";
                        setState(State.ERROR);
                    });
                    return;
                }
                BackendApi.fetchFaceImage(faceUrl, new BackendApi.Callback<byte[]>() {
                    @Override
                    public void onSuccess(byte[] faceBytes) {
                        runOnUiThread(() -> {
                            if (imageFace != null && faceBytes.length > 0) {
                                imageFace.setImageBitmap(
                                        BitmapFactory.decodeByteArray(faceBytes, 0, faceBytes.length)
                                );
                            }
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
                    String validationError = validateMrzKeys(value);
                    if (validationError != null) {
                        lastErrorMessage = validationError;
                        setState(State.ERROR);
                        return;
                    }
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
        if (newState == State.NFC_WAIT) {
            String validationError = validateMrzKeys(mrzKeys);
            if (validationError != null) {
                lastErrorMessage = validationError;
                newState = State.ERROR;
            }
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
        updateManualInputControls();
        if (uiState.toastMessage != null) {
            Toast.makeText(this, uiState.toastMessage, Toast.LENGTH_LONG).show();
        }
    }

    private void handleDebugResponse(String source, String response) {
        if ("recognize".equals(source)) {
            lastRecognizeResponse = response;
        } else if ("nfc".equals(source)) {
            lastNfcResponse = response;
        } else if ("face".equals(source)) {
            lastFaceResponse = response;
        }
        runOnUiThread(this::updateDebugPanel);
    }

    private void updateDebugPanel() {
        if (textDebugRecognize != null) {
            textDebugRecognize.setText(lastRecognizeResponse == null ? "—" : lastRecognizeResponse);
        }
        if (textDebugNfc != null) {
            textDebugNfc.setText(lastNfcResponse == null ? "—" : lastNfcResponse);
        }
        if (textDebugFace != null) {
            textDebugFace.setText(lastFaceResponse == null ? "—" : lastFaceResponse);
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

    static Models.MRZKeys buildManualMrzKeys(
            String documentNumber,
            String birthDate,
            String expiryDate
    ) {
        if (isBlank(documentNumber) || isBlank(birthDate) || isBlank(expiryDate)) {
            return null;
        }
        String normalizedBirth = normalizeMrzDate(birthDate);
        String normalizedExpiry = normalizeMrzDate(expiryDate);
        if (normalizedBirth == null || normalizedExpiry == null) {
            return null;
        }
        Models.MRZKeys keys = new Models.MRZKeys();
        keys.document_number = documentNumber.trim();
        keys.date_of_birth = normalizedBirth;
        keys.date_of_expiry = normalizedExpiry;
        return keys;
    }

    static String validateMrzInputs(String documentNumber, String birthDate, String expiryDate) {
        if (isBlank(documentNumber) || isBlank(birthDate) || isBlank(expiryDate)) {
            return "Заполните номер документа, дату рождения и срок действия";
        }
        boolean birthValid = normalizeMrzDate(birthDate) != null;
        boolean expiryValid = normalizeMrzDate(expiryDate) != null;
        if (!birthValid && !expiryValid) {
            return "Дата рождения и срок действия должны быть в формате YYMMDD (или YYYYMMDD, YYYY-MM-DD)";
        }
        if (!birthValid) {
            return "Дата рождения должна быть в формате YYMMDD (или YYYYMMDD, YYYY-MM-DD)";
        }
        if (!expiryValid) {
            return "Срок действия должен быть в формате YYMMDD (или YYYYMMDD, YYYY-MM-DD)";
        }
        return null;
    }

    static String validateMrzKeys(Models.MRZKeys keys) {
        if (keys == null) {
            return "Нет данных MRZ для запуска NFC";
        }
        return validateMrzInputs(keys.document_number, keys.date_of_birth, keys.date_of_expiry);
    }

    static JsonObject tryBuildNfcPayload(Models.NfcResult result, StringBuilder errorMessage) {
        try {
            return NfcPayloadBuilder.build(result);
        } catch (IllegalArgumentException e) {
            if (errorMessage != null) {
                errorMessage.append(e.getMessage());
            }
            return null;
        }
    }

    static String validateNfcResult(Models.NfcResult result) {
        if (result == null) {
            return "Нет NFC-данных";
        }
        if (result.passport == null || result.passport.isEmpty()) {
            return "Паспортные данные не считаны с чипа";
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String ensureAbsoluteUrl(String url) {
        if (url == null) {
            return null;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return BackendConfig.getBaseUrl() + url;
    }

    private static boolean isValidMrzDate(String value) {
        return value != null && value.trim().matches(MRZ_DATE_REGEX);
    }

    static String normalizeMrzDate(String value) {
        if (isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.matches(MRZ_DATE_REGEX)) {
            return trimmed;
        }
        if (trimmed.matches(MRZ_DATE_YYYYMMDD_REGEX)) {
            return formatMrzDate(trimmed, "yyyyMMdd");
        }
        if (trimmed.matches(MRZ_DATE_YYYY_MM_DD_REGEX)) {
            return formatMrzDate(trimmed, "yyyy-MM-dd");
        }
        return null;
    }

    private static String formatMrzDate(String value, String pattern) {
        SimpleDateFormat inputFormat = new SimpleDateFormat(pattern, Locale.US);
        inputFormat.setLenient(false);
        SimpleDateFormat outputFormat = new SimpleDateFormat("yyMMdd", Locale.US);
        outputFormat.setLenient(false);
        try {
            Date parsed = inputFormat.parse(value);
            if (parsed == null) {
                return null;
            }
            return outputFormat.format(parsed);
        } catch (ParseException e) {
            return null;
        }
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

    private void handleManualNfcStart() {
        Models.MRZKeys keys = buildManualMrzKeys(
                inputDocumentNumber.getText().toString(),
                inputBirthDate.getText().toString(),
                inputExpiryDate.getText().toString()
        );
        String validationError = validateMrzInputs(
                inputDocumentNumber.getText().toString(),
                inputBirthDate.getText().toString(),
                inputExpiryDate.getText().toString()
        );
        if (validationError != null || keys == null) {
            lastErrorMessage = validationError == null
                    ? "Заполните номер документа, дату рождения и срок действия"
                    : validationError;
            setState(State.ERROR);
            return;
        }
        mrzKeys = keys;
        lastErrorMessage = null;
        setState(State.NFC_WAIT);
    }

    private void updateManualInputControls() {
        boolean inputsEnabled = currentState == State.CAMERA
                || currentState == State.RESULT
                || currentState == State.ERROR;
        inputDocumentNumber.setEnabled(inputsEnabled);
        inputBirthDate.setEnabled(inputsEnabled);
        inputExpiryDate.setEnabled(inputsEnabled);
        boolean hasValues = validateMrzInputs(
                inputDocumentNumber.getText().toString(),
                inputBirthDate.getText().toString(),
                inputExpiryDate.getText().toString()
        ) == null;
        btnStartNfcManual.setEnabled(inputsEnabled && hasValues);
    }

    private class ManualInputWatcher implements android.text.TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(android.text.Editable s) {
            updateManualInputControls();
        }
    }
}
