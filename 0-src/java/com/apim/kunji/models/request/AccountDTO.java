package com.apim.kunji.models.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * Represents a single account entry within a statement request.
 *
 * <p>Example JSON:
 * <pre>
 *   { "accountNumber": "SG-111111111-SGD", "fromDate": "27-11-2025", "toDate": "28-11-2025" }
 * </pre>
 */
public class AccountDTO {

    @JsonProperty("accountNumber")
    private String accountNumber;

    @JsonFormat(pattern = "dd-MM-yyyy")
    @JsonProperty("fromDate")
    private LocalDate fromDate;

    @JsonFormat(pattern = "dd-MM-yyyy")
    @JsonProperty("toDate")
    private LocalDate toDate;

    public AccountDTO() {}

    public AccountDTO(String accountNumber, LocalDate fromDate, LocalDate toDate) {
        this.accountNumber = accountNumber;
        this.fromDate = fromDate;
        this.toDate = toDate;
    }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public LocalDate getFromDate() { return fromDate; }
    public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }

    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate toDate) { this.toDate = toDate; }
}
