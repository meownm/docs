package com.demo.passport;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public final class PhotoCaptureUtils {
    static final int MIN_IMAGE_BYTES = 500 * 1024;

    public static byte[] readImageBytes(File file) throws IOException {
        return readImageBytes(file, MIN_IMAGE_BYTES);
    }

    public static byte[] readImageBytes(File file, int minBytes) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("File must not be null");
        }
        if (!file.exists()) {
            throw new FileNotFoundException("File does not exist");
        }
        long length = file.length();
        if (length <= minBytes) {
            throw new IllegalArgumentException("Image too small");
        }
        if (length > Integer.MAX_VALUE) {
            throw new IOException("File too large");
        }
        byte[] buffer = new byte[(int) length];
        try (FileInputStream stream = new FileInputStream(file)) {
            int offset = 0;
            int read;
            while (offset < buffer.length
                    && (read = stream.read(buffer, offset, buffer.length - offset)) != -1) {
                offset += read;
            }
            if (offset < buffer.length) {
                throw new IOException("Failed to read entire file");
            }
        }
        return buffer;
    }

    private PhotoCaptureUtils() {}
}
