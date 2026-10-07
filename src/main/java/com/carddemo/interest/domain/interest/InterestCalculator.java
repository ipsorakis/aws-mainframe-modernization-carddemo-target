package com.carddemo.interest.domain.interest;

import com.carddemo.interest.domain.TransactionCategoryBalance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * CBACT04C {@code 1300-COMPUTE-INTEREST} and the {@code WS-TOTAL-INT} roll-up of the main loop.
 * Stateless and free of I/O.
 */
public class InterestCalculator {

    /** {@code PIC S9(09)V99} of TRAN-CAT-BAL, WS-MONTHLY-INT and WS-TOTAL-INT. */
    private static final int AMOUNT_INTEGER_DIGITS = 9;
    private static final int AMOUNT_SCALE = 2;
    /** {@code PIC S9(04)V99} of DIS-INT-RATE. */
    private static final int RATE_INTEGER_DIGITS = 4;
    private static final int RATE_SCALE = 2;
    /** 12 months x 100 (the rate is a percentage). */
    private static final BigDecimal DIVISOR = BigDecimal.valueOf(1200);

    /**
     * {@code COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200} (CBACT04C lines 464-465).
     * No {@code ROUNDED}: decimals beyond the cent are truncated toward zero. No
     * {@code ON SIZE ERROR}: integer digits beyond the ninth are silently dropped.
     *
     * @param balance      TRAN-CAT-BAL PIC S9(09)V99
     * @param interestRate DIS-INT-RATE PIC S9(04)V99
     * @return WS-MONTHLY-INT PIC S9(09)V99
     */
    public BigDecimal monthlyInterest(BigDecimal balance, BigDecimal interestRate) {
        BigDecimal bal = CobolDecimal.requireFits(balance, AMOUNT_INTEGER_DIGITS, AMOUNT_SCALE, "TRAN-CAT-BAL");
        BigDecimal rate = CobolDecimal.requireFits(interestRate, RATE_INTEGER_DIGITS, RATE_SCALE, "DIS-INT-RATE");
        BigDecimal quotient = bal.multiply(rate).divide(DIVISOR, AMOUNT_SCALE, RoundingMode.DOWN);
        return CobolDecimal.store(quotient, AMOUNT_INTEGER_DIGITS, AMOUNT_SCALE);
    }

    /**
     * Rolls monthly interest up per account, as the CBACT04C main loop does (lines 188-222):
     * {@code WS-TOTAL-INT} is reset to zero whenever {@code TRANCAT-ACCT-ID} differs from the
     * previous record (lines 194-201), interest is computed only when {@code DIS-INT-RATE NOT = 0}
     * (lines 214-217), and each {@code WS-MONTHLY-INT} is added to {@code WS-TOTAL-INT}
     * (line 467).
     *
     * <p>Only consecutive records are grouped. Input must be in {@code TRAN-CAT-KEY} order, as
     * delivered by {@code TransactionCategoryBalanceReader}; an account that reappears after a
     * different account yields a second result, exactly like the COBOL control break.
     *
     * @param entries TCATBALF records in read order, each with its resolved rate
     * @return one result per control-break group, in input order
     */
    public List<AccountInterest> rollUp(List<RatedCategoryBalance> entries) {
        Objects.requireNonNull(entries, "entries");
        List<AccountInterest> results = new ArrayList<>();
        Long currentAccountId = null;
        BigDecimal totalInterest = BigDecimal.ZERO.setScale(AMOUNT_SCALE);
        List<CategoryInterest> lines = new ArrayList<>();

        for (RatedCategoryBalance entry : entries) {
            TransactionCategoryBalance categoryBalance = entry.categoryBalance();
            long accountId = categoryBalance.key().accountId();
            if (currentAccountId == null || currentAccountId != accountId) {
                if (currentAccountId != null) {
                    results.add(new AccountInterest(currentAccountId, totalInterest, lines));
                }
                currentAccountId = accountId;
                totalInterest = BigDecimal.ZERO.setScale(AMOUNT_SCALE);
                lines = new ArrayList<>();
            }
            if (entry.interestRate().signum() != 0) {
                BigDecimal monthly = monthlyInterest(categoryBalance.balance(), entry.interestRate());
                totalInterest = CobolDecimal.store(totalInterest.add(monthly), AMOUNT_INTEGER_DIGITS, AMOUNT_SCALE);
                lines.add(new CategoryInterest(
                        categoryBalance.key(), categoryBalance.balance(), entry.interestRate(), monthly));
            }
        }
        if (currentAccountId != null) {
            results.add(new AccountInterest(currentAccountId, totalInterest, lines));
        }
        return List.copyOf(results);
    }
}
