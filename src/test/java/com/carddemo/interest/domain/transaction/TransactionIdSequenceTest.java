package com.carddemo.interest.domain.transaction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CBACT04C {@code 1300-B-WRITE-TX} lines 474-480 ({@code ADD 1 TO WS-TRANID-SUFFIX}; {@code STRING
 * PARM-DATE, WS-TRANID-SUFFIX DELIMITED BY SIZE INTO TRAN-ID}) and line 173
 * ({@code WS-TRANID-SUFFIX PIC 9(06) VALUE 0}).
 */
class TransactionIdSequenceTest {

    private static final ParmDate PARM = ParmDate.of("2022071800");

    @Test
    void startsAtZeroSoTheFirstIdEndsInOne() {
        var sequence = new TransactionIdSequence(PARM);

        assertThat(sequence.lastSuffix()).isZero();
        assertThat(sequence.next()).isEqualTo("2022071800000001");
        assertThat(sequence.lastSuffix()).isEqualTo(1);
    }

    @Test
    void incrementsByOnePerCall() {
        var sequence = new TransactionIdSequence(PARM);

        assertThat(IntStream.range(0, 3).mapToObj(i -> sequence.next()))
                .containsExactly("2022071800000001", "2022071800000002", "2022071800000003");
    }

    @ParameterizedTest(name = "suffix {0} -> {1}")
    @CsvSource({
            "0,      2022071800000001",
            "8,      2022071800000009",
            "9,      2022071800000010",
            "99,     2022071800000100",
            "99998,  2022071800099999",
            "99999,  2022071800100000",
            "999998, 2022071800999999",
    })
    void zeroPadsTheSuffixToSixDigitsAndAlwaysYieldsSixteenCharacters(int lastSuffix, String expectedId) {
        String id = new TransactionIdSequence(PARM, lastSuffix).next();

        assertThat(id).isEqualTo(expectedId).hasSize(16);
    }

    @Test
    void throwsInsteadOfWrappingWhenTheSuffixWouldExceed999999() {
        var sequence = new TransactionIdSequence(PARM, 999_998);
        assertThat(sequence.next()).isEqualTo("2022071800999999");

        assertThatThrownBy(sequence::next)
                .isInstanceOf(TransactionIdSuffixOverflowException.class)
                .hasMessageContaining("PIC 9(06)")
                .hasMessageContaining("999999")
                .hasMessageContaining("2022071800");
        assertThat(sequence.lastSuffix()).isEqualTo(999_999);
        assertThatThrownBy(sequence::next).isInstanceOf(TransactionIdSuffixOverflowException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1_000_000, Integer.MAX_VALUE, Integer.MIN_VALUE})
    void rejectsAStartingSuffixOutsidePic9_06(int lastSuffix) {
        assertThatIllegalArgumentException().isThrownBy(() -> new TransactionIdSequence(PARM, lastSuffix));
    }
}
