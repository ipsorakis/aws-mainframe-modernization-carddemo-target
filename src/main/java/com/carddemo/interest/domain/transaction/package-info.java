/**
 * Interest transaction generation (DM-8).
 *
 * <p>Owns CBACT04C paragraphs {@code 1300-B-WRITE-TX} and {@code Z-GET-DB2-FORMAT-TIMESTAMP}:
 * build the type {@code 01} / category {@code 05} / source {@code System} transaction whose ID is
 * the job parm date followed by a running sequence number.
 *
 * <p>{@code PIC X(n)} fields of the built {@link com.carddemo.interest.domain.Transaction} are
 * fixed width: right-padded with spaces or right-truncated to {@code n}, as a COBOL {@code MOVE}
 * into an alphanumeric field does.
 */
package com.carddemo.interest.domain.transaction;
