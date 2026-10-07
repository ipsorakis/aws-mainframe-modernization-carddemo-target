package com.carddemo.interest.domain.interest;

import java.math.BigDecimal;
import java.util.List;

/**
 * Interest for one run of consecutive TCATBALF records with the same {@code TRANCAT-ACCT-ID}: the
 * value of {@code WS-TOTAL-INT} when the account control break (or end of file) triggers
 * {@code 1050-UPDATE-ACCOUNT}.
 *
 * @param accountId     TRANCAT-ACCT-ID PIC 9(11)
 * @param totalInterest WS-TOTAL-INT PIC S9(09)V99
 * @param lines         one entry per category whose rate was non-zero, in input order; empty when
 *                      every rate was zero (the account is still posted with a zero total)
 */
public record AccountInterest(
        long accountId,
        BigDecimal totalInterest,
        List<CategoryInterest> lines) {

    public AccountInterest {
        lines = List.copyOf(lines);
    }
}
