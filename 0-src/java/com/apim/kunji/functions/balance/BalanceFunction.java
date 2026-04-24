package com.apim.kunji.functions.balance;

import com.apim.kunji.models.request.BalanceRequestDTO;
import com.apim.kunji.models.response.BalanceResponseDTO;
import com.apim.kunji.repositories.BalanceRepository;
import com.apim.kunji.repositories.CustomerAccountMappingRepository;
import com.apim.kunji.repositories.OpenCloseRepository;
import com.apim.kunji.services.BalanceService;
import com.apim.kunji.util.CorrelationContext;
import com.apim.kunji.util.EntitlementException;
import com.apim.kunji.util.FunctionRequestHelper;
import com.apim.kunji.util.JsonUtil;
import com.apim.kunji.util.HttpRetryExhaustedException;
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
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Azure HTTP Trigger for the Balance API (POST /api/balance).
 *
 * <p>Flow:
 * <ol>
 *   <li>Extract correlation headers + customer ID from APIM</li>
 *   <li>Parse and validate the request body</li>
 *   <li>Delegate to {@link BalanceService} (entitlement checks → Kunji ESB → audit)</li>
 *   <li>Return structured HTTP responses</li>
 * </ol>
 *
 * <p>Response codes:
 * <ul>
 *   <li>200 — balance returned successfully</li>
 *   <li>400 — invalid request body or missing fields</li>
 *   <li>403 — account not mapped, not active, or balance access denied</li>
 *   <li>503 — system is closed (open/close gate)</li>
 *   <li>504 — Kunji ESB timeout after retries</li>
 *   <li>500 — unexpected server error</li>
 * </ul>
 */
public class BalanceFunction {

    private static final Logger LOG = Logger.getLogger(BalanceFunction.class.getName());

    private final BalanceService balanceService;

    public BalanceFunction() {
        this.balanceService = new BalanceService(
                new BalanceRepository(),
                new CustomerAccountMappingRepository(),
                new OpenCloseRepository());
    }

    // For testing
    BalanceFunction(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @FunctionName("Balance")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.POST},
                    route = "balance",
                    authLevel = AuthorizationLevel.FUNCTION
            ) HttpRequestMessage<Optional<String>> request,
            ExecutionContext context) {

        // --- 1. Extract headers (injected by APIM policy) ---
        Map<String, String> headers = request.getHeaders();
        String correlationId = headers.getOrDefault("x-correlation-id", "unknown");
        String messageId     = headers.getOrDefault("x-message-id", "unknown");
        String customerId    = headers.get("x-customer-id");

        CorrelationContext.set(correlationId, messageId);

        try {
            // --- 2. Validate customer ID header ---
            if (customerId == null || customerId.isBlank()) {
                return FunctionRequestHelper.badRequest(request, "x-customer-id header is required");
            }

            // --- 3. Parse request body ---
            String body = request.getBody().orElse("").trim();
            if (body.isEmpty()) {
                return FunctionRequestHelper.badRequest(request, "Request body is required");
            }

            BalanceRequestDTO dto;
            try {
                dto = JsonUtil.getMapper().readValue(body, BalanceRequestDTO.class);
            } catch (JsonProcessingException e) {
                LOG.warning("[" + correlationId + "] Invalid JSON: " + e.getMessage());
                return FunctionRequestHelper.badRequest(request, "Invalid JSON: " + e.getOriginalMessage());
            }

            if (dto.getAccounts() == null || dto.getAccounts().isEmpty()) {
                return FunctionRequestHelper.badRequest(request, "accounts array is required and must not be empty");
            }
            if (dto.getFormat() == null || dto.getFormat().isBlank()) {
                return FunctionRequestHelper.badRequest(request, "format is required (e.g. CAMT.052)");
            }

            // --- 4. Delegate to service (entitlement checks + Kunji ESB) ---
            BalanceResponseDTO response = balanceService.getBalance(dto, customerId);

            String responseJson = JsonUtil.getMapper().writeValueAsString(response);
            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-ID", correlationId)
                    .body(responseJson)
                    .build();

        } catch (EntitlementException e) {
            LOG.log(Level.WARNING, "[" + correlationId + "] Entitlement check failed: " +
                    e.getReason() + " — " + e.getMessage(), e);
            return request.createResponseBuilder(e.getHttpStatus())
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-ID", correlationId)
                    .body("{\"error\":\"" + e.getMessage() + "\",\"reason\":\"" + e.getReason() + "\"}")
                    .build();

        } catch (HttpRetryExhaustedException e) {
            LOG.log(Level.WARNING, "[" + correlationId + "] Kunji timeout: " + e.getMessage(), e);
            return FunctionRequestHelper.gatewayTimeout(request, correlationId);

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
