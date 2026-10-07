package com.carddemo.interest.application;

import com.carddemo.interest.application.port.CardXrefRepository;
import com.carddemo.interest.application.port.TransactionWriter;
import com.carddemo.interest.domain.CardXref;
import com.carddemo.interest.domain.Transaction;
import com.carddemo.interest.domain.transaction.CardXrefNotFoundException;
import com.carddemo.interest.domain.transaction.InterestTransactionFactory;
import com.carddemo.interest.domain.transaction.InterestTransactionWriteException;
import com.carddemo.interest.domain.transaction.MonthlyInterestLine;
import com.carddemo.interest.domain.transaction.TransactionIdSequence;

import java.util.Objects;

/**
 * CBACT04C {@code 1110-GET-XREF-DATA} and {@code 1300-B-WRITE-TX}: look up the account's card and
 * write one interest transaction per monthly interest line through {@link TransactionWriter}.
 * Failures that abend the COBOL program surface as domain exceptions.
 */
public class InterestTransactionEmitter {

    private final InterestTransactionFactory factory;
    private final CardXrefRepository cardXrefs;
    private final TransactionWriter transactions;

    public InterestTransactionEmitter(
            InterestTransactionFactory factory, CardXrefRepository cardXrefs, TransactionWriter transactions) {
        this.factory = Objects.requireNonNull(factory);
        this.cardXrefs = Objects.requireNonNull(cardXrefs);
        this.transactions = Objects.requireNonNull(transactions);
    }

    /**
     * {@code 1110-GET-XREF-DATA}: performed once per account, on the account control break.
     *
     * @throws CardXrefNotFoundException if the account has no card cross reference
     */
    public CardXref cardXrefFor(long accountId) {
        return cardXrefs.findByAccountId(accountId)
                .orElseThrow(() -> new CardXrefNotFoundException(accountId));
    }

    /**
     * {@code 1300-B-WRITE-TX}: build the interest transaction and WRITE it to TRANSACT.
     *
     * @throws InterestTransactionWriteException if the writer fails
     */
    public Transaction emit(MonthlyInterestLine line, CardXref cardXref, TransactionIdSequence sequence) {
        Transaction transaction = factory.create(line, cardXref, sequence);
        try {
            transactions.write(transaction);
        } catch (InterestTransactionWriteException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InterestTransactionWriteException(transaction.id(), e);
        }
        return transaction;
    }
}
