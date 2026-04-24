package com.apim.kunji.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Shared ObjectMapper singleton configured for the project.
 *
 * <p>Registers JavaTimeModule so Instant, LocalDate, etc. serialize as ISO-8601 strings
 * (not numeric timestamps). Thread-safe after construction.
 *
 * <p>Usage:
 * <pre>
 *   BalanceResponseDTO dto = JsonUtil.getMapper().readValue(json, BalanceResponseDTO.class);
 *   String json = JsonUtil.getMapper().writeValueAsString(dto);
 * </pre>
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonUtil() {}

    public static ObjectMapper getMapper() {
        return MAPPER;
    }
}
