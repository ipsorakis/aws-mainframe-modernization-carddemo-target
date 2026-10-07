package com.carddemo.interest.domain.transaction;

import java.util.Objects;

/**
 * Run-scoped generator for interest {@code TRAN-ID}s: {@code PARM-DATE} followed by
 * {@code WS-TRANID-SUFFIX} ({@code PIC 9(06) VALUE 0}), zero-padded to 6 digits.
 *
 * <p>Mirrors CBACT04C {@code 1300-B-WRITE-TX}: {@code ADD 1 TO WS-TRANID-SUFFIX} then
 * {@code STRING PARM-DATE, WS-TRANID-SUFFIX DELIMITED BY SIZE INTO TRAN-ID}. The suffix starts at 0,
 * so the first ID ends in {@code 000001}, and it is never reset between accounts. Create one
 * instance per job run. Not thread-safe.
 */
public final class TransactionIdSequence {

    public static final int MAX_SUFFIX = 999_999;

    private final ParmDate parmDate;
    private int suffix;

    public TransactionIdSequence(ParmDate parmDate) {
        this(parmDate, 0);
    }

    /**
     * @param lastSuffix the value {@code WS-TRANID-SUFFIX} holds before the next {@link #next()}
     */
    public TransactionIdSequence(ParmDate parmDate, int lastSuffix) {
        this.parmDate = Objects.requireNonNull(parmDate);
        if (lastSuffix < 0 || lastSuffix > MAX_SUFFIX) {
            throw new IllegalArgumentException(
                    "WS-TRANID-SUFFIX must be within 0.." + MAX_SUFFIX + " (PIC 9(06)), got " + lastSuffix);
        }
        this.suffix = lastSuffix;
    }

    /**
     * Increments the suffix and returns the 16-character {@code TRAN-ID}.
     *
     * @throws TransactionIdSuffixOverflowException if the suffix is already {@value #MAX_SUFFIX};
     *                                              the sequence is left unchanged
     */
    public String next() {
        if (suffix >= MAX_SUFFIX) {
            throw new TransactionIdSuffixOverflowException(parmDate);
        }
        suffix++;
        return parmDate.value() + String.format("%06d", suffix);
    }

    /** Current value of {@code WS-TRANID-SUFFIX}: the suffix of the last ID issued, 0 before the first. */
    public int lastSuffix() {
        return suffix;
    }

    public ParmDate parmDate() {
        return parmDate;
    }
}
