package com.carddemo.interest.domain.interest;

import com.carddemo.interest.domain.DisclosureGroup;
import com.carddemo.interest.domain.TransactionCategoryBalance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Characterises CBACT04C {@code 1200-GET-INTEREST-RATE} (lines 415-440) and
 * {@code 1200-A-GET-DEFAULT-INT-RATE} (lines 443-460) against an in-memory DISCGRP behind the
 * {@code DisclosureGroupRepository} port. The key is built from ACCT-GROUP-ID, TRANCAT-CD and
 * TRANCAT-TYPE-CD (lines 210-212).
 */
class InterestRateResolverTest {

    private final InMemoryDisclosureGroupRepository discgrp = new InMemoryDisclosureGroupRepository()
            .with("GOLD", "01", 1, "15.00")
            .with("GOLD", "01", 2, "0.00")
            .with("DEFAULT", "01", 1, "24.00")
            .with("DEFAULT", "01", 2, "18.00")
            .with("DEFAULT", "02", 1, "0.00");

    private final InterestRateResolver resolver = new InterestRateResolver(discgrp::findByKey);

    @ParameterizedTest(name = "[{index}] {5}")
    @CsvSource(delimiter = '|', textBlock = """
            # ACCT-GROUP-ID | TYPE | CAT | group used | DIS-INT-RATE | case
            GOLD            | 01   | 1   | GOLD       | 15.00        | own group found, status 00 (lines 416, 422)
            GOLD            | 01   | 2   | GOLD       | 0.00         | own group with zero rate does not fall back (line 436)
            GOLD            | 02   | 1   | DEFAULT    | 0.00         | own group missing, status 23, DEFAULT rate 0 (lines 436-438)
            SILVER          | 01   | 1   | DEFAULT    | 24.00        | unknown group falls back to DEFAULT (lines 436-438)
            SILVER          | 01   | 2   | DEFAULT    | 18.00        | fallback uses the record's own type and category (lines 211-212)
            ''              | 01   | 1   | DEFAULT    | 24.00        | blank ACCT-GROUP-ID, as in the sample data, falls back
            DEFAULT         | 01   | 1   | DEFAULT    | 24.00        | account already in DEFAULT is found directly
            """)
    void resolvesTheDisclosureGroupWithDefaultFallback(
            String groupId, String typeCode, int categoryCode, String expectedGroup, String expectedRate,
            String description) {
        DisclosureGroup resolved = resolver.resolve(groupId, key(typeCode, categoryCode));

        assertThat(resolved.key()).isEqualTo(new DisclosureGroup.Key(expectedGroup, typeCode, categoryCode));
        assertThat(resolved.interestRate()).isEqualTo(new BigDecimal(expectedRate));
    }

    @Test
    void readsTheAccountGroupFirstAndDefaultOnlyOnStatus23() {
        resolver.resolve("GOLD", key("01", 1));
        resolver.resolve("SILVER", key("01", 1));

        assertThat(discgrp.reads()).containsExactly(
                new DisclosureGroup.Key("GOLD", "01", 1),
                new DisclosureGroup.Key("SILVER", "01", 1),
                new DisclosureGroup.Key("DEFAULT", "01", 1));
    }

    @ParameterizedTest(name = "[{index}] group {0}, type {1}, category {2}")
    @CsvSource(delimiter = '|', textBlock = """
            SILVER  | 03 | 1
            GOLD    | 01 | 9
            DEFAULT | 09 | 9
            """)
    void missingDefaultGroupIsFatal(String groupId, String typeCode, int categoryCode) {
        // 1200-A-GET-DEFAULT-INT-RATE: status not 00 -> 'ERROR READING DEFAULT DISCLOSURE GROUP' and
        // 9999-ABEND-PROGRAM (lines 446-459).
        assertThatThrownBy(() -> resolver.resolve(groupId, key(typeCode, categoryCode)))
                .isInstanceOfSatisfying(DisclosureGroupNotFoundException.class, e -> {
                    assertThat(e.accountGroupKey()).isEqualTo(new DisclosureGroup.Key(groupId, typeCode, categoryCode));
                    assertThat(e.defaultGroupKey()).isEqualTo(new DisclosureGroup.Key("DEFAULT", typeCode, categoryCode));
                })
                .hasMessageStartingWith("ERROR READING DEFAULT DISCLOSURE GROUP");
    }

    @Test
    void resolvedRatesFeedTheRollUp() {
        // Main loop lines 210-217: resolve the rate per record, then compute only when it is non-zero.
        var calculator = new InterestCalculator();
        var balances = List.of(
                new TransactionCategoryBalance(new TransactionCategoryBalance.Key(7, "01", 1), new BigDecimal("1000.00")),
                new TransactionCategoryBalance(new TransactionCategoryBalance.Key(7, "01", 2), new BigDecimal("1000.00")),
                new TransactionCategoryBalance(new TransactionCategoryBalance.Key(7, "02", 1), new BigDecimal("1000.00")));

        var rated = balances.stream()
                .map(b -> new RatedCategoryBalance(b, resolver.resolve("GOLD", b.key()).interestRate()))
                .toList();

        AccountInterest result = calculator.rollUp(rated).getFirst();
        assertThat(result.totalInterest()).isEqualTo(new BigDecimal("12.50"));
        assertThat(result.lines()).extracting(CategoryInterest::categoryKey)
                .containsExactly(new TransactionCategoryBalance.Key(7, "01", 1));
    }

    private static TransactionCategoryBalance.Key key(String typeCode, int categoryCode) {
        return new TransactionCategoryBalance.Key(1, typeCode, categoryCode);
    }
}
