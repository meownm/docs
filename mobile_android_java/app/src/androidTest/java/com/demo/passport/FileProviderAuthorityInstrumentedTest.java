package com.demo.passport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.net.Uri;

import androidx.core.content.FileProvider;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class FileProviderAuthorityInstrumentedTest {

    @Test
    public void manifestAuthority_matchesExpectedPackage() {
        Context context = ApplicationProvider.getApplicationContext();
        String expectedAuthority = MainActivity.buildFileProviderAuthority(context.getPackageName());
        ProviderInfo providerInfo = context.getPackageManager()
                .resolveContentProvider(expectedAuthority, 0);
        assertNotNull(providerInfo);
        assertEquals(expectedAuthority, providerInfo.authority);
    }

    @Test
    public void manifestAuthority_returnsNullForUnexpectedAuthority() {
        Context context = ApplicationProvider.getApplicationContext();
        ProviderInfo providerInfo = context.getPackageManager()
                .resolveContentProvider("com.demo.passport.unexpected.fileprovider", 0);
        assertNull(providerInfo);
    }

    @Test
    public void fileProviderUri_usesExpectedAuthority() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        String expectedAuthority = MainActivity.buildFileProviderAuthority(context.getPackageName());
        File tempFile = File.createTempFile("passport_test_", ".jpg", context.getCacheDir());
        Uri uri = FileProvider.getUriForFile(context, expectedAuthority, tempFile);
        assertEquals(expectedAuthority, uri.getAuthority());
    }

    @Test
    public void fileProviderUri_rejectsInvalidAuthority() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        String invalidAuthority = "com.demo.passport.invalid.fileprovider";
        File tempFile = File.createTempFile("passport_test_", ".jpg", context.getCacheDir());
        assertThrows(IllegalArgumentException.class,
                () -> FileProvider.getUriForFile(context, invalidAuthority, tempFile));
    }
}
