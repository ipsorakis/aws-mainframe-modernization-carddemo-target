package com.carddemo.interest.domain.transaction;

/** COBOL {@code MOVE} into a {@code PIC X(n)} field: left-justify, pad with spaces, truncate on the right. */
final class PicX {

    private PicX() {
    }

    static String fit(String value, int length) {
        if (value.length() >= length) {
            return value.substring(0, length);
        }
        return value + " ".repeat(length - value.length());
    }

    static String spaces(int length) {
        return " ".repeat(length);
    }
}
