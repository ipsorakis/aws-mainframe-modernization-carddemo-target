package com.carddemo.interest.domain.interest;

import com.carddemo.interest.domain.TransactionCategoryBalance;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One TCATBALF record paired with the {@code DIS-INT-RATE} resolved for it by
 * {@code 1200-GET-INTEREST-RATE} (see {@link InterestRateResolver}).
 *
 * @param categoryBalance TRAN-CAT-BAL-RECORD
 * @param interestRate    DIS-INT-RATE PIC S9(04)V99 (annual percentage rate)
 */
public record RatedCategoryBalance(
        TransactionCategoryBalance categoryBalance,
        BigDecimal interestRate) {

    public RatedCategoryBalance {
        Objects.requireNonNull(categoryBalance, "categoryBalance");
        Objects.requireNonNull(interestRate, "interestRate");
    }
}
