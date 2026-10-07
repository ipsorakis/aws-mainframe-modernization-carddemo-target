package com.carddemo.interest.adapter.persistence;

import com.carddemo.interest.application.port.AccountRepository;
import com.carddemo.interest.domain.Account;
import com.carddemo.interest.domain.account.AccountRewriteException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * In-memory ACCTFILE keyed by {@code ACCT-ID}, for tests and local runs. Follows KSDS semantics:
 * REWRITE of a key that does not exist fails (VSAM status '23') instead of inserting.
 */
public class InMemoryAccountRepository implements AccountRepository {

    private final Map<Long, Account> accountsById = new ConcurrentSkipListMap<>();

    public InMemoryAccountRepository(Iterable<Account> initialAccounts) {
        for (Account account : initialAccounts) {
            Objects.requireNonNull(account, "account");
            if (accountsById.putIfAbsent(account.id(), account) != null) {
                throw new IllegalArgumentException("Duplicate ACCT-ID " + account.id());
            }
        }
    }

    public InMemoryAccountRepository(Account... initialAccounts) {
        this(List.of(initialAccounts));
    }

    @Override
    public Optional<Account> findById(long accountId) {
        return Optional.ofNullable(accountsById.get(accountId));
    }

    @Override
    public void update(Account account) {
        Objects.requireNonNull(account, "account");
        if (accountsById.computeIfPresent(account.id(), (id, previous) -> account) == null) {
            throw new AccountRewriteException(account.id(), "record not found (status 23)");
        }
    }

    /** All stored accounts in {@code ACCT-ID} order. */
    public List<Account> findAll() {
        return List.copyOf(accountsById.values());
    }
}
