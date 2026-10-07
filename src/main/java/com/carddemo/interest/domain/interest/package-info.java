/**
 * Interest rate resolution and interest computation (DM-6).
 *
 * <ul>
 *   <li>{@link com.carddemo.interest.domain.interest.InterestRateResolver}: {@code 1200-GET-INTEREST-RATE}
 *       and {@code 1200-A-GET-DEFAULT-INT-RATE} (lookup by account group, falling back to
 *       {@code DEFAULT}).</li>
 *   <li>{@link com.carddemo.interest.domain.interest.InterestCalculator}: {@code 1300-COMPUTE-INTEREST}
 *       ({@code (TRAN-CAT-BAL * DIS-INT-RATE) / 1200}, truncated to the cent) and the per-account
 *       {@code WS-TOTAL-INT} roll-up driven by the main loop's control break.</li>
 * </ul>
 *
 * <p>Fee handling for the {@code 1400-COMPUTE-FEES} stub is decided in DM-9.
 */
package com.carddemo.interest.domain.interest;
