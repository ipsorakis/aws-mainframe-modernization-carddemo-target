package com.carddemo.interest.domain.transaction;

/**
 * No card cross reference exists for the account. In CBACT04C {@code 1110-GET-XREF-DATA}
 * (lines 393-413) the {@code INVALID KEY} read leaves {@code XREFFILE-STATUS} not {@code '00'}, which
 * displays {@code 'ERROR READING XREF FILE'} and abends the program.
 */
public class CardXrefNotFoundException extends RuntimeException {

    private final long accountId;

    public CardXrefNotFoundException(long accountId) {
        super("ACCOUNT NOT FOUND in XREFFILE: " + String.format("%011d", accountId));
        this.accountId = accountId;
    }

    public long accountId() {
        return accountId;
    }
}
