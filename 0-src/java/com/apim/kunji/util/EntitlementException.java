package com.apim.kunji.util;

import com.microsoft.azure.functions.HttpStatus;

/**
 * Thrown when an entitlement pre-check fails before the Kunji ESB call.
 *
 * <p>Each {@link Reason} maps to a specific HTTP status code and carries a default message.
 * The {@code detail} field identifies the offending account or context for App Insights logging.
 */
public class EntitlementException extends Exception {

    public enum Reason {
        SERVICE_CLOSED(HttpStatus.SERVICE_UNAVAILABLE,
                "System is currently closed for processing"),
        ACCOUNT_NOT_MAPPED(HttpStatus.FORBIDDEN,
                "Account is not mapped to this customer"),
        ACCOUNT_NOT_ACTIVE(HttpStatus.FORBIDDEN,
                "Account is temporarily deactivated"),
        BALANCE_ACCESS_DENIED(HttpStatus.FORBIDDEN,
                "Account does not have balance inquiry access");

        private final HttpStatus httpStatus;
        private final String defaultMessage;

        Reason(HttpStatus httpStatus, String defaultMessage) {
            this.httpStatus = httpStatus;
            this.defaultMessage = defaultMessage;
        }

        public HttpStatus getHttpStatus() { return httpStatus; }
        public String getDefaultMessage() { return defaultMessage; }
    }

    private final Reason reason;
    private final String detail;

    public EntitlementException(Reason reason, String detail) {
        super(reason.getDefaultMessage() + (detail != null ? ": " + detail : ""));
        this.reason = reason;
        this.detail = detail;
    }

    public EntitlementException(Reason reason) {
        this(reason, null);
    }

    public Reason getReason() { return reason; }
    public String getDetail() { return detail; }
    public HttpStatus getHttpStatus() { return reason.getHttpStatus(); }
}
