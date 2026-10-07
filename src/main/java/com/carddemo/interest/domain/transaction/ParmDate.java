package com.carddemo.interest.domain.transaction;

import java.util.Objects;

/**
 * The job parameter {@code PARM-DATE} (LINKAGE SECTION, {@code PIC X(10)}), e.g. {@code '2022071800'}
 * from {@code INTCALC.jcl} ({@code EXEC PGM=CBACT04C,PARM='2022071800'}). CBACT04C uses it only as
 * the first 10 characters of every interest {@code TRAN-ID}.
 *
 * <p>The COBOL program never checks {@code PARM-LENGTH}, so a shorter parm would leak whatever
 * follows it in storage into {@code TRAN-ID}; here anything other than exactly 10 characters is
 * rejected. The content is not interpreted as a date, matching the COBOL.
 *
 * @param value PARM-DATE PIC X(10)
 */
public record ParmDate(String value) {

    public static final int LENGTH = 10;

    public ParmDate {
        Objects.requireNonNull(value, "PARM-DATE must not be null");
        if (value.length() != LENGTH) {
            throw new IllegalArgumentException(
                    "PARM-DATE must be exactly " + LENGTH + " characters (PIC X(10)), got "
                            + value.length() + ": '" + value + "'");
        }
    }

    public static ParmDate of(String value) {
        return new ParmDate(value);
    }
}
