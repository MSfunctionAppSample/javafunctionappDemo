package com.apim.kunji.services;

import com.apim.kunji.models.MappedAccount;
import com.apim.kunji.models.request.BalanceRequestDTO;
import com.apim.kunji.models.response.BalanceResponseDTO;
import com.apim.kunji.repositories.BalanceRepository;
import com.apim.kunji.repositories.CustomerAccountMappingRepository;
import com.apim.kunji.repositories.CustomerAccountMappingRepository.AccountMappingRow;
import com.apim.kunji.repositories.OpenCloseRepository;
import com.apim.kunji.util.CorrelationContext;
import com.apim.kunji.util.EntitlementException;
import com.apim.kunji.util.EntitlementException.Reason;
import com.apim.kunji.util.HttpRetryExhaustedException;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Orchestration service for the Balance API.
 *
 * <p>Flow:
 * <ol>
 *   <li>Validate request fields</li>
 *   <li>Pre-check: Open/Close gate</li>
 *   <li>Pre-check: Customer account mapping + entitlement (is_active, has_account_balance_access)</li>
 *   <li>Call Kunji ESB using resolved T24 account numbers + company IDs</li>
 *   <li>Map response → best-effort audit</li>
 * </ol>
 *
 * <p>Uses constructor injection so tests can pass mock dependencies.
 */
public class BalanceService {

    private static final Logger LOG = Logger.getLogger(BalanceService.class.getName());

    private final BalanceRepository repository;
    private final CustomerAccountMappingRepository mappingRepository;
    private final OpenCloseRepository openCloseRepository;

    public BalanceService(BalanceRepository repository,
                          CustomerAccountMappingRepository mappingRepository,
                          OpenCloseRepository openCloseRepository) {
        this.repository = repository;
        this.mappingRepository = mappingRepository;
        this.openCloseRepository = openCloseRepository;
    }

    /**
     * Processes a balance inquiry for one or more accounts.
     *
     * @param request    the validated request DTO
     * @param customerId the customer ID from the {@code x-customer-id} APIM header
     * @return the balance response DTO
     * @throws IllegalArgumentException if the request fields are invalid
     * @throws EntitlementException     if any pre-check fails (open/close, mapping, access)
     * @throws HttpRetryExhaustedException    if the Kunji ESB call times out after retries
     * @throws SQLException             on DB failure during entitlement checks
     */
    public BalanceResponseDTO getBalance(BalanceRequestDTO request, String customerId)
            throws HttpRetryExhaustedException, EntitlementException, SQLException {

        String correlationId = CorrelationContext.getCorrelationId();
        String status = "SUCCESS";

        // --- 1. Validate request fields ---
        if (request.getAccounts() == null || request.getAccounts().isEmpty()) {
            throw new IllegalArgumentException("At least one account is required");
        }
        for (var account : request.getAccounts()) {
            if (account.getAccountNumber() == null || account.getAccountNumber().isBlank()) {
                throw new IllegalArgumentException("accountNumber is required for each account entry");
            }
        }
        if (request.getFormat() == null || request.getFormat().isBlank()) {
            throw new IllegalArgumentException("format is required (e.g. CAMT.052)");
        }

        // --- 2. Pre-check: Open/Close gate ---
        if (!openCloseRepository.isOpen()) {
            LOG.warning("[" + correlationId + "] Service is closed — rejecting balance request");
            throw new EntitlementException(Reason.SERVICE_CLOSED);
        }

        // --- 3. Pre-check: Customer account mapping + entitlement ---
        List<String> requestedAccounts = request.getAccounts().stream()
                .map(BalanceRequestDTO.BalanceAccountItem::getAccountNumber)
                .toList();

        List<AccountMappingRow> rows = mappingRepository.findByCustomerAndAccounts(
                customerId, requestedAccounts);

        Map<String, AccountMappingRow> rowsByAccount = rows.stream()
                .collect(Collectors.toMap(AccountMappingRow::customerAccountNumber, Function.identity()));

        List<MappedAccount> mappedAccounts = new java.util.ArrayList<>();

        for (String accountNumber : requestedAccounts) {
            AccountMappingRow row = rowsByAccount.get(accountNumber);

            if (row == null) {
                LOG.warning("[" + correlationId + "] Account not mapped: " + accountNumber +
                        " for customer: " + customerId);
                throw new EntitlementException(Reason.ACCOUNT_NOT_MAPPED, accountNumber);
            }
            if (!row.isActive()) {
                LOG.warning("[" + correlationId + "] Account not active: " + accountNumber);
                throw new EntitlementException(Reason.ACCOUNT_NOT_ACTIVE, accountNumber);
            }
            if (!row.hasAccountBalanceAccess()) {
                LOG.warning("[" + correlationId + "] Balance access denied: " + accountNumber);
                throw new EntitlementException(Reason.BALANCE_ACCESS_DENIED, accountNumber);
            }

            mappedAccounts.add(new MappedAccount(
                    accountNumber, row.t24AccountNumber(), row.companyId()));
        }

        LOG.info("[" + correlationId + "] Entitlement passed for " + mappedAccounts.size() +
                " account(s), customer: " + customerId);

        try {
            // --- 4. Call Kunji ESB (using resolved T24 accounts + company IDs) ---
            // TODO: Build Kunji request body using mappedAccounts:
            //   for (MappedAccount ma : mappedAccounts) {
            //       // use ma.t24AccountNumber() and ma.companyId() to construct the ESB payload
            //   }
            //   HttpRequest req = HttpRequest.newBuilder(URI.create(kunjiBaseUrl + "/balance"))
            //       .header("Content-Type", "application/json")
            //       .header("X-Correlation-ID", correlationId)
            //       .POST(HttpRequest.BodyPublishers.ofString(kunjiPayload))
            //       .build();
            //   HttpResponse<String> resp = httpRetryClient.sendWithRetry(req);

            // --- 5. Map Kunji response to BalanceResponseDTO ---
            // TODO: Parse resp.body() into BalanceResponseDTO

            // Placeholder — remove when implementing
            throw new UnsupportedOperationException(
                    "TODO: Implement Kunji ESB call and response mapping");

        } catch (UnsupportedOperationException e) {
            status = "NOT_IMPLEMENTED";
            throw e;
        } catch (Exception e) {
            status = "ERROR";
            throw new RuntimeException("Balance inquiry failed: " + e.getMessage(), e);
        } finally {
            // --- 6. Best-effort audit ---
            try {
                String accounts = requestedAccounts.stream()
                        .reduce((a, b) -> a + "," + b)
                        .orElse("none");
                repository.insertAuditEntry(correlationId, accounts, status);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Audit write failed (non-fatal): " + e.getMessage(), e);
            }
        }
    }
}
