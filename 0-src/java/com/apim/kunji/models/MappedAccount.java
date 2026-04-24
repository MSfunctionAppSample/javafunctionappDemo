package com.apim.kunji.models;

/**
 * Resolved account details from the {@code customer_account_mapping} table.
 *
 * <p>After entitlement checks pass, the service layer uses these to build
 * the Kunji ESB request body (T24 account number + company ID).
 *
 * @param customerAccountNumber the customer-facing account number from the request
 * @param t24AccountNumber      the T-24 internal account number for Kunji
 * @param companyId             the company ID for Kunji request construction
 */
public record MappedAccount(
        String customerAccountNumber,
        String t24AccountNumber,
        String companyId
) {}
