package com.carddemo.interest.domain.account;

/**
 * {@code 1050-UPDATE-ACCOUNT} (CBACT04C lines 356-369): REWRITE of the account record returned a
 * non-'00' status ('ERROR RE-WRITING ACCOUNT FILE'), which the COBOL program treats as fatal and
 * abends.
 */
public final class AccountRewriteException extends AccountUpdateException {

    public AccountRewriteException(long accountId, String reason) {
        this(accountId, reason, null);
    }

    public AccountRewriteException(long accountId, String reason, Throwable cause) {
        super(accountId, "Error re-writing account " + accountId + ": " + reason, cause);
    }
}
