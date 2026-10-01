package com.carddemo.interest.domain;

import java.math.BigDecimal;

/**
 * Transaction record. Mirrors {@code TRAN-RECORD} in copybook CVTRA05Y (RECLN 350). CBACT04C writes
 * one of these per interest-bearing category balance to the TRANSACT file.
 *
 * @param id                  TRAN-ID            PIC X(16)
 * @param typeCode            TRAN-TYPE-CD       PIC X(02)
 * @param categoryCode        TRAN-CAT-CD        PIC 9(04)
 * @param source              TRAN-SOURCE        PIC X(10)
 * @param description         TRAN-DESC          PIC X(100)
 * @param amount              TRAN-AMT           PIC S9(09)V99
 * @param merchantId          TRAN-MERCHANT-ID   PIC 9(09)
 * @param merchantName        TRAN-MERCHANT-NAME PIC X(50)
 * @param merchantCity        TRAN-MERCHANT-CITY PIC X(50)
 * @param merchantZip         TRAN-MERCHANT-ZIP  PIC X(10)
 * @param cardNumber          TRAN-CARD-NUM      PIC X(16)
 * @param originTimestamp     TRAN-ORIG-TS       PIC X(26) (DB2 format yyyy-MM-dd-HH.mm.ss.ffffff)
 * @param processedTimestamp  TRAN-PROC-TS       PIC X(26) (DB2 format yyyy-MM-dd-HH.mm.ss.ffffff)
 */
public record Transaction(
        String id,
        String typeCode,
        int categoryCode,
        String source,
        String description,
        BigDecimal amount,
        long merchantId,
        String merchantName,
        String merchantCity,
        String merchantZip,
        String cardNumber,
        String originTimestamp,
        String processedTimestamp) {
}
