package com.carddemo.interest.application.port;

import com.carddemo.interest.domain.TransactionCategoryBalance;

import java.util.stream.Stream;

/** TCATBALF: indexed file read sequentially ({@code 1000-TCATBALF-GET-NEXT}). */
public interface TransactionCategoryBalanceReader {

    /**
     * Streams every category balance in {@code TRAN-CAT-KEY} order, so records for one account are
     * contiguous. Callers must close the stream.
     */
    Stream<TransactionCategoryBalance> readAllInKeyOrder();
}
