package com.apim.kunji.util;

import com.apim.kunji.models.response.KunjiErrorResponseDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.HttpStatus;

import java.net.http.HttpResponse;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Maps Kunji ESB HTTP error responses to client-facing {@link HttpStatus} codes
 * and safe error messages.
 *
 * <p>Kunji error codes must never be forwarded verbatim to the client — they may
 * leak internal system details. This class translates them to appropriate client responses.
 *
 * <p>Mapping:
 * <pre>
 *   Kunji Code   HTTP  Reason                     Client Status
 *   ----------   ----  -------------------------  ----------------------------
 *   API-EG0001   400   Bad request                400 (client sent bad data)
 *   API-EG0002   401   Auth token invalid         500 (internal auth issue)
 *   API-EG0003   403   Access denied              500 (internal permission issue)
 *   API-EG0004   408   Timeout                    504 (exhausted after retries)
 *   API-EG0005   429   Rate limited               503 (service temporarily unavailable)
 *   API-EG0006   500   System error               502 (upstream error)
 *   API-EG0007   502   Payload limit exceeded     400 (narrow your query)
 *   API-EG0008   413   Request too large          400 (reduce accounts in request)
 *   UNKNOWN      any   Unexpected                 500
 * </pre>
 */
public final class KunjiErrorMapper {

    private static final Logger LOG = Logger.getLogger(KunjiErrorMapper.class.getName());

    private KunjiErrorMapper() {}

    /**
     * Parses a Kunji error response and returns the appropriate client-facing HTTP status.
     *
     * @param response the raw Kunji HTTP response (non-2xx)
     * @return client-facing {@link HttpStatus}
     */
    public static HttpStatus toClientStatus(HttpResponse<String> response) {
        String reasonCode = parseReasonCode(response);
        return switch (reasonCode) {
            case "API-EG0001" -> HttpStatus.BAD_REQUEST;           // bad input we sent
            case "API-EG0002" -> HttpStatus.INTERNAL_SERVER_ERROR; // our auth token issue
            case "API-EG0003" -> HttpStatus.INTERNAL_SERVER_ERROR; // our permissions issue
            case "API-EG0004" -> HttpStatus.GATEWAY_TIMEOUT;       // timeout after retries
            case "API-EG0005" -> HttpStatus.SERVICE_UNAVAILABLE;   // rate limited after retries
            case "API-EG0006" -> HttpStatus.BAD_GATEWAY;           // Kunji system error
            case "API-EG0007" -> HttpStatus.BAD_REQUEST;           // payload too large — narrow query
            case "API-EG0008" -> HttpStatus.BAD_REQUEST;           // request too large — reduce accounts
            default            -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * Returns a safe client-facing error message for the given Kunji reason code.
     * Does not expose internal Kunji descriptions.
     */
    public static String toClientMessage(HttpResponse<String> response) {
        String reasonCode = parseReasonCode(response);
        return switch (reasonCode) {
            case "API-EG0001" -> "Invalid request sent to upstream service.";
            case "API-EG0002", "API-EG0003" -> "An internal configuration error occurred. Contact support.";
            case "API-EG0004" -> "Upstream service timed out. Please retry.";
            case "API-EG0005" -> "Service temporarily unavailable due to rate limiting. Please retry later.";
            case "API-EG0006" -> "Upstream system error. Contact support if this persists.";
            case "API-EG0007" -> "Response too large. Please narrow your query (fewer accounts or shorter date range).";
            case "API-EG0008" -> "Too many accounts in a single request. Please reduce the number of accounts.";
            default            -> "An unexpected error occurred. Contact support.";
        };
    }

    /**
     * Extracts the Kunji reasonCode from the response body.
     * Returns {@code "UNKNOWN"} if the body cannot be parsed.
     */
    public static String parseReasonCode(HttpResponse<String> response) {
        try {
            KunjiErrorResponseDTO error = JsonUtil.getMapper()
                    .readValue(response.body(), KunjiErrorResponseDTO.class);
            return error.getReasonCode();
        } catch (JsonProcessingException e) {
            LOG.log(Level.WARNING, "Could not parse Kunji error body (HTTP " +
                    response.statusCode() + "): " + e.getMessage());
            return "UNKNOWN";
        }
    }
}
