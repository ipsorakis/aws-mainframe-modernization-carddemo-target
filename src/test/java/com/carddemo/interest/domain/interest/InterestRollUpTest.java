package com.carddemo.interest.domain.interest;

import com.carddemo.interest.domain.TransactionCategoryBalance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Characterises the CBACT04C main loop's {@code WS-TOTAL-INT} roll-up: control break on
 * {@code TRANCAT-ACCT-ID} with {@code MOVE 0 TO WS-TOTAL-INT} (lines 194-201), the
 * {@code IF DIS-INT-RATE NOT = 0} guard (lines 214-217) and {@code ADD WS-MONTHLY-INT TO
 * WS-TOTAL-INT} (line 467) into {@code WS-TOTAL-INT PIC S9(09)V99} (line 169).
 */
class InterestRollUpTest {

    private final InterestCalculator calculator = new InterestCalculator();

    /** Expected result as (account id, WS-TOTAL-INT, WS-MONTHLY-INT per line). */
    record Expected(long accountId, String total, List<String> monthly) {
    }

    static Stream<Arguments> rollUps() {
        return Stream.of(
                Arguments.of("empty file: no account is posted (lines 188-222)",
                        List.of(),
                        List.of()),
                Arguments.of("single category (line 467)",
                        List.of(rated(1, "01", 1, "1000.00", "24.00")),
                        List.of(new Expected(1, "20.00", List.of("20.00")))),
                Arguments.of("multi-category account sums every category (line 467)",
                        List.of(rated(1, "01", 1, "1000.00", "24.00"),
                                rated(1, "01", 2, "1234.56", "19.99"),
                                rated(1, "02", 1, "-100.00", "11.99")),
                        List.of(new Expected(1, "39.57", List.of("20.00", "20.56", "-0.99")))),
                Arguments.of("total is the sum of truncated lines, not the truncated sum (lines 464-467)",
                        List.of(rated(1, "01", 1, "100.00", "11.99"),
                                rated(1, "01", 2, "100.00", "11.99")),
                        List.of(new Expected(1, "1.98", List.of("0.99", "0.99")))),
                Arguments.of("zero rate is skipped: no line, nothing added (lines 214-217)",
                        List.of(rated(1, "01", 1, "1000.00", "0.00"),
                                rated(1, "01", 2, "1000.00", "24.00")),
                        List.of(new Expected(1, "20.00", List.of("20.00")))),
                Arguments.of("account with only zero rates is still posted with 0.00 (lines 194-201, 220)",
                        List.of(rated(1, "01", 1, "1000.00", "0.00"),
                                rated(2, "01", 1, "1000.00", "24.00")),
                        List.of(new Expected(1, "0.00", List.of()),
                                new Expected(2, "20.00", List.of("20.00")))),
                Arguments.of("zero balance with non-zero rate still yields a 0.00 line (lines 214-215)",
                        List.of(rated(1, "01", 1, "0.00", "24.00")),
                        List.of(new Expected(1, "0.00", List.of("0.00")))),
                Arguments.of("negative balances net against positive ones (line 467)",
                        List.of(rated(1, "01", 1, "-1000.00", "24.00"),
                                rated(1, "01", 2, "500.00", "24.00")),
                        List.of(new Expected(1, "-10.00", List.of("-20.00", "10.00")))),
                Arguments.of("control break resets WS-TOTAL-INT (line 200)",
                        List.of(rated(1, "01", 1, "1000.00", "24.00"),
                                rated(1, "01", 2, "1000.00", "12.00"),
                                rated(2, "01", 1, "1234.56", "19.99"),
                                rated(3, "01", 1, "-100.00", "11.99")),
                        List.of(new Expected(1, "30.00", List.of("20.00", "10.00")),
                                new Expected(2, "20.56", List.of("20.56")),
                                new Expected(3, "-0.99", List.of("-0.99")))),
                Arguments.of("break compares with the previous record only, so a reappearing account is a new group (line 194)",
                        List.of(rated(1, "01", 1, "1000.00", "24.00"),
                                rated(2, "01", 1, "1000.00", "24.00"),
                                rated(1, "01", 2, "1000.00", "12.00")),
                        List.of(new Expected(1, "20.00", List.of("20.00")),
                                new Expected(2, "20.00", List.of("20.00")),
                                new Expected(1, "10.00", List.of("10.00")))),
                Arguments.of("account id 0 is a real account, distinct from the initial WS-LAST-ACCT-NUM spaces (line 167)",
                        List.of(rated(0, "01", 1, "1000.00", "24.00")),
                        List.of(new Expected(0, "20.00", List.of("20.00")))),
                Arguments.of("ADD into WS-TOTAL-INT drops high-order digits without ON SIZE ERROR (line 467)",
                        List.of(rated(1, "01", 1, "119999999.99", "9999.99"),
                                rated(1, "01", 2, "119999999.99", "9999.99")),
                        List.of(new Expected(1, "999997999.82", List.of("999998999.91", "999998999.91")))));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("rollUps")
    void rollsUpMonthlyInterestPerAccountControlBreak(
            String description, List<RatedCategoryBalance> input, List<Expected> expected) {
        List<AccountInterest> results = calculator.rollUp(input);

        assertThat(results).extracting(AccountInterest::accountId)
                .containsExactlyElementsOf(expected.stream().map(Expected::accountId).toList());
        for (int i = 0; i < expected.size(); i++) {
            AccountInterest result = results.get(i);
            assertThat(result.totalInterest()).isEqualTo(new BigDecimal(expected.get(i).total()));
            assertThat(result.lines()).extracting(CategoryInterest::monthlyInterest)
                    .containsExactlyElementsOf(expected.get(i).monthly().stream().map(BigDecimal::new).toList());
        }
    }

    @Test
    void linesCarryTheSourceKeyBalanceAndRateForTransactionGeneration() {
        // 1300-B-WRITE-TX (lines 473-515) moves WS-MONTHLY-INT to TRAN-AMT for each computed line.
        RatedCategoryBalance input = rated(42, "01", 3, "1234.56", "19.99");

        CategoryInterest line = calculator.rollUp(List.of(input)).getFirst().lines().getFirst();

        assertThat(line).isEqualTo(new CategoryInterest(
                new TransactionCategoryBalance.Key(42, "01", 3),
                new BigDecimal("1234.56"), new BigDecimal("19.99"), new BigDecimal("20.56")));
    }

    @Test
    void resultLinesAreImmutable() {
        List<CategoryInterest> lines = calculator.rollUp(List.of(rated(1, "01", 1, "1.00", "1.00")))
                .getFirst().lines();

        assertThatThrownBy(lines::clear)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static RatedCategoryBalance rated(long accountId, String typeCode, int categoryCode, String balance, String rate) {
        return new RatedCategoryBalance(
                new TransactionCategoryBalance(
                        new TransactionCategoryBalance.Key(accountId, typeCode, categoryCode), new BigDecimal(balance)),
                new BigDecimal(rate));
    }
}
