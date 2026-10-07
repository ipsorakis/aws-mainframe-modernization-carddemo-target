package com.carddemo.interest.domain.transaction;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Input of {@code 1300-B-WRITE-TX}: {@code ACCT-ID PIC 9(11)} (CVACT01Y), {@code WS-MONTHLY-INT PIC S9(09)V99} (line 168). */
class MonthlyInterestLineTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "0,             0.00",
            "0.00,          0.00",
            "1.5,           1.50",
            "-1.5,          -1.50",
            "999999999.99,  999999999.99",
            "-999999999.99, -999999999.99",
            "12.3400,       12.34",
    })
    void normalisesToScaleTwoWithinPicS9_09V99(BigDecimal amount, String expected) {
        var line = new MonthlyInterestLine(1L, amount);

        assertThat(line.monthlyInterest()).hasToString(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000000000.00", "-1000000000", "1E+9", "0.001", "-12.345"})
    void rejectsAmountsThatCannotBeHeldByWsMonthlyInt(String amount) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MonthlyInterestLine(1L, new BigDecimal(amount)))
                .withMessageContaining("PIC S9(09)V99");
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, 100_000_000_000L, Long.MAX_VALUE})
    void rejectsAccountIdsOutsidePic9_11(long accountId) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MonthlyInterestLine(accountId, BigDecimal.ONE))
                .withMessageContaining("PIC 9(11)");
    }
}
