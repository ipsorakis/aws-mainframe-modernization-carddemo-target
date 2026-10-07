package com.carddemo.interest.domain.account;

/**
 * Base type for the conditions under which CBACT04C performs {@code 9999-ABEND-PROGRAM} (or, for
 * {@link AccountBalanceOverflowException}, silently corrupts data) while reading or rewriting an
 * account. Callers must treat any of these as fatal for the run.
 */
public abstract sealed class AccountUpdateException extends RuntimeException
        permits AccountNotFoundException, AccountRewriteException, AccountBalanceOverflowException {

    private final long accountId;

    AccountUpdateException(long accountId, String message, Throwable cause) {
        super(message, cause);
        this.accountId = accountId;
    }

    /** {@code ACCT-ID} of the account being read or rewritten. */
    public long accountId() {
        return accountId;
    }
}
