package com.demo.passport;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "APP";
    private static final String BACKEND_URL = "http://192.168.1.125:30450";

    private NfcAdapter nfcAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Log.d(TAG, "UI attached");

        Button btn = findViewById(R.id.btnTakePhoto);

        btn.setOnClickListener(v -> {
            Log.d(TAG, "Take photo clicked");
            // TODO: camera intent
            sendFakePhotoForNow();
        });

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
    }

    private void sendFakePhotoForNow() {
        // ИМИТАЦИЯ backend 200
        Toast.makeText(this, "Документ распознан", Toast.LENGTH_SHORT).show();
        enableNfcMode();
    }

    private void enableNfcMode() {
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC не поддерживается", Toast.LENGTH_LONG).show();
            return;
        }

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_MUTABLE
                );

        nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        Toast.makeText(this, "Приложите паспорт к NFC", Toast.LENGTH_LONG).show();
    }
}
