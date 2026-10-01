package com.carddemo.interest.adapter.persistence;

import com.carddemo.interest.application.port.CardXrefRepository;
import com.carddemo.interest.domain.CardXref;

import java.util.Optional;

/** Placeholder {@link CardXrefRepository}; no storage is wired yet. */
public class PlaceholderCardXrefRepository implements CardXrefRepository {

    @Override
    public Optional<CardXref> findByAccountId(long accountId) {
        throw notImplemented();
    }

    private static UnsupportedOperationException notImplemented() {
        return new UnsupportedOperationException("Persistence adapter not implemented yet");
    }
}
