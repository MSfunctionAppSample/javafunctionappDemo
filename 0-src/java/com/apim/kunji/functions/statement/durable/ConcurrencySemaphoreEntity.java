package com.apim.kunji.functions.statement.durable;

import com.apim.kunji.util.DatabaseConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * DB-backed concurrency semaphore limiting Kunji ESB to {@value MAX_SLOTS} concurrent calls.
 *
 * <p>Uses atomic SQL UPDATE with row-count check — no Durable Entity needed.
 * The Java Durable Functions SDK 1.0.0 does not support Durable Entities.
 *
 * <p>Schema required:
 * <pre>
 *   CREATE TABLE kunji_semaphore (
 *     id          INT PRIMARY KEY DEFAULT 1,
 *     active_count INT NOT NULL DEFAULT 0,
 *     updated_at  DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
 *     CONSTRAINT chk_single_row CHECK (id = 1)
 *   );
 *   INSERT INTO kunji_semaphore (id, active_count) VALUES (1, 0);
 * </pre>
 *
 * <p>Usage:
 * <pre>
 *   if (!ConcurrencySemaphoreEntity.tryAcquire()) {
 *       // semaphore full — orchestrator should use createTimer() and retry
 *   }
 *   try {
 *       // call Kunji
 *   } finally {
 *       ConcurrencySemaphoreEntity.release();
 *   }
 * </pre>
 */
public class ConcurrencySemaphoreEntity {

    private static final Logger LOG = Logger.getLogger(ConcurrencySemaphoreEntity.class.getName());

    static final int MAX_SLOTS = 10;

    // Atomic increment only if under limit — rowcount 0 means denied
    private static final String SQL_ACQUIRE =
            "UPDATE kunji_semaphore SET active_count = active_count + 1, updated_at = SYSUTCDATETIME() " +
            "WHERE id = 1 AND active_count < ?";

    private static final String SQL_RELEASE =
            "UPDATE kunji_semaphore SET active_count = CASE WHEN active_count > 0 THEN active_count - 1 ELSE 0 END, " +
            "updated_at = SYSUTCDATETIME() WHERE id = 1";

    private static final String SQL_COUNT =
            "SELECT active_count FROM kunji_semaphore WHERE id = 1";

    private ConcurrencySemaphoreEntity() {}

    /**
     * Attempts to acquire a semaphore slot atomically.
     *
     * @return {@code true} if a slot was granted; {@code false} if all {@value MAX_SLOTS} slots are active
     * @throws SQLException on DB error
     */
    public static boolean tryAcquire() throws SQLException {
        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_ACQUIRE)) {
            ps.setInt(1, MAX_SLOTS);
            int updated = ps.executeUpdate();
            boolean acquired = updated > 0;
            if (!acquired) {
                LOG.warning("Semaphore full — all " + MAX_SLOTS + " Kunji slots in use");
            }
            return acquired;
        }
    }

    /**
     * Releases a previously acquired semaphore slot.
     * Safe to call even if active_count is already 0 (idempotent floor at 0).
     *
     * @throws SQLException on DB error
     */
    public static void release() throws SQLException {
        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_RELEASE)) {
            ps.executeUpdate();
        }
    }

    /**
     * Returns the current number of active slots. For monitoring/logging only.
     *
     * @throws SQLException on DB error
     */
    public static int activeCount() throws SQLException {
        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_COUNT);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt("active_count") : 0;
        }
    }
}
