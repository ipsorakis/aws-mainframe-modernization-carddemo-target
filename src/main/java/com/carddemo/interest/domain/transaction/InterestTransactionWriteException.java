package com.carddemo.interest.domain.transaction;

/**
 * The interest transaction could not be written to TRANSACT. In CBACT04C {@code 1300-B-WRITE-TX}
 * (lines 500-514) a {@code TRANFILE-STATUS} other than {@code '00'} displays
 * {@code 'ERROR WRITING TRANSACTION RECORD'} and abends the program.
 */
public class InterestTransactionWriteException extends RuntimeException {

    private final String transactionId;

    public InterestTransactionWriteException(String transactionId, Throwable cause) {
        super("ERROR WRITING TRANSACTION RECORD " + transactionId, cause);
        this.transactionId = transactionId;
    }

    public String transactionId() {
        return transactionId;
    }
}
