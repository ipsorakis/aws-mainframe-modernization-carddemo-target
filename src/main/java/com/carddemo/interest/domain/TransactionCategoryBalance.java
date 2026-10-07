package com.carddemo.interest.domain;

import java.math.BigDecimal;

/**
 * Balance per account, transaction type and category. Mirrors {@code TRAN-CAT-BAL-RECORD} in
 * copybook CVTRA01Y (RECLN 50). CBACT04C reads this file sequentially in key order, which groups
 * records by account.
 *
 * @param key     TRAN-CAT-KEY
 * @param balance TRAN-CAT-BAL PIC S9(09)V99
 */
public record TransactionCategoryBalance(
        Key key,
        BigDecimal balance) {

    /**
     * {@code TRAN-CAT-KEY}.
     *
     * @param accountId           TRANCAT-ACCT-ID PIC 9(11)
     * @param transactionTypeCode TRANCAT-TYPE-CD PIC X(02)
     * @param categoryCode        TRANCAT-CD      PIC 9(04)
     */
    public record Key(
            long accountId,
            String transactionTypeCode,
            int categoryCode) {
    }
}
