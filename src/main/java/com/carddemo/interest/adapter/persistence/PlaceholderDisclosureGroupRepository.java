package com.carddemo.interest.adapter.persistence;

import com.carddemo.interest.application.port.DisclosureGroupRepository;
import com.carddemo.interest.domain.DisclosureGroup;

import java.util.Optional;

/** Placeholder {@link DisclosureGroupRepository}; no storage is wired yet. */
public class PlaceholderDisclosureGroupRepository implements DisclosureGroupRepository {

    @Override
    public Optional<DisclosureGroup> findByKey(DisclosureGroup.Key key) {
        throw notImplemented();
    }

    private static UnsupportedOperationException notImplemented() {
        return new UnsupportedOperationException("Persistence adapter not implemented yet");
    }
}
