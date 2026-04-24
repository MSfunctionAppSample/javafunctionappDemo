package com.apim.kunji.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationContextTest {

    @AfterEach
    void cleanup() {
        CorrelationContext.clear();
    }

    @Test
    void setAndGet_returnsCorrectValues() {
        CorrelationContext.set("corr-123", "msg-456");
        assertEquals("corr-123", CorrelationContext.getCorrelationId());
        assertEquals("msg-456", CorrelationContext.getMessageId());
    }

    @Test
    void clear_removesValues() {
        CorrelationContext.set("corr-123", "msg-456");
        CorrelationContext.clear();
        assertNull(CorrelationContext.getCorrelationId());
        assertNull(CorrelationContext.getMessageId());
    }

    @Test
    void snapshot_capturesCurrentValues() {
        CorrelationContext.set("corr-snap", "msg-snap");
        CorrelationContext.CorrelationSnapshot snap = CorrelationContext.snapshot();
        assertEquals("corr-snap", snap.correlationId());
        assertEquals("msg-snap", snap.messageId());
    }

    @Test
    void snapshotRestore_restoresValues() {
        CorrelationContext.set("corr-restore", "msg-restore");
        CorrelationContext.CorrelationSnapshot snap = CorrelationContext.snapshot();

        CorrelationContext.clear();
        assertNull(CorrelationContext.getCorrelationId());

        snap.restore();
        assertEquals("corr-restore", CorrelationContext.getCorrelationId());
        assertEquals("msg-restore", CorrelationContext.getMessageId());
    }

    @Test
    void threadIsolation_otherThreadSeesNull() throws InterruptedException {
        CorrelationContext.set("corr-main", "msg-main");

        AtomicReference<String> otherThreadValue = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread.ofVirtual().start(() -> {
            otherThreadValue.set(CorrelationContext.getCorrelationId());
            latch.countDown();
        });

        latch.await();
        assertNull(otherThreadValue.get(), "Other thread should not see main thread's context");
        assertEquals("corr-main", CorrelationContext.getCorrelationId());
    }

    @Test
    void snapshotRestore_acrossVirtualThread() throws Exception {
        CorrelationContext.set("corr-vt", "msg-vt");
        CorrelationContext.CorrelationSnapshot snap = CorrelationContext.snapshot();

        AtomicReference<String> childCorrelation = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread.ofVirtual().start(() -> {
            snap.restore();
            childCorrelation.set(CorrelationContext.getCorrelationId());
            CorrelationContext.clear();
            latch.countDown();
        });

        latch.await();
        assertEquals("corr-vt", childCorrelation.get());
    }
}
