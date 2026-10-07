package com.carddemo.interest.domain.account;

import java.math.BigDecimal;

/**
 * {@code 1050-UPDATE-ACCOUNT} line 352: {@code ADD WS-TOTAL-INT TO ACCT-CURR-BAL} produced a value
 * that does not fit {@code ACCT-CURR-BAL PIC S9(10)V99}.
 *
 * <p>Deliberate deviation from the legacy program: the COBOL ADD has no {@code ON SIZE ERROR}
 * clause, so it silently drops the high-order digits and rewrites a corrupted balance. The Java
 * port refuses to post instead.
 */
public final class AccountBalanceOverflowException extends AccountUpdateException {

    private final BigDecimal currentBalance;
    private final BigDecimal totalInterest;

    public AccountBalanceOverflowException(long accountId, BigDecimal currentBalance, BigDecimal totalInterest) {
        super(accountId, "ACCT-CURR-BAL PIC S9(10)V99 overflow for account " + accountId + ": "
                + currentBalance.toPlainString() + " + " + totalInterest.toPlainString()
                + " exceeds " + AccountInterestPosting.MAX_CURRENT_BALANCE.toPlainString() + " in magnitude", null);
        this.currentBalance = currentBalance;
        this.totalInterest = totalInterest;
    }

    public BigDecimal currentBalance() {
        return currentBalance;
    }

    public BigDecimal totalInterest() {
        return totalInterest;
    }
}
