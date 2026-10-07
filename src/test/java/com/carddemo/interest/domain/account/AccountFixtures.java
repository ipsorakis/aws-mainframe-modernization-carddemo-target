package com.carddemo.interest.domain.account;

import com.carddemo.interest.domain.Account;

import java.math.BigDecimal;

/** Hand-built CVACT01Y fixtures; the sample data cannot exercise these paths (see DM-9 notes). */
public final class AccountFixtures {

    private AccountFixtures() {
    }

    public static Account account(long id, String currentBalance) {
        return account(id, currentBalance, "125.40", "310.15");
    }

    public static Account account(long id, String currentBalance, String cycleCredit, String cycleDebit) {
        return new Account(
                id,
                "Y",
                new BigDecimal(currentBalance),
                new BigDecimal("5000.00"),
                new BigDecimal("1500.00"),
                "2014-11-20",
                "2025-05-20",
                "2025-05-20",
                new BigDecimal(cycleCredit),
                new BigDecimal(cycleDebit),
                "12345     ",
                "A000000000");
    }
}
