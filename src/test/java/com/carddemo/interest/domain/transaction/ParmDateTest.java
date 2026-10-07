package com.carddemo.interest.domain.transaction;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/** CBACT04C LINKAGE SECTION line 178, {@code PARM-DATE PIC X(10)}; INTCALC.jcl {@code PARM='2022071800'}. */
class ParmDateTest {

    @ParameterizedTest
    @ValueSource(strings = {"2022071800", "2026100700", "ABCDEFGHIJ", "          "})
    void acceptsAnyTenCharacterValueLikeTheUncheckedCobolParm(String value) {
        assertThat(ParmDate.of(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "20220718", "202207180", "20220718000", "2022-07-18-00"})
    void rejectsAnythingButExactlyTenCharacters(String value) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ParmDate.of(value))
                .withMessageContaining("exactly 10 characters")
                .withMessageContaining("got " + value.length());
    }

    @ParameterizedTest
    @NullSource
    void rejectsNull(String value) {
        assertThatNullPointerException().isThrownBy(() -> ParmDate.of(value));
    }
}
