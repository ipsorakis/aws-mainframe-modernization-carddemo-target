package com.carddemo.interest.domain.transaction;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One monthly interest amount to turn into a transaction: the input of {@code 1300-B-WRITE-TX},
 * produced per category balance by {@code 1300-COMPUTE-INTEREST} (DM-6).
 *
 * <p>Minimal contract owned by DM-8 until DM-6's output type lands on main; to be aligned with it.
 *
 * @param accountId       ACCT-ID        PIC 9(11)
 * @param monthlyInterest WS-MONTHLY-INT PIC S9(09)V99
 */
public record MonthlyInterestLine(long accountId, BigDecimal monthlyInterest) {

    public static final long MAX_ACCOUNT_ID = 99_999_999_999L;
    static final int AMOUNT_INTEGER_DIGITS = 9;
    static final int AMOUNT_SCALE = 2;

    public MonthlyInterestLine {
        if (accountId < 0 || accountId > MAX_ACCOUNT_ID) {
            throw new IllegalArgumentException("ACCT-ID must fit PIC 9(11), got " + accountId);
        }
        Objects.requireNonNull(monthlyInterest, "WS-MONTHLY-INT must not be null");
        if (monthlyInterest.stripTrailingZeros().scale() > AMOUNT_SCALE
                || monthlyInterest.precision() - monthlyInterest.scale() > AMOUNT_INTEGER_DIGITS) {
            throw new IllegalArgumentException("WS-MONTHLY-INT must fit PIC S9(09)V99, got " + monthlyInterest);
        }
        monthlyInterest = monthlyInterest.setScale(AMOUNT_SCALE);
    }
}
