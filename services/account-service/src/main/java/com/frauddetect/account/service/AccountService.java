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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Account business logic (Section 4 + Section 9). Balance mutations run in a transaction and rely on
 * the entity's optimistic {@code @Version} to serialise concurrent credit/debit requests — a lost
 * update on a balance is a correctness defect, so the second writer fails and retries rather than
 * silently overwriting. Domain rules (frozen account, insufficient funds, overdraft limit) surface
 * as {@link BusinessRuleException} → HTTP 422 via the common exception handler.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        String accountNumber = (request.accountNumber() == null || request.accountNumber().isBlank())
                ? generateAccountNumber()
                : request.accountNumber();

        if (repository.existsByAccountNumber(accountNumber)) {
            throw new ConflictException("Account number already exists: " + accountNumber);
        }

        AccountEntity entity = new AccountEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setCustomerId(request.customerId());
        entity.setAccountNumber(accountNumber);
        entity.setType(request.type());
        entity.setCurrency(request.currency());
        entity.setBalance(request.initialBalance());
        entity.setCreditLimit(request.type() == AccountType.CREDIT
                ? nullToZero(request.creditLimit())
                : BigDecimal.ZERO);
        entity.setStatus(AccountStatus.ACTIVE);
        entity.setOpenedAt(Instant.now());

        AccountEntity saved = repository.save(entity);
        log.info("Opened account {} ({}) for customer {}", saved.getId(), saved.getAccountNumber(),
                saved.getCustomerId());
        return AccountResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public AccountResponse getById(String id) {
        return AccountResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> byCustomer(String customerId, Pageable pageable) {
        return repository.findByCustomerId(customerId, pageable).map(AccountResponse::from);
    }

    @Transactional
    public AccountResponse credit(String id, BalanceChangeRequest request) {
        AccountEntity account = find(id);
        requireOperable(account);
        account.setBalance(account.getBalance().add(request.amount()));
        log.info("Credited {} {} to account {} ({})", request.amount(), account.getCurrency(), id,
                reasonOf(request));
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse debit(String id, BalanceChangeRequest request) {
        AccountEntity account = find(id);
        requireOperable(account);

        BigDecimal projected = account.getBalance().subtract(request.amount());
        BigDecimal floor = account.getCreditLimit().negate();
        if (projected.compareTo(floor) < 0) {
            throw new BusinessRuleException(
                    "Insufficient funds on account %s: balance %s, credit limit %s, requested debit %s"
                            .formatted(id, account.getBalance(), account.getCreditLimit(), request.amount()));
        }
        account.setBalance(projected);
        log.info("Debited {} {} from account {} ({})", request.amount(), account.getCurrency(), id,
                reasonOf(request));
        return AccountResponse.from(account);
    }

    /** Set the account lifecycle status (e.g. FROZEN as a fraud response, or CLOSED). */
    @Transactional
    public AccountResponse changeStatus(String id, AccountStatus status) {
        AccountEntity account = find(id);
        if (account.getStatus() == AccountStatus.CLOSED && status != AccountStatus.CLOSED) {
            throw new BusinessRuleException("Account " + id + " is CLOSED and cannot be reopened");
        }
        account.setStatus(status);
        log.info("Account {} status changed to {}", id, status);
        return AccountResponse.from(account);
    }

    private AccountEntity find(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
    }

    /** Balance operations require an ACTIVE account; FROZEN/CLOSED reject with a 422. */
    private static void requireOperable(AccountEntity account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Account %s is %s; balance operations are not permitted".formatted(account.getId(),
                            account.getStatus()));
        }
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String reasonOf(BalanceChangeRequest request) {
        return request.reason() == null || request.reason().isBlank() ? "no reason given" : request.reason();
    }

    /** Generates a demo IBAN-like account number; production would delegate to a numbering service. */
    private static String generateAccountNumber() {
        long n = Math.abs(ThreadLocalRandom.current().nextLong() % 1_0000_0000_0000L);
        return "FD%012d".formatted(n);
    }
}
