package com.apim.kunji.functions.statement;

import com.apim.kunji.models.response.StatementResponseDTO;
import com.apim.kunji.repositories.StatementRepository;
import com.apim.kunji.services.StatementService;
import com.apim.kunji.util.CorrelationContext;
import com.apim.kunji.util.JsonUtil;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Azure HTTP Trigger for Statement Retrieval (GET /api/statement/{correlationId}).
 *
 * <p>Reads from DB and Blob Storage only — no Kunji ESB call.
 *
 * <p>Response codes:
 * <ul>
 *   <li>200 — READY, payload returned (or blob reference)</li>
 *   <li>202 — PENDING, still processing</li>
 *   <li>400 — correlationId missing</li>
 *   <li>404 — correlationId not found</li>
 *   <li>500 — unexpected server error</li>
 * </ul>
 */
public class StatementRetrievalFunction {

    private static final Logger LOG = Logger.getLogger(StatementRetrievalFunction.class.getName());

    private final StatementService statementService;

    public StatementRetrievalFunction() {
        this.statementService = new StatementService(new StatementRepository());
    }

    StatementRetrievalFunction(StatementService statementService) {
        this.statementService = statementService;
    }

    @FunctionName("StatementRetrieval")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.GET},
                    route = "statement/{correlationId}",
                    authLevel = AuthorizationLevel.FUNCTION
            ) HttpRequestMessage<Optional<String>> request,
            @BindingName("correlationId") String correlationId,
            ExecutionContext context) {

        String headerCorrelationId = request.getHeaders().getOrDefault("x-correlation-id", correlationId);
        String messageId = request.getHeaders().getOrDefault("x-message-id", "unknown");
        CorrelationContext.set(headerCorrelationId, messageId);

        try {
            if (correlationId == null || correlationId.isBlank()) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                        .header("Content-Type", "application/json")
                        .body("{\"error\":\"correlationId path parameter is required\"}")
                        .build();
            }

            // --- Query DB for job status ---
            StatementResponseDTO response = statementService.getStatementStatus(correlationId);

            if (response == null) {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                        .header("Content-Type", "application/json")
                        .header("X-Correlation-ID", correlationId)
                        .body("{\"error\":\"Statement request not found for correlationId: " + correlationId + "\"}")
                        .build();
            }

            // --- Map status to HTTP code ---
            HttpStatus httpStatus = switch (response.getStatus()) {
                case "READY"   -> HttpStatus.OK;
                case "PENDING" -> HttpStatus.ACCEPTED;
                case "FAILED"  -> HttpStatus.INTERNAL_SERVER_ERROR;
                default        -> HttpStatus.ACCEPTED;
            };

            String responseJson = JsonUtil.getMapper().writeValueAsString(response);
            return request.createResponseBuilder(httpStatus)
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-ID", correlationId)
                    .body(responseJson)
                    .build();

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "[" + correlationId + "] DB error: " + e.getMessage(), e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-ID", correlationId)
                    .body("{\"error\":\"An internal error occurred. Correlation ID: " + correlationId + "\"}")
                    .build();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[" + correlationId + "] Unexpected error: " + e.getMessage(), e);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-ID", correlationId)
                    .body("{\"error\":\"An internal error occurred. Correlation ID: " + correlationId + "\"}")
                    .build();

        } finally {
            CorrelationContext.clear();
        }
    }
}
