package com.apim.kunji.functions.statement.durable;

import com.apim.kunji.util.BlobStorageManager;
import com.apim.kunji.util.CompressionUtil;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Durable Activity — merges all intermediate account blobs into a single consolidated payload.
 *
 * <p><b>Stream-through design (per architecture §5.5):</b>
 * Process one blob at a time: {@code openInputStream()} → {@code GZIPInputStream} → append →
 * release. Never hold multiple blobs in memory simultaneously.
 *
 * <p>Failed accounts are included as structured error entries in the final payload so the
 * consumer knows which accounts are missing.
 *
 * <p><b>Write-Blob-First pattern (per architecture §4.5):</b>
 * Upload consolidated blob before updating DB status. Blob upload is idempotent (overwrite = safe).
 * If DB write fails afterwards, blob becomes an orphan cleaned by Timer Trigger.
 *
 * <p><b>Developers:</b> Implement the TODO blocks below.
 */
public class ConsolidatorActivity {

    private static final Logger LOG = Logger.getLogger(ConsolidatorActivity.class.getName());

    private static final String INTERMEDIATE_CONTAINER = System.getenv("BLOB_CONTAINER_INTERMEDIATE");
    private static final String CONSOLIDATED_CONTAINER = System.getenv("BLOB_CONTAINER_CONSOLIDATED");

    @FunctionName("ConsolidatorActivity")
    public void run(
            @DurableActivityTrigger(name = "correlationId") String correlationId,
            ExecutionContext context) {

        try {
            // --- 1. List all intermediate blobs for this correlationId ---
            // TODO: List blobs under correlationId/ prefix in INTERMEDIATE_CONTAINER
            //   ListBlobsOptions opts = new ListBlobsOptions()
            //       .setPrefix(correlationId + "/");
            //   Iterable<BlobItem> blobs = BlobStorageManager.getServiceClient()
            //       .getBlobContainerClient(INTERMEDIATE_CONTAINER)
            //       .listBlobs(opts, null);

            // --- 2. Stream-through merge (one blob at a time) ---
            // TODO: Merge each intermediate blob into a consolidated output stream.
            //   ByteArrayOutputStream mergedJson = new ByteArrayOutputStream();
            //   mergedJson.write('[');
            //   boolean first = true;
            //
            //   for (BlobItem blobItem : blobs) {
            //       if (!first) mergedJson.write(',');
            //       first = false;
            //
            //       try (InputStream compressed = BlobStorageManager.downloadAsStream(
            //               INTERMEDIATE_CONTAINER, blobItem.getName());
            //            InputStream decompressed = CompressionUtil.decompressStream(compressed)) {
            //
            //           decompressed.transferTo(mergedJson);  // stream-through, no full in-memory load
            //
            //       } catch (IOException e) {
            //           // Include structured error entry for failed account
            //           String errorEntry = String.format("{\"error\":\"failed\",\"blob\":\"%s\"}",
            //               blobItem.getName());
            //           mergedJson.write(errorEntry.getBytes());
            //           LOG.warning("[" + correlationId + "] Blob read failed: " + blobItem.getName());
            //       }
            //   }
            //   mergedJson.write(']');

            // --- 3. Compress and upload consolidated blob (Write-Blob-First) ---
            // TODO:
            //   byte[] compressed = CompressionUtil.compressFromStream(
            //       new ByteArrayInputStream(mergedJson.toByteArray()));
            //   String consolidatedBlobName = correlationId + "-consolidated.gz";
            //   BlobStorageManager.uploadFromStream(
            //       CONSOLIDATED_CONTAINER, consolidatedBlobName,
            //       new ByteArrayInputStream(compressed), compressed.length);

            LOG.warning("[" + correlationId + "] TODO: implement ConsolidatorActivity");

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[" + correlationId + "] Consolidation failed: " + e.getMessage(), e);
            throw new RuntimeException("Consolidation failed for correlationId: " + correlationId, e);
        }
    }
}
