package com.demo.passport;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

/**
 * Diagnostic screen for displaying NFC reading results.
 * Shows all raw data from the NFC reading session in a structured format.
 *
 * Sections order (per specification):
 * 1. NFC Session
 * 2. Access & MRZ Keys
 * 3. DG1 (MRZ Data)
 * 4. DG2 (Face Image)
 * 5. Other Data Groups
 * 6. Errors & Warnings
 */
public class NfcDiagnosticActivity extends AppCompatActivity {

    private static final String EXTRA_DIAGNOSTIC_DATA = "diagnostic_data";
    private static final String EXTRA_DG2_RAW_BYTES = "dg2_raw_bytes";

    // Section 1: NFC Session
    private TextView textSessionStatus;
    private TextView textAccessMethod;
    private TextView textPaceSupported;
    private TextView textBacSupported;
    private TextView textDocumentType;
    private TextView textIssuingCountry;
    private TextView textChipInfo;
    private TextView textReadTime;
    private TextView textLdsVersion;

    // Section 2: Access & MRZ Keys
    private TextView textDocNumberMasked;
    private TextView textMrzDob;
    private TextView textMrzExpiry;
    private TextView textMrzKeyHash;

    // Section 3: DG1 (MRZ Data)
    private TextView textDg1DocNumber;
    private TextView textDg1IssuingState;
    private TextView textDg1Nationality;
    private TextView textDg1Surname;
    private TextView textDg1GivenNames;
    private TextView textDg1Dob;
    private TextView textDg1Sex;
    private TextView textDg1Expiry;
    private TextView textDg1Optional;
    private TextView textDg1RawSize;

    // Section 4: DG2 (Face Image)
    private TextView textDg2Status;
    private ImageView imageDg2Preview;
    private TextView textDg2Format;
    private TextView textDg2Width;
    private TextView textDg2Height;
    private TextView textDg2Size;

    // Section 5: Other Data Groups
    private TextView textDg3Presence;
    private TextView textDg11Presence;
    private TextView textDg12Presence;
    private TextView textDg14Presence;

    // Section 6: Errors
    private LinearLayout containerErrors;
    private TextView textNoErrors;
    private LinearLayout sectionErrors;

    private Button btnClose;

    /**
     * Creates an Intent to launch this activity with the given diagnostic data.
     */
    public static Intent createIntent(@NonNull Context context,
                                       @NonNull NfcDiagnosticData data) {
        Intent intent = new Intent(context, NfcDiagnosticActivity.class);
        intent.putExtra(EXTRA_DIAGNOSTIC_DATA, data);
        // Pass DG2 raw bytes separately since it's transient
        if (data.dg2RawBytes != null) {
            intent.putExtra(EXTRA_DG2_RAW_BYTES, data.dg2RawBytes);
        }
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nfc_diagnostic);

        initViews();

        NfcDiagnosticData data = extractDiagnosticData();
        if (data == null) {
            finish();
            return;
        }

        displayDiagnosticData(data);

        btnClose.setOnClickListener(v -> finish());
    }

    private void initViews() {
        // Section 1: NFC Session
        textSessionStatus = findViewById(R.id.textSessionStatus);
        textAccessMethod = findViewById(R.id.textAccessMethod);
        textPaceSupported = findViewById(R.id.textPaceSupported);
        textBacSupported = findViewById(R.id.textBacSupported);
        textDocumentType = findViewById(R.id.textDocumentType);
        textIssuingCountry = findViewById(R.id.textIssuingCountry);
        textChipInfo = findViewById(R.id.textChipInfo);
        textReadTime = findViewById(R.id.textReadTime);
        textLdsVersion = findViewById(R.id.textLdsVersion);

        // Section 2: Access & MRZ Keys
        textDocNumberMasked = findViewById(R.id.textDocNumberMasked);
        textMrzDob = findViewById(R.id.textMrzDob);
        textMrzExpiry = findViewById(R.id.textMrzExpiry);
        textMrzKeyHash = findViewById(R.id.textMrzKeyHash);

        // Section 3: DG1 (MRZ Data)
        textDg1DocNumber = findViewById(R.id.textDg1DocNumber);
        textDg1IssuingState = findViewById(R.id.textDg1IssuingState);
        textDg1Nationality = findViewById(R.id.textDg1Nationality);
        textDg1Surname = findViewById(R.id.textDg1Surname);
        textDg1GivenNames = findViewById(R.id.textDg1GivenNames);
        textDg1Dob = findViewById(R.id.textDg1Dob);
        textDg1Sex = findViewById(R.id.textDg1Sex);
        textDg1Expiry = findViewById(R.id.textDg1Expiry);
        textDg1Optional = findViewById(R.id.textDg1Optional);
        textDg1RawSize = findViewById(R.id.textDg1RawSize);

        // Section 4: DG2 (Face Image)
        textDg2Status = findViewById(R.id.textDg2Status);
        imageDg2Preview = findViewById(R.id.imageDg2Preview);
        textDg2Format = findViewById(R.id.textDg2Format);
        textDg2Width = findViewById(R.id.textDg2Width);
        textDg2Height = findViewById(R.id.textDg2Height);
        textDg2Size = findViewById(R.id.textDg2Size);

        // Section 5: Other Data Groups
        textDg3Presence = findViewById(R.id.textDg3Presence);
        textDg11Presence = findViewById(R.id.textDg11Presence);
        textDg12Presence = findViewById(R.id.textDg12Presence);
        textDg14Presence = findViewById(R.id.textDg14Presence);

        // Section 6: Errors
        containerErrors = findViewById(R.id.containerErrors);
        textNoErrors = findViewById(R.id.textNoErrors);
        sectionErrors = findViewById(R.id.sectionErrors);

        btnClose = findViewById(R.id.btnClose);
    }

    @Nullable
    private NfcDiagnosticData extractDiagnosticData() {
        Intent intent = getIntent();
        if (intent == null) {
            return null;
        }

        NfcDiagnosticData data;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data = intent.getSerializableExtra(EXTRA_DIAGNOSTIC_DATA, NfcDiagnosticData.class);
        } else {
            @SuppressWarnings("deprecation")
            NfcDiagnosticData legacyData =
                    (NfcDiagnosticData) intent.getSerializableExtra(EXTRA_DIAGNOSTIC_DATA);
            data = legacyData;
        }

        if (data == null) {
            return null;
        }

        // Restore transient dg2RawBytes
        byte[] dg2Bytes = intent.getByteArrayExtra(EXTRA_DG2_RAW_BYTES);
        if (dg2Bytes != null) {
            data.dg2RawBytes = dg2Bytes;
        }

        return data;
    }

    private void displayDiagnosticData(@NonNull NfcDiagnosticData data) {
        displayNfcSession(data);
        displayMrzKeys(data);
        displayDg1(data);
        displayDg2(data);
        displayOtherDg(data);
        displayErrors(data);
    }

    private void displayNfcSession(@NonNull NfcDiagnosticData data) {
        textSessionStatus.setText(formatField("status", data.status));
        textAccessMethod.setText(formatField("access_method_used", data.accessMethodUsed));
        textPaceSupported.setText(formatField("pace_supported", data.paceSupported));
        textBacSupported.setText(formatField("bac_supported", data.bacSupported));
        textDocumentType.setText(formatField("document_type", data.documentType));
        textIssuingCountry.setText(formatField("issuing_country", data.issuingCountry));
        textChipInfo.setText(formatField("chip_info", data.chipInfo));
        textReadTime.setText(formatField("read_time_ms", data.readTimeMs));
        textLdsVersion.setText(formatField("lds_version", data.ldsVersion));
    }

    private void displayMrzKeys(@NonNull NfcDiagnosticData data) {
        textDocNumberMasked.setText(formatField("document_number_masked", data.documentNumberMasked));
        textMrzDob.setText(formatField("date_of_birth", data.dateOfBirth));
        textMrzExpiry.setText(formatField("date_of_expiry", data.dateOfExpiry));
        textMrzKeyHash.setText(formatField("mrz_key_hash", data.mrzKeyHash));
    }

    private void displayDg1(@NonNull NfcDiagnosticData data) {
        textDg1DocNumber.setText(formatField("document_number", data.dg1DocumentNumber));
        textDg1IssuingState.setText(formatField("issuing_state", data.dg1IssuingState));
        textDg1Nationality.setText(formatField("nationality", data.dg1Nationality));
        textDg1Surname.setText(formatField("surname", data.dg1Surname));
        textDg1GivenNames.setText(formatField("given_names", data.dg1GivenNames));
        textDg1Dob.setText(formatField("date_of_birth", data.dg1DateOfBirth));
        textDg1Sex.setText(formatField("sex", data.dg1Sex));
        textDg1Expiry.setText(formatField("date_of_expiry", data.dg1DateOfExpiry));
        textDg1Optional.setText(formatField("optional_data_raw", data.dg1OptionalDataRaw));

        if (data.dg1RawSize > 0) {
            textDg1RawSize.setText(String.format(Locale.US, "[raw_size: %d bytes]", data.dg1RawSize));
            textDg1RawSize.setVisibility(View.VISIBLE);
        } else {
            textDg1RawSize.setVisibility(View.GONE);
        }
    }

    private void displayDg2(@NonNull NfcDiagnosticData data) {
        if (!data.dg2Present) {
            textDg2Status.setText("DG2: not available");
            imageDg2Preview.setVisibility(View.GONE);
            textDg2Format.setVisibility(View.GONE);
            textDg2Width.setVisibility(View.GONE);
            textDg2Height.setVisibility(View.GONE);
            textDg2Size.setVisibility(View.GONE);
            return;
        }

        textDg2Status.setText(formatField("status", "present"));

        // Try to display the face image
        Bitmap faceImage = data.decodeFaceImage();
        if (faceImage != null) {
            imageDg2Preview.setImageBitmap(faceImage);
            imageDg2Preview.setVisibility(View.VISIBLE);
        } else {
            imageDg2Preview.setVisibility(View.GONE);
        }

        textDg2Format.setText(formatField("image_format", data.dg2ImageFormat));
        textDg2Format.setVisibility(View.VISIBLE);

        if (data.dg2WidthPx > 0) {
            textDg2Width.setText(formatField("width_px", data.dg2WidthPx));
            textDg2Width.setVisibility(View.VISIBLE);
        } else {
            textDg2Width.setVisibility(View.GONE);
        }

        if (data.dg2HeightPx > 0) {
            textDg2Height.setText(formatField("height_px", data.dg2HeightPx));
            textDg2Height.setVisibility(View.VISIBLE);
        } else {
            textDg2Height.setVisibility(View.GONE);
        }

        textDg2Size.setText(formatField("size_bytes", data.dg2SizeBytes));
        textDg2Size.setVisibility(View.VISIBLE);
    }

    private void displayOtherDg(@NonNull NfcDiagnosticData data) {
        // DG3 (Fingerprint) - never read per constraints
        textDg3Presence.setText("DG3 (Fingerprint): not read (EAC not implemented)");

        // DG11, DG12, DG14 - presence only
        textDg11Presence.setText(formatDgPresence("DG11", data.dg11Present));
        textDg12Presence.setText(formatDgPresence("DG12", data.dg12Present));
        textDg14Presence.setText(formatDgPresence("DG14", data.dg14Present));
    }

    private void displayErrors(@NonNull NfcDiagnosticData data) {
        containerErrors.removeAllViews();

        if (data.errors.isEmpty()) {
            textNoErrors.setVisibility(View.VISIBLE);
            textNoErrors.setText("No errors");
            return;
        }

        textNoErrors.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < data.errors.size(); i++) {
            NfcDiagnosticData.DiagnosticError error = data.errors.get(i);
            View errorView = createErrorView(inflater, error, i + 1);
            containerErrors.addView(errorView);
        }
    }

    private View createErrorView(LayoutInflater inflater,
                                  NfcDiagnosticData.DiagnosticError error,
                                  int index) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, 8, 0, 8);

        // Error header
        TextView header = new TextView(this);
        header.setText(String.format(Locale.US, "Error #%d", index));
        header.setTextColor(0xFFD32F2F);
        header.setTextSize(14);
        container.addView(header);

        // Error fields
        addErrorField(container, "stage", error.stage);
        addErrorField(container, "error_code", error.errorCode);
        addErrorField(container, "error_message", error.errorMessage);
        addErrorField(container, "sw", error.sw);

        return container;
    }

    private void addErrorField(LinearLayout container, String name, @Nullable String value) {
        if (value == null || value.isEmpty()) {
            return;
        }

        TextView field = new TextView(this);
        field.setText(formatField(name, value));
        field.setTextSize(13);
        field.setTypeface(android.graphics.Typeface.MONOSPACE);
        container.addView(field);
    }

    @NonNull
    private String formatField(@NonNull String name, @Nullable String value) {
        return name + ": " + (value != null && !value.isEmpty() ? value : "-");
    }

    @NonNull
    private String formatField(@NonNull String name, boolean value) {
        return name + ": " + value;
    }

    @NonNull
    private String formatField(@NonNull String name, long value) {
        return name + ": " + value;
    }

    @NonNull
    private String formatField(@NonNull String name, int value) {
        return name + ": " + value;
    }

    @NonNull
    private String formatDgPresence(@NonNull String dgName, boolean present) {
        return dgName + ": " + (present ? "present" : "not available");
    }
}
