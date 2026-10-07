package com.carddemo.interest.domain.account;

import com.carddemo.interest.domain.Account;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Pure port of the computation in CBACT04C {@code 1050-UPDATE-ACCOUNT} (lines 350-354):
 *
 * <pre>
 *     ADD WS-TOTAL-INT  TO ACCT-CURR-BAL
 *     MOVE 0 TO ACCT-CURR-CYC-CREDIT
 *     MOVE 0 TO ACCT-CURR-CYC-DEBIT
 * </pre>
 *
 * All other {@code ACCOUNT-RECORD} (CVACT01Y) fields are carried over unchanged. The REWRITE that
 * follows (lines 356-369) is done by the caller through {@code AccountRepository.update}.
 */
public final class AccountInterestPosting {

    /** Scale of every {@code PIC S9(10)V99} field in CVACT01Y. */
    static final int AMOUNT_SCALE = 2;

    /** Largest magnitude {@code ACCT-CURR-BAL PIC S9(10)V99} can hold. */
    public static final BigDecimal MAX_CURRENT_BALANCE = new BigDecimal("9999999999.99");

    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(AMOUNT_SCALE);

    /**
     * Returns a new account with {@code totalInterest} added to the current balance and both cycle
     * totals reset to zero.
     *
     * @param account       the account as read by {@code 1100-GET-ACCT-DATA}
     * @param totalInterest {@code WS-TOTAL-INT PIC S9(09)V99}, may be zero or negative
     * @throws AccountBalanceOverflowException if the new balance does not fit {@code S9(10)V99}
     *         (COBOL would silently truncate the high-order digits instead)
     */
    public Account post(Account account, BigDecimal totalInterest) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(totalInterest, "totalInterest");
        Objects.requireNonNull(account.currentBalance(), "account.currentBalance");

        // ADD without ROUNDED: the exact sum is stored truncated to the receiving field's scale.
        BigDecimal newBalance = account.currentBalance().add(totalInterest)
                .setScale(AMOUNT_SCALE, RoundingMode.DOWN);
        if (newBalance.abs().compareTo(MAX_CURRENT_BALANCE) > 0) {
            throw new AccountBalanceOverflowException(account.id(), account.currentBalance(), totalInterest);
        }

        return new Account(
                account.id(),
                account.activeStatus(),
                newBalance,
                account.creditLimit(),
                account.cashCreditLimit(),
                account.openDate(),
                account.expirationDate(),
                account.reissueDate(),
                ZERO_AMOUNT,
                ZERO_AMOUNT,
                account.addressZip(),
                account.groupId());
    }
}
