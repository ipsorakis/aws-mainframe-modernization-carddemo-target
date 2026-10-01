package com.carddemo.interest.application.port;

import com.carddemo.interest.domain.DisclosureGroup;

import java.util.Optional;

/** DISCGRP: indexed file with random access. */
public interface DisclosureGroupRepository {

    /**
     * {@code 1200-GET-INTEREST-RATE} / {@code 1200-A-GET-DEFAULT-INT-RATE}: READ DISCGRP-FILE by
     * {@code DIS-GROUP-KEY}. An empty result corresponds to file status {@code 23}.
     */
    Optional<DisclosureGroup> findByKey(DisclosureGroup.Key key);
}
