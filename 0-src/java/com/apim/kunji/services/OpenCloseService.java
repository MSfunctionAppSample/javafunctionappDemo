package com.apim.kunji.services;

import com.apim.kunji.models.request.OpenCloseRequestDTO;
import com.apim.kunji.repositories.OpenCloseRepository;

import java.sql.SQLException;

/**
 * Service for the Open/Close API.
 *
 * <p>Validates that Status is exactly "Open" or "Close" and maps to a DB bit value.
 */
public class OpenCloseService {

    private final OpenCloseRepository repository;

    public OpenCloseService(OpenCloseRepository repository) {
        this.repository = repository;
    }

    /**
     * Validates the request and persists the status.
     *
     * @param request the inbound DTO
     * @throws IllegalArgumentException if Status is null, blank, or not "Open"/"Close"
     * @throws SQLException             on DB failure
     */
    public void applyStatus(OpenCloseRequestDTO request) throws SQLException {
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            throw new IllegalArgumentException("Status is required");
        }

        int statusBit = switch (request.getStatus()) {
            case "Open"  -> 1;
            case "Close" -> 0;
            default -> throw new IllegalArgumentException(
                    "Status must be 'Open' or 'Close', got: " + request.getStatus());
        };

        repository.setStatus(statusBit);
    }
}
