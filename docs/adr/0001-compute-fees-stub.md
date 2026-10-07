# ADR 0001: Handling of the `1400-COMPUTE-FEES` stub in CBACT04C

- **Status:** Proposed (awaiting human acceptance)
- **Date:** 2026-10-01
- **Jira:** [DM-9](https://cognition-london-demos.atlassian.net/browse/DM-9) (epic [DM-1](https://cognition-london-demos.atlassian.net/browse/DM-1))
- **Related:** DM-6 (interest calculation), DM-7 (account update), DM-8 (interest transaction generation), DM-22 (service scaffold)
- **Legacy source:** `ipsorakis/aws-mainframe-modernization-carddemo` at commit
  [`59cc6c2`](https://github.com/ipsorakis/aws-mainframe-modernization-carddemo/tree/59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e)

## Decision (summary)

Port **no fee logic**. Preserve legacy behaviour exactly (no fees are computed, no fee
transactions are written, no fee amount reaches the account balance), and model the stub as a
**no-op extension point** in the domain: a `FeeCalculator` port with a `NoFeeCalculator` default
(option **b**). Real fee computation (option c) is out of scope for DM-1 until the business
supplies a fee specification.

## Context

DM-1 ports the monthly interest batch program `CBACT04C` (job `INTCALC`) to a Java 21 / Spring
Boot service (`com.carddemo.interest`, with `domain`, `application` and `adapter` packages, per
DM-22). `CBACT04C` contains a paragraph `1400-COMPUTE-FEES` that is called on every interest-
bearing record but has no body. We need to decide, and record, what the Java service does with it.

## Evidence

All line numbers refer to `app/cbl/CBACT04C.cbl` at commit `59cc6c2`.

### E1. The stub (lines 517-520)

```cobol
      *---------------------------------------------------------------*
       1400-COMPUTE-FEES.
      * To be implemented
           EXIT.
```

The paragraph contains a comment and `EXIT` only. It reads no file, moves no field, and
writes nothing. Executing it has no observable effect.

### E2. Call site and guard (main loop, lines 188-222)

```cobol
           PERFORM UNTIL END-OF-FILE = 'Y'
               IF  END-OF-FILE = 'N'
                   PERFORM 1000-TCATBALF-GET-NEXT
                   IF  END-OF-FILE = 'N'
                     ADD 1 TO WS-RECORD-COUNT
                     DISPLAY TRAN-CAT-BAL-RECORD
                     IF TRANCAT-ACCT-ID NOT= WS-LAST-ACCT-NUM
                       IF WS-FIRST-TIME NOT = 'Y'
                          PERFORM 1050-UPDATE-ACCOUNT
                       ELSE
                          MOVE 'N' TO WS-FIRST-TIME
                       END-IF
                       MOVE 0 TO WS-TOTAL-INT
                       MOVE TRANCAT-ACCT-ID TO WS-LAST-ACCT-NUM
                       MOVE TRANCAT-ACCT-ID TO FD-ACCT-ID
                       PERFORM 1100-GET-ACCT-DATA
                       MOVE TRANCAT-ACCT-ID TO FD-XREF-ACCT-ID
                       PERFORM 1110-GET-XREF-DATA
                     END-IF
                     MOVE ACCT-GROUP-ID TO FD-DIS-ACCT-GROUP-ID
                     MOVE TRANCAT-CD TO FD-DIS-TRAN-CAT-CD
                     MOVE TRANCAT-TYPE-CD TO FD-DIS-TRAN-TYPE-CD
                     PERFORM 1200-GET-INTEREST-RATE
                     IF DIS-INT-RATE NOT = 0
                       PERFORM 1300-COMPUTE-INTEREST
                       PERFORM 1400-COMPUTE-FEES
                     END-IF
                   END-IF
               ELSE
                    PERFORM 1050-UPDATE-ACCOUNT
               END-IF
           END-PERFORM.
```

Facts that follow directly from this code:

- `1400-COMPUTE-FEES` is performed **once per transaction-category-balance (TCATBALF) record**,
  not once per account.
- **Guard:** it runs only when `DIS-INT-RATE NOT = 0`, i.e. only when the disclosure-group rate
  resolved for (`ACCT-GROUP-ID`, `TRANCAT-TYPE-CD`, `TRANCAT-CD`), after the `DEFAULT` group
  fallback in `1200-GET-INTEREST-RATE` / `1200-A-GET-DEFAULT-INT-RATE`, is non-zero.
  Records with a zero rate (e.g. type `02` payments, type `03` credits, or every record in group
  `ZEROAPR`) never reach it.
- It runs **after** `1300-COMPUTE-INTEREST`, which has already added `WS-MONTHLY-INT` to
  `WS-TOTAL-INT` and written the interest transaction through `1300-B-WRITE-TX`.
- It runs **before** `1050-UPDATE-ACCOUNT` for the current account. That update happens on the
  next account control break and adds only `WS-TOTAL-INT` to `ACCT-CURR-BAL` (lines 350-354).
  The end-of-file `ELSE PERFORM 1050-UPDATE-ACCOUNT` (lines 219-220) looks unreachable. When
  `1000-TCATBALF-GET-NEXT` sets `END-OF-FILE` to `'Y'`, the inner `IF` is skipped and the
  test-before `PERFORM UNTIL` exits, so the last account appears never to be rewritten. This
  comes from reading the code and should be confirmed by a characterization run (see DM-7
  below).

  ```cobol
       1050-UPDATE-ACCOUNT.
      * Update the balances in account record to reflect posted trans.
           ADD WS-TOTAL-INT  TO ACCT-CURR-BAL
           MOVE 0 TO ACCT-CURR-CYC-CREDIT
           MOVE 0 TO ACCT-CURR-CYC-DEBIT
  ```

  There is no fee accumulator (no `WS-TOTAL-FEE` or similar) anywhere in WORKING-STORAGE
  (lines 166-173 declare only `WS-LAST-ACCT-NUM`, `WS-MONTHLY-INT`, `WS-TOTAL-INT`,
  `WS-FIRST-TIME`, `WS-RECORD-COUNT`, `WS-TRANID-SUFFIX`).

### E3. Data in scope when the stub is reached

| Record (copybook) | Fields available | Notes |
|---|---|---|
| `TRAN-CAT-BAL-RECORD` (`CVTRA01Y`, 50 bytes) | `TRANCAT-ACCT-ID` 9(11), `TRANCAT-TYPE-CD` X(2), `TRANCAT-CD` 9(4), `TRAN-CAT-BAL` S9(9)V99, `FILLER` X(22) | The current record. |
| `ACCOUNT-RECORD` (`CVACT01Y`, 300 bytes) | `ACCT-ID`, `ACCT-ACTIVE-STATUS`, `ACCT-CURR-BAL`, `ACCT-CREDIT-LIMIT`, `ACCT-CASH-CREDIT-LIMIT`, `ACCT-OPEN-DATE`, `ACCT-EXPIRAION-DATE`, `ACCT-REISSUE-DATE`, `ACCT-CURR-CYC-CREDIT`, `ACCT-CURR-CYC-DEBIT`, `ACCT-ADDR-ZIP`, `ACCT-GROUP-ID`, `FILLER` X(178) | Read once per account. Not yet updated for this run: the cycle credit/debit are still the pre-reset values and the balance excludes this run's interest. |
| `DIS-GROUP-RECORD` (`CVTRA02Y`, 50 bytes) | `DIS-ACCT-GROUP-ID` X(10), `DIS-TRAN-TYPE-CD` X(2), `DIS-TRAN-CAT-CD` 9(4), `DIS-INT-RATE` S9(4)V99, `FILLER` X(28) | Only an interest rate. No fee rate or fee amount field. |
| `CARD-XREF-RECORD` (`CVACT03Y`) | `XREF-CARD-NUM`, `XREF-CUST-ID`, `XREF-ACCT-ID` | Used by `1300-B-WRITE-TX` for `TRAN-CARD-NUM`. |
| Working storage | `WS-MONTHLY-INT`, `WS-TOTAL-INT`, `WS-TRANID-SUFFIX`, `PARM-DATE` (from JCL `PARM='2022071800'`) | `TRANSACT-FILE` (`CVTRA05Y`, 350 bytes) is open for output. |

## What the repository does and does not tell us

### Facts (verifiable in the legacy repo)

1. **The stub has always been empty.** `1400-COMPUTE-FEES` is identical in `Release 1.0`
   (`8c797e2`, 2022-09-01) and in the latest change to the file (`fb78417`, 2025-08-03,
   "Add optional modules and enhanced functionality").
2. **Fees are mentioned in exactly one place outside the stub:** the job comment in
   `app/jcl/INTCALC.jcl` line 20, `//* Process transaction balance file and compute interest and fees.`
   The README describes `INTCALC` / `CBACT04C` only as "Run interest calculations". The
   Control-M folder `MONTHLY-InterestCalculation` (`app/scheduler/CardDemo.controlm`) runs
   `CLOSEFIL -> INTCALC -> COMBTRAN -> WAITSTEP -> OPENFIL` and has no fee step.
3. **No fee-related data element exists.** A case-insensitive search of the repository for
   `fee` finds only the stub, its `PERFORM`, the JCL comment, and unrelated hits (`FEEDBACK-CODE`
   in `CSUTLDTC`, "feeds" in a README, merchant names such as "Feeney" in `dailytran.txt`, the
   Apache licence). No copybook declares a fee amount, fee rate, fee schedule, fee waiver flag or
   last-fee date. No program other than `CBACT04C` refers to fees.
4. **No fee transaction type or category exists in reference data.**
   `app/data/ASCII/trantype.txt` defines `01` Purchase, `02` Payment, `03` Credit,
   `04` Authorization, `05` Refund, `06` Reversal, `07` Adjustment.
   `app/data/ASCII/trancatg.txt` defines 18 categories, and the only system-charge category is
   `01`/`0005` "Interest Amount", which `1300-B-WRITE-TX` uses for the interest transaction.
   No category is named or coded for fees.
5. **The disclosure group carries an interest rate only.** `app/data/ASCII/discgrp.txt`
   (51 records, groups `A000000000`, `DEFAULT` and `ZEROAPR`) populates `DIS-INT-RATE`
   (e.g. `00150{` = +15.00, `00250{` = +25.00) and leaves the 28-byte `FILLER` all zeros.
6. **Related "limit" logic elsewhere is validation, not charging.** `CBTRN02C` rejects
   over-limit transactions (reason 102, "OVERLIMIT TRANSACTION") and expired-account
   transactions (reason 103). It posts no charge.

### Inference (not supported by code or data, flag before relying on it)

- The JCL comment and the stub suggest the original authors planned fee assessment as part of
  the monthly cycle. They left no specification.
- Because the call sits inside the per-category loop and the `DIS-INT-RATE NOT = 0` guard, the
  intended fees were *possibly* balance- or category-driven (for example, cash-advance fees on
  categories `0002`/`0004`), charged alongside interest. That placement could also be
  incidental. Account-level fees such as annual, late-payment or over-limit fees would not fit
  this guard naturally, because zero-APR accounts would never be charged.
- The unused `FILLER` in `DIS-GROUP-RECORD` and `TRAN-CAT-BAL-RECORD` *could* have been reserved
  for fee parameters. Nothing in the repository says so.

**Conclusion:** the repository contains no evidence of intended fee rules, amounts, triggers or
posting conventions. The legacy system computes no fees.

## Options considered

### (a) Scope out: parity, no fee concept in the target

Don't port the paragraph. Record the gap in this ADR and in the DM-1 parity notes.

- Pros: exact parity, nothing to build or test.
- Cons: the call site disappears from the target. If fees are specified later, someone has to
  rediscover where they belong (per category record, after interest, under the non-zero-rate
  guard, before the account update) and retrofit the orchestration in DM-6/DM-7/DM-8.

### (b) No-op extension point: parity plus a seam (recommended)

Add a domain port `FeeCalculator` in `com.carddemo.interest.domain`. The application service
invokes it at the legacy call site. The default implementation `NoFeeCalculator` always returns
"no fee".

Contract (to be implemented under DM-6, not in this PR):

| Aspect | Contract |
|---|---|
| Invocation | Once per TCATBALF record, only when the resolved disclosure rate is non-zero, immediately after interest for that record has been computed. Same position and guard as `PERFORM 1400-COMPUTE-FEES`. |
| Inputs | Immutable domain views of the category balance (account id, type code, category code, balance), the account (as read, before this run's update), the resolved disclosure group (group id, type, category, rate) and the run context (parm date). |
| Output | A value object with a fee amount (`BigDecimal`, scale 2) and, optionally, fee-transaction details. `NoFeeCalculator` returns zero and no transaction. |
| Side effects | None. The port is pure. Persistence of any future fee transaction or balance change goes through the existing application/adapter flow. |
| Wiring | `NoFeeCalculator` is the only bean. No configuration flag ships until a real implementation exists. |

- Pros: parity is preserved by construction. The legacy call site and guard are documented in
  code where a future implementer will find them. Fees can be added later by supplying a new
  implementation, without touching interest logic.
- Cons: adds a small abstraction that does no work today. Mitigated by keeping it to a single
  interface, a single default, and one test.

### (c) Implement real fee logic

Design and build fee assessment in the Java service.

This needs business inputs that **do not exist** anywhere in the legacy system:

1. **Fee catalogue:** which fees (cash-advance, annual, late-payment, over-limit, returned
   payment, foreign transaction, ...), and whether they are per category, per account or per
   cycle.
2. **Amounts and formulas:** flat amounts, percentages of balance or transaction, minimums and
   caps, and the rounding mode. The legacy interest compute truncates (no `ROUNDED`), so a rule
   would have to be chosen explicitly.
3. **Parameter source:** new disclosure-group fields (repurposing `FILLER` changes a VSAM
   layout), a new fee-schedule file or table, or account-level overrides.
4. **Triggers and eligibility:** due-date and payment history (not available in `CBACT04C`
   inputs), credit-limit breach (`ACCT-CREDIT-LIMIT` vs `ACCT-CURR-BAL` is available), account
   status, waivers, promotional groups (`ZEROAPR`), and whether the `DIS-INT-RATE NOT = 0` guard
   should apply at all.
5. **Posting conventions:** new transaction type/category codes (reference data changes to
   `TRANTYPE`/`TRANCATG`, and the Db2 transaction-type module), transaction description format,
   ID sequencing relative to interest transactions, and whether fees accrue into `ACCT-CURR-BAL`
   in `1050-UPDATE-ACCOUNT` or post separately.
6. **Regulatory and disclosure requirements:** for example fee caps and cardholder disclosures
   for the relevant jurisdiction.
7. **Downstream impact:** `COMBTRAN`, `CREASTMT` (statements) and `TRANREPT` would start seeing
   new transaction codes.

- Pros: delivers a capability the original authors may have intended.
- Cons: it is new functionality, not migration. It deliberately breaks parity, cannot be
  characterised against the legacy system, and depends on a specification nobody has. It would
  block DM-1.

## Recommendation and rationale

Adopt **(b)**: a `FeeCalculator` domain port with a `NoFeeCalculator` default, wired at the
legacy call site and guard, and producing no fees.

- **Parity:** the legacy program observably computes no fees (E1, Facts 1-5). `NoFeeCalculator`
  reproduces that exactly, so characterization tests against legacy output stay valid.
- **No evidence for (c):** the repository holds no fee rules, data or codes, only a one-line
  JCL comment and an empty paragraph. Implementing fees would mean inventing business rules.
- **(b) over (a):** the seam costs almost nothing and keeps the legacy hook's exact semantics
  (per category record, after interest, under the non-zero-rate guard, before the account
  update) visible and testable. That makes a future fee feature a contained change.
- If reviewers prefer the fewest abstractions, **(a)** is an acceptable fallback, provided this
  ADR stays as the record of the gap.

Real fee computation (c) should be raised as a separate, business-owned story once a fee
specification covering the inputs in option (c) exists. It is not part of DM-1.

## Consequences and impact

### DM-6: interest calculation component

- The per-record orchestration must call `FeeCalculator` at the same point and under the same
  guard as the COBOL code: after `computeInterest` and only when the rate is non-zero.
- Fee amounts must **not** be folded into the interest accumulator (`WS-TOTAL-INT`
  equivalent). Interest results must be identical whether or not the fee hook exists.
- DM-6 owns the port, the default and their tests. This ADR adds no code.

### DM-7: account update

- With `NoFeeCalculator`, the account update stays exactly as legacy:
  `currBal += totalInterest`, and cycle credit and debit are reset to zero. No fee term.
- Any future fee posting to the balance needs a separate decision (same update or separate
  posting) and is out of scope.
- Not fee-related, but found during this analysis: the end-of-file account update looks
  unreachable (see E2), so the last account in TCATBALF may never get its interest added. DM-7
  should characterise that behaviour against legacy output and decide, explicitly, between
  bug-for-bug parity and a fix.

### DM-8: interest transaction generation

- Only interest transactions (type `01`, category `0005`, source `System`, description
  `Int. for a/c <acct>`) are emitted. The fee hook emits **no transactions**.
- The transaction ID suffix (`WS-TRANID-SUFFIX` equivalent) increments only for interest
  transactions. A future fee transaction would shift every following ID and break ID-level
  parity. A future implementation must define its own sequencing explicitly.

### Parity and characterization testing

- **Golden-master assertions (legacy == target):**
  - The number of output transactions equals the number of TCATBALF records with a non-zero
    resolved rate. No extra records are written.
  - No output transaction carries a type/category other than `01`/`0005`.
  - For each account, the `ACCT-CURR-BAL` delta equals the sum of that account's interest
    amounts.
- **Unit and contract tests for the seam (DM-6):**
  - `NoFeeCalculator` returns zero and no transaction for any input.
  - The orchestrator invokes the port exactly once per non-zero-rate record and never for
    zero-rate records. This is a characterization of the legacy guard.
  - Swapping in a test double that returns a non-zero fee must not change interest results.
    This proves fee and interest are kept separate.
- **Fixture note (observed in legacy sample data, relevant to building fixtures):**
  - All 50 records in `app/data/ASCII/tcatbal.txt` are type `01`, category `0001`, with a zero
    balance.
  - In `acctdata.txt`, and in the EBCDIC `AWS.M2.CARDDEMO.ACCTDATA.PS`, the value `A000000000`
    sits at bytes 103-112. Per `CVACT01Y` that is `ACCT-ADDR-ZIP`, while `ACCT-GROUP-ID`
    (bytes 113-122) is blank.
  - Under the copybook layout, every account therefore resolves to the `DEFAULT` group: 01/0001
    gives a rate of 15.00, so the guard passes, the fee hook is invoked 50 times, and 50 interest
    transactions of 0.00 are written.
  - Characterization fixtures need non-zero balances and mixed type/category/group records to
    exercise both sides of the guard.

### Follow-ups

- On acceptance, add the `FeeCalculator` / `NoFeeCalculator` acceptance criteria to DM-6.
- Optional: open a business-owned story "Specify and implement card fees" that references
  option (c) of this ADR.
