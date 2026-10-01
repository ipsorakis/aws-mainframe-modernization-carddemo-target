package com.carddemo.interest.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Domain tests are plain JUnit 5 + AssertJ: no Spring context, no mocks of infrastructure. */
class TransactionCategoryBalanceTest {

    @Test
    void keysWithSameCopybookFieldsAreEqual() {
        var first = new TransactionCategoryBalance.Key(1L, "01", 5);
        var second = new TransactionCategoryBalance.Key(1L, "01", 5);

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    @Test
    void balanceKeepsTheTwoImpliedDecimalPlacesOfPicS9_09V99() {
        var balance = new TransactionCategoryBalance(
                new TransactionCategoryBalance.Key(1L, "01", 5), new BigDecimal("-1234.50"));

        assertThat(balance.balance().scale()).isEqualTo(2);
        assertThat(balance.balance()).isEqualByComparingTo("-1234.5");
    }
}
