package com.apim.kunji.models.request;

import com.apim.kunji.models.MappedAccount;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Request DTO for Kunji ESB calls (balance and statement).
 *
 * <p>Example JSON:
 * <pre>
 *   {
 *     "header": {
 *       "correlationId": "abc-123",
 *       "messageId": "msg-456"
 *     },
 *     "body": {
 *       "format": "CAMT.052",
 *       "account": [
 *         {
 *           "companyId": "SG01",
 *           "accountNumber": "T24-XXX",
 *           "selectionCriteria": [
 *             { "fieldName": "BOOKING.DATE", "operand": "GE", "value": "20250101" },
 *             { "fieldName": "BOOKING.DATE", "operand": "LE", "value": "20250131" }
 *           ]
 *         }
 *       ]
 *     }
 *   }
 * </pre>
 *
 * <p>Use the static factory methods to build from resolved {@link MappedAccount} data:
 * <ul>
 *   <li>{@link #forBalance} — no date filters</li>
 *   <li>{@link #forStatement} — with date-range selection criteria per account</li>
 * </ul>
 */
public class KunjiRequestDTO {

    private static final DateTimeFormatter KUNJI_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    @JsonProperty("header")
    private Header header;

    @JsonProperty("body")
    private Body body;

    public KunjiRequestDTO() {}

    public KunjiRequestDTO(Header header, Body body) {
        this.header = header;
        this.body = body;
    }

    public Header getHeader() { return header; }
    public Body getBody() { return body; }

    // --- Factory methods ---

    /**
     * Builds a Kunji balance request (no selection criteria).
     */
    public static KunjiRequestDTO forBalance(String correlationId, String messageId,
                                             String format, List<MappedAccount> accounts) {
        List<AccountEntry> entries = accounts.stream()
                .map(ma -> new AccountEntry(ma.companyId(), ma.t24AccountNumber(), List.of()))
                .toList();
        return new KunjiRequestDTO(
                new Header(correlationId, messageId),
                new Body(format, entries));
    }

    /**
     * Builds a Kunji statement request with date-range selection criteria per account.
     *
     * @param accountDates per-account date ranges, matched by customer account number
     *                     to the corresponding {@link MappedAccount}
     */
    public static KunjiRequestDTO forStatement(String correlationId, String messageId,
                                               String format, List<MappedAccount> accounts,
                                               List<AccountDateRange> accountDates) {
        // Build a lookup: customerAccountNumber → date range
        var datesByAccount = accountDates.stream()
                .collect(java.util.stream.Collectors.toMap(
                        AccountDateRange::accountNumber, d -> d));

        List<AccountEntry> entries = accounts.stream()
                .map(ma -> {
                    AccountDateRange dates = datesByAccount.get(ma.customerAccountNumber());
                    List<SelectionCriteria> criteria = dates != null
                            ? List.of(
                                    new SelectionCriteria("BOOKING.DATE", "GE", dates.fromDate().format(KUNJI_DATE)),
                                    new SelectionCriteria("BOOKING.DATE", "LE", dates.toDate().format(KUNJI_DATE)))
                            : List.of();
                    return new AccountEntry(ma.companyId(), ma.t24AccountNumber(), criteria);
                })
                .toList();
        return new KunjiRequestDTO(
                new Header(correlationId, messageId),
                new Body(format, entries));
    }

    /** Per-account date range input for {@link #forStatement}. */
    public record AccountDateRange(String accountNumber, LocalDate fromDate, LocalDate toDate) {}

    // --- Nested structures ---

    public static class Header {
        @JsonProperty("correlationId")
        private String correlationId;

        @JsonProperty("messageId")
        private String messageId;

        public Header() {}

        public Header(String correlationId, String messageId) {
            this.correlationId = correlationId;
            this.messageId = messageId;
        }

        public String getCorrelationId() { return correlationId; }
        public String getMessageId() { return messageId; }
    }

    public static class Body {
        @JsonProperty("format")
        private String format;

        @JsonProperty("account")
        private List<AccountEntry> account;

        public Body() {}

        public Body(String format, List<AccountEntry> account) {
            this.format = format;
            this.account = account;
        }

        public String getFormat() { return format; }
        public List<AccountEntry> getAccount() { return account; }
    }

    public static class AccountEntry {
        @JsonProperty("companyId")
        private String companyId;

        @JsonProperty("accountNumber")
        private String accountNumber;

        @JsonProperty("selectionCriteria")
        private List<SelectionCriteria> selectionCriteria;

        public AccountEntry() {}

        public AccountEntry(String companyId, String accountNumber, List<SelectionCriteria> selectionCriteria) {
            this.companyId = companyId;
            this.accountNumber = accountNumber;
            this.selectionCriteria = selectionCriteria;
        }

        public String getCompanyId() { return companyId; }
        public String getAccountNumber() { return accountNumber; }
        public List<SelectionCriteria> getSelectionCriteria() { return selectionCriteria; }
    }

    public static class SelectionCriteria {
        @JsonProperty("fieldName")
        private String fieldName;

        @JsonProperty("operand")
        private String operand;

        @JsonProperty("value")
        private String value;

        public SelectionCriteria() {}

        public SelectionCriteria(String fieldName, String operand, String value) {
            this.fieldName = fieldName;
            this.operand = operand;
            this.value = value;
        }

        public String getFieldName() { return fieldName; }
        public String getOperand() { return operand; }
        public String getValue() { return value; }
    }
}
