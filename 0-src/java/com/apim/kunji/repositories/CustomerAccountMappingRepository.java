package com.apim.kunji.repositories;

import com.apim.kunji.util.DatabaseConnectionManager;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class CustomerAccountMappingRepository {

    private static final Logger LOG = Logger.getLogger(CustomerAccountMappingRepository.class.getName());

    private static final String SP_GET_MAPPINGS =
            "{call sp_GetCustomerAccountMappings(?, ?)}";

    /**
     * Fetches entitlement and mapping rows for all requested accounts belonging to a customer.
     *
     * <p>Passes account numbers as a comma-delimited string to the stored procedure.
     * The SP uses {@code STRING_SPLIT} to expand them. Accounts not found in the result
     * set are missing from the mapping table entirely.
     *
     * @param customerId     the customer ID from the APIM header
     * @param accountNumbers the customer-facing account numbers from the request
     * @return rows found in the DB (may be fewer than requested if some are not mapped)
     * @throws SQLException on DB error
     */
    public List<AccountMappingRow> findByCustomerAndAccounts(String customerId,
                                                              List<String> accountNumbers)
            throws SQLException {

        for (String acct : accountNumbers) {
            if (acct.contains(",")) {
                throw new IllegalArgumentException(
                        "Account number must not contain commas: " + acct);
            }
        }

        String csv = String.join(",", accountNumbers);

        List<AccountMappingRow> results = new ArrayList<>();

        try (Connection conn = DatabaseConnectionManager.getConnection();
             CallableStatement cs = conn.prepareCall(SP_GET_MAPPINGS)) {

            cs.setString(1, customerId);
            cs.setString(2, csv);

            try (ResultSet rs = cs.executeQuery()) {
                while (rs.next()) {
                    results.add(new AccountMappingRow(
                            rs.getString("customer_account_number"),
                            rs.getString("t24_account_number"),
                            rs.getString("company_id"),
                            rs.getBoolean("has_account_balance_access"),
                            rs.getBoolean("has_account_statement_access"),
                            rs.getBoolean("is_active")
                    ));
                }
            }
        }

        return results;
    }

    /**
     * Raw row from the {@code customer_account_mapping} table.
     * The service layer uses these fields to perform entitlement validation.
     */
    public record AccountMappingRow(
            String customerAccountNumber,
            String t24AccountNumber,
            String companyId,
            boolean hasAccountBalanceAccess,
            boolean hasAccountStatementAccess,
            boolean isActive
    ) {}
}
