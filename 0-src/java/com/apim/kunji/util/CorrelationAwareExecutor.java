package com.apim.kunji.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * Executes tasks on Virtual Threads with automatic CorrelationContext propagation.
 *
 * <p>Captures the caller's correlation context at submission time and restores it
 * on the child Virtual Thread before executing the task. Cleans up after execution.
 *
 * <p>Usage:
 * <pre>
 *   Future&lt;String&gt; result = CorrelationAwareExecutor.submit(() -> callKunji(accountId));
 * </pre>
 */
public final class CorrelationAwareExecutor {

    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private CorrelationAwareExecutor() {}

    public static <T> Future<T> submit(Supplier<T> task) {
        CorrelationContext.CorrelationSnapshot snap = CorrelationContext.snapshot();
        return EXECUTOR.submit(() -> {
            snap.restore();
            try {
                return task.get();
            } finally {
                CorrelationContext.clear();
            }
        });
    }

    public static Future<?> run(Runnable task) {
        CorrelationContext.CorrelationSnapshot snap = CorrelationContext.snapshot();
        return EXECUTOR.submit(() -> {
            snap.restore();
            try {
                task.run();
            } finally {
                CorrelationContext.clear();
            }
        });
    }
}
