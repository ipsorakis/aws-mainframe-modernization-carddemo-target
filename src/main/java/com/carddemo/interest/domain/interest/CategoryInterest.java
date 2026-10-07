package com.carddemo.interest.domain.interest;

import com.carddemo.interest.domain.TransactionCategoryBalance;

import java.math.BigDecimal;

/**
 * Monthly interest for one category balance: the value of {@code WS-MONTHLY-INT} each time
 * {@code 1300-COMPUTE-INTEREST} runs, i.e. one interest transaction for {@code 1300-B-WRITE-TX}.
 *
 * @param categoryKey     TRAN-CAT-KEY of the source TCATBALF record
 * @param balance         TRAN-CAT-BAL PIC S9(09)V99
 * @param interestRate    DIS-INT-RATE PIC S9(04)V99
 * @param monthlyInterest WS-MONTHLY-INT PIC S9(09)V99
 */
public record CategoryInterest(
        TransactionCategoryBalance.Key categoryKey,
        BigDecimal balance,
        BigDecimal interestRate,
        BigDecimal monthlyInterest) {
}
