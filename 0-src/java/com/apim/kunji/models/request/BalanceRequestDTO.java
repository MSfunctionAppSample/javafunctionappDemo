package com.apim.kunji.models.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Inbound request DTO for Balance API.
 *
 * <p>Example JSON:
 * <pre>
 *   {
 *     "accounts": [
 *       { "accountNumber": "Acc1" },
 *       { "accountNumber": "Acc2" }
 *     ],
 *     "format": "CAMT.052"
 *   }
 * </pre>
 *
 * <p>Note: {@code correlationId} is not part of the request body — it is extracted
 * from the {@code x-correlation-id} APIM header by the function layer.
 */
public class BalanceRequestDTO {

    @JsonProperty("accounts")
    private List<BalanceAccountItem> accounts;

    @JsonProperty("format")
    private String format;

    public BalanceRequestDTO() {}

    public BalanceRequestDTO(List<BalanceAccountItem> accounts, String format) {
        this.accounts = accounts;
        this.format = format;
    }

    public List<BalanceAccountItem> getAccounts() { return accounts; }
    public void setAccounts(List<BalanceAccountItem> accounts) { this.accounts = accounts; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    /**
     * A single account entry within a balance request.
     */
    public static class BalanceAccountItem {

        @JsonProperty("accountNumber")
        private String accountNumber;

        public BalanceAccountItem() {}

        public BalanceAccountItem(String accountNumber) {
            this.accountNumber = accountNumber;
        }

        public String getAccountNumber() { return accountNumber; }
        public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    }
}
