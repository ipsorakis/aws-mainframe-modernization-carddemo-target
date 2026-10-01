package com.carddemo.interest.adapter.persistence;

import com.carddemo.interest.application.port.TransactionCategoryBalanceReader;
import com.carddemo.interest.domain.TransactionCategoryBalance;

import java.util.stream.Stream;

/** Placeholder {@link TransactionCategoryBalanceReader}; no storage is wired yet. */
public class PlaceholderTransactionCategoryBalanceReader implements TransactionCategoryBalanceReader {

    @Override
    public Stream<TransactionCategoryBalance> readAllInKeyOrder() {
        throw notImplemented();
    }

    private static UnsupportedOperationException notImplemented() {
        return new UnsupportedOperationException("Persistence adapter not implemented yet");
    }
}
