package com.apim.kunji.util;

import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;

/**
 * Shared HTTP error response builders for Azure Function handlers.
 * Ensures consistent JSON error shape and correlation ID headers across all functions.
 */
public final class FunctionRequestHelper {

    private FunctionRequestHelper() {}

    /** HTTP 400 Bad Request with a JSON {@code {"error": "..."}} body. */
    public static HttpResponseMessage badRequest(HttpRequestMessage<?> req, String message) {
        return req.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("{\"error\":\"" + message + "\"}")
                .build();
    }

    /** HTTP 504 Gateway Timeout with a generic retry message and {@code X-Correlation-ID} header. */
    public static HttpResponseMessage gatewayTimeout(HttpRequestMessage<?> req, String correlationId) {
        return req.createResponseBuilder(HttpStatus.GATEWAY_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("X-Correlation-ID", correlationId)
                .body("{\"error\":\"Upstream service timed out. Please retry.\"}")
                .build();
    }

    /** HTTP 500 Internal Server Error including the correlation ID for traceability. */
    public static HttpResponseMessage serverError(HttpRequestMessage<?> req, String correlationId) {
        return req.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .header("X-Correlation-ID", correlationId)
                .body("{\"error\":\"An internal error occurred. Correlation ID: " + correlationId + "\"}")
                .build();
    }
}
