package com.carddemo.interest.domain.interest;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Emulates storing a value into a signed zoned-decimal field {@code PIC S9(i)V9(d)} by a COBOL
 * {@code COMPUTE}/{@code ADD}/{@code MOVE} with no {@code ROUNDED} and no {@code ON SIZE ERROR}:
 * excess decimals are truncated toward zero and excess high-order integer digits are dropped,
 * keeping the sign.
 */
final class CobolDecimal {

    private CobolDecimal() {
    }

    /** Stores {@code value} into {@code PIC S9(integerDigits)V9(scale)}. */
    static BigDecimal store(BigDecimal value, int integerDigits, int scale) {
        BigInteger unscaled = value.setScale(scale, RoundingMode.DOWN).unscaledValue();
        BigInteger modulus = BigInteger.TEN.pow(integerDigits + scale);
        return new BigDecimal(unscaled.remainder(modulus), scale);
    }

    /** Rejects values that a {@code PIC S9(integerDigits)V9(scale)} field could not hold. */
    static BigDecimal requireFits(BigDecimal value, int integerDigits, int scale, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        BigDecimal normalized = value.stripTrailingZeros();
        int integerPart = Math.max(normalized.precision() - normalized.scale(), 0);
        if (normalized.scale() > scale || integerPart > integerDigits) {
            throw new IllegalArgumentException("%s %s does not fit PIC S9(%d)V%s"
                    .formatted(fieldName, value.toPlainString(), integerDigits, "9".repeat(scale)));
        }
        return value.setScale(scale, RoundingMode.UNNECESSARY);
    }
}
