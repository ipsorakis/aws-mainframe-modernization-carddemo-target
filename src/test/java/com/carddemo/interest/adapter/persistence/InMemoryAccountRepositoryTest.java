package com.carddemo.interest.adapter.persistence;

import com.carddemo.interest.domain.Account;
import com.carddemo.interest.domain.account.AccountRewriteException;
import org.junit.jupiter.api.Test;

import static com.carddemo.interest.domain.account.AccountFixtures.account;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ACCTFILE KSDS semantics used by CBACT04C: random READ by key (line 373) and REWRITE (line 356). */
class InMemoryAccountRepositoryTest {

    @Test
    void findsAccountsByIdAndReturnsEmptyForUnknownKeys() {
        var repository = new InMemoryAccountRepository(account(1L, "10.00"));

        assertThat(repository.findById(1L)).contains(account(1L, "10.00"));
        assertThat(repository.findById(2L)).isEmpty();
    }

    @Test
    void updateReplacesTheStoredRecord() {
        var repository = new InMemoryAccountRepository(account(1L, "10.00"), account(2L, "20.00"));
        Account rewritten = account(1L, "11.00", "0.00", "0.00");

        repository.update(rewritten);

        assertThat(repository.findAll()).containsExactly(rewritten, account(2L, "20.00"));
    }

    @Test
    void updateOfAMissingKeyFailsInsteadOfInserting() {
        var repository = new InMemoryAccountRepository(account(1L, "10.00"));

        assertThatThrownBy(() -> repository.update(account(9L, "1.00")))
                .isInstanceOf(AccountRewriteException.class)
                .hasMessageContaining("status 23");
        assertThat(repository.findAll()).containsExactly(account(1L, "10.00"));
    }

    @Test
    void rejectsDuplicateAccountIds() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InMemoryAccountRepository(account(1L, "1.00"), account(1L, "2.00")))
                .withMessageContaining("Duplicate ACCT-ID 1");
    }
}
