package com.demo.passport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PhotoCaptureUtilsTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void readImageBytes_acceptsFileAboveMinimumSize() throws IOException {
        File file = temporaryFolder.newFile("photo.jpg");
        int targetSize = PhotoCaptureUtils.MIN_IMAGE_BYTES + 1;
        writeBytes(file, targetSize);

        byte[] data = PhotoCaptureUtils.readImageBytes(file);

        assertEquals(targetSize, data.length);
    }

    @Test
    public void readImageBytes_rejectsSmallFile() throws IOException {
        File file = temporaryFolder.newFile("small.jpg");
        writeBytes(file, 128);

        assertThrows(IllegalArgumentException.class, () -> PhotoCaptureUtils.readImageBytes(file));
    }

    @Test
    public void readImageBytes_missingFileThrows() {
        File file = new File(temporaryFolder.getRoot(), "missing.jpg");

        assertThrows(FileNotFoundException.class, () -> PhotoCaptureUtils.readImageBytes(file));
    }

    private void writeBytes(File file, int size) throws IOException {
        byte[] buffer = new byte[1024];
        int remaining = size;
        try (FileOutputStream stream = new FileOutputStream(file)) {
            while (remaining > 0) {
                int chunk = Math.min(remaining, buffer.length);
                stream.write(buffer, 0, chunk);
                remaining -= chunk;
            }
        }
    }
}
