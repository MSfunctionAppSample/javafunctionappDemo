package com.apim.kunji.util;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Generic HTTP client with built-in retry logic. Configure via {@link #builder()}.
 *
 * <p>Implements full-jitter exponential backoff on retryable HTTP status codes and IOExceptions.
 * Throws {@link HttpRetryExhaustedException} when all attempts are exhausted.
 *
 * <p>Usage:
 * <pre>
 *   HttpRetryClient client = HttpRetryClient.builder()
 *           .maxAttempts(3)
 *           .retryableCodes(Set.of(429, 503, 504))
 *           .correlationIdSupplier(MyContext::getCorrelationId)
 *           .build();
 *
 *   HttpRequest req = HttpRequest.newBuilder(URI.create(url))
 *           .header("Content-Type", "application/json")
 *           .POST(HttpRequest.BodyPublishers.ofString(body))
 *           .build();
 *   HttpResponse&lt;String&gt; resp = client.sendWithRetry(req);
 * </pre>
 */
public final class HttpRetryClient {

    private static final Logger LOG = Logger.getLogger(HttpRetryClient.class.getName());

    private final int maxAttempts;
    private final long baseDelayMs;
    private final long capMs;
    private final double jitterFactor;
    private final Set<Integer> retryableCodes;
    private final Supplier<String> correlationIdSupplier;
    private final HttpClient httpClient;

    // Non-null only during tests; production path uses httpClient
    private volatile HttpSender testSender;

    /**
     * Functional interface for the HTTP send operation.
     * Extracted so tests can inject a lambda instead of mocking the sealed HttpClient.
     */
    @FunctionalInterface
    public interface HttpSender {
        HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
    }

    private HttpRetryClient(Builder builder) {
        this.maxAttempts = builder.maxAttempts;
        this.baseDelayMs = builder.baseDelayMs;
        this.capMs = builder.capMs;
        this.jitterFactor = builder.jitterFactor;
        this.retryableCodes = Set.copyOf(builder.retryableCodes);
        this.correlationIdSupplier = builder.correlationIdSupplier;
        this.httpClient = builder.httpClient != null ? builder.httpClient : buildDefaultClient();
        LOG.info("HttpRetryClient initialized (maxAttempts=" + maxAttempts + ", HTTP/2, VT executor)");
    }

    private static HttpClient buildDefaultClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(5))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }

    /** Returns the underlying {@link HttpClient}. */
    public HttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * Sends the request with up to {@code maxAttempts} attempts, retrying on
     * configured retryable HTTP codes and IOExceptions.
     *
     * @throws HttpRetryExhaustedException if all attempts are exhausted
     * @throws IOException                 on non-retryable I/O failure
     * @throws InterruptedException        if the thread is interrupted during backoff
     */
    public HttpResponse<String> sendWithRetry(HttpRequest request)
            throws IOException, InterruptedException {

        HttpSender sender = testSender != null
                ? testSender
                : req -> httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        String correlationId = correlationIdSupplier.get();
        IOException lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            final int currentAttempt = attempt;
            try {
                LOG.fine(() -> String.format("[%s] Attempt %d → %s %s",
                        correlationId, currentAttempt, request.method(), request.uri()));

                HttpResponse<String> response = sender.send(request);
                int status = response.statusCode();

                if (!retryableCodes.contains(status)) {
                    return response;
                }

                LOG.warning(String.format("[%s] Attempt %d returned %d — retryable",
                        correlationId, attempt, status));
                lastException = new IOException("HTTP " + status + " from " + request.uri());

            } catch (IOException e) {
                LOG.warning(String.format("[%s] Attempt %d IOException: %s",
                        correlationId, attempt, e.getMessage()));
                lastException = e;
            }

            if (attempt < maxAttempts) {
                long delay = calculateDelay(attempt);
                LOG.fine(() -> String.format("[%s] Sleeping %d ms before retry", correlationId, delay));
                Thread.sleep(delay);
            }
        }

        throw new HttpRetryExhaustedException(
                String.format("All %d attempts exhausted for %s %s",
                        maxAttempts, request.method(), request.uri()),
                lastException);
    }

    /**
     * Full-jitter exponential backoff: min(cap, base * 2^(attempt-1)) then ±jitterFactor.
     * Package-private for testing.
     */
    long calculateDelay(int attempt) {
        long exponential = Math.min(capMs, baseDelayMs * (1L << (attempt - 1)));
        double jitter = 1.0 + (ThreadLocalRandom.current().nextDouble() * 2 - 1) * jitterFactor;
        return Math.max(1, (long) (exponential * jitter));
    }

    /** For testing: inject a sender lambda instead of mocking the sealed HttpClient. */
    void setSender(HttpSender sender) {
        testSender = sender;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link HttpRetryClient}. All fields have sensible defaults. */
    public static final class Builder {
        private int maxAttempts = 3;
        private long baseDelayMs = 500;
        private long capMs = 10_000;
        private double jitterFactor = 0.30;
        private Set<Integer> retryableCodes = Set.of(408, 429, 503, 504);
        private Supplier<String> correlationIdSupplier = () -> "";
        private HttpClient httpClient;

        /** Maximum number of send attempts (default: 3). */
        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        /** Base delay for exponential backoff in milliseconds (default: 500). */
        public Builder baseDelayMs(long baseDelayMs) {
            this.baseDelayMs = baseDelayMs;
            return this;
        }

        /** Maximum backoff cap in milliseconds (default: 10 000). */
        public Builder capMs(long capMs) {
            this.capMs = capMs;
            return this;
        }

        /** Jitter factor as a fraction of the delay, e.g. 0.30 = ±30% (default: 0.30). */
        public Builder jitterFactor(double jitterFactor) {
            this.jitterFactor = jitterFactor;
            return this;
        }

        /** HTTP status codes that trigger a retry (default: 408, 429, 503, 504). */
        public Builder retryableCodes(Set<Integer> retryableCodes) {
            this.retryableCodes = retryableCodes;
            return this;
        }

        /**
         * Supplier for a correlation/trace ID included in log messages (default: empty string).
         * Example: {@code CorrelationContext::getCorrelationId}
         */
        public Builder correlationIdSupplier(Supplier<String> correlationIdSupplier) {
            this.correlationIdSupplier = correlationIdSupplier;
            return this;
        }

        /**
         * Supply a custom {@link HttpClient} (default: HTTP/2, 5 s connect timeout,
         * virtual-thread executor).
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public HttpRetryClient build() {
            return new HttpRetryClient(this);
        }
    }
}
