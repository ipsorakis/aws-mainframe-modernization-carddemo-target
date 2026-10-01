/**
 * Account balance update after interest posting (DM-7).
 *
 * <p>Owns CBACT04C paragraph {@code 1050-UPDATE-ACCOUNT}: add the account's total monthly interest
 * to {@code ACCT-CURR-BAL} and reset {@code ACCT-CURR-CYC-CREDIT} / {@code ACCT-CURR-CYC-DEBIT}.
 */
package com.carddemo.interest.domain.account;
