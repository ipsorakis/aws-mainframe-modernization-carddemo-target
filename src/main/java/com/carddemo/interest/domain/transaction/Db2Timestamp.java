package com.carddemo.interest.domain.transaction;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * CBACT04C {@code Z-GET-DB2-FORMAT-TIMESTAMP}: {@code FUNCTION CURRENT-DATE} rearranged into the
 * 26-character {@code DB2-FORMAT-TS}, {@code yyyy-MM-dd-HH.mm.ss.hh0000}.
 *
 * <p>{@code CURRENT-DATE} resolves only to hundredths of a second ({@code COB-MIL PIC X(02)}), which
 * are moved to {@code DB2-MIL}; the remaining 4 digits are the literal {@code '0000'}
 * ({@code DB2-REST}). Sub-hundredth precision is dropped, not rounded. {@code CURRENT-DATE} is local
 * time, so the clock's zone is used and no offset is written.
 */
public final class Db2Timestamp {

    public static final int LENGTH = 26;

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd-HH.mm.ss");
    private static final int NANOS_PER_HUNDREDTH = 10_000_000;

    private Db2Timestamp() {
    }

    public static String now(Clock clock) {
        return format(LocalDateTime.now(Objects.requireNonNull(clock)));
    }

    public static String format(LocalDateTime dateTime) {
        int hundredths = dateTime.getNano() / NANOS_PER_HUNDREDTH;
        return DATE_TIME.format(dateTime) + "." + String.format("%02d", hundredths) + "0000";
    }
}
