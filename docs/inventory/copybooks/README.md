# Copybook record layouts (BNP-5)

Field-by-field layouts of the record-layout copybooks in `app/cpy/` of the source repo
[`ipsorakis/aws-mainframe-modernization-carddemo`](https://github.com/ipsorakis/aws-mainframe-modernization-carddemo)
(read-only reference), with the dataset each record is stored in, the IDCAMS `RECORDSIZE`/`KEYS`
of that dataset, and the programs that `COPY` the member.

Scope is the top-level `app/` tree. The extension folders `app/app-authorization-ims-db2-mq`,
`app/app-transaction-type-db2` and `app/app-vsam-mq` also exist but are out of scope.

## Files

| Path | What |
|---|---|
| `layouts/<COPYBOOK>.json` | One machine-readable spec per copybook (17 files, JSON) |
| `layouts/_index.json` | Summary of all specs, the 13 non-layout members, and every IDCAMS DEFINE found in `app/jcl/` |
| `tools/gen_layouts.py` | Generator: parses copybooks, JCL, CSD and `COPY` statements; writes the specs and the generated tables below |
| `tools/verify_layouts.py` | Cross-check: compiles a GnuCOBOL stub per copybook and compares `LENGTH OF` and byte offset of every named item with the spec; checks the spec set against `git ls-files app/cpy` |

Regenerate and verify:

```bash
sudo apt-get install -y gnucobol          # cobc 3.1.2 was used
python3 docs/inventory/copybooks/tools/gen_layouts.py    --source <path-to-source-checkout>
python3 docs/inventory/copybooks/tools/verify_layouts.py --source <path-to-source-checkout>
```

The verifier compiles with `cobc -std=ibm -ftab-width=3` and checks 244 data items across 23 records
(every named item, including group items, items inside `REDEFINES` and the first element of every
`OCCURS`). All offsets and lengths agree with the specs.

## Spec format

Each `layouts/<COPYBOOK>.json` has the copybook name, `source_file`, `source_commit`, the `RECLN`
from the header comment (if any), `copied_by` (program, file, line) and one entry per 01-level
record: `name`, `length`, `datasets` (DSN, organization, IDCAMS RECORDSIZE/KEYS with the field the
key lands on, alternate indexes, CICS FILE names from `app/csd/CARDDEMO.CSD`, sample data file,
and the source lines that prove the record/dataset link) and `fields`, in source order:

| Key | Meaning |
|---|---|
| `level`, `name`, `parent`, `line` | COBOL level, data name (`FILLER` for unnamed), parent group, line in the copybook |
| `type` | `group`, `alphanumeric`, `numeric`, `numeric-edited` |
| `pic`, `usage` | PIC as written; `DISPLAY`, `COMP` (also BINARY / COMP-4) or `COMP-3` (null for groups) |
| `offset` | 0-based byte offset from the start of the 01 record |
| `length`, `total_length` | Bytes of one occurrence; `length * occurs` |
| `signed`, `sign_encoding`, `digits`, `scale` | Sign and decimal places (`scale` = digits after the implied `V`) |
| `occurs`, `occurs_path` | `OCCURS n`; `occurs_path` lists every OCCURS ancestor with its stride: element offset = `offset + sum(i * stride)` with 0-based `i` |
| `redefines`, `in_redefines` | Item this one redefines; nearest enclosing REDEFINES item (fields under different variants overlap) |
| `value`, `level_88` | `VALUE` clause; 88-level condition names with their values (`{"from","thru"}` for THRU ranges) |

Storage rules (IBM Enterprise COBOL, confirmed by `cobc -std=ibm`): DISPLAY numeric is one byte per
digit with the sign overpunched in the zone of the last byte (`S` and `V` take no space); COMP is
2/4/8 bytes for 1-4/5-9/10-18 digits, big-endian; COMP-3 is `digits // 2 + 1` bytes. The sample
files under `app/data/EBCDIC/` are EBCDIC; the code page is not stated in the source, cp037 decodes
them cleanly (inferred).

## Copybook -> record -> dataset

Every record-to-dataset link is backed by source lines listed in `datasets[].evidence` of the spec
(a `READ ... INTO` / `WRITE ... FROM` in a program plus the DD or CSD `DSNAME` that binds the file);
the generator fails if any cited line changes. "Key match" means the IDCAMS KEYS (length, offset)
lands exactly on one named field.

<!-- BEGIN GENERATED: summary -->
| Copybook | Record | Length | Dataset (DSN) | IDCAMS RECORDSIZE / KEYS (len off) -> key field | Length match | Key match | Programs that COPY it |
|---|---|---:|---|---|---|---|---|
| [CUSTREC](layouts/CUSTREC.json) | CUSTOMER-RECORD | 500 | `AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS` | RECORDSIZE(500 500) KEYS(9 0) -> CUST-ID | yes | yes | CBSTM03A |
| [CVCUS01Y](layouts/CVCUS01Y.json) | CUSTOMER-RECORD | 500 | `AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS` | RECORDSIZE(500 500) KEYS(9 0) -> CUST-ID | yes | yes | CBCUS01C, CBEXPORT, CBIMPORT, CBTRN01C, COACTUPC, COACTVWC, COCRDSLC, COCRDUPC |
| [CVACT01Y](layouts/CVACT01Y.json) | ACCOUNT-RECORD | 300 | `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS` | RECORDSIZE(300 300) KEYS(11 0) -> ACCT-ID | yes | yes | CBACT01C, CBACT04C, CBEXPORT, CBIMPORT, CBSTM03A, CBTRN01C, CBTRN02C, COACTUPC, COACTVWC, COBIL00C, COTRN02C |
| [CVACT02Y](layouts/CVACT02Y.json) | CARD-RECORD | 150 | `AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS` | RECORDSIZE(150 150) KEYS(16 0) -> CARD-NUM<br>AIX KEYS(11 16) -> CARD-ACCT-ID | yes | yes | CBACT02C, CBEXPORT, CBIMPORT, CBTRN01C, COACTVWC, COCRDLIC, COCRDSLC, COCRDUPC |
| [CVACT03Y](layouts/CVACT03Y.json) | CARD-XREF-RECORD | 50 | `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS` | RECORDSIZE(50 50) KEYS(16 0) -> XREF-CARD-NUM<br>AIX KEYS(11 25) -> XREF-ACCT-ID | yes | yes | CBACT03C, CBACT04C, CBEXPORT, CBIMPORT, CBSTM03A, CBTRN01C, CBTRN02C, CBTRN03C, COACTUPC, COACTVWC, COBIL00C, COTRN02C |
| [CVCRD01Y](layouts/CVCRD01Y.json) | CC-WORK-AREAS | 213 | none (working storage only) | n/a | n/a | n/a | COACTUPC, COACTVWC, COCRDLIC, COCRDSLC, COCRDUPC |
| [CVTRA01Y](layouts/CVTRA01Y.json) | TRAN-CAT-BAL-RECORD | 50 | `AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS` | RECORDSIZE(50 50) KEYS(17 0) -> TRAN-CAT-KEY | yes | yes | CBACT04C, CBTRN02C |
| [CVTRA02Y](layouts/CVTRA02Y.json) | DIS-GROUP-RECORD | 50 | `AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS` | RECORDSIZE(50 50) KEYS(16 0) -> DIS-GROUP-KEY | yes | yes | CBACT04C |
| [CVTRA03Y](layouts/CVTRA03Y.json) | TRAN-TYPE-RECORD | 60 | `AWS.M2.CARDDEMO.TRANTYPE.VSAM.KSDS` | RECORDSIZE(60 60) KEYS(2 0) -> TRAN-TYPE | yes | yes | CBTRN03C |
| [CVTRA04Y](layouts/CVTRA04Y.json) | TRAN-CAT-RECORD | 60 | `AWS.M2.CARDDEMO.TRANCATG.VSAM.KSDS` | RECORDSIZE(60 60) KEYS(6 0) -> TRAN-CAT-KEY | yes | yes | CBTRN03C |
| [CVTRA05Y](layouts/CVTRA05Y.json) | TRAN-RECORD | 350 | `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS` | RECORDSIZE(350 350) KEYS(16 0) -> TRAN-ID<br>AIX KEYS(26 304) -> TRAN-PROC-TS | yes | yes | CBACT04C, CBEXPORT, CBIMPORT, CBTRN01C, CBTRN02C, CBTRN03C, COBIL00C, CORPT00C, COTRN00C, COTRN01C, COTRN02C |
| [CVTRA06Y](layouts/CVTRA06Y.json) | DALYTRAN-RECORD | 350 | `AWS.M2.CARDDEMO.DALYTRAN.PS` | sequential (no IDCAMS DEFINE) | n/a (no RECORDSIZE) | n/a | CBTRN01C, CBTRN02C |
| [CVTRA07Y](layouts/CVTRA07Y.json) | REPORT-NAME-HEADER | 115 | `AWS.M2.CARDDEMO.TRANREPT(+1)` | sequential (no IDCAMS DEFINE), LRECL 133 | fits in 133 | n/a | CBTRN03C |
| [CVTRA07Y](layouts/CVTRA07Y.json) | TRANSACTION-DETAIL-REPORT | 114 | `AWS.M2.CARDDEMO.TRANREPT(+1)` | sequential (no IDCAMS DEFINE), LRECL 133 | fits in 133 | n/a | CBTRN03C |
| [CVTRA07Y](layouts/CVTRA07Y.json) | TRANSACTION-HEADER-1 | 114 | `AWS.M2.CARDDEMO.TRANREPT(+1)` | sequential (no IDCAMS DEFINE), LRECL 133 | fits in 133 | n/a | CBTRN03C |
| [CVTRA07Y](layouts/CVTRA07Y.json) | TRANSACTION-HEADER-2 | 133 | `AWS.M2.CARDDEMO.TRANREPT(+1)` | sequential (no IDCAMS DEFINE), LRECL 133 | fits in 133 | n/a | CBTRN03C |
| [CVTRA07Y](layouts/CVTRA07Y.json) | REPORT-PAGE-TOTALS | 112 | `AWS.M2.CARDDEMO.TRANREPT(+1)` | sequential (no IDCAMS DEFINE), LRECL 133 | fits in 133 | n/a | CBTRN03C |
| [CVTRA07Y](layouts/CVTRA07Y.json) | REPORT-ACCOUNT-TOTALS | 112 | `AWS.M2.CARDDEMO.TRANREPT(+1)` | sequential (no IDCAMS DEFINE), LRECL 133 | fits in 133 | n/a | CBTRN03C |
| [CVTRA07Y](layouts/CVTRA07Y.json) | REPORT-GRAND-TOTALS | 112 | `AWS.M2.CARDDEMO.TRANREPT(+1)` | sequential (no IDCAMS DEFINE), LRECL 133 | fits in 133 | n/a | CBTRN03C |
| [CSUSR01Y](layouts/CSUSR01Y.json) | SEC-USER-DATA | 80 | `AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS` | RECORDSIZE(80 80) KEYS(8 0) -> SEC-USR-ID | yes | yes | COACTUPC, COACTVWC, COADM01C, COCRDLIC, COCRDSLC, COCRDUPC, COMEN01C, COSGN00C, COUSR00C, COUSR01C, COUSR02C, COUSR03C |
| [CVEXPORT](layouts/CVEXPORT.json) | EXPORT-RECORD | 500 | `AWS.M2.CARDDEMO.EXPORT.DATA` | RECORDSIZE(500 500) KEYS(4 28) -> EXPORT-SEQUENCE-NUM/EXPORT-BRANCH-ID | yes | **no** | CBEXPORT, CBIMPORT |
| [COSTM01](layouts/COSTM01.json) | TRNX-RECORD | 350 | `AWS.M2.CARDDEMO.TRXFL.VSAM.KSDS` | RECORDSIZE(350 350) KEYS(32 0) -> TRNX-KEY | yes | yes | CBSTM03A |
| [UNUSED1Y](layouts/UNUSED1Y.json) | UNUSED-DATA | 80 | none (working storage only) | n/a | n/a | n/a | none |
<!-- END GENERATED: summary -->

### Mismatches and records with no RECORDSIZE

- **CVEXPORT / `AWS.M2.CARDDEMO.EXPORT.DATA`: key offset is off by one.** Record length matches
  (500 = `RECORDSIZE(500 500)`), but `CBEXPORT.jcl` line 32 defines `KEYS(4 28)` while
  `CBEXPORT.cbl` line 68 declares `RECORD KEY IS EXPORT-SEQUENCE-NUM`, which is at offset 27
  (`EXPORT-REC-TYPE` 1 byte + `EXPORT-TIMESTAMP` 26 bytes), length 4 (`PIC 9(9) COMP`). The VSAM key
  therefore covers the last 3 bytes of `EXPORT-SEQUENCE-NUM` plus the first byte of
  `EXPORT-BRANCH-ID`. In the sample `AWS.M2.CARDDEMO.EXPORT.DATA.PS`, bytes 27-30 of record 1 are
  `00000001` and bytes 31-34 are `0001` (EBCDIC), consistent with the copybook offsets. The intended
  definition is probably `KEYS(4 27)` (inferred). For the ETL use the copybook offsets.
- **CVTRA06Y / `AWS.M2.CARDDEMO.DALYTRAN.PS`**: a sequential dataset (`POSTTRAN.jcl` line 31,
  `CBTRN01C.cbl`/`CBTRN02C.cbl` `DALYTRAN-FILE`), no IDCAMS DEFINE, so no RECORDSIZE to compare.
  The 350-byte layout divides the 105000-byte sample file into 300 whole records.
- **CVTRA07Y**: seven 01-level print lines (112-133 bytes) for the transaction report written by
  `CBTRN03C.cbl` (`FD-REPTFILE-REC PIC X(133)`, line 85) to the GDG `AWS.M2.CARDDEMO.TRANREPT(+1)`
  with `LRECL=133` (`TRANREPT.jcl` lines 78-80). Shorter lines are padded to 133 by the MOVE to the
  FD record (inferred from COBOL MOVE rules). Not a VSAM file.
- **CVCRD01Y** (`CC-WORK-AREAS`, 213 bytes): CICS working-storage work area (AID flags as 88-levels,
  next program/map/mapset, messages, numeric REDEFINES of account/card/customer IDs). Not stored in
  any file.
- **UNUSED1Y** (`UNUSED-DATA`, 80 bytes): same shape as `SEC-USER-DATA` (8+20+20+8+1+23), copied by
  no program in `app/cbl/`. Not mapped to a dataset.
- **CUSTREC** vs **CVCUS01Y**: both define `CUSTOMER-RECORD` (500 bytes) for the same dataset; they
  differ only in the name of the field at offset 308 (`CUST-DOB-YYYYMMDD` vs `CUST-DOB-YYYY-MM-DD`,
  both `PIC X(10)`). `CUSTREC.cpy` lines 6-22 start with tab characters; with cobc's default tab
  width (8) the PIC clauses run past column 72 and the member does not compile. With tab width 3
  every line lines up with lines 1-5, so that width is used (inferred author intent). Only
  `CBSTM03A.CBL` copies it.
- **COSTM01** (`TRNX-RECORD`, 350 bytes): transaction layout re-keyed by card number + transaction
  ID (`TRNX-KEY`, 32 bytes) for statement generation; matches the `AWS.M2.CARDDEMO.TRXFL.VSAM.KSDS`
  cluster defined in `CREASTMT.JCL` line 29 (`KEYS(32 0)`, `RECORDSIZE(350 350)`).

Other IDCAMS observations (no copybook mismatch):

- `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS` is defined twice with identical values (`TRANFILE.jcl`
  line 49 and `TRANBKP.jcl` line 54); the AIX is defined in `TRANFILE.jcl` and `TRANIDX.jcl`.
- `DEFCUST.jcl` line 35 defines `AWS.CUSTDATA.CLUSTER` with `KEYS(10 0)` / `RECORDSIZE(500 500)`.
  No other file in `app/` refers to that DSN and `CUST-ID` is 9 bytes; it looks like a leftover
  sample definition (inferred) and is not mapped to a copybook.
- The user-security file also has ESDS and RRDS variants (`ESDSRRDS.jcl` lines 65 and 99,
  `RECORDSIZE(80 80)`), which fit `SEC-USER-DATA`.

### All IDCAMS DEFINEs in `app/jcl/`

<!-- BEGIN GENERATED: idcams -->
| DSN | Kind | Org | RECORDSIZE | KEYS (len off) | Defined in |
|---|---|---|---|---|---|
| `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS` | CLUSTER | KSDS | 300 300 | 11 0 | app/jcl/ACCTFILE.jcl:36 |
| `AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS` | CLUSTER | KSDS | 150 150 | 16 0 | app/jcl/CARDFILE.jcl:50 |
| `AWS.M2.CARDDEMO.CARDDATA.VSAM.AIX` | ALTERNATEINDEX |  | 150 150 | 11 16 | app/jcl/CARDFILE.jcl:83 |
| `AWS.M2.CARDDEMO.CARDDATA.VSAM.AIX.PATH` | PATH |  |  |  | app/jcl/CARDFILE.jcl:100 |
| `AWS.M2.CARDDEMO.EXPORT.DATA` | CLUSTER | KSDS | 500 500 | 4 28 | app/jcl/CBEXPORT.jcl:30 |
| `AWS.M2.CARDDEMO.TRXFL.VSAM.KSDS` | CLUSTER | KSDS | 350 350 | 32 0 | app/jcl/CREASTMT.JCL:29 |
| `AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS` | CLUSTER | KSDS | 500 500 | 9 0 | app/jcl/CUSTFILE.jcl:46 |
| `AWS.M2.CARDDEMO.DALYREJS` | GENERATIONDATAGROUP |  |  |  | app/jcl/DALYREJS.jcl:24 |
| `AWS.CUSTDATA.CLUSTER` | CLUSTER | KSDS | 500 500 | 10 0 | app/jcl/DEFCUST.jcl:35 |
| `AWS.M2.CARDDEMO.TRANSACT.BKUP` | GENERATIONDATAGROUP |  |  |  | app/jcl/DEFGDGB.jcl:24 |
| `AWS.M2.CARDDEMO.TRANSACT.DALY` | GENERATIONDATAGROUP |  |  |  | app/jcl/DEFGDGB.jcl:30 |
| `AWS.M2.CARDDEMO.TRANREPT` | GENERATIONDATAGROUP |  |  |  | app/jcl/DEFGDGB.jcl:36 |
| `AWS.M2.CARDDEMO.TCATBALF.BKUP` | GENERATIONDATAGROUP |  |  |  | app/jcl/DEFGDGB.jcl:42 |
| `AWS.M2.CARDDEMO.SYSTRAN` | GENERATIONDATAGROUP |  |  |  | app/jcl/DEFGDGB.jcl:48 |
| `AWS.M2.CARDDEMO.TRANSACT.COMBINED` | GENERATIONDATAGROUP |  |  |  | app/jcl/DEFGDGB.jcl:54 |
| `AWS.M2.CARDDEMO.TRANTYPE.BKUP` | GENERATIONDATAGROUP |  |  |  | app/jcl/DEFGDGD.jcl:27 |
| `AWS.M2.CARDDEMO.TRANCATG.PS.BKUP` | GENERATIONDATAGROUP |  |  |  | app/jcl/DEFGDGD.jcl:50 |
| `AWS.M2.CARDDEMO.DISCGRP.BKUP` | GENERATIONDATAGROUP |  |  |  | app/jcl/DEFGDGD.jcl:73 |
| `AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS` | CLUSTER | KSDS | 50 50 | 16 0 | app/jcl/DISCGRP.jcl:36 |
| `AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS` | CLUSTER | KSDS | 80 80 | 8 0 | app/jcl/DUSRSECJ.jcl:64 |
| `AWS.M2.CARDDEMO.USRSEC.VSAM.ESDS` | CLUSTER | ESDS | 80 80 |  | app/jcl/ESDSRRDS.jcl:65 |
| `AWS.M2.CARDDEMO.USRSEC.VSAM.RRDS` | CLUSTER | RRDS | 80 80 |  | app/jcl/ESDSRRDS.jcl:99 |
| `AWS.M2.CARDDEMO.TRANREPT` | GENERATIONDATAGROUP |  |  |  | app/jcl/REPTFILE.jcl:25 |
| `AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS` | CLUSTER | KSDS | 50 50 | 17 0 | app/jcl/TCATBALF.jcl:36 |
| `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS` | CLUSTER | KSDS | 350 350 | 16 0 | app/jcl/TRANBKP.jcl:54 |
| `AWS.M2.CARDDEMO.TRANCATG.VSAM.KSDS` | CLUSTER | KSDS | 60 60 | 6 0 | app/jcl/TRANCATG.jcl:36 |
| `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS` | CLUSTER | KSDS | 350 350 | 16 0 | app/jcl/TRANFILE.jcl:49 |
| `AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX` | ALTERNATEINDEX |  | 350 350 | 26 304 | app/jcl/TRANFILE.jcl:82 |
| `AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX.PATH` | PATH |  |  |  | app/jcl/TRANFILE.jcl:99 |
| `AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX` | ALTERNATEINDEX |  | 350 350 | 26 304 | app/jcl/TRANIDX.jcl:25 |
| `AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX.PATH` | PATH |  |  |  | app/jcl/TRANIDX.jcl:42 |
| `AWS.M2.CARDDEMO.TRANTYPE.VSAM.KSDS` | CLUSTER | KSDS | 60 60 | 2 0 | app/jcl/TRANTYPE.jcl:36 |
| `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS` | CLUSTER | KSDS | 50 50 | 16 0 | app/jcl/XREFFILE.jcl:39 |
| `AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX` | ALTERNATEINDEX |  | 50 50 | 11 25 | app/jcl/XREFFILE.jcl:72 |
| `AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.PATH` | PATH |  |  |  | app/jcl/XREFFILE.jcl:90 |
<!-- END GENERATED: idcams -->

### Sample data check

Every sample file under `app/data/EBCDIC/` that maps to a layout is an exact multiple of the
record length. (`AWS.M2.CARDDEMO.ACCDATA.PS` is a byte-identical copy of `ACCTDATA.PS`.)

<!-- BEGIN GENERATED: samples -->
| Copybook | Record | Sample file | Bytes | Records | Whole records |
|---|---|---|---:|---:|---|
| CUSTREC | CUSTOMER-RECORD | `app/data/EBCDIC/AWS.M2.CARDDEMO.CUSTDATA.PS` | 25000 | 50 | yes |
| CVCUS01Y | CUSTOMER-RECORD | `app/data/EBCDIC/AWS.M2.CARDDEMO.CUSTDATA.PS` | 25000 | 50 | yes |
| CVACT01Y | ACCOUNT-RECORD | `app/data/EBCDIC/AWS.M2.CARDDEMO.ACCTDATA.PS` | 15000 | 50 | yes |
| CVACT02Y | CARD-RECORD | `app/data/EBCDIC/AWS.M2.CARDDEMO.CARDDATA.PS` | 7500 | 50 | yes |
| CVACT03Y | CARD-XREF-RECORD | `app/data/EBCDIC/AWS.M2.CARDDEMO.CARDXREF.PS` | 2500 | 50 | yes |
| CVTRA01Y | TRAN-CAT-BAL-RECORD | `app/data/EBCDIC/AWS.M2.CARDDEMO.TCATBALF.PS` | 2500 | 50 | yes |
| CVTRA02Y | DIS-GROUP-RECORD | `app/data/EBCDIC/AWS.M2.CARDDEMO.DISCGRP.PS` | 2550 | 51 | yes |
| CVTRA03Y | TRAN-TYPE-RECORD | `app/data/EBCDIC/AWS.M2.CARDDEMO.TRANTYPE.PS` | 420 | 7 | yes |
| CVTRA04Y | TRAN-CAT-RECORD | `app/data/EBCDIC/AWS.M2.CARDDEMO.TRANCATG.PS` | 1080 | 18 | yes |
| CVTRA06Y | DALYTRAN-RECORD | `app/data/EBCDIC/AWS.M2.CARDDEMO.DALYTRAN.PS` | 105000 | 300 | yes |
| CSUSR01Y | SEC-USER-DATA | `app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS` | 800 | 10 | yes |
| CVEXPORT | EXPORT-RECORD | `app/data/EBCDIC/AWS.M2.CARDDEMO.EXPORT.DATA.PS` | 250000 | 500 | yes |
<!-- END GENERATED: samples -->

## Non-layout members of `app/cpy/`

`git ls-files app/cpy` returns 30 files: the 17 layouts above and these 13, which hold no file
record layout (classification in `gen_layouts.NON_LAYOUT_COPYBOOKS`; `verify_layouts.py` fails if
a tracked member is unclassified):

| Copybook | Content |
|---|---|
| COADM02Y | Admin menu option table (working storage) |
| COCOM01Y | CICS COMMAREA (`CARDDEMO-COMMAREA`) |
| CODATECN | Date-conversion parameter area (a copybook, not a program) |
| COMEN02Y | Main menu option table (working storage) |
| COTTL01Y | Screen title literals |
| CSDAT01Y | Current date/time working storage |
| CSLKPCDY | Phone area code / state / ZIP lookup tables with 88-levels |
| CSMSG01Y | Common screen messages |
| CSMSG02Y | Abend data work area |
| CSSETATY | BMS attribute-setting PROCEDURE DIVISION snippet (`COPY ... REPLACING`) |
| CSSTRPFY | PF-key storing PROCEDURE DIVISION snippet |
| CSUTLDPY | Date-validation PROCEDURE DIVISION paragraphs |
| CSUTLDWY | Date-validation working storage |

## Differences from ticket

| # | Ticket says | Source shows |
|---|---|---|
| 1 | Summary lists CUSTREC, CVACT01Y-03Y, CVCRD01Y, CVTRA01Y-07Y, CSUSR01Y (13) | Description adds CVCUS01Y and CVEXPORT (15). All 15 exist. |
| 2 | 15 record-layout copybooks | 17: `COSTM01.CPY` (statement transaction layout, upper-case extension) and `UNUSED1Y.cpy` (unused 80-byte layout) are also record layouts. `app/cpy/` has 30 files in total. |
| 3 | CVEXPORT is "the only master-file copybook with COMP-3" | It is the only `app/cpy/` member with COMP-3 at all (5 fields), but it is the branch-migration export file, not a master file. It also has COMP (binary) fields and REDEFINES. The six master copybooks (CVACT01Y, CVCUS01Y, CVACT02Y, CVTRA05Y, CSUSR01Y, CVACT03Y) are all DISPLAY with no REDEFINES/OCCURS. |
| 4 | Record lengths match the VSAM RECORDSIZE in `app/jcl/*FILE.jcl` | `*FILE.jcl` is only ACCTFILE, CARDFILE, CUSTFILE, TRANFILE, XREFFILE (VSAM) and REPTFILE (a GDG). The other clusters are in `TCATBALF.jcl`, `DISCGRP.jcl`, `TRANCATG.jcl`, `TRANTYPE.jcl`, `DUSRSECJ.jcl`, `CBEXPORT.jcl` and `CREASTMT.JCL`. |
| 5 | Every record has a VSAM RECORDSIZE | All 13 VSAM-backed records (12 clusters; CUSTREC and CVCUS01Y share CUSTDATA) match their RECORDSIZE. CVTRA06Y (sequential PS), CVTRA07Y (133-byte print GDG), CVCRD01Y (working storage) and UNUSED1Y (unused) have no RECORDSIZE. |
| 6 | (not mentioned) | `CBEXPORT.jcl` `KEYS(4 28)` does not line up with `EXPORT-SEQUENCE-NUM` at offset 27 (see Mismatches). |
| 7 | (not mentioned) | `CUSTREC.cpy` contains tab characters and does not compile with cobc's default tab width; it needs tab width 3. |
| 8 | CVTRA07Y is a "report layout" (singular) | It holds 7 separate 01-level records. |
