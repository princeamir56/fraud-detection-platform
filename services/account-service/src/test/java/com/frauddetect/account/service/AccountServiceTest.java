package com.frauddetect.account.service;

import com.frauddetect.account.domain.AccountEntity;
import com.frauddetect.account.domain.AccountStatus;
import com.frauddetect.account.domain.AccountType;
import com.frauddetect.account.dto.AccountResponse;
import com.frauddetect.account.dto.BalanceChangeRequest;
import com.frauddetect.account.dto.CreateAccountRequest;
import com.frauddetect.account.repository.AccountRepository;
import com.frauddetect.common.error.BusinessRuleException;
import com.frauddetect.common.error.ConflictException;
import com.frauddetect.common.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock AccountRepository repository;

    @InjectMocks AccountService service;

    private CreateAccountRequest request(AccountType type, BigDecimal balance, BigDecimal creditLimit) {
        return new CreateAccountRequest("cust-1", null, type, "USD", balance, creditLimit);
    }

    private AccountEntity account(String id, AccountType type, BigDecimal balance,
                                  BigDecimal creditLimit, AccountStatus status) {
        AccountEntity e = new AccountEntity();
        e.setId(id);
        e.setCustomerId("cust-1");
        e.setAccountNumber("FD000000000001");
        e.setType(type);
        e.setCurrency("USD");
        e.setBalance(balance);
        e.setCreditLimit(creditLimit);
        e.setStatus(status);
        e.setOpenedAt(Instant.now());
        return e;
    }

    @Test
    void createGeneratesIdAndAccountNumberAndPersists() {
        when(repository.existsByAccountNumber(anyString())).thenReturn(false);
        when(repository.save(any(AccountEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = service.create(request(AccountType.CHECKING, new BigDecimal("100.00"), null));

        assertThat(response.id()).isNotBlank();
        assertThat(response.accountNumber()).startsWith("FD");
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.creditLimit()).isEqualByComparingTo("0.00");
        verify(repository).save(any(AccountEntity.class));
    }

    @Test
    void createKeepsCreditLimitOnlyForCreditAccounts() {
        when(repository.existsByAccountNumber(anyString())).thenReturn(false);
        when(repository.save(any(AccountEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse credit = service.create(
                request(AccountType.CREDIT, BigDecimal.ZERO, new BigDecimal("500.00")));
        assertThat(credit.creditLimit()).isEqualByComparingTo("500.00");
    }

    @Test
    void createRejectsDuplicateAccountNumber() {
        var req = new CreateAccountRequest("cust-1", "FD-DUP", AccountType.CHECKING, "USD",
                BigDecimal.ZERO, null);
        when(repository.existsByAccountNumber("FD-DUP")).thenReturn(true);

        assertThatThrownBy(() -> service.create(req)).isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void debitWithinBalanceSucceeds() {
        when(repository.findById("a1"))
                .thenReturn(Optional.of(account("a1", AccountType.CHECKING, new BigDecimal("100.00"),
                        BigDecimal.ZERO, AccountStatus.ACTIVE)));

        AccountResponse response = service.debit("a1", new BalanceChangeRequest(new BigDecimal("40.00"), "atm"));

        assertThat(response.balance()).isEqualByComparingTo("60.00");
    }

    @Test
    void debitBeyondBalanceOnDebitAccountIsRejected() {
        when(repository.findById("a1"))
                .thenReturn(Optional.of(account("a1", AccountType.CHECKING, new BigDecimal("30.00"),
                        BigDecimal.ZERO, AccountStatus.ACTIVE)));

        assertThatThrownBy(() -> service.debit("a1", new BalanceChangeRequest(new BigDecimal("50.00"), null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void creditAccountMayGoNegativeDownToCreditLimit() {
        when(repository.findById("c1"))
                .thenReturn(Optional.of(account("c1", AccountType.CREDIT, new BigDecimal("0.00"),
                        new BigDecimal("200.00"), AccountStatus.ACTIVE)));

        AccountResponse response = service.debit("c1", new BalanceChangeRequest(new BigDecimal("150.00"), null));
        assertThat(response.balance()).isEqualByComparingTo("-150.00");
    }

    @Test
    void frozenAccountRejectsBalanceOperations() {
        when(repository.findById("f1"))
                .thenReturn(Optional.of(account("f1", AccountType.CHECKING, new BigDecimal("100.00"),
                        BigDecimal.ZERO, AccountStatus.FROZEN)));

        assertThatThrownBy(() -> service.credit("f1", new BalanceChangeRequest(new BigDecimal("10.00"), null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void missingAccountReturnsNotFound() {
        when(repository.findById("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById("nope")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void closedAccountCannotBeReopened() {
        when(repository.findById("x1"))
                .thenReturn(Optional.of(account("x1", AccountType.CHECKING, BigDecimal.ZERO,
                        BigDecimal.ZERO, AccountStatus.CLOSED)));

        assertThatThrownBy(() -> service.changeStatus("x1", AccountStatus.ACTIVE))
                .isInstanceOf(BusinessRuleException.class);
    }
}
