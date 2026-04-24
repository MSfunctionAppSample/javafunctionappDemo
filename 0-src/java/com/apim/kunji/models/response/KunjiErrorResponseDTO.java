package com.apim.kunji.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Parses the Kunji ESB error response payload.
 *
 * <p>Kunji error payload:
 * <pre>
 *   {
 *     "reason": {
 *       "reasonCode": "API-EG0005",
 *       "reasonDescription": "The number of requests has exceeded the allowed threshold..."
 *     }
 *   }
 * </pre>
 *
 * <p>Known reason codes:
 * <ul>
 *   <li>API-EG0001 (400) — Mandatory header or body missing/invalid</li>
 *   <li>API-EG0002 (401) — Access token missing or invalid</li>
 *   <li>API-EG0003 (403) — Access denied</li>
 *   <li>API-EG0004 (408) — Request timeout — retried by {@code HttpRetryClient}</li>
 *   <li>API-EG0005 (429) — Rate limit exceeded — retried by {@code HttpRetryClient}</li>
 *   <li>API-EG0006 (500) — System error — contact Kunji support</li>
 *   <li>API-EG0007 (502) — Response payload limit exceeded — narrow query filters</li>
 *   <li>API-EG0008 (413) — Request payload too large — reduce accounts in request</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class KunjiErrorResponseDTO {

    @JsonProperty("reason")
    private Reason reason;

    public KunjiErrorResponseDTO() {}

    public Reason getReason() { return reason; }
    public void setReason(Reason reason) { this.reason = reason; }

    /** Returns the reasonCode, or {@code "UNKNOWN"} if the response could not be parsed. */
    public String getReasonCode() {
        return reason != null && reason.reasonCode != null ? reason.reasonCode : "UNKNOWN";
    }

    /** Returns the reasonDescription, or an empty string if not present. */
    public String getReasonDescription() {
        return reason != null && reason.reasonDescription != null ? reason.reasonDescription : "";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Reason {

        @JsonProperty("reasonCode")
        private String reasonCode;

        @JsonProperty("reasonDescription")
        private String reasonDescription;

        public Reason() {}

        public String getReasonCode() { return reasonCode; }
        public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

        public String getReasonDescription() { return reasonDescription; }
        public void setReasonDescription(String reasonDescription) { this.reasonDescription = reasonDescription; }
    }
}
