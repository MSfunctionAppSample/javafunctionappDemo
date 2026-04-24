package com.apim.kunji.functions.statement;

import com.apim.kunji.models.request.StatementRequestDTO;
import com.apim.kunji.repositories.StatementRepository;
import com.apim.kunji.services.StatementService;
import com.apim.kunji.util.CorrelationContext;
import com.apim.kunji.util.FunctionRequestHelper;
import com.apim.kunji.util.JsonUtil;
import com.apim.kunji.util.HttpRetryExhaustedException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.durabletask.DurableTaskClient;
import com.microsoft.durabletask.azurefunctions.DurableClientInput;

import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Azure HTTP Trigger for Statement Initiation (POST /api/statement).
 *
 * <p>Flow:
 * <ol>
 *   <li>Extract correlation headers from APIM</li>
 *   <li>Parse + validate request body</li>
 *   <li>Delegate to {@link StatementService#initiateStatement} (Kunji ACK + DB insert)</li>
 *   <li>Start {@code PollingOrchestrator} via Durable Client</li>
 *   <li>Return HTTP 202 Accepted with orchestration instance ID</li>
 * </ol>
 *
 * <p>Response codes:
 * <ul>
 *   <li>202 — accepted, orchestration started</li>
 *   <li>400 — invalid request body or missing fields</li>
 *   <li>409 — duplicate correlationId (idempotent re-submission)</li>
 *   <li>504 — Kunji ESB timeout after retries</li>
 *   <li>500 — unexpected server error</li>
 * </ul>
 */
public class StatementInitiationFunction {

    private static final Logger LOG = Logger.getLogger(StatementInitiationFunction.class.getName());

    private final StatementService statementService;

    public StatementInitiationFunction() {
        this.statementService = new StatementService(new StatementRepository());
    }

    StatementInitiationFunction(StatementService statementService) {
        this.statementService = statementService;
    }

    @FunctionName("StatementInitiation")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.POST},
                    route = "statement",
                    authLevel = AuthorizationLevel.FUNCTION
            ) HttpRequestMessage<Optional<String>> request,
            @DurableClientInput(name = "durableClient") DurableTaskClient durableClient,
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

            StatementRequestDTO dto;
            try {
                dto = JsonUtil.getMapper().readValue(body, StatementRequestDTO.class);
            } catch (JsonProcessingException e) {
                return FunctionRequestHelper.badRequest(request, "Invalid JSON: " + e.getOriginalMessage());
            }

            // --- 3. Initiate (Kunji ACK + DB insert) ---
            long requestId;
            try {
                requestId = statementService.initiateStatement(dto);
            } catch (IllegalStateException e) {
                // Duplicate correlationId
                return request.createResponseBuilder(HttpStatus.CONFLICT)
                        .header("Content-Type", "application/json")
                        .header("X-Correlation-ID", correlationId)
                        .body("{\"error\":\"" + e.getMessage() + "\"}")
                        .build();
            }

            // --- 4. Start Durable orchestrator ---
            // TODO: Pass requestId + correlationId as input to the orchestrator
            //   String instanceId = durableClient.scheduleNewOrchestrationInstance(
            //       "PollingOrchestrator",
            //       new OrchestrationInput(correlationId, requestId, dto.getAccounts()));
            //
            // Placeholder — remove when implementing:
            String instanceId = "orchestration-not-started-" + correlationId;
            LOG.warning("[" + correlationId + "] TODO: start PollingOrchestrator for requestId=" + requestId);

            return request.createResponseBuilder(HttpStatus.ACCEPTED)
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-ID", correlationId)
                    .body(String.format(
                            "{\"message\":\"Statement generation started\",\"correlationId\":\"%s\",\"instanceId\":\"%s\"}",
                            correlationId, instanceId))
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
