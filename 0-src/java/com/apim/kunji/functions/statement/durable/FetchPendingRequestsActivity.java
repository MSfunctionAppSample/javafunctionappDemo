package com.apim.kunji.functions.statement.durable;

import com.apim.kunji.util.DatabaseConnectionManager;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Durable Activity — fetches the list of pending account items for a given statement request.
 *
 * <p>Called by {@link PollingOrchestrator} as the first step before polling begins.
 * Queries {@code statement_request_item} for all child accounts of the given requestId.
 *
 * <p>Returns a JSON array of account poll inputs (serialized as String for Durable compatibility).
 *
 * <p><b>Developers:</b> Implement the TODO query below once the DB schema is finalized.
 */
public class FetchPendingRequestsActivity {

    private static final Logger LOG = Logger.getLogger(FetchPendingRequestsActivity.class.getName());

    private static final String QUERY_PENDING_ITEMS =
            "SELECT account_number, date_from, date_to, format_type " +
            "FROM statement_request_item " +
            "WHERE request_id = ? AND status = 'PENDING'";

    @FunctionName("FetchPendingRequestsActivity")
    public List<String> run(
            @DurableActivityTrigger(name = "requestId") long requestId,
            ExecutionContext context) {

        List<String> accounts = new ArrayList<>();

        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(QUERY_PENDING_ITEMS)) {

            ps.setLong(1, requestId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // TODO: Serialize into PollInput record/class for type safety.
                    // Placeholder: build a minimal JSON string per row.
                    //   PollInput input = new PollInput(requestId, rs.getString("account_number"), ...);
                    //   accounts.add(JsonUtil.getMapper().writeValueAsString(input));

                    String accountJson = String.format(
                            "{\"requestId\":%d,\"accountNumber\":\"%s\",\"dateFrom\":\"%s\",\"dateTo\":\"%s\",\"formatType\":\"%s\"}",
                            requestId,
                            rs.getString("account_number"),
                            rs.getDate("date_from"),
                            rs.getDate("date_to"),
                            rs.getString("format_type")
                    );
                    accounts.add(accountJson);
                }
            }

            LOG.info("FetchPendingRequestsActivity: requestId=" + requestId +
                    ", accounts=" + accounts.size());

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to fetch pending accounts for requestId=" + requestId, e);
            throw new RuntimeException("DB error fetching pending accounts", e);
        }

        return accounts;
    }
}
