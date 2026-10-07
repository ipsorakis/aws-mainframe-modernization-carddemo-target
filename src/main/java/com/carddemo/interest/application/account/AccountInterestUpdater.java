package com.carddemo.interest.application.account;

import com.carddemo.interest.application.port.AccountRepository;
import com.carddemo.interest.domain.Account;
import com.carddemo.interest.domain.account.AccountBalanceOverflowException;
import com.carddemo.interest.domain.account.AccountInterestPosting;
import com.carddemo.interest.domain.account.AccountNotFoundException;
import com.carddemo.interest.domain.account.AccountRewriteException;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Reads an account and rewrites it with its monthly interest posted, mirroring CBACT04C
 * {@code 1100-GET-ACCT-DATA} and {@code 1050-UPDATE-ACCOUNT}. Every non-'00' file status that makes
 * the COBOL program abend surfaces here as an {@link com.carddemo.interest.domain.account.AccountUpdateException}.
 */
public class AccountInterestUpdater {

    private final AccountRepository accounts;
    private final AccountInterestPosting posting;

    public AccountInterestUpdater(AccountRepository accounts, AccountInterestPosting posting) {
        this.accounts = Objects.requireNonNull(accounts);
        this.posting = Objects.requireNonNull(posting);
    }

    /**
     * {@code 1100-GET-ACCT-DATA} (lines 372-391): READ ACCOUNT-FILE by {@code ACCT-ID}.
     *
     * @throws AccountNotFoundException on INVALID KEY (COBOL abends)
     */
    public Account getAccount(long accountId) {
        return accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    /**
     * {@code 1050-UPDATE-ACCOUNT} (lines 350-370) for an account already read by
     * {@link #getAccount(long)}, as the COBOL program does: it rewrites the {@code ACCOUNT-RECORD}
     * it read at the account control break.
     *
     * @return the account as rewritten
     * @throws AccountBalanceOverflowException if the new balance does not fit {@code S9(10)V99};
     *         nothing is rewritten
     * @throws AccountRewriteException if the REWRITE fails (COBOL abends)
     */
    public Account postInterest(Account account, BigDecimal totalInterest) {
        Account updated = posting.post(account, totalInterest);
        accounts.update(updated);
        return updated;
    }

    /**
     * Reads the account by id ({@code 1100-GET-ACCT-DATA}), then posts and rewrites it
     * ({@code 1050-UPDATE-ACCOUNT}).
     *
     * @throws AccountNotFoundException if the account does not exist; nothing is rewritten
     * @throws AccountBalanceOverflowException if the new balance does not fit {@code S9(10)V99}
     * @throws AccountRewriteException if the REWRITE fails
     */
    public Account postInterest(long accountId, BigDecimal totalInterest) {
        return postInterest(getAccount(accountId), totalInterest);
    }
}
