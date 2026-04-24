package com.apim.kunji.repositories;

import com.apim.kunji.util.DatabaseConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repository for Balance API audit records.
 *
 * <p>Demonstrates the JDBC pattern used across the project:
 * <ul>
 *   <li>Get pooled connection from {@link DatabaseConnectionManager}</li>
 *   <li>Use {@link PreparedStatement} with typed scalar parameters (never raw JSON)</li>
 *   <li>Wrap in try-with-resources for automatic cleanup</li>
 * </ul>
 *
 * <p>Developers: copy this class as a starting template for new repositories.
 */
public class BalanceRepository {

    private static final Logger LOG = Logger.getLogger(BalanceRepository.class.getName());

    private static final String INSERT_AUDIT_SQL =
            "INSERT INTO BalanceAudit (correlationId, accountNumber, status, requestTimestamp) " +
            "VALUES (?, ?, ?, ?)";

    /**
     * Inserts a best-effort audit entry for a Balance API call.
     *
     * @param correlationId the correlation ID from the request header
     * @param accountNumber the account number queried
     * @param status        outcome: "SUCCESS", "TIMEOUT", "ERROR"
     */
    public void insertAuditEntry(String correlationId, String accountNumber, String status) {
        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_AUDIT_SQL)) {

            ps.setString(1, correlationId);
            ps.setString(2, accountNumber);
            ps.setString(3, status);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));

            ps.executeUpdate();

        } catch (SQLException e) {
            // Best-effort audit — log but don't propagate
            LOG.log(Level.WARNING, "Failed to insert balance audit entry: " + e.getMessage(), e);
        }
    }
}
