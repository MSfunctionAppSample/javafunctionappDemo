package com.apim.kunji.repositories;

import com.apim.kunji.models.request.AccountDTO;
import com.apim.kunji.util.DatabaseConnectionManager;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for Statement Initiation and Retrieval.
 *
 * <p>Demonstrates the multi-table transaction pattern:
 * <ol>
 *   <li>Disable autoCommit on the connection</li>
 *   <li>Call parent SP with idempotency check (OUTPUT params)</li>
 *   <li>Batch-insert child rows via {@code executeBatch()}</li>
 *   <li>Explicit commit or rollback in Java</li>
 * </ol>
 *
 * <p>All SP parameters are typed scalars — no raw JSON reaches the database.
 */
public class StatementRepository {

    private static final Logger LOG = Logger.getLogger(StatementRepository.class.getName());

    private static final String SP_INSERT_PARENT =
            "{call sp_InsertStatementRequest(?, ?, ?, ?, ?)}";

    private static final String SP_INSERT_ITEM =
            "{call sp_InsertStatementItem(?, ?, ?, ?, ?)}";

    private static final String QUERY_JOB_STATUS =
            "SELECT status, completed_at FROM statement_request WHERE correlation_id = ?";

    private static final String SP_UPDATE_STATUS =
            "{call sp_UpdateStatementStatus(?, ?, ?)}";

    /**
     * Inserts a parent statement request + all child account items in a single Java-controlled
     * transaction. Returns the generated request ID, or -1 if the correlationId already exists
     * (idempotency — caller should return HTTP 409).
     *
     * @param correlationId unique request ID from APIM header
     * @param format        statement format (e.g. "CAMT.053"), stored against each account item
     * @param accounts      list of account items to insert
     * @return generated requestId, or -1 on idempotent duplicate
     * @throws SQLException on DB error (caller must handle + return 500)
     */
    public long insertStatementRequest(String correlationId, String format,
                                       List<AccountDTO> accounts) throws SQLException {

        try (Connection conn = DatabaseConnectionManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // --- 1. Parent SP (with idempotency check) ---
                long requestId;
                try (CallableStatement cs = conn.prepareCall(SP_INSERT_PARENT)) {
                    cs.setString(1, correlationId);
                    cs.setString(2, "PENDING");
                    cs.setTimestamp(3, Timestamp.from(Instant.now()));
                    cs.registerOutParameter(4, Types.BIGINT);   // @RequestId OUTPUT
                    cs.registerOutParameter(5, Types.INTEGER);  // @ReturnCode OUTPUT
                    cs.execute();

                    int returnCode = cs.getInt(5);
                    if (returnCode == 1) {
                        // Duplicate correlationId — idempotent, no insert
                        conn.rollback();
                        return -1L;
                    }
                    requestId = cs.getLong(4);
                }

                // --- 2. Child batch inserts ---
                try (CallableStatement cs = conn.prepareCall(SP_INSERT_ITEM)) {
                    for (AccountDTO account : accounts) {
                        cs.setLong(1, requestId);
                        cs.setString(2, account.getAccountNumber());
                        cs.setDate(3, toSqlDate(account.getFromDate()));
                        cs.setDate(4, toSqlDate(account.getToDate()));
                        cs.setString(5, format);
                        cs.addBatch();
                    }
                    cs.executeBatch();
                }

                conn.commit();
                return requestId;

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Returns the current status of a statement job, or null if not found.
     *
     * @return "PENDING", "READY", "FAILED", or null if correlationId unknown
     */
    public String getJobStatus(String correlationId) throws SQLException {
        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(QUERY_JOB_STATUS)) {

            ps.setString(1, correlationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("status");
                }
                return null;
            }
        }
    }

    /**
     * Updates the job status for a given correlationId.
     *
     * @param correlationId the request correlation ID
     * @param status        new status: "READY", "FAILED"
     * @param completedAt   timestamp of completion (null if FAILED with no timestamp)
     */
    public void updateJobStatus(String correlationId, String status, Instant completedAt)
            throws SQLException {

        try (Connection conn = DatabaseConnectionManager.getConnection();
             CallableStatement cs = conn.prepareCall(SP_UPDATE_STATUS)) {

            cs.setString(1, correlationId);
            cs.setString(2, status);
            cs.setTimestamp(3, completedAt != null ? Timestamp.from(completedAt) : null);
            cs.execute();
        }
    }

    /**
     * Best-effort audit log for a statement job event. Never throws — logged on failure.
     */
    public void insertAuditEntry(String correlationId, String event, String detail) {
        // TODO: implement once audit table schema is defined
        LOG.fine(() -> String.format("[%s] Audit: %s — %s", correlationId, event, detail));
    }

    private static Date toSqlDate(LocalDate localDate) {
        return localDate != null ? Date.valueOf(localDate) : null;
    }
}
