package com.carddemo.interest.domain.transaction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CBACT04C {@code Z-GET-DB2-FORMAT-TIMESTAMP} lines 613-626 and the {@code COBOL-TS} /
 * {@code DB2-FORMAT-TS} layouts at lines 140-165 ({@code EEEE-MM-DD-UU.MM.SS.HH0000}).
 */
class Db2TimestampTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "2022-07-18T00:00:00,           2022-07-18-00.00.00.000000",
            "2022-07-18T09:05:07.12,        2022-07-18-09.05.07.120000",
            "2022-07-18T23:59:59.999999999, 2022-07-18-23.59.59.990000",
            "2022-07-18T12:30:45.009,       2022-07-18-12.30.45.000000",
            "2022-07-18T12:30:45.019,       2022-07-18-12.30.45.010000",
            "2024-02-29T01:02:03.456789,    2024-02-29-01.02.03.450000",
            "0999-01-01T00:00:00,           0999-01-01-00.00.00.000000",
    })
    void keepsHundredthsTruncatedAndAppendsLiteralZeros(LocalDateTime dateTime, String expected) {
        assertThat(Db2Timestamp.format(dateTime)).isEqualTo(expected).hasSize(Db2Timestamp.LENGTH);
    }

    @Test
    void readsLocalTimeInTheClocksZoneLikeFunctionCurrentDate() {
        Instant instant = Instant.parse("2022-07-18T23:30:00.25Z");

        assertThat(Db2Timestamp.now(Clock.fixed(instant, ZoneOffset.UTC)))
                .isEqualTo("2022-07-18-23.30.00.250000");
        assertThat(Db2Timestamp.now(Clock.fixed(instant, ZoneId.of("America/Chicago"))))
                .isEqualTo("2022-07-18-18.30.00.250000");
        assertThat(Db2Timestamp.now(Clock.fixed(instant, ZoneId.of("Europe/Athens"))))
                .isEqualTo("2022-07-19-02.30.00.250000");
    }
}
