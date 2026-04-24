package com.apim.kunji.functions.statement.durable;

import com.apim.kunji.util.CorrelationContext;
import com.apim.kunji.util.JsonUtil;
import com.apim.kunji.util.HttpRetryExhaustedException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Durable Activity — polls Kunji ESB for a single account's statement status.
 *
 * <p>Makes <b>ONE attempt</b> per invocation and returns the result as a String.
 * The {@link PollingOrchestrator} owns the retry loop via durable timers.
 *
 * <p>Return values:
 * <ul>
 *   <li>{@code "READY"}           — data available, blob saved</li>
 *   <li>{@code "PENDING"}         — Kunji still processing, orchestrator will retry</li>
 *   <li>{@code "FAILED_PERMANENT"} — unrecoverable error, orchestrator stops retrying</li>
 * </ul>
 *
 * <p><b>Developers:</b> Implement the TODO blocks below.
 */
public class PollKunjiAccountActivity {

    private static final Logger LOG = Logger.getLogger(PollKunjiAccountActivity.class.getName());

    private static final String KUNJI_BASE_URL = System.getenv("KUNJI_BASE_URL");
    private static final String BLOB_CONTAINER  = System.getenv("BLOB_CONTAINER_INTERMEDIATE");

    @FunctionName("PollKunjiAccount")
    public String run(
            @DurableActivityTrigger(name = "accountJson") String accountJson,
            ExecutionContext context) {

        String correlationId = "unknown";

        try {
            // --- 1. Deserialize input ---
            // TODO: Define PollInput record/class (correlationId, accountNumber, requestId, etc.)
            //   PollInput input = JsonUtil.getMapper().readValue(accountJson, PollInput.class);
            //   correlationId = input.correlationId();
            //   CorrelationContext.set(correlationId, input.messageId());

            // --- 2. Call Kunji ESB for this account ---
            // TODO:
            //   HttpRequest req = HttpRequest.newBuilder(
            //       URI.create(KUNJI_BASE_URL + "/statement/status/" + input.accountNumber()))
            //       .header("X-Correlation-ID", correlationId)
            //       .GET()
            //       .build();
            //   HttpResponse<String> resp = httpRetryClient.sendWithRetry(req);

            // --- 3. Check readiness signal ---
            // TODO: Implement isDataReady() per architecture §5.6
            //   if (isDataReady(resp)) {
            //
            //     // --- 4. Save intermediate blob (Write-Blob-First pattern) ---
            //     byte[] compressed = CompressionUtil.compressFromStream(
            //         new ByteArrayInputStream(resp.body().getBytes(StandardCharsets.UTF_8)));
            //     String blobName = correlationId + "/" + input.accountNumber() + ".gz";
            //     BlobStorageManager.uploadFromStream(BLOB_CONTAINER, blobName,
            //         new ByteArrayInputStream(compressed), compressed.length);
            //
            //     return "READY";
            //   }

            // Placeholder — remove when implementing
            LOG.warning("[" + correlationId + "] TODO: implement PollKunjiAccount activity");
            return "PENDING";

        } catch (Exception e) {
            // TODO: once httpRetryClient.sendWithRetry() is called above, add:
            //   } catch (HttpRetryExhaustedException e) { return "PENDING"; }
            // before this catch block.
            LOG.log(Level.SEVERE, "[" + correlationId + "] Unrecoverable error: " + e.getMessage(), e);
            return "FAILED_PERMANENT";

        } finally {
            CorrelationContext.clear();
        }
    }

    /**
     * Determines if Kunji's HTTP response contains ready data.
     *
     * <p>Per architecture §5.6: current signal is GZIP presence in HTTP 200.
     * This is fragile — isolate here so it can be swapped when Kunji improves its contract.
     *
     * TODO: Negotiate with Kunji team for a stable signal (distinct status code or body field).
     */
    private boolean isDataReady(HttpResponse<String> response) {
        if (response.statusCode() != 200) return false;
        // TODO: check Content-Encoding: gzip or response body field
        return response.headers().firstValue("Content-Encoding")
                .map("gzip"::equalsIgnoreCase)
                .orElse(false);
    }
}
