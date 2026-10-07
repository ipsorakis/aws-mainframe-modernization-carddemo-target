package com.carddemo.interest.application.port;

import com.carddemo.interest.domain.CardXref;

import java.util.Optional;

/** XREFFILE: indexed file read by its alternate key. */
public interface CardXrefRepository {

    /** {@code 1110-GET-XREF-DATA}: READ XREF-FILE KEY IS {@code XREF-ACCT-ID}. */
    Optional<CardXref> findByAccountId(long accountId);
}
