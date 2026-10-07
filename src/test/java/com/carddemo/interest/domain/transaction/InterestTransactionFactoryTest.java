package com.carddemo.interest.domain.transaction;

import com.carddemo.interest.domain.CardXref;
import com.carddemo.interest.domain.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** CBACT04C {@code 1300-B-WRITE-TX} lines 473-498, building {@code TRAN-RECORD} (CVTRA05Y). */
class InterestTransactionFactoryTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2022-07-18T10:15:30.987654Z"), ZoneOffset.UTC);
    private static final ParmDate PARM = ParmDate.of("2022071800");

    private final InterestTransactionFactory factory = new InterestTransactionFactory(CLOCK);

    private static CardXref xref(String cardNumber, long accountId) {
        return new CardXref(cardNumber, 1L, accountId);
    }

    @Test
    void buildsTheFullTranRecord() {
        var sequence = new TransactionIdSequence(PARM);

        Transaction tx = factory.create(
                new MonthlyInterestLine(1L, new BigDecimal("12.34")), xref("4111111111111111", 1L), sequence);

        assertThat(tx.id()).isEqualTo("2022071800000001");                                   // 474-480
        assertThat(tx.typeCode()).isEqualTo("01");                                           // 482
        assertThat(tx.categoryCode()).isEqualTo(5);                                          // 483, '05' into 9(04)
        assertThat(tx.source()).isEqualTo("System    ").hasSize(10);                         // 484
        assertThat(tx.description()).isEqualTo("Int. for a/c 00000000001" + " ".repeat(76)); // 485-489
        assertThat(tx.amount()).isEqualTo(new BigDecimal("12.34"));                          // 490
        assertThat(tx.merchantId()).isZero();                                                // 491
        assertThat(tx.merchantName()).isBlank().hasSize(50);                                 // 492
        assertThat(tx.merchantCity()).isBlank().hasSize(50);                                 // 493
        assertThat(tx.merchantZip()).isBlank().hasSize(10);                                  // 494
        assertThat(tx.cardNumber()).isEqualTo("4111111111111111");                           // 495
        assertThat(tx.originTimestamp()).isEqualTo("2022-07-18-10.15.30.980000");            // 496-497
        assertThat(tx.processedTimestamp()).isEqualTo(tx.originTimestamp());                 // 498
    }

    @Test
    void carriesTheSuffixAcrossAccountsWithoutResetting() {
        var sequence = new TransactionIdSequence(PARM);
        var account1 = xref("4111111111111111", 1L);
        var account2 = xref("4222222222222222", 2L);
        var account3 = xref("4333333333333333", 27L);

        List<Transaction> written = List.of(
                factory.create(new MonthlyInterestLine(1L, new BigDecimal("1.00")), account1, sequence),
                factory.create(new MonthlyInterestLine(1L, new BigDecimal("2.00")), account1, sequence),
                factory.create(new MonthlyInterestLine(2L, new BigDecimal("3.00")), account2, sequence),
                factory.create(new MonthlyInterestLine(27L, new BigDecimal("4.00")), account3, sequence),
                factory.create(new MonthlyInterestLine(27L, new BigDecimal("5.00")), account3, sequence));

        assertThat(written).extracting(Transaction::id).containsExactly(
                "2022071800000001", "2022071800000002", "2022071800000003", "2022071800000004", "2022071800000005");
        assertThat(written).extracting(Transaction::cardNumber).containsExactly(
                "4111111111111111", "4111111111111111", "4222222222222222", "4333333333333333", "4333333333333333");
        assertThat(written).extracting(tx -> tx.description().strip()).containsExactly(
                "Int. for a/c 00000000001", "Int. for a/c 00000000001", "Int. for a/c 00000000002",
                "Int. for a/c 00000000027", "Int. for a/c 00000000027");
    }

    @ParameterizedTest(name = "ACCT-ID {0}")
    @CsvSource({
            "0,           Int. for a/c 00000000000",
            "1,           Int. for a/c 00000000001",
            "4100000001,  Int. for a/c 04100000001",
            "99999999999, Int. for a/c 99999999999",
    })
    void descriptionKeepsAcctIdLeadingZerosAndIsSpacePaddedToPicX100(long accountId, String expectedText) {
        Transaction tx = factory.create(new MonthlyInterestLine(accountId, BigDecimal.ONE),
                xref("4111111111111111", accountId), new TransactionIdSequence(PARM));

        assertThat(tx.description()).hasSize(100).startsWith(expectedText);
        assertThat(tx.description().substring(expectedText.length())).isEqualTo(" ".repeat(76));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "0.00",
            "0.01",
            "-0.01",
            "-1234.56",
            "999999999.99",
            "-999999999.99",
    })
    void movesWsMonthlyIntToTranAmtUnchangedIncludingZeroAndNegative(BigDecimal amount) {
        Transaction tx = factory.create(new MonthlyInterestLine(1L, amount),
                xref("4111111111111111", 1L), new TransactionIdSequence(PARM));

        assertThat(tx.amount()).isEqualTo(amount).extracting(BigDecimal::scale).isEqualTo(2);
    }

    @ParameterizedTest(name = "XREF-CARD-NUM ''{0}''")
    @CsvSource(value = {
            "4111111111111111|4111111111111111",
            "411111111111|'411111111111    '",
            "41111111111111119999|4111111111111111",
    }, delimiter = '|')
    void cardNumberIsXrefCardNumFittedToPicX16(String xrefCardNumber, String expected) {
        Transaction tx = factory.create(new MonthlyInterestLine(1L, BigDecimal.ONE),
                xref(xrefCardNumber, 1L), new TransactionIdSequence(PARM));

        assertThat(tx.cardNumber()).isEqualTo(expected).hasSize(16);
    }

    @Test
    void takesTheTimestampFromTheInjectedClockInItsZone() {
        var chicago = new InterestTransactionFactory(
                Clock.fixed(Instant.parse("2022-07-18T05:00:00.05Z"), ZoneId.of("America/Chicago")));

        Transaction tx = chicago.create(new MonthlyInterestLine(1L, BigDecimal.ONE),
                xref("4111111111111111", 1L), new TransactionIdSequence(PARM));

        assertThat(tx.originTimestamp()).isEqualTo("2022-07-18-00.00.00.050000");
        assertThat(tx.processedTimestamp()).isEqualTo("2022-07-18-00.00.00.050000");
    }

    @Test
    void rejectsAnXrefForAnotherAccountWithoutConsumingAnId() {
        var sequence = new TransactionIdSequence(PARM);

        assertThatIllegalArgumentException().isThrownBy(() -> factory.create(
                new MonthlyInterestLine(1L, BigDecimal.ONE), xref("4222222222222222", 2L), sequence));
        assertThat(sequence.lastSuffix()).isZero();
    }

    @Test
    void propagatesSuffixOverflow() {
        var sequence = new TransactionIdSequence(PARM, TransactionIdSequence.MAX_SUFFIX);

        assertThatThrownBy(() -> factory.create(
                new MonthlyInterestLine(1L, BigDecimal.ONE), xref("4111111111111111", 1L), sequence))
                .isInstanceOf(TransactionIdSuffixOverflowException.class);
    }
}
