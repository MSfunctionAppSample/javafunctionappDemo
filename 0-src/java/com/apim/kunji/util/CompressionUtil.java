package com.apim.kunji.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * GZIP compression/decompression utility.
 * Uses built-in java.util.zip — zero external dependencies.
 */
public final class CompressionUtil {

    private static final int BUFFER_SIZE = 8192;

    private CompressionUtil() {}

    public static byte[] compress(byte[] data) throws IOException {
        var baos = new ByteArrayOutputStream(data.length);
        try (var gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
        }
        return baos.toByteArray();
    }

    public static byte[] decompress(byte[] compressed) throws IOException {
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return gzip.readAllBytes();
        }
    }

    /**
     * Wraps an InputStream in a GZIPInputStream for on-the-fly decompression.
     * Caller is responsible for closing the returned stream.
     */
    public static InputStream decompressStream(InputStream compressed) throws IOException {
        return new GZIPInputStream(compressed, BUFFER_SIZE);
    }

    /**
     * Compresses data from an InputStream into a byte array.
     * Suitable for payloads that fit in memory (e.g., API responses before blob upload).
     */
    public static byte[] compressFromStream(InputStream input) throws IOException {
        var baos = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(baos)) {
            input.transferTo(gzip);
        }
        return baos.toByteArray();
    }
}
