package com.apim.kunji.functions.balance;

import com.apim.kunji.models.request.BalanceRequestDTO;
import com.apim.kunji.models.response.BalanceResponseDTO;
import com.apim.kunji.models.response.BalanceResponseDTO.BalanceAccountResult;
import com.apim.kunji.services.BalanceService;
import com.apim.kunji.util.CorrelationContext;
import com.apim.kunji.util.EntitlementException;
import com.apim.kunji.util.HttpRetryExhaustedException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BalanceFunctionTest {

    @Mock private BalanceService balanceService;
    @Mock private HttpRequestMessage<Optional<String>> httpRequest;
    @Mock private ExecutionContext executionContext;
    @Mock private HttpResponseMessage.Builder responseBuilder;
    @Mock private HttpResponseMessage httpResponse;

    private BalanceFunction function;

    private static final String VALID_BODY =
            "{\"accounts\":[{\"accountNumber\":\"Acc1\"},{\"accountNumber\":\"Acc2\"}],\"format\":\"CAMT.052\"}";

    @BeforeEach
    void setUp() {
        function = new BalanceFunction(balanceService);

        Map<String, String> headers = new HashMap<>();
        headers.put("x-correlation-id", "test-corr-123");
        headers.put("x-message-id", "test-msg-456");
        headers.put("x-customer-id", "CUST-001");

        when(httpRequest.getHeaders()).thenReturn(headers);
        lenient().when(executionContext.getLogger()).thenReturn(Logger.getLogger("test"));

        when(httpRequest.createResponseBuilder(any(HttpStatus.class))).thenReturn(responseBuilder);
        when(responseBuilder.header(anyString(), anyString())).thenReturn(responseBuilder);
        when(responseBuilder.body(any())).thenReturn(responseBuilder);
        when(responseBuilder.build()).thenReturn(httpResponse);
    }

    @AfterEach
    void verifyContextCleared() {
        assertNull(CorrelationContext.getCorrelationId(),
                "CorrelationContext must be cleared in finally block");
    }

    @Test
    void run_emptyBody_returns400() {
        when(httpRequest.getBody()).thenReturn(Optional.empty());
        function.run(httpRequest, executionContext);
        verify(httpRequest).createResponseBuilder(HttpStatus.BAD_REQUEST);
    }

    @Test
    void run_invalidJson_returns400() {
        when(httpRequest.getBody()).thenReturn(Optional.of("{not-valid-json}"));
        function.run(httpRequest, executionContext);
        verify(httpRequest).createResponseBuilder(HttpStatus.BAD_REQUEST);
    }

    @Test
    void run_missingAccounts_returns400() {
        when(httpRequest.getBody()).thenReturn(Optional.of("{\"format\":\"CAMT.052\"}"));
        function.run(httpRequest, executionContext);
        verify(httpRequest).createResponseBuilder(HttpStatus.BAD_REQUEST);
    }

    @Test
    void run_missingFormat_returns400() {
        when(httpRequest.getBody()).thenReturn(
                Optional.of("{\"accounts\":[{\"accountNumber\":\"Acc1\"}]}"));
        function.run(httpRequest, executionContext);
        verify(httpRequest).createResponseBuilder(HttpStatus.BAD_REQUEST);
    }

    @Test
    void run_missingCustomerId_returns400() {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-correlation-id", "test-corr-123");
        headers.put("x-message-id", "test-msg-456");
        // No x-customer-id
        when(httpRequest.getHeaders()).thenReturn(headers);

        function.run(httpRequest, executionContext);
        verify(httpRequest).createResponseBuilder(HttpStatus.BAD_REQUEST);
    }

    @Test
    void run_successfulResponse_returns200WithCorrelationHeader() throws Exception {
        when(httpRequest.getBody()).thenReturn(Optional.of(VALID_BODY));

        BalanceAccountResult acct = new BalanceAccountResult();
        acct.setAccountNumber("Acc1");
        acct.setAccountCurrency("USD");
        acct.setClearedBalance(BigDecimal.valueOf(10000));
        acct.setOnlineBalance(BigDecimal.valueOf(10050));
        BalanceResponseDTO dto = new BalanceResponseDTO(Instant.now(), List.of(acct));
        when(balanceService.getBalance(any(BalanceRequestDTO.class), eq("CUST-001"))).thenReturn(dto);

        function.run(httpRequest, executionContext);

        verify(httpRequest).createResponseBuilder(HttpStatus.OK);
        verify(responseBuilder).header("X-Correlation-ID", "test-corr-123");
    }

    @Test
    void run_serviceClosed_returns503() throws Exception {
        when(httpRequest.getBody()).thenReturn(Optional.of(VALID_BODY));
        when(balanceService.getBalance(any(), anyString())).thenThrow(
                new EntitlementException(EntitlementException.Reason.SERVICE_CLOSED));

        function.run(httpRequest, executionContext);
        verify(httpRequest).createResponseBuilder(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void run_accountNotMapped_returns403() throws Exception {
        when(httpRequest.getBody()).thenReturn(Optional.of(VALID_BODY));
        when(balanceService.getBalance(any(), anyString())).thenThrow(
                new EntitlementException(EntitlementException.Reason.ACCOUNT_NOT_MAPPED, "Acc1"));

        function.run(httpRequest, executionContext);
        verify(httpRequest).createResponseBuilder(HttpStatus.FORBIDDEN);
    }

    @Test
    void run_balanceAccessDenied_returns403() throws Exception {
        when(httpRequest.getBody()).thenReturn(Optional.of(VALID_BODY));
        when(balanceService.getBalance(any(), anyString())).thenThrow(
                new EntitlementException(EntitlementException.Reason.BALANCE_ACCESS_DENIED, "Acc1"));

        function.run(httpRequest, executionContext);
        verify(httpRequest).createResponseBuilder(HttpStatus.FORBIDDEN);
    }

    @Test
    void run_kunjiTimeout_returns504() throws Exception {
        when(httpRequest.getBody()).thenReturn(Optional.of(VALID_BODY));
        when(balanceService.getBalance(any(), anyString())).thenThrow(
                new HttpRetryExhaustedException("All 3 attempts exhausted"));
        function.run(httpRequest, executionContext);
        verify(httpRequest).createResponseBuilder(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void run_unexpectedException_returns500() throws Exception {
        when(httpRequest.getBody()).thenReturn(Optional.of(VALID_BODY));
        when(balanceService.getBalance(any(), anyString())).thenThrow(new RuntimeException("unexpected"));
        function.run(httpRequest, executionContext);
        verify(httpRequest).createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void run_correlationIdExtractedFromHeader() {
        when(httpRequest.getBody()).thenReturn(Optional.empty());
        function.run(httpRequest, executionContext);
        verify(httpRequest, atLeastOnce()).getHeaders();
    }
}
