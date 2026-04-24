package com.apim.kunji.util;

/**
 * Thread-local carrier for request-scoped correlation data.
 *
 * <p>Usage pattern (mandatory in every Function entry point):
 * <pre>
 *   CorrelationContext.set(correlationId, messageId);
 *   try {
 *       // ... service calls, repository calls, logging ...
 *   } finally {
 *       CorrelationContext.clear();
 *   }
 * </pre>
 *
 * <p>For cross-thread propagation (e.g., Virtual Threads), use snapshot/restore:
 * <pre>
 *   var snap = CorrelationContext.snapshot();
 *   executor.submit(() -> { snap.restore(); try { ... } finally { CorrelationContext.clear(); } });
 * </pre>
 */
public final class CorrelationContext {

    private static final ThreadLocal<String> CORRELATION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> MESSAGE_ID = new ThreadLocal<>();

    private CorrelationContext() {}

    public static void set(String correlationId, String messageId) {
        CORRELATION_ID.set(correlationId);
        MESSAGE_ID.set(messageId);
    }

    public static String getCorrelationId() {
        return CORRELATION_ID.get();
    }

    public static String getMessageId() {
        return MESSAGE_ID.get();
    }

    public static void clear() {
        CORRELATION_ID.remove();
        MESSAGE_ID.remove();
    }

    public static CorrelationSnapshot snapshot() {
        return new CorrelationSnapshot(CORRELATION_ID.get(), MESSAGE_ID.get());
    }

    public record CorrelationSnapshot(String correlationId, String messageId) {
        public void restore() {
            CorrelationContext.set(correlationId, messageId);
        }
    }
}
