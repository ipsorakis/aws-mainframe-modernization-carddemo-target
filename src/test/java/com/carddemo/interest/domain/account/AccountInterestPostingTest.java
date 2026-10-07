package com.carddemo.interest.domain.account;

import com.carddemo.interest.domain.Account;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static com.carddemo.interest.domain.account.AccountFixtures.account;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Characterises CBACT04C {@code 1050-UPDATE-ACCOUNT}, lines 350-354. */
class AccountInterestPostingTest {

    private final AccountInterestPosting posting = new AccountInterestPosting();

    /** Line 352 {@code ADD WS-TOTAL-INT TO ACCT-CURR-BAL}: S9(10)V99 += S9(09)V99. */
    @ParameterizedTest(name = "[{index}] {3}: {0} + {1} = {2}")
    @CsvSource(delimiter = '|', textBlock = """
            1000.00        | 12.34        | 1012.34        | positive balance, positive interest
            1000.00        | 0.00         | 1000.00        | zero interest leaves the balance as is
            0.00           | 0.00         | 0.00           | zero balance, zero interest
            0.00           | 0.01         | 0.01           | smallest positive interest
            -250.75        | 3.10         | -247.65        | negative balance (credit) with positive interest
            -250.75        | -3.10        | -253.85        | negative balance with negative interest
            100.00         | -0.01        | 99.99          | negative interest (negative TRAN-CAT-BAL)
            1.00           | -2.50        | -1.50          | negative interest crosses zero
            -1.00          | 2.50         | 1.50           | positive interest crosses zero
            9999999999.98  | 0.01         | 9999999999.99  | lands exactly on PIC S9(10)V99 max
            9999999999.99  | 0.00         | 9999999999.99  | max balance, zero interest
            -9999999999.99 | 0.00         | -9999999999.99 | min balance, zero interest
            -9999999999.98 | -0.01        | -9999999999.99 | lands exactly on PIC S9(10)V99 min
            9000000000.00  | 999999999.99 | 9999999999.99  | max WS-TOTAL-INT S9(09)V99 still fits
            9999999999.99  | -999999999.99 | 9000000000.00 | max balance, min WS-TOTAL-INT
            """)
    void addsTotalInterestToCurrentBalance(String balance, String interest, String expected, String scenario) {
        Account updated = posting.post(account(1L, balance), new BigDecimal(interest));

        assertThat(updated.currentBalance()).isEqualByComparingTo(expected);
        assertThat(updated.currentBalance().scale()).isEqualTo(2);
    }

    /**
     * Line 352: ADD without ROUNDED stores the sum truncated to the receiving field's two decimals
     * (toward zero). WS-TOTAL-INT is itself V99, so this only guards callers that pass more scale.
     */
    @ParameterizedTest(name = "[{index}] {0} + {1} = {2}")
    @CsvSource(delimiter = '|', textBlock = """
            100.00  | 0.019  | 100.01
            100.00  | 0.0099 | 100.00
            -100.00 | -0.019 | -100.01
            0.00    | -0.009 | 0.00
            """)
    void truncatesSubCentDigitsTowardZero(String balance, String interest, String expected) {
        Account updated = posting.post(account(1L, balance), new BigDecimal(interest));

        assertThat(updated.currentBalance()).isEqualByComparingTo(expected);
        assertThat(updated.currentBalance().scale()).isEqualTo(2);
    }

    /** Lines 353-354 {@code MOVE 0 TO ACCT-CURR-CYC-CREDIT / ACCT-CURR-CYC-DEBIT}, regardless of interest. */
    @ParameterizedTest(name = "[{index}] credit={0} debit={1} interest={2}")
    @CsvSource(delimiter = '|', textBlock = """
            125.40         | 310.15         | 0.00
            125.40         | 310.15         | 7.25
            125.40         | 310.15         | -7.25
            0.00           | 0.00           | 0.00
            -42.00         | -17.50         | 1.00
            9999999999.99  | 9999999999.99  | 0.00
            -9999999999.99 | -9999999999.99 | 0.00
            """)
    void resetsCycleCreditAndDebitToZero(String credit, String debit, String interest) {
        Account updated = posting.post(account(1L, "1000.00", credit, debit), new BigDecimal(interest));

        assertThat(updated.currentCycleCredit()).isEqualTo(new BigDecimal("0.00"));
        assertThat(updated.currentCycleDebit()).isEqualTo(new BigDecimal("0.00"));
    }

    /** Lines 352-356: only three fields change; the rest of ACCOUNT-RECORD is rewritten as read. */
    @Test
    void leavesEveryOtherAccountFieldUnchanged() {
        Account original = account(41L, "1000.00");

        Account updated = posting.post(original, new BigDecimal("12.34"));

        assertThat(updated)
                .usingRecursiveComparison()
                .ignoringFields("currentBalance", "currentCycleCredit", "currentCycleDebit")
                .isEqualTo(original);
    }

    @Test
    void returnsANewAccountAndLeavesTheInputUntouched() {
        Account original = account(41L, "1000.00");

        Account updated = posting.post(original, new BigDecimal("12.34"));

        assertThat(updated).isNotSameAs(original);
        assertThat(original).isEqualTo(account(41L, "1000.00"));
    }

    /**
     * Line 352 has no ON SIZE ERROR, so COBOL silently drops the high-order digit
     * (e.g. 9999999999.99 + 0.01 is stored as 0000000000.00). Deliberate deviation: we throw.
     */
    @ParameterizedTest(name = "[{index}] {0} + {1}")
    @CsvSource(delimiter = '|', textBlock = """
            9999999999.99  | 0.01
            9999999999.99  | 999999999.99
            9000000000.01  | 999999999.99
            -9999999999.99 | -0.01
            -9999999999.99 | -999999999.99
            """)
    void refusesToSilentlyTruncateAnOverflowingBalance(String balance, String interest) {
        Account original = account(7L, balance);

        assertThatThrownBy(() -> posting.post(original, new BigDecimal(interest)))
                .isInstanceOfSatisfying(AccountBalanceOverflowException.class, e -> {
                    assertThat(e.accountId()).isEqualTo(7L);
                    assertThat(e.currentBalance()).isEqualByComparingTo(balance);
                    assertThat(e.totalInterest()).isEqualByComparingTo(interest);
                })
                .hasMessageContaining("PIC S9(10)V99 overflow")
                .hasMessageContaining("account 7");
    }

    @Test
    void rejectsNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> posting.post(null, BigDecimal.ONE));
        assertThatNullPointerException().isThrownBy(() -> posting.post(account(1L, "0.00"), null));
    }
}
