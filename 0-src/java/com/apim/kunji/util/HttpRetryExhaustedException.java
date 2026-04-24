package com.apim.kunji.util;

import java.io.IOException;

/**
 * Thrown when all retry attempts have been exhausted for an HTTP request.
 * Extends {@link IOException} so it integrates naturally with HTTP I/O handling.
 */
public class HttpRetryExhaustedException extends IOException {

    public HttpRetryExhaustedException(String message) {
        super(message);
    }

    public HttpRetryExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
