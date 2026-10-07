package com.carddemo.interest.domain.transaction;

import com.carddemo.interest.domain.CardXref;
import com.carddemo.interest.domain.Transaction;

import java.time.Clock;
import java.util.Objects;

/**
 * Builds the interest {@code TRAN-RECORD} (CVTRA05Y) of CBACT04C {@code 1300-B-WRITE-TX}
 * (lines 473-498), up to but excluding the {@code WRITE}.
 */
public final class InterestTransactionFactory {

    static final String TYPE_CODE = "01";
    static final int CATEGORY_CODE = 5;
    static final String SOURCE = "System";
    static final String DESCRIPTION_PREFIX = "Int. for a/c ";
    static final long MERCHANT_ID = 0L;

    static final int SOURCE_LENGTH = 10;
    static final int DESCRIPTION_LENGTH = 100;
    static final int MERCHANT_NAME_LENGTH = 50;
    static final int MERCHANT_CITY_LENGTH = 50;
    static final int MERCHANT_ZIP_LENGTH = 10;
    static final int CARD_NUMBER_LENGTH = 16;

    private final Clock clock;

    public InterestTransactionFactory(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Consumes the next ID from {@code sequence} and builds the transaction.
     *
     * @param line     the account and its monthly interest ({@code ACCT-ID}, {@code WS-MONTHLY-INT})
     * @param cardXref the account's card cross reference, read by {@code 1110-GET-XREF-DATA}
     * @param sequence the run-scoped {@code TRAN-ID} generator
     * @throws IllegalArgumentException             if {@code cardXref} belongs to a different account
     * @throws TransactionIdSuffixOverflowException if the suffix is exhausted
     */
    public Transaction create(MonthlyInterestLine line, CardXref cardXref, TransactionIdSequence sequence) {
        Objects.requireNonNull(line);
        Objects.requireNonNull(cardXref);
        Objects.requireNonNull(sequence);
        if (cardXref.accountId() != line.accountId()) {
            throw new IllegalArgumentException("Card xref " + cardXref.cardNumber() + " is for account "
                    + cardXref.accountId() + ", not " + line.accountId());
        }

        String id = sequence.next();                                                    // 474-480
        String timestamp = Db2Timestamp.now(clock);                                     // 496

        return new Transaction(
                id,
                TYPE_CODE,                                                              // 482
                CATEGORY_CODE,                                                          // 483
                PicX.fit(SOURCE, SOURCE_LENGTH),                                        // 484
                PicX.fit(description(line.accountId()), DESCRIPTION_LENGTH),           // 485-489
                line.monthlyInterest(),                                                 // 490
                MERCHANT_ID,                                                            // 491
                PicX.spaces(MERCHANT_NAME_LENGTH),                                      // 492
                PicX.spaces(MERCHANT_CITY_LENGTH),                                      // 493
                PicX.spaces(MERCHANT_ZIP_LENGTH),                                       // 494
                PicX.fit(cardXref.cardNumber(), CARD_NUMBER_LENGTH),                    // 495
                timestamp,                                                              // 497
                timestamp);                                                             // 498
    }

    /** {@code STRING 'Int. for a/c ', ACCT-ID DELIMITED BY SIZE}: ACCT-ID is PIC 9(11), so zero-padded. */
    static String description(long accountId) {
        return DESCRIPTION_PREFIX + String.format("%011d", accountId);
    }
}
