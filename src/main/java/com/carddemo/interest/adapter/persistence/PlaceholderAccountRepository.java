package com.carddemo.interest.adapter.persistence;

import com.carddemo.interest.application.port.AccountRepository;
import com.carddemo.interest.domain.Account;

import java.util.Optional;

/** Placeholder {@link AccountRepository}; no storage is wired yet. */
public class PlaceholderAccountRepository implements AccountRepository {

    @Override
    public Optional<Account> findById(long accountId) {
        throw notImplemented();
    }

    @Override
    public void update(Account account) {
        throw notImplemented();
    }

    private static UnsupportedOperationException notImplemented() {
        return new UnsupportedOperationException("Persistence adapter not implemented yet");
    }
}
