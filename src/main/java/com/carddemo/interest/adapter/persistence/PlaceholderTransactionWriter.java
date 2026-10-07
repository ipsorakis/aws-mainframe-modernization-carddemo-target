package com.carddemo.interest.adapter.persistence;

import com.carddemo.interest.application.port.TransactionWriter;
import com.carddemo.interest.domain.Transaction;

/** Placeholder {@link TransactionWriter}; no storage is wired yet. */
public class PlaceholderTransactionWriter implements TransactionWriter {

    @Override
    public void write(Transaction transaction) {
        throw notImplemented();
    }

    private static UnsupportedOperationException notImplemented() {
        return new UnsupportedOperationException("Persistence adapter not implemented yet");
    }
}
