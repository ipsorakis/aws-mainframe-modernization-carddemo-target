# CardDemo Interest Service (target)

This repository holds the Java modernization target for the CardDemo monthly interest calculation batch: a Spring Boot service that ports the COBOL program `CBACT04C` (run by `INTCALC.jcl` in the `MONTHLY-InterestCalculation` Control-M folder) from the mainframe reference application in [ipsorakis/aws-mainframe-modernization-carddemo](https://github.com/ipsorakis/aws-mainframe-modernization-carddemo), which remains the read-only source of truth for behaviour.

Tracking: [DM-22](https://cognition-london-demos.atlassian.net/browse/DM-22) (scaffolding), part of epic DM-4 "Infrastructure and deployment"; the business logic lands in DM-6, DM-7, DM-8 and DM-9 under epic DM-1 "Interest calculation domain service (Java)".

## Build and test

Requirements: JDK 21. Maven itself is provided by the wrapper (Maven 3.9.16), so no local install is needed.

```bash
./mvnw -B verify          # compile, run all tests (unit, Spring context, ArchUnit) and package
./mvnw test -Dtest=ArchitectureTest   # run a single test class
./mvnw spring-boot:run    # start the application context (no job runs yet)
```

On Windows use `mvnw.cmd`. If Maven Central is unreachable, point the wrapper at a mirror with `MVNW_REPOURL=<mirror>/maven2`.

CI (`.github/workflows/ci.yml`) runs `./mvnw -B verify` on Temurin 21 for every pull request and every push to `main`.

## Stack and versions

All versions are pinned; do not introduce floating ranges.

| Component | Version |
|---|---|
| Java | 21 |
| Spring Boot (`spring-boot-starter-parent`) | 3.5.16 |
| Maven (via wrapper, `only-script` distribution) | 3.9.16 |
| Testing | JUnit 5 + AssertJ (managed by Spring Boot), ArchUnit 1.5.0 |

Single Maven module, `groupId` `com.carddemo`, `artifactId` `carddemo-interest-service`, base package `com.carddemo.interest`.

## Package layout and rules

```
com.carddemo.interest
├── InterestServiceApplication           Spring Boot entry point
├── domain                               pure Java model (copybook records)
│   ├── interest                         DM-6: rate lookup + interest computation
│   ├── account                          DM-7: account balance update
│   └── transaction                      DM-8: interest transaction generation
├── application                          use cases / orchestration
│   ├── InterestCalculationJob           CBACT04C main loop (skeleton)
│   └── port                             outbound data-access ports
├── adapter
│   └── persistence                      port implementations (placeholders for now)
└── config                               Spring wiring (@Configuration)
```

Rules (the ones marked *enforced* fail the build via `ArchitectureTest`):

1. **`domain` is plain Java.** No Spring, Jakarta/javax, or I/O (`java.io`, `java.nio`, `java.sql`, `java.net`) imports, and no dependency on `application`, `adapter` or `config`. *Enforced.*
2. **`application` never depends on `adapter` or `config`.** It talks to data only through the interfaces in `application.port`. *Enforced.*
3. **Ports live in `application.port`**, one per CBACT04C file: `TransactionCategoryBalanceReader` (TCATBALF), `AccountRepository` (ACCTFILE), `CardXrefRepository` (XREFFILE), `DisclosureGroupRepository` (DISCGRP), `TransactionWriter` (TRANSACT).
4. **Adapters implement ports** in `adapter.*`. The `adapter.persistence` classes are placeholders that throw `UnsupportedOperationException`; no database is wired yet.
5. **Spring wiring lives in `config`.** `domain`, `application` and `adapter` classes are plain classes created by `@Bean` methods in `config.InterestJobConfiguration`, not component-scanned.
6. **Copybook mapping.** Domain records mirror the copybooks field by field, with a Javadoc `@param` naming the COBOL field and its PIC clause. Signed amounts with implied decimals (`PIC S9(n)V99`, zoned or packed) are `BigDecimal`; unsigned integer keys (`PIC 9(n)`) are `long`/`int`; `PIC X(n)` is `String`; `FILLER` is not modelled.
7. **Tests.** Domain tests are plain JUnit 5 + AssertJ with no Spring context (see `domain/TransactionCategoryBalanceTest`). Use `@SpringBootTest` only for wiring tests (see `InterestServiceApplicationTests`).

Domain records and their copybooks:

| Record | Copybook | COBOL record |
|---|---|---|
| `domain.Account` | CVACT01Y | `ACCOUNT-RECORD` |
| `domain.CardXref` | CVACT03Y | `CARD-XREF-RECORD` |
| `domain.TransactionCategoryBalance` | CVTRA01Y | `TRAN-CAT-BAL-RECORD` |
| `domain.DisclosureGroup` | CVTRA02Y | `DIS-GROUP-RECORD` |
| `domain.Transaction` | CVTRA05Y | `TRAN-RECORD` |

## CBACT04C paragraph ownership

Source: [`app/cbl/CBACT04C.cbl`](https://github.com/ipsorakis/aws-mainframe-modernization-carddemo/blob/main/app/cbl/CBACT04C.cbl), run by [`app/jcl/INTCALC.jcl`](https://github.com/ipsorakis/aws-mainframe-modernization-carddemo/blob/main/app/jcl/INTCALC.jcl) (step `STEP15`, `PARM='2022071800'`) as the `INTCALC` job in the `MONTHLY-InterestCalculation` Control-M folder (`CLOSEFIL` → `INTCALC` → `COMBTRAN` → `WAITSTEP` → `OPENFIL`).

Classes marked *(planned)* do not exist yet; the owning ticket creates them in the listed package.

| COBOL paragraph | Behaviour | Java owner | Ticket |
|---|---|---|---|
| PROCEDURE DIVISION main loop | Read category balances in key order, break on account change, drive the paragraphs below | `application.InterestCalculationJob` | DM-22 (skeleton); filled in by DM-6/DM-7/DM-8 |
| `1000-TCATBALF-GET-NEXT` | Sequential read of TCATBALF | `application.port.TransactionCategoryBalanceReader` → `adapter.persistence` | DM-22 (port) |
| `1050-UPDATE-ACCOUNT` | `ACCT-CURR-BAL += WS-TOTAL-INT`; reset cycle credit/debit; REWRITE | `domain.account` *(planned `AccountInterestPosting`)* + `AccountRepository.update` | DM-7 |
| `1100-GET-ACCT-DATA` | READ ACCTFILE by `ACCT-ID` | `application.port.AccountRepository.findById` | DM-7 |
| `1110-GET-XREF-DATA` | READ XREFFILE by alternate key `XREF-ACCT-ID` (card number for the interest transaction) | `application.port.CardXrefRepository.findByAccountId` | DM-8 |
| `1200-GET-INTEREST-RATE` | READ DISCGRP by (`ACCT-GROUP-ID`, type, category) | `domain.interest.InterestRateResolver` (lookup = `DisclosureGroupRepository::findByKey`) | DM-6 |
| `1200-A-GET-DEFAULT-INT-RATE` | On status `23`, retry with group `DEFAULT`; missing default abends (`DisclosureGroupNotFoundException`) | `domain.interest.InterestRateResolver` | DM-6 |
| `1300-COMPUTE-INTEREST` | `WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200` (truncated to the cent); add to `WS-TOTAL-INT`, reset on account break | `domain.interest.InterestCalculator` (`monthlyInterest`, `rollUp`) | DM-6 |
| `1300-B-WRITE-TX` | Build type `01` / cat `05` / source `System` transaction, ID = parm date + sequence, description `Int. for a/c <acct>`; WRITE | `domain.transaction` *(planned `InterestTransactionFactory`)* + `TransactionWriter.write` | DM-8 |
| `Z-GET-DB2-FORMAT-TIMESTAMP` | DB2-format timestamp for `TRAN-ORIG-TS` / `TRAN-PROC-TS` | `domain.transaction` (with an injected `java.time.Clock`) | DM-8 |
| `1400-COMPUTE-FEES` | Empty stub in COBOL ("To be implemented") | Decision pending: `domain.interest` or explicitly scoped out | DM-9 |

Business logic is intentionally **not** implemented in this scaffold: `InterestCalculationJob.run` throws `UnsupportedOperationException` until DM-6/DM-7/DM-8 land.
