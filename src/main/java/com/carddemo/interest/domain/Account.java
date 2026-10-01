package com.carddemo.interest.domain;

import java.math.BigDecimal;

/**
 * Account master record. Mirrors {@code ACCOUNT-RECORD} in copybook CVACT01Y (RECLN 300).
 *
 * @param id               ACCT-ID                 PIC 9(11)
 * @param activeStatus     ACCT-ACTIVE-STATUS      PIC X(01)
 * @param currentBalance   ACCT-CURR-BAL           PIC S9(10)V99
 * @param creditLimit      ACCT-CREDIT-LIMIT       PIC S9(10)V99
 * @param cashCreditLimit  ACCT-CASH-CREDIT-LIMIT  PIC S9(10)V99
 * @param openDate         ACCT-OPEN-DATE          PIC X(10)
 * @param expirationDate   ACCT-EXPIRAION-DATE     PIC X(10) (sic, COBOL spelling)
 * @param reissueDate      ACCT-REISSUE-DATE       PIC X(10)
 * @param currentCycleCredit ACCT-CURR-CYC-CREDIT  PIC S9(10)V99
 * @param currentCycleDebit  ACCT-CURR-CYC-DEBIT   PIC S9(10)V99
 * @param addressZip       ACCT-ADDR-ZIP           PIC X(10)
 * @param groupId          ACCT-GROUP-ID           PIC X(10); key into the disclosure group file
 */
public record Account(
        long id,
        String activeStatus,
        BigDecimal currentBalance,
        BigDecimal creditLimit,
        BigDecimal cashCreditLimit,
        String openDate,
        String expirationDate,
        String reissueDate,
        BigDecimal currentCycleCredit,
        BigDecimal currentCycleDebit,
        String addressZip,
        String groupId) {
}
