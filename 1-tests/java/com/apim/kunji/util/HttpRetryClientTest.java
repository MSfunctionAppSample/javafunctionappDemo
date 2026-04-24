package com.apim.kunji.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HttpRetryClientTest {

    private final HttpRetryClient client = HttpRetryClient.builder().build();

    private final HttpRequest testRequest = HttpRequest.newBuilder(URI.create("http://localhost/test"))
            .GET()
            .build();

    @AfterEach
    void resetSender() {
        client.setSender(null);
    }

    /** Minimal HttpResponse stub — avoids Mockito sealed-class restrictions on JDK 25. */
    private static HttpResponse<String> stubResponse(int statusCode) {
        return new HttpResponse<>() {
            @Override public int statusCode() { return statusCode; }
            @Override public HttpRequest request() { return null; }
            @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
            @Override public HttpHeaders headers() {
                return HttpHeaders.of(Map.of(), (k, v) -> true);
            }
            @Override public String body() { return ""; }
            @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
            @Override public URI uri() { return URI.create("http://localhost/test"); }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }

    // --- calculateDelay bounds ---

    @Test
    void calculateDelay_attempt1_inExpectedRange() {
        // base=500ms, ±30% jitter → [350, 650]
        for (int i = 0; i < 100; i++) {
            long delay = client.calculateDelay(1);
            assertTrue(delay >= 350, "delay=" + delay + " should be >= 350");
            assertTrue(delay <= 650, "delay=" + delay + " should be <= 650");
        }
    }

    @Test
    void calculateDelay_attempt2_inExpectedRange() {
        // attempt 2: exponential=1000ms → [700, 1300]
        for (int i = 0; i < 100; i++) {
            long delay = client.calculateDelay(2);
            assertTrue(delay >= 700, "delay=" + delay + " should be >= 700");
            assertTrue(delay <= 1300, "delay=" + delay + " should be <= 1300");
        }
    }

    @Test
    void calculateDelay_highAttempt_cappedAt10s() {
        // cap=10000ms ±30% → max ~13000
        for (int i = 0; i < 50; i++) {
            long delay = client.calculateDelay(20);
            assertTrue(delay <= 13_000, "delay=" + delay + " should not far exceed cap");
            assertTrue(delay >= 1, "delay must be positive");
        }
    }

    // --- Retry on 503, then success ---

    @Test
    void sendWithRetry_503ThenSuccess_returnsSuccessResponse() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        client.setSender(req ->
                calls.incrementAndGet() == 1 ? stubResponse(503) : stubResponse(200));

        HttpResponse<String> result = client.sendWithRetry(testRequest);

        assertEquals(200, result.statusCode());
        assertEquals(2, calls.get());
    }

    // --- 429 retryable — all 3 attempts ---

    @Test
    void sendWithRetry_429Repeatedly_throwsHttpRetryExhaustedException() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        client.setSender(req -> { calls.incrementAndGet(); return stubResponse(429); });

        assertThrows(HttpRetryExhaustedException.class,
                () -> client.sendWithRetry(testRequest));

        assertEquals(3, calls.get());
    }

    // --- IOException on all attempts ---

    @Test
    void sendWithRetry_ioExceptionAllAttempts_throwsHttpRetryExhaustedException() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        client.setSender(req -> {
            calls.incrementAndGet();
            throw new IOException("connection refused");
        });

        HttpRetryExhaustedException ex = assertThrows(HttpRetryExhaustedException.class,
                () -> client.sendWithRetry(testRequest));

        assertTrue(ex.getMessage().contains("All 3 attempts exhausted"));
        assertEquals(3, calls.get());
    }

    // --- Non-retryable 400 — no retry ---

    @Test
    void sendWithRetry_400_returnsImmediatelyWithoutRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        client.setSender(req -> { calls.incrementAndGet(); return stubResponse(400); });

        HttpResponse<String> result = client.sendWithRetry(testRequest);

        assertEquals(400, result.statusCode());
        assertEquals(1, calls.get());
    }

    // --- 200 success on first attempt ---

    @Test
    void sendWithRetry_200OnFirstAttempt_returnsImmediately() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        client.setSender(req -> { calls.incrementAndGet(); return stubResponse(200); });

        HttpResponse<String> result = client.sendWithRetry(testRequest);

        assertEquals(200, result.statusCode());
        assertEquals(1, calls.get());
    }
}
