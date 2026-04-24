package com.apim.kunji.models.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Outbound response DTO for Statement Retrieval API.
 *
 * <p>Example JSON (READY):
 * <pre>
 *   {
 *     "correlationId": "abc-def-123",
 *     "status": "READY",
 *     "generatedAt": "2026-04-15T10:30:00Z"
 *   }
 * </pre>
 *
 * <p>Example JSON (PENDING):
 * <pre>
 *   { "correlationId": "abc-def-123", "status": "PENDING" }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StatementResponseDTO {

    @JsonProperty("correlationId")
    private String correlationId;

    @JsonProperty("status")
    private String status; // PENDING, READY, FAILED

    @JsonProperty("generatedAt")
    private Instant generatedAt;

    @JsonProperty("errorMessage")
    private String errorMessage;

    public StatementResponseDTO() {}

    public StatementResponseDTO(String correlationId, String status) {
        this.correlationId = correlationId;
        this.status = status;
    }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
