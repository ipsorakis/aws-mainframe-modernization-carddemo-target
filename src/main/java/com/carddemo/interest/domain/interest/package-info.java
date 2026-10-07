/**
 * Interest rate resolution and interest computation (DM-6).
 *
 * <p>Owns CBACT04C paragraphs {@code 1200-GET-INTEREST-RATE}, {@code 1200-A-GET-DEFAULT-INT-RATE}
 * and {@code 1300-COMPUTE-INTEREST} ({@code (TRAN-CAT-BAL * DIS-INT-RATE) / 1200}, accumulated per
 * account into {@code WS-TOTAL-INT}). Fee handling for the {@code 1400-COMPUTE-FEES} stub is
 * decided in DM-9.
 */
package com.carddemo.interest.domain.interest;
