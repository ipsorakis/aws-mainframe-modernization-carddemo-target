package com.carddemo.interest.application.port;

import com.carddemo.interest.domain.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** In-memory TRANSACT for tests: keeps written records in order; can be told to fail like a bad TRANFILE-STATUS. */
public class InMemoryTransactionWriter implements TransactionWriter {

    private final List<Transaction> written = new ArrayList<>();
    private Predicate<Transaction> failWhen = transaction -> false;

    @Override
    public void write(Transaction transaction) {
        if (failWhen.test(transaction)) {
            throw new IllegalStateException("simulated TRANFILE-STATUS '34' (out of space)");
        }
        written.add(transaction);
    }

    public InMemoryTransactionWriter failWhen(Predicate<Transaction> condition) {
        this.failWhen = condition;
        return this;
    }

    public List<Transaction> written() {
        return List.copyOf(written);
    }
}
