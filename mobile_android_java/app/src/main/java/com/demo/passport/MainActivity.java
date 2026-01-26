package com.demo.passport;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMS = 10;

    private Button btnCapture;
    private Button btnNfc;
    private TextView tvResult;

    private MRZKeys mrzKeys = null;
    private final Gson gson = new Gson();

    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
                try {
                    // For demo: get thumbnail bitmap bytes (not full-res)
                    var bmp = (android.graphics.Bitmap) result.getData().getExtras().get("data");
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, bos);
                    byte[] jpeg = bos.toByteArray();

                    tvResult.setText("Отправляю фото на backend...");
                    new Thread(() -> {
                        try {
                            RecognizeResponse resp = BackendApi.recognizePassport(jpeg);
                            runOnUiThread(() -> {
                                if (resp.error != null) {
                                    tvResult.setText("Ошибка распознавания: " + resp.error.error_code + " " + resp.error.message);
                                    btnNfc.setEnabled(false);
                                    mrzKeys = null;
                                } else {
                                    mrzKeys = resp.mrz;
                                    tvResult.setText("MRZ keys:\n" + gson.toJson(resp.mrz));
                                    btnNfc.setEnabled(true);
                                }
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> tvResult.setText("Ошибка запроса: " + e.getMessage()));
                        }
                    }).start();

                } catch (Exception e) {
                    tvResult.setText("Ошибка камеры: " + e.getMessage());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnCapture = findViewById(R.id.btnCapture);
        btnNfc = findViewById(R.id.btnNfc);
        tvResult = findViewById(R.id.tvResult);

        ensurePerms();

        btnCapture.setOnClickListener(v -> {
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraLauncher.launch(i);
        });

        btnNfc.setOnClickListener(v -> {
            tvResult.setText("Поднесите паспорт к NFC. Ожидаю тег...");
            // NFC flow is handled in onNewIntent when the tag is discovered.
        });
    }

    private void ensurePerms() {
        String[] perms = new String[]{Manifest.permission.CAMERA};
        boolean need = false;
        for (String p : perms) {
            if (ActivityCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) need = true;
        }
        if (need) ActivityCompat.requestPermissions(this, perms, REQ_PERMS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
        if (adapter != null) {
            Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            var pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_MUTABLE);
            adapter.enableForegroundDispatch(this, pendingIntent, null, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
        if (adapter != null) adapter.disableForegroundDispatch(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag == null) return;

        if (mrzKeys == null) {
            tvResult.setText("Сначала распознайте MRZ keys по фото (номер/даты).");
            return;
        }

        tvResult.setText("Читаю NFC (eMRTD)...");
        new Thread(() -> {
            try {
                NfcResult nfc = NfcPassportReader.readPassport(tag, mrzKeys);
                BackendApi.sendNfcScan(nfc);
                runOnUiThread(() -> tvResult.setText("NFC-скан сохранён на backend. Web должен показать фото."));
            } catch (Exception e) {
                runOnUiThread(() -> tvResult.setText("Ошибка NFC: " + e.getMessage()));
            }
        }).start();
    }
}
