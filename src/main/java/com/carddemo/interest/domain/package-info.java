/**
 * Pure domain model for the CBACT04C interest calculation port.
 *
 * <p>Rules: plain Java only. No Spring, Jakarta, persistence or I/O imports; no dependency on
 * {@code ..application..}, {@code ..adapter..} or {@code ..config..}. Enforced by
 * {@code ArchitectureTest}.
 *
 * <p>The records in this package mirror the COBOL copybooks read and written by CBACT04C. Signed
 * amounts with an implied decimal point ({@code PIC S9(n)V99}) are {@link java.math.BigDecimal};
 * unsigned integer keys ({@code PIC 9(n)}) are {@code long} or {@code int}; alphanumerics
 * ({@code PIC X(n)}) are {@link java.lang.String}. {@code FILLER} fields are not modelled.
 */
package com.carddemo.interest.domain;
