package com.apim.kunji.models.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound request DTO for the Open/Close API.
 *
 * <p>Example JSON:
 * <pre>
 *   { "Status": "Open" }
 *   { "Status": "Close" }
 * </pre>
 *
 * <p>No other values are accepted.
 */
public class OpenCloseRequestDTO {

    @JsonProperty("Status")
    private String status;

    public OpenCloseRequestDTO() {}

    public OpenCloseRequestDTO(String status) {
        this.status = status;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
