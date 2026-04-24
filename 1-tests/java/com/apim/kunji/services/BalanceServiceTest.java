package com.apim.kunji.services;

import com.apim.kunji.models.request.BalanceRequestDTO;
import com.apim.kunji.models.request.BalanceRequestDTO.BalanceAccountItem;
import com.apim.kunji.repositories.BalanceRepository;
import com.apim.kunji.repositories.CustomerAccountMappingRepository;
import com.apim.kunji.repositories.CustomerAccountMappingRepository.AccountMappingRow;
import com.apim.kunji.repositories.OpenCloseRepository;
import com.apim.kunji.util.CorrelationContext;
import com.apim.kunji.util.EntitlementException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock private BalanceRepository repository;
    @Mock private CustomerAccountMappingRepository mappingRepository;
    @Mock private OpenCloseRepository openCloseRepository;

    private BalanceService service;

    private static final String CUSTOMER_ID = "CUST-001";

    @BeforeEach
    void setUp() {
        service = new BalanceService(repository, mappingRepository, openCloseRepository);
        CorrelationContext.set("corr-test", "msg-test");
    }

    @AfterEach
    void cleanup() {
        CorrelationContext.clear();
    }

    private static BalanceRequestDTO validRequest() {
        return new BalanceRequestDTO(
                List.of(new BalanceAccountItem("ACC-001"), new BalanceAccountItem("ACC-002")),
                "CAMT.052"
        );
    }

    private static AccountMappingRow mappedRow(String accountNumber) {
        return new AccountMappingRow(accountNumber, "T24-" + accountNumber, "COMP-001",
                true, false, true);
    }

    // --- Validation tests ---

    @Test
    void getBalance_nullAccounts_throwsIllegalArgumentException() {
        BalanceRequestDTO request = new BalanceRequestDTO(null, "CAMT.052");
        assertThrows(IllegalArgumentException.class, () -> service.getBalance(request, CUSTOMER_ID));
    }

    @Test
    void getBalance_emptyAccounts_throwsIllegalArgumentException() {
        BalanceRequestDTO request = new BalanceRequestDTO(List.of(), "CAMT.052");
        assertThrows(IllegalArgumentException.class, () -> service.getBalance(request, CUSTOMER_ID));
    }

    @Test
    void getBalance_blankAccountNumber_throwsIllegalArgumentException() {
        BalanceRequestDTO request = new BalanceRequestDTO(
                List.of(new BalanceAccountItem("  ")), "CAMT.052");
        assertThrows(IllegalArgumentException.class, () -> service.getBalance(request, CUSTOMER_ID));
    }

    @Test
    void getBalance_missingFormat_throwsIllegalArgumentException() {
        BalanceRequestDTO request = new BalanceRequestDTO(
                List.of(new BalanceAccountItem("ACC-001")), null);
        assertThrows(IllegalArgumentException.class, () -> service.getBalance(request, CUSTOMER_ID));
    }

    // --- Open/Close gate ---

    @Test
    void getBalance_serviceClosed_throwsEntitlementException() throws Exception {
        when(openCloseRepository.isOpen()).thenReturn(false);

        EntitlementException ex = assertThrows(EntitlementException.class,
                () -> service.getBalance(validRequest(), CUSTOMER_ID));

        assertEquals(EntitlementException.Reason.SERVICE_CLOSED, ex.getReason());
    }

    // --- Account mapping + entitlement ---

    @Test
    void getBalance_accountNotMapped_throwsEntitlementException() throws Exception {
        when(openCloseRepository.isOpen()).thenReturn(true);
        when(mappingRepository.findByCustomerAndAccounts(eq(CUSTOMER_ID), anyList()))
                .thenReturn(List.of(mappedRow("ACC-001")));  // ACC-002 missing

        EntitlementException ex = assertThrows(EntitlementException.class,
                () -> service.getBalance(validRequest(), CUSTOMER_ID));

        assertEquals(EntitlementException.Reason.ACCOUNT_NOT_MAPPED, ex.getReason());
        assertEquals("ACC-002", ex.getDetail());
    }

    @Test
    void getBalance_accountNotActive_throwsEntitlementException() throws Exception {
        when(openCloseRepository.isOpen()).thenReturn(true);
        AccountMappingRow inactive = new AccountMappingRow("ACC-001", "T24-001", "COMP-001",
                true, false, false);  // is_active = false
        when(mappingRepository.findByCustomerAndAccounts(eq(CUSTOMER_ID), anyList()))
                .thenReturn(List.of(inactive, mappedRow("ACC-002")));

        EntitlementException ex = assertThrows(EntitlementException.class,
                () -> service.getBalance(validRequest(), CUSTOMER_ID));

        assertEquals(EntitlementException.Reason.ACCOUNT_NOT_ACTIVE, ex.getReason());
        assertEquals("ACC-001", ex.getDetail());
    }

    @Test
    void getBalance_balanceAccessDenied_throwsEntitlementException() throws Exception {
        when(openCloseRepository.isOpen()).thenReturn(true);
        AccountMappingRow noAccess = new AccountMappingRow("ACC-001", "T24-001", "COMP-001",
                false, false, true);  // has_account_balance_access = false
        when(mappingRepository.findByCustomerAndAccounts(eq(CUSTOMER_ID), anyList()))
                .thenReturn(List.of(noAccess, mappedRow("ACC-002")));

        EntitlementException ex = assertThrows(EntitlementException.class,
                () -> service.getBalance(validRequest(), CUSTOMER_ID));

        assertEquals(EntitlementException.Reason.BALANCE_ACCESS_DENIED, ex.getReason());
        assertEquals("ACC-001", ex.getDetail());
    }

    // --- Happy path (hits TODO) + audit ---

    @Test
    void getBalance_allChecksPassed_throwsUnsupportedOperation_auditFires() throws Exception {
        when(openCloseRepository.isOpen()).thenReturn(true);
        when(mappingRepository.findByCustomerAndAccounts(eq(CUSTOMER_ID), anyList()))
                .thenReturn(List.of(mappedRow("ACC-001"), mappedRow("ACC-002")));

        assertThrows(UnsupportedOperationException.class,
                () -> service.getBalance(validRequest(), CUSTOMER_ID));

        verify(repository, times(1)).insertAuditEntry(eq("corr-test"), anyString(), anyString());
    }

    @Test
    void getBalance_auditFailure_doesNotPropagateAuditException() throws Exception {
        when(openCloseRepository.isOpen()).thenReturn(true);
        when(mappingRepository.findByCustomerAndAccounts(eq(CUSTOMER_ID), anyList()))
                .thenReturn(List.of(mappedRow("ACC-001"), mappedRow("ACC-002")));
        doThrow(new RuntimeException("DB down")).when(repository)
                .insertAuditEntry(anyString(), anyString(), anyString());

        assertThrows(UnsupportedOperationException.class,
                () -> service.getBalance(validRequest(), CUSTOMER_ID));
    }
}
