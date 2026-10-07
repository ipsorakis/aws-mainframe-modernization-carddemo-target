package com.carddemo.interest.domain.interest;

import com.carddemo.interest.application.port.DisclosureGroupRepository;
import com.carddemo.interest.domain.DisclosureGroup;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory DISCGRP: an empty result is file status 23. Records every key read. */
class InMemoryDisclosureGroupRepository implements DisclosureGroupRepository {

    private final Map<DisclosureGroup.Key, DisclosureGroup> records = new HashMap<>();
    private final List<DisclosureGroup.Key> reads = new ArrayList<>();

    InMemoryDisclosureGroupRepository with(String groupId, String typeCode, int categoryCode, String rate) {
        var key = new DisclosureGroup.Key(groupId, typeCode, categoryCode);
        records.put(key, new DisclosureGroup(key, new BigDecimal(rate)));
        return this;
    }

    @Override
    public Optional<DisclosureGroup> findByKey(DisclosureGroup.Key key) {
        reads.add(key);
        return Optional.ofNullable(records.get(key));
    }

    List<DisclosureGroup.Key> reads() {
        return reads;
    }
}
