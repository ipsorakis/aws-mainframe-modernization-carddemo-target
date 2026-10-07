package com.carddemo.interest.application.account;

import com.carddemo.interest.adapter.persistence.InMemoryAccountRepository;
import com.carddemo.interest.application.port.AccountRepository;
import com.carddemo.interest.domain.Account;
import com.carddemo.interest.domain.account.AccountBalanceOverflowException;
import com.carddemo.interest.domain.account.AccountInterestPosting;
import com.carddemo.interest.domain.account.AccountNotFoundException;
import com.carddemo.interest.domain.account.AccountRewriteException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Optional;

import static com.carddemo.interest.domain.account.AccountFixtures.account;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Characterises CBACT04C {@code 1100-GET-ACCT-DATA} (lines 372-391) and {@code 1050-UPDATE-ACCOUNT} (350-370). */
class AccountInterestUpdaterTest {

    private final InMemoryAccountRepository repository = new InMemoryAccountRepository(
            account(1L, "1000.00"), account(2L, "-250.75"), account(3L, "9999999999.99"));
    private final AccountInterestUpdater updater = new AccountInterestUpdater(repository, new AccountInterestPosting());

    /** Lines 352-356: ADD, two MOVE 0, then REWRITE FD-ACCTFILE-REC FROM ACCOUNT-RECORD. */
    @ParameterizedTest(name = "[{index}] acct {0}: {1} + {2} = {3}")
    @CsvSource(delimiter = '|', textBlock = """
            1 | 1000.00       | 12.34 | 1012.34
            1 | 1000.00       | 0.00  | 1000.00
            2 | -250.75       | -3.10 | -253.85
            3 | 9999999999.99 | 0.00  | 9999999999.99
            """)
    void rewritesTheAccountWithInterestPostedAndCyclesReset(long id, String before, String interest, String after) {
        Account returned = updater.postInterest(id, new BigDecimal(interest));

        Account stored = repository.findById(id).orElseThrow();
        assertThat(stored).isEqualTo(returned);
        assertThat(stored.currentBalance()).isEqualByComparingTo(after);
        assertThat(stored.currentCycleCredit()).isZero();
        assertThat(stored.currentCycleDebit()).isZero();
    }

    @Test
    void rewritesOnlyTheTargetAccount() {
        updater.postInterest(1L, new BigDecimal("12.34"));

        assertThat(repository.findById(2L)).contains(account(2L, "-250.75"));
        assertThat(repository.findById(3L)).contains(account(3L, "9999999999.99"));
    }

    /** Main loop line 203 reads the account once at the control break; 1050 later rewrites that record. */
    @Test
    void postsInterestOnAnAccountAlreadyRead() {
        Account read = updater.getAccount(1L);

        updater.postInterest(read, new BigDecimal("5.00"));

        assertThat(repository.findById(1L).orElseThrow().currentBalance()).isEqualByComparingTo("1005.00");
    }

    /** Lines 373-389: INVALID KEY leaves ACCTFILE-STATUS '23', APPL-RESULT 12, 9999-ABEND-PROGRAM. */
    @Test
    void missingAccountIsAnExplicitErrorAndNothingIsRewritten() {
        assertThatThrownBy(() -> updater.postInterest(99L, new BigDecimal("1.00")))
                .isInstanceOfSatisfying(AccountNotFoundException.class, e -> assertThat(e.accountId()).isEqualTo(99L))
                .hasMessage("Account not found: 99");
        assertThatThrownBy(() -> updater.getAccount(99L)).isInstanceOf(AccountNotFoundException.class);
        assertThat(repository.findById(99L)).isEmpty();
    }

    /** Lines 356-368: non-'00' REWRITE status -> 'ERROR RE-WRITING ACCOUNT FILE' and abend. */
    @Test
    void failedRewriteIsAnExplicitError() {
        AccountRepository failingRewrite = new AccountRepository() {
            @Override
            public Optional<Account> findById(long accountId) {
                return Optional.of(account(accountId, "1000.00"));
            }

            @Override
            public void update(Account account) {
                throw new AccountRewriteException(account.id(), "status 21 (sequence error)");
            }
        };
        var failing = new AccountInterestUpdater(failingRewrite, new AccountInterestPosting());

        assertThatThrownBy(() -> failing.postInterest(5L, new BigDecimal("1.00")))
                .isInstanceOfSatisfying(AccountRewriteException.class, e -> assertThat(e.accountId()).isEqualTo(5L))
                .hasMessageContaining("Error re-writing account 5");
    }

    /** REWRITE of an account that vanished after the read: status '23' on REWRITE, abend. */
    @Test
    void rewriteOfAnAccountThatNoLongerExistsFails() {
        Account neverStored = account(42L, "10.00");

        assertThatThrownBy(() -> updater.postInterest(neverStored, BigDecimal.ONE))
                .isInstanceOf(AccountRewriteException.class)
                .hasMessageContaining("account 42");
        assertThat(repository.findById(42L)).isEmpty();
    }

    /** Line 352 overflow (deliberate deviation): the corrupted balance must never reach the REWRITE. */
    @Test
    void overflowIsRejectedBeforeTheRewrite() {
        assertThatThrownBy(() -> updater.postInterest(3L, new BigDecimal("0.01")))
                .isInstanceOf(AccountBalanceOverflowException.class);

        assertThat(repository.findById(3L)).contains(account(3L, "9999999999.99"));
    }
}
