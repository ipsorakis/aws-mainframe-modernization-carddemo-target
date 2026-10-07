package com.carddemo.interest.domain.account;

/**
 * {@code 1100-GET-ACCT-DATA} (CBACT04C lines 372-391): READ ACCOUNT-FILE returned a non-'00'
 * status (INVALID KEY, status '23'), which the COBOL program treats as fatal and abends.
 */
public final class AccountNotFoundException extends AccountUpdateException {

    public AccountNotFoundException(long accountId) {
        super(accountId, "Account not found: " + accountId, null);
    }
}
