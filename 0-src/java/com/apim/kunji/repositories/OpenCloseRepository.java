package com.apim.kunji.repositories;

import com.apim.kunji.util.DatabaseConnectionManager;

import java.sql.Connection;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.logging.Logger;

/**
 * Repository for the Open/Close status table.
 *
 * <p>The {@code openclose} table holds a single row representing the current system status.
 * Every write is an UPSERT — the SP inserts the row on first call and updates it thereafter.
 *
 * <p>Expected DB schema:
 * <pre>
 *   CREATE TABLE openclose (
 *     id         INT         NOT NULL CONSTRAINT PK_openclose PRIMARY KEY DEFAULT 1,
 *     status     BIT         NOT NULL,
 *     updated_at DATETIME2   NOT NULL DEFAULT SYSUTCDATETIME(),
 *     CONSTRAINT chk_single_row CHECK (id = 1)
 *   );
 *   INSERT INTO openclose (id, status) VALUES (1, 0);   -- initial state: Closed
 * </pre>
 *
 * <p>Expected stored procedure:
 * <pre>
 *   CREATE PROCEDURE sp_SetOpenCloseStatus
 *       @Status    BIT,
 *       @UpdatedAt DATETIME2
 *   AS BEGIN
 *       MERGE openclose WITH (HOLDLOCK) AS target
 *       USING (SELECT 1 AS id) AS source ON target.id = source.id
 *       WHEN MATCHED    THEN UPDATE SET status = @Status, updated_at = @UpdatedAt
 *       WHEN NOT MATCHED THEN INSERT (id, status, updated_at) VALUES (1, @Status, @UpdatedAt);
 *   END
 * </pre>
 */
public class OpenCloseRepository {

    private static final Logger LOG = Logger.getLogger(OpenCloseRepository.class.getName());

    private static final String SP_SET_STATUS =
            "{call sp_SetOpenCloseStatus(?, ?)}";

    private static final String QUERY_STATUS =
            "SELECT status FROM openclose WHERE id = 1";

    /**
     * Returns {@code true} if the system is currently open for processing.
     *
     * @return {@code true} if status = 1 (Open), {@code false} if 0 (Closed) or row missing
     * @throws SQLException on DB error
     */
    public boolean isOpen() throws SQLException {
        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(QUERY_STATUS);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getBoolean("status");
        }
    }

    /**
     * Persists the open/close status.
     *
     * @param statusBit {@code 1} for Open, {@code 0} for Close
     * @throws SQLException on DB error
     */
    public void setStatus(int statusBit) throws SQLException {
        try (Connection conn = DatabaseConnectionManager.getConnection();
             CallableStatement cs = conn.prepareCall(SP_SET_STATUS)) {

            cs.setInt(1, statusBit);
            cs.setTimestamp(2, Timestamp.from(Instant.now()));
            cs.execute();

            LOG.fine("openclose status set to " + statusBit);
        }
    }
}
