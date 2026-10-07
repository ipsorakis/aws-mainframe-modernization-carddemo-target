package com.carddemo.interest.domain.transaction;

/**
 * Thrown when {@code WS-TRANID-SUFFIX} ({@code PIC 9(06)}) would go past {@value TransactionIdSequence#MAX_SUFFIX}.
 *
 * <p>The COBOL {@code ADD 1 TO WS-TRANID-SUFFIX} has no {@code ON SIZE ERROR}, so it would silently
 * wrap to {@code 000000} and produce duplicate {@code TRAN-ID}s; the Java port stops instead.
 */
public class TransactionIdSuffixOverflowException extends IllegalStateException {

    public TransactionIdSuffixOverflowException(ParmDate parmDate) {
        super("WS-TRANID-SUFFIX PIC 9(06) exhausted: more than " + TransactionIdSequence.MAX_SUFFIX
                + " interest transactions for PARM-DATE '" + parmDate.value() + "'");
    }
}
