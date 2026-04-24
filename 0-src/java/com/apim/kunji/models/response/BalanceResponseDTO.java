package com.apim.kunji.models.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Outbound response DTO for Balance API — mirrors the Kunji ESB response structure.
 *
 * <p>Example JSON:
 * <pre>
 *   {
 *     "dateTime": "2025-10-23T09:00:00Z",
 *     "accounts": [
 *       {
 *         "accountNumber": "CC-XXXXXX_CCY",
 *         "accountType": "SAVINGS",
 *         "accountCurrency": "USD",
 *         "branchBICCode": "SMBCSGSGXXX",
 *         "branchCode": "8806",
 *         "clearedBalance": 10000,
 *         "onlineBalance": 10050
 *       }
 *     ]
 *   }
 * </pre>
 */
public class BalanceResponseDTO {

    @JsonProperty("dateTime")
    private Instant dateTime;

    @JsonProperty("accounts")
    private List<BalanceAccountResult> accounts;

    public BalanceResponseDTO() {}

    public BalanceResponseDTO(Instant dateTime, List<BalanceAccountResult> accounts) {
        this.dateTime = dateTime;
        this.accounts = accounts;
    }

    public Instant getDateTime() { return dateTime; }
    public void setDateTime(Instant dateTime) { this.dateTime = dateTime; }

    public List<BalanceAccountResult> getAccounts() { return accounts; }
    public void setAccounts(List<BalanceAccountResult> accounts) { this.accounts = accounts; }

    /**
     * Balance details for a single account returned by Kunji ESB.
     */
    public static class BalanceAccountResult {

        @JsonProperty("accountNumber")
        private String accountNumber;

        @JsonProperty("accountType")
        private String accountType;

        @JsonProperty("accountCurrency")
        private String accountCurrency;

        @JsonProperty("branchBICCode")
        private String branchBICCode;

        @JsonProperty("branchCode")
        private String branchCode;

        @JsonProperty("clearedbalance")
        private BigDecimal clearedBalance;

        @JsonProperty("onlineBalance")
        private BigDecimal onlineBalance;

        public BalanceAccountResult() {}

        public String getAccountNumber() { return accountNumber; }
        public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

        public String getAccountType() { return accountType; }
        public void setAccountType(String accountType) { this.accountType = accountType; }

        public String getAccountCurrency() { return accountCurrency; }
        public void setAccountCurrency(String accountCurrency) { this.accountCurrency = accountCurrency; }

        public String getBranchBICCode() { return branchBICCode; }
        public void setBranchBICCode(String branchBICCode) { this.branchBICCode = branchBICCode; }

        public String getBranchCode() { return branchCode; }
        public void setBranchCode(String branchCode) { this.branchCode = branchCode; }

        public BigDecimal getClearedBalance() { return clearedBalance; }
        public void setClearedBalance(BigDecimal clearedBalance) { this.clearedBalance = clearedBalance; }

        public BigDecimal getOnlineBalance() { return onlineBalance; }
        public void setOnlineBalance(BigDecimal onlineBalance) { this.onlineBalance = onlineBalance; }
    }
}
