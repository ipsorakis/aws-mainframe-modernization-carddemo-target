package com.carddemo.interest.domain;

/**
 * Card / customer / account cross reference. Mirrors {@code CARD-XREF-RECORD} in copybook CVACT03Y
 * (RECLN 50). CBACT04C reads it by the alternate key {@code XREF-ACCT-ID}.
 *
 * @param cardNumber XREF-CARD-NUM PIC X(16) (primary key)
 * @param customerId XREF-CUST-ID  PIC 9(09)
 * @param accountId  XREF-ACCT-ID  PIC 9(11) (alternate key)
 */
public record CardXref(
        String cardNumber,
        long customerId,
        long accountId) {
}
