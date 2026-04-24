package com.apim.kunji.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CompressionUtilTest {

    private static final String SAMPLE = "Hello, Kunji APIM Middleware! This is a test payload for compression.";

    @Test
    void compress_decompress_roundTrip() throws IOException {
        byte[] original = SAMPLE.getBytes(StandardCharsets.UTF_8);
        byte[] compressed = CompressionUtil.compress(original);
        byte[] decompressed = CompressionUtil.decompress(compressed);
        assertArrayEquals(original, decompressed);
    }

    @Test
    void compress_producesSmallOutput_forRepetitiveData() throws IOException {
        // Repetitive data should compress well
        String repetitive = "AAAAAAAAAA".repeat(100);
        byte[] original = repetitive.getBytes(StandardCharsets.UTF_8);
        byte[] compressed = CompressionUtil.compress(original);
        assertTrue(compressed.length < original.length, "Compressed size should be smaller");
    }

    @Test
    void decompress_emptyGzipInput_returnsEmptyArray() throws IOException {
        byte[] empty = CompressionUtil.compress(new byte[0]);
        byte[] result = CompressionUtil.decompress(empty);
        assertArrayEquals(new byte[0], result);
    }

    @Test
    void decompressStream_returnsReadableStream() throws IOException {
        byte[] original = SAMPLE.getBytes(StandardCharsets.UTF_8);
        byte[] compressed = CompressionUtil.compress(original);

        try (InputStream stream = CompressionUtil.decompressStream(new ByteArrayInputStream(compressed))) {
            byte[] result = stream.readAllBytes();
            assertArrayEquals(original, result);
        }
    }

    @Test
    void compressFromStream_roundTrip() throws IOException {
        byte[] original = SAMPLE.getBytes(StandardCharsets.UTF_8);
        byte[] compressed = CompressionUtil.compressFromStream(new ByteArrayInputStream(original));
        byte[] decompressed = CompressionUtil.decompress(compressed);
        assertArrayEquals(original, decompressed);
    }

    @Test
    void compressFromStream_emptyInput_returnsValidGzip() throws IOException {
        byte[] compressed = CompressionUtil.compressFromStream(new ByteArrayInputStream(new byte[0]));
        byte[] decompressed = CompressionUtil.decompress(compressed);
        assertArrayEquals(new byte[0], decompressed);
    }
}
