package com.apim.kunji.util;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;

import java.io.InputStream;
import java.util.logging.Logger;

/**
 * Singleton Azure Blob Storage client with streaming operations.
 *
 * <p>Initialized lazily from the {@code STORAGE_CONNECTION_STRING} environment variable.
 * The Azure SDK uses Netty with a built-in HTTP connection pool internally.
 *
 * <p>All operations are streaming — never load full blobs into memory.
 *
 * <p>Usage:
 * <pre>
 *   try (InputStream is = BlobStorageManager.downloadAsStream("intermediate", "blob-001.gz")) {
 *       // process stream
 *   }
 * </pre>
 */
public final class BlobStorageManager {

    private static final Logger LOG = Logger.getLogger(BlobStorageManager.class.getName());
    private static volatile BlobServiceClient serviceClient;

    private BlobStorageManager() {}

    public static BlobServiceClient getServiceClient() {
        if (serviceClient == null) {
            synchronized (BlobStorageManager.class) {
                if (serviceClient == null) {
                    String connStr = System.getenv("STORAGE_CONNECTION_STRING");
                    if (connStr == null || connStr.isBlank()) {
                        throw new IllegalStateException("STORAGE_CONNECTION_STRING environment variable is not set");
                    }
                    serviceClient = new BlobServiceClientBuilder()
                            .connectionString(connStr)
                            .buildClient();
                    LOG.info("BlobServiceClient initialized");
                }
            }
        }
        return serviceClient;
    }

    public static InputStream downloadAsStream(String containerName, String blobName) {
        BlobClient client = getServiceClient()
                .getBlobContainerClient(containerName)
                .getBlobClient(blobName);
        return client.openInputStream();
    }

    public static void uploadFromStream(String containerName, String blobName,
                                         InputStream data, long length) {
        BlobClient client = getServiceClient()
                .getBlobContainerClient(containerName)
                .getBlobClient(blobName);
        client.upload(data, length, true); // overwrite=true for idempotency
    }

    public static void deleteBlob(String containerName, String blobName) {
        BlobClient client = getServiceClient()
                .getBlobContainerClient(containerName)
                .getBlobClient(blobName);
        client.deleteIfExists();
    }

    // For testing: allows injecting a mock client
    static void setServiceClient(BlobServiceClient client) {
        serviceClient = client;
    }
}
