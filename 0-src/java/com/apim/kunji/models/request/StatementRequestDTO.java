package com.apim.kunji.models.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Inbound request DTO for Statement Initiation API.
 *
 * <p>Example JSON:
 * <pre>
 *   {
 *     "accounts": [
 *       { "accountNumber": "SG-111111111-SGD", "fromDate": "27-11-2025", "toDate": "28-11-2025" },
 *       { "accountNumber": "SG-111111111-USD", "fromDate": "27-11-2025", "toDate": "28-11-2025" }
 *     ],
 *     "format": "CAMT.053"
 *   }
 * </pre>
 *
 * <p>The {@code correlationId} is NOT in the body — it comes from the {@code x-correlation-id}
 * APIM header and is propagated via {@link com.apim.kunji.util.CorrelationContext}.
 */
public class StatementRequestDTO {

    @JsonProperty("accounts")
    private List<AccountDTO> accounts;

    @JsonProperty("format")
    private String format;

    public StatementRequestDTO() {}

    public StatementRequestDTO(List<AccountDTO> accounts, String format) {
        this.accounts = accounts;
        this.format = format;
    }

    public List<AccountDTO> getAccounts() { return accounts; }
    public void setAccounts(List<AccountDTO> accounts) { this.accounts = accounts; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
}
