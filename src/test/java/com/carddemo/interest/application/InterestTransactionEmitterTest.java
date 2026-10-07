package com.carddemo.interest.application;

import com.carddemo.interest.application.port.CardXrefRepository;
import com.carddemo.interest.application.port.InMemoryTransactionWriter;
import com.carddemo.interest.domain.CardXref;
import com.carddemo.interest.domain.Transaction;
import com.carddemo.interest.domain.transaction.CardXrefNotFoundException;
import com.carddemo.interest.domain.transaction.InterestTransactionFactory;
import com.carddemo.interest.domain.transaction.InterestTransactionWriteException;
import com.carddemo.interest.domain.transaction.MonthlyInterestLine;
import com.carddemo.interest.domain.transaction.ParmDate;
import com.carddemo.interest.domain.transaction.TransactionIdSequence;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** CBACT04C {@code 1110-GET-XREF-DATA} (lines 393-413) and the WRITE of {@code 1300-B-WRITE-TX} (lines 500-514). */
class InterestTransactionEmitterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2022-07-18T10:15:30.12Z"), ZoneOffset.UTC);

    private final Map<Long, CardXref> xrefs = Map.of(
            1L, new CardXref("4111111111111111", 10L, 1L),
            2L, new CardXref("4222222222222222", 20L, 2L));
    private final CardXrefRepository cardXrefs = accountId -> Optional.ofNullable(xrefs.get(accountId));
    private final InMemoryTransactionWriter writer = new InMemoryTransactionWriter();
    private final InterestTransactionEmitter emitter =
            new InterestTransactionEmitter(new InterestTransactionFactory(CLOCK), cardXrefs, writer);

    @Test
    void writesOneTransactionPerLineInOrderWithARunScopedSuffix() {
        var sequence = new TransactionIdSequence(ParmDate.of("2022071800"));

        CardXref account1 = emitter.cardXrefFor(1L);
        emitter.emit(new MonthlyInterestLine(1L, new BigDecimal("10.00")), account1, sequence);
        emitter.emit(new MonthlyInterestLine(1L, new BigDecimal("-0.42")), account1, sequence);
        CardXref account2 = emitter.cardXrefFor(2L);
        emitter.emit(new MonthlyInterestLine(2L, new BigDecimal("0.00")), account2, sequence);

        assertThat(writer.written())
                .extracting(Transaction::id, Transaction::cardNumber, Transaction::amount)
                .containsExactly(
                        tuple("2022071800000001", "4111111111111111", new BigDecimal("10.00")),
                        tuple("2022071800000002", "4111111111111111", new BigDecimal("-0.42")),
                        tuple("2022071800000003", "4222222222222222", new BigDecimal("0.00")));
    }

    @Test
    void missingXrefIsADomainException() {
        assertThatThrownBy(() -> emitter.cardXrefFor(3L))
                .isInstanceOf(CardXrefNotFoundException.class)
                .hasMessageContaining("00000000003");
    }

    @Test
    void writeFailureIsADomainExceptionCarryingTheTranId() {
        writer.failWhen(tx -> tx.id().endsWith("000002"));
        var sequence = new TransactionIdSequence(ParmDate.of("2022071800"));
        CardXref account1 = emitter.cardXrefFor(1L);
        emitter.emit(new MonthlyInterestLine(1L, BigDecimal.ONE), account1, sequence);

        assertThatThrownBy(() -> emitter.emit(new MonthlyInterestLine(1L, BigDecimal.TEN), account1, sequence))
                .isInstanceOf(InterestTransactionWriteException.class)
                .hasMessageContaining("ERROR WRITING TRANSACTION RECORD 2022071800000002")
                .hasCauseInstanceOf(IllegalStateException.class)
                .extracting(e -> ((InterestTransactionWriteException) e).transactionId())
                .isEqualTo("2022071800000002");
        assertThat(writer.written()).extracting(Transaction::id).containsExactly("2022071800000001");
    }

    @Test
    void writerThrowingTheDomainExceptionIsNotWrappedTwice() {
        var failure = new InterestTransactionWriteException("x", null);
        var emitterWithFailingWriter = new InterestTransactionEmitter(
                new InterestTransactionFactory(CLOCK), cardXrefs, tx -> { throw failure; });

        assertThatThrownBy(() -> emitterWithFailingWriter.emit(new MonthlyInterestLine(1L, BigDecimal.ONE),
                emitter.cardXrefFor(1L), new TransactionIdSequence(ParmDate.of("2022071800"))))
                .isSameAs(failure);
    }
}
