package com.carddemo.interest.application.port;

import com.carddemo.interest.domain.Transaction;

/** TRANSACT: sequential output file (new SYSTRAN GDG generation). */
public interface TransactionWriter {

    /** {@code 1300-B-WRITE-TX}: WRITE FD-TRANFILE-REC. */
    void write(Transaction transaction);
}
