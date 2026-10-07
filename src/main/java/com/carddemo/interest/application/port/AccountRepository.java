package com.carddemo.interest.application.port;

import com.carddemo.interest.domain.Account;

import java.util.Optional;

/** ACCTFILE: indexed file opened I-O with random access. */
public interface AccountRepository {

    /** {@code 1100-GET-ACCT-DATA}: READ ACCOUNT-FILE by {@code ACCT-ID}. */
    Optional<Account> findById(long accountId);

    /**
     * {@code 1050-UPDATE-ACCOUNT}: REWRITE of an existing account record.
     *
     * @throws com.carddemo.interest.domain.account.AccountRewriteException if the record does not exist or cannot be rewritten
     *         (any non-'00' status, on which CBACT04C abends)
     */
    void update(Account account);
}
