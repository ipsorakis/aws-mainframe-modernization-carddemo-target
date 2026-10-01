package com.carddemo.interest.domain;

import java.math.BigDecimal;

/**
 * Interest rate per account group, transaction type and category. Mirrors {@code DIS-GROUP-RECORD}
 * in copybook CVTRA02Y (RECLN 50).
 *
 * @param key          DIS-GROUP-KEY
 * @param interestRate DIS-INT-RATE PIC S9(04)V99 (annual percentage rate)
 */
public record DisclosureGroup(
        Key key,
        BigDecimal interestRate) {

    /**
     * {@code DIS-GROUP-KEY}.
     *
     * @param accountGroupId      DIS-ACCT-GROUP-ID PIC X(10)
     * @param transactionTypeCode DIS-TRAN-TYPE-CD  PIC X(02)
     * @param categoryCode        DIS-TRAN-CAT-CD   PIC 9(04)
     */
    public record Key(
            String accountGroupId,
            String transactionTypeCode,
            int categoryCode) {
    }
}
