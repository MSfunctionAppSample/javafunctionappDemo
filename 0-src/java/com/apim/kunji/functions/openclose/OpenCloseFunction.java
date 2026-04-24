package com.apim.kunji.functions.openclose;

import com.apim.kunji.models.request.OpenCloseRequestDTO;
import com.apim.kunji.repositories.OpenCloseRepository;
import com.apim.kunji.services.OpenCloseService;
import com.apim.kunji.util.CorrelationContext;
import com.apim.kunji.util.FunctionRequestHelper;
import com.apim.kunji.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Azure HTTP Trigger for the Open/Close API (POST /api/openclose).
 *
 * <p>Accepts:
 * <pre>
 *   { "Status": "Open" }   → DB bit 1, HTTP 204
 *   { "Status": "Close" }  → DB bit 0, HTTP 204
 * </pre>
 *
 * <p>Response codes:
 * <ul>
 *   <li>204 — status persisted successfully (no body)</li>
 *   <li>400 — missing body, invalid JSON, or Status not "Open"/"Close"</li>
 *   <li>500 — unexpected server error</li>
 * </ul>
 */
public class OpenCloseFunction {

    private static final Logger LOG = Logger.getLogger(OpenCloseFunction.class.getName());

    private final OpenCloseService openCloseService;

    public OpenCloseFunction() {
        this.openCloseService = new OpenCloseService(new OpenCloseRepository());
    }

    OpenCloseFunction(OpenCloseService openCloseService) {
        this.openCloseService = openCloseService;
    }

    @FunctionName("OpenClose")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.POST},
                    route = "openclose",
                    authLevel = AuthorizationLevel.FUNCTION
            ) HttpRequestMessage<Optional<String>> request,
            ExecutionContext context) {

        // --- 1. Extract headers (injected by APIM policy) ---
        String correlationId = request.getHeaders().getOrDefault("x-correlation-id", "unknown");
        String messageId     = request.getHeaders().getOrDefault("x-message-id", "unknown");
        CorrelationContext.set(correlationId, messageId);

        try {
            // --- 2. Parse body ---
            String body = request.getBody().orElse("").trim();
            if (body.isEmpty()) {
                return FunctionRequestHelper.badRequest(request, "Request body is required");
            }

            OpenCloseRequestDTO dto;
            try {
                dto = JsonUtil.getMapper().readValue(body, OpenCloseRequestDTO.class);
            } catch (JsonProcessingException e) {
                return FunctionRequestHelper.badRequest(request, "Invalid JSON: " + e.getOriginalMessage());
            }

            // --- 3. Apply status (validation + DB write in service) ---
            openCloseService.applyStatus(dto);

            // --- 4. Return 204 No Content ---
            return request.createResponseBuilder(HttpStatus.NO_CONTENT)
                    .header("X-Correlation-ID", correlationId)
                    .build();

        } catch (IllegalArgumentException e) {
            return FunctionRequestHelper.badRequest(request, e.getMessage());

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "[" + correlationId + "] DB error: " + e.getMessage(), e);
            return FunctionRequestHelper.serverError(request, correlationId);

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[" + correlationId + "] Unexpected error: " + e.getMessage(), e);
            return FunctionRequestHelper.serverError(request, correlationId);

        } finally {
            CorrelationContext.clear();
        }
    }
}
