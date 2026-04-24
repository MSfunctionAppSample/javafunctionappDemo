package com.apim.kunji.services;

import com.apim.kunji.models.request.StatementRequestDTO;
import com.apim.kunji.models.response.StatementResponseDTO;
import com.apim.kunji.repositories.StatementRepository;
import com.apim.kunji.util.CorrelationContext;
import com.apim.kunji.util.HttpRetryExhaustedException;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestration service for Statement Initiation and Retrieval APIs.
 *
 * <p><b>Initiation flow:</b>
 * <ol>
 *   <li>Validate request (accounts non-empty, date range valid)</li>
 *   <li>TODO: Send ACK request to Kunji ESB</li>
 *   <li>Insert parent job + child accounts in one Java-controlled transaction</li>
 *   <li>Return requestId for orchestrator to start polling</li>
 * </ol>
 *
 * <p><b>Retrieval flow:</b>
 * <ol>
 *   <li>Query job status by correlationId</li>
 *   <li>If READY → return response with blob reference</li>
 *   <li>If PENDING → return 202 status</li>
 *   <li>If not found → return null (caller returns 404)</li>
 * </ol>
 *
 * <p>Uses constructor injection for testability.
 */
public class StatementService {

    private static final Logger LOG = Logger.getLogger(StatementService.class.getName());

    private final StatementRepository repository;

    public StatementService(StatementRepository repository) {
        this.repository = repository;
    }

    /**
     * Initiates a statement generation request.
     *
     * @param request validated request DTO
     * @return generated requestId (used to start the Durable orchestrator)
     * @throws IllegalArgumentException on invalid input
     * @throws IllegalStateException    on duplicate correlationId (HTTP 409)
     * @throws HttpRetryExhaustedException    if Kunji ESB ACK call times out
     * @throws SQLException             on DB failure
     */
    public long initiateStatement(StatementRequestDTO request)
            throws HttpRetryExhaustedException, SQLException {

        // --- 1. Validate ---
        if (request.getFormat() == null || request.getFormat().isBlank()) {
            throw new IllegalArgumentException("format is required");
        }
        if (request.getAccounts() == null || request.getAccounts().isEmpty()) {
            throw new IllegalArgumentException("At least one account is required");
        }
        for (var account : request.getAccounts()) {
            if (account.getAccountNumber() == null || account.getAccountNumber().isBlank()) {
                throw new IllegalArgumentException("accountNumber is required for each account");
            }
            if (account.getFromDate() == null || account.getToDate() == null) {
                throw new IllegalArgumentException("fromDate and toDate are required for each account");
            }
            if (account.getToDate().isBefore(account.getFromDate())) {
                throw new IllegalArgumentException(
                        "toDate must not be before fromDate for account: " + account.getAccountNumber());
            }
        }

        String correlationId = CorrelationContext.getCorrelationId();

        // --- 2. Kunji ESB ACK ---
        // TODO: Send ACK to Kunji ESB to register the statement request upstream
        //   HttpRequest req = HttpRequest.newBuilder(URI.create(kunjiBaseUrl + "/statement/initiate"))
        //       .header("Content-Type", "application/json")
        //       .header("X-Correlation-ID", correlationId)
        //       .POST(HttpRequest.BodyPublishers.ofString(
        //           JsonUtil.getMapper().writeValueAsString(request)))
        //       .build();
        //   HttpResponse<String> resp = httpRetryClient.sendWithRetry(req);
        //   if (resp.statusCode() != 200 && resp.statusCode() != 202) {
        //       throw new RuntimeException("Kunji ESB rejected statement initiation: " + resp.statusCode());
        //   }

        // --- 3. Persist (parent + children in one tx) ---
        long requestId = repository.insertStatementRequest(
                correlationId,
                request.getFormat(),
                request.getAccounts()
        );

        if (requestId == -1L) {
            throw new IllegalStateException("Duplicate correlationId: " + correlationId);
        }

        LOG.info(String.format("[%s] Statement job created: requestId=%d, accounts=%d",
                correlationId, requestId, request.getAccounts().size()));

        return requestId;
    }

    /**
     * Retrieves the current status of a statement request.
     *
     * @param correlationId the correlation ID from the original initiation request
     * @return response DTO with current status, or null if not found
     * @throws SQLException on DB failure
     */
    public StatementResponseDTO getStatementStatus(String correlationId) throws SQLException {
        String status = repository.getJobStatus(correlationId);

        if (status == null) {
            return null; // caller returns HTTP 404
        }

        StatementResponseDTO response = new StatementResponseDTO(correlationId, status);

        if ("READY".equals(status)) {
            // TODO: Attach blob download reference or payload
            //   String blobName = correlationId + "-consolidated.gz";
            //   try (InputStream is = BlobStorageManager.downloadAsStream(containerName, blobName)) {
            //       byte[] compressed = is.readAllBytes();
            //       byte[] json = CompressionUtil.decompress(compressed);
            //       // attach to response or stream directly
            //   }
        }

        return response;
    }
}
