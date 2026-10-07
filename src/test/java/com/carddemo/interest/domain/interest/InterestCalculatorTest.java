package com.carddemo.interest.domain.interest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Characterises CBACT04C {@code 1300-COMPUTE-INTEREST}, lines 464-465:
 * {@code COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200} with no {@code ROUNDED}
 * and no {@code ON SIZE ERROR}, into {@code WS-MONTHLY-INT PIC S9(09)V99} (line 168).
 */
class InterestCalculatorTest {

    private final InterestCalculator calculator = new InterestCalculator();

    @ParameterizedTest(name = "[{index}] {3}: {0} x {1} / 1200 = {2}")
    @CsvSource(delimiter = '|', textBlock = """
            # TRAN-CAT-BAL  | DIS-INT-RATE | WS-MONTHLY-INT | case
            0.00            | 24.00        | 0.00           | zero balance still yields 0.00
            1000.00         | 24.00        | 20.00          | exact result
            1234.56         | 19.99        | 20.56          | 20.565712 truncated, not rounded to 20.57
            100.00          | 11.99        | 0.99           | 0.999166 truncated, not rounded to 1.00
            0.05            | 24.00        | 0.00           | 0.001 truncated to zero
            0.01            | 9999.99      | 0.08           | smallest balance, max rate
            -100.00         | 11.99        | -0.99          | negative balance truncates toward zero, not to -1.00
            -1234.56        | 19.99        | -20.56         | negative balance gives negative interest
            500.00          | -5.00        | -2.08          | negative rate is non-zero, so interest is negative
            999999999.99    | 1.20         | 999999.99      | max balance, 999999.99999 truncated
            119999999.99    | 9999.99      | 999998999.91   | largest product here that still fits S9(09)V99
            """)
    void monthlyInterestTruncatesToTheCent(String balance, String rate, String expected, String description) {
        BigDecimal monthly = calculator.monthlyInterest(new BigDecimal(balance), new BigDecimal(rate));

        assertThat(monthly).isEqualTo(new BigDecimal(expected));
        assertThat(monthly.scale()).isEqualTo(2);
    }

    @ParameterizedTest(name = "[{index}] {3}: {0} x {1} / 1200 = {2}")
    @CsvSource(delimiter = '|', textBlock = """
            # TRAN-CAT-BAL  | DIS-INT-RATE | WS-MONTHLY-INT | case (exact quotient)
            999999999.99    | 9999.99      | 333324999.91   | max PIC values (8333324999.916)
            -999999999.99   | 9999.99      | -333324999.91  | min balance keeps the sign (-8333324999.916)
            999999999.99    | -9999.99     | -333324999.91  | min rate keeps the sign (-8333324999.916)
            143999999.99    | 9999.99      | 199998799.91   | just over 10 integer digits (1199998799.916)
            """)
    void monthlyInterestSilentlyDropsHighOrderDigitsWithoutOnSizeError(
            String balance, String rate, String expected, String description) {
        assertThat(calculator.monthlyInterest(new BigDecimal(balance), new BigDecimal(rate)))
                .isEqualTo(new BigDecimal(expected));
    }

    @Test
    void acceptsAmountsWithFewerThanTwoDecimals() {
        // TRAN-CAT-BAL / DIS-INT-RATE always carry two implied decimals; 1000 and 24 mean 1000.00 and 24.00.
        assertThat(calculator.monthlyInterest(new BigDecimal("1000"), new BigDecimal("24")))
                .isEqualTo(new BigDecimal("20.00"));
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvSource(delimiter = '|', textBlock = """
            1000000000.00 | 1.00     | balance wider than TRAN-CAT-BAL PIC S9(09)V99
            1.001         | 1.00     | balance with three decimals
            1.00          | 10000.00 | rate wider than DIS-INT-RATE PIC S9(04)V99
            1.00          | 1.005    | rate with three decimals
            """)
    void rejectsInputsThatDoNotFitTheCopybookPictures(String balance, String rate, String description) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.monthlyInterest(new BigDecimal(balance), new BigDecimal(rate)));
    }
}
