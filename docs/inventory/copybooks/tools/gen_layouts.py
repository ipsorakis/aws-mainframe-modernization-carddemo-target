#!/usr/bin/env python3
"""Generate machine-readable record layouts for the CardDemo copybooks.

Reads the read-only source repo (ipsorakis/aws-mainframe-modernization-carddemo),
parses every record-layout copybook in app/cpy/, the IDCAMS DEFINEs in app/jcl/,
the CICS FILE definitions in app/csd/ and the COPY statements in app/cbl/, then writes:

  docs/inventory/copybooks/layouts/<COPYBOOK>.json   one spec per copybook
  docs/inventory/copybooks/layouts/_index.json       summary of all specs
  docs/inventory/copybooks/README.md                 tables between GENERATED markers

Usage:
  python3 gen_layouts.py --source ~/repos/aws-mainframe-modernization-carddemo

Offsets are 0-based byte offsets from the start of the 01 record. Sizes follow
IBM Enterprise COBOL rules: DISPLAY = 1 byte per character position (S and V take
no space, sign is overpunched in the last byte), COMP/BINARY = 2/4/8 bytes for
1-4/5-9/10-18 digits, COMP-3 = digits//2 + 1. Cross-check with verify_layouts.py.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT_DIR = HERE.parent
LAYOUT_DIR = OUT_DIR / "layouts"

# Copybooks that hold record layouts. The first 15 are the ones the BNP-5 ticket
# names; COSTM01 and UNUSED1Y also exist in app/cpy and are record layouts.
TICKET_COPYBOOKS = [
    "CUSTREC", "CVCUS01Y", "CVACT01Y", "CVACT02Y", "CVACT03Y", "CVCRD01Y",
    "CVTRA01Y", "CVTRA02Y", "CVTRA03Y", "CVTRA04Y", "CVTRA05Y", "CVTRA06Y",
    "CVTRA07Y", "CSUSR01Y", "CVEXPORT",
]
EXTRA_COPYBOOKS = ["COSTM01", "UNUSED1Y"]
LAYOUT_COPYBOOKS = TICKET_COPYBOOKS + EXTRA_COPYBOOKS

# app/cpy members that are not file record layouts, with the reason (checked by
# reading each member; verify_layouts.py asserts every app/cpy file is classified).
NON_LAYOUT_COPYBOOKS = {
    "COADM02Y": "admin menu option table (working storage)",
    "COCOM01Y": "CICS COMMAREA (CARDDEMO-COMMAREA)",
    "CODATECN": "date-conversion parameter area (copybook, not a program)",
    "COMEN02Y": "main menu option table (working storage)",
    "COTTL01Y": "screen title literals",
    "CSDAT01Y": "current date/time working storage",
    "CSLKPCDY": "phone area code / state / zip lookup tables with 88-levels",
    "CSMSG01Y": "common screen messages",
    "CSMSG02Y": "abend data work area",
    "CSSETATY": "BMS attribute-setting PROCEDURE DIVISION snippet (COPY ... REPLACING)",
    "CSSTRPFY": "PF-key storing PROCEDURE DIVISION snippet",
    "CSUTLDPY": "date-validation PROCEDURE DIVISION paragraphs",
    "CSUTLDWY": "date-validation working storage",
}

# Which dataset each record is stored in, with evidence from the source repo.
# Each evidence item is (path, line, text); the generator asserts that `text`
# appears on that line so citations cannot silently drift.
DATASETS = {
    ("CVACT01Y", "ACCOUNT-RECORD"): [{
        "dsn": "AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS", "cics_file": "ACCTDAT",
        "sample": "app/data/EBCDIC/AWS.M2.CARDDEMO.ACCTDATA.PS",
        "evidence": [("app/cbl/CBACT01C.cbl", 166, "READ ACCTFILE-FILE INTO ACCOUNT-RECORD"),
                     ("app/jcl/READACCT.jcl", 36, "DSN=AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS"),
                     ("app/csd/CARDDEMO.CSD", 2, "DSNAME(AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS)")]}],
    ("CVACT02Y", "CARD-RECORD"): [{
        "dsn": "AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS", "cics_file": "CARDDAT (CARDAIX via AIX path)",
        "sample": "app/data/EBCDIC/AWS.M2.CARDDEMO.CARDDATA.PS",
        "evidence": [("app/cbl/CBACT02C.cbl", 93, "READ CARDFILE-FILE INTO CARD-RECORD"),
                     ("app/jcl/READCARD.jcl", 26, "DSN=AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS"),
                     ("app/csd/CARDDEMO.CSD", 26, "DSNAME(AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS)"),
                     ("app/csd/CARDDEMO.CSD", 14, "DSNAME(AWS.M2.CARDDEMO.CARDDATA.VSAM.AIX.PATH)")]}],
    ("CVACT03Y", "CARD-XREF-RECORD"): [{
        "dsn": "AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS", "cics_file": "CCXREF (CXACAIX via AIX path)",
        "sample": "app/data/EBCDIC/AWS.M2.CARDDEMO.CARDXREF.PS",
        "evidence": [("app/cbl/CBACT03C.cbl", 93, "READ XREFFILE-FILE INTO CARD-XREF-RECORD"),
                     ("app/jcl/READXREF.jcl", 26, "DSN=AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS"),
                     ("app/csd/CARDDEMO.CSD", 39, "DSNAME(AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS)"),
                     ("app/csd/CARDDEMO.CSD", 65, "DSNAME(AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.PATH)")]}],
    ("CVCUS01Y", "CUSTOMER-RECORD"): [{
        "dsn": "AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS", "cics_file": "CUSTDAT",
        "sample": "app/data/EBCDIC/AWS.M2.CARDDEMO.CUSTDATA.PS",
        "evidence": [("app/cbl/CBCUS01C.cbl", 93, "READ CUSTFILE-FILE INTO CUSTOMER-RECORD"),
                     ("app/jcl/READCUST.jcl", 25, "DSN=AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS"),
                     ("app/csd/CARDDEMO.CSD", 52, "DSNAME(AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS)")]}],
    ("CUSTREC", "CUSTOMER-RECORD"): [{
        "dsn": "AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS", "cics_file": None,
        "sample": "app/data/EBCDIC/AWS.M2.CARDDEMO.CUSTDATA.PS",
        "evidence": [("app/cbl/CBSTM03A.CBL", 388, "MOVE WS-M03B-FLDT TO CUSTOMER-RECORD"),
                     ("app/jcl/CREASTMT.JCL", 86, "CUSTFILE DD  DISP=SHR,DSN=AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS")]}],
    ("CVTRA01Y", "TRAN-CAT-BAL-RECORD"): [{
        "dsn": "AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS", "cics_file": None,
        "sample": "app/data/EBCDIC/AWS.M2.CARDDEMO.TCATBALF.PS",
        "evidence": [("app/cbl/CBACT04C.cbl", 326, "READ TCATBAL-FILE INTO TRAN-CAT-BAL-RECORD"),
                     ("app/jcl/INTCALC.jcl", 28, "DSN=AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS")]}],
    ("CVTRA02Y", "DIS-GROUP-RECORD"): [{
        "dsn": "AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS", "cics_file": None,
        "sample": "app/data/EBCDIC/AWS.M2.CARDDEMO.DISCGRP.PS",
        "evidence": [("app/cbl/CBACT04C.cbl", 416, "READ DISCGRP-FILE INTO DIS-GROUP-RECORD"),
                     ("app/jcl/INTCALC.jcl", 36, "DSN=AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS")]}],
    ("CVTRA03Y", "TRAN-TYPE-RECORD"): [{
        "dsn": "AWS.M2.CARDDEMO.TRANTYPE.VSAM.KSDS", "cics_file": None,
        "sample": "app/data/EBCDIC/AWS.M2.CARDDEMO.TRANTYPE.PS",
        "evidence": [("app/cbl/CBTRN03C.cbl", 495, "READ TRANTYPE-FILE INTO TRAN-TYPE-RECORD"),
                     ("app/jcl/TRANREPT.jcl", 70, "DSN=AWS.M2.CARDDEMO.TRANTYPE.VSAM.KSDS")]}],
    ("CVTRA04Y", "TRAN-CAT-RECORD"): [{
        "dsn": "AWS.M2.CARDDEMO.TRANCATG.VSAM.KSDS", "cics_file": None,
        "sample": "app/data/EBCDIC/AWS.M2.CARDDEMO.TRANCATG.PS",
        "evidence": [("app/cbl/CBTRN03C.cbl", 505, "READ TRANCATG-FILE INTO TRAN-CAT-RECORD"),
                     ("app/jcl/TRANREPT.jcl", 72, "DSN=AWS.M2.CARDDEMO.TRANCATG.VSAM.KSDS")]}],
    ("CVTRA05Y", "TRAN-RECORD"): [{
        "dsn": "AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS", "cics_file": "TRANSACT",
        "sample": None,
        "evidence": [("app/cbl/CBTRN02C.cbl", 564, "WRITE FD-TRANFILE-REC FROM TRAN-RECORD"),
                     ("app/jcl/POSTTRAN.jcl", 29, "DSN=AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS"),
                     ("app/csd/CARDDEMO.CSD", 77, "DSNAME(AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS)")]}],
    ("CVTRA06Y", "DALYTRAN-RECORD"): [{
        "dsn": "AWS.M2.CARDDEMO.DALYTRAN.PS", "cics_file": None, "sequential": True,
        "sample": "app/data/EBCDIC/AWS.M2.CARDDEMO.DALYTRAN.PS",
        "evidence": [("app/cbl/CBTRN02C.cbl", 346, "READ DALYTRAN-FILE INTO DALYTRAN-RECORD"),
                     ("app/jcl/POSTTRAN.jcl", 31, "DSN=AWS.M2.CARDDEMO.DALYTRAN.PS")]}],
    ("CSUSR01Y", "SEC-USER-DATA"): [{
        "dsn": "AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS", "cics_file": "USRSEC",
        "sample": "app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS",
        "evidence": [("app/cbl/COSGN00C.cbl", 213, "INTO      (SEC-USER-DATA)"),
                     ("app/cbl/COSGN00C.cbl", 39, "VALUE 'USRSEC  '"),
                     ("app/csd/CARDDEMO.CSD", 89, "DSNAME(AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS)")]}],
    ("CVEXPORT", "EXPORT-RECORD"): [{
        "dsn": "AWS.M2.CARDDEMO.EXPORT.DATA", "cics_file": None,
        "sample": "app/data/EBCDIC/AWS.M2.CARDDEMO.EXPORT.DATA.PS",
        "evidence": [("app/cbl/CBEXPORT.cbl", 301, "WRITE EXPORT-OUTPUT-RECORD FROM EXPORT-RECORD"),
                     ("app/cbl/CBIMPORT.cbl", 261, "READ EXPORT-INPUT INTO EXPORT-RECORD"),
                     ("app/jcl/CBEXPORT.jcl", 63, "DSN=AWS.M2.CARDDEMO.EXPORT.DATA")]}],
    ("COSTM01", "TRNX-RECORD"): [{
        "dsn": "AWS.M2.CARDDEMO.TRXFL.VSAM.KSDS", "cics_file": None, "sample": None,
        "evidence": [("app/cbl/CBSTM03A.CBL", 756, "MOVE WS-M03B-FLDT TO TRNX-RECORD"),
                     ("app/jcl/CREASTMT.JCL", 83, "TRNXFILE DD  DISP=SHR,DSN=AWS.M2.CARDDEMO.TRXFL.VSAM.KSDS")]}],
}
# Print lines of the daily transaction report (not a VSAM file).
REPORT_FILE = {
    "dsn": "AWS.M2.CARDDEMO.TRANREPT(+1)", "lrecl": 133, "sequential": True, "cics_file": None,
    "sample": None,
    "evidence": [("app/cbl/CBTRN03C.cbl", 85, "01 FD-REPTFILE-REC       PIC X(133)"),
                 ("app/jcl/TRANREPT.jcl", 78, "LRECL=133"),
                 ("app/jcl/TRANREPT.jcl", 80, "DSN=AWS.M2.CARDDEMO.TRANREPT(+1)")]}
for _rec in ["REPORT-NAME-HEADER", "TRANSACTION-DETAIL-REPORT", "TRANSACTION-HEADER-1",
             "TRANSACTION-HEADER-2", "REPORT-PAGE-TOTALS", "REPORT-ACCOUNT-TOTALS",
             "REPORT-GRAND-TOTALS"]:
    DATASETS[("CVTRA07Y", _rec)] = [REPORT_FILE]

# CUSTREC.cpy lines 6-22 start with tab characters. With width 3 they line up
# with lines 1-5 exactly (inferred author intent); with cobc's default width 8
# the PIC clauses run past column 72 and the member does not compile.
# verify_layouts.py compiles with the matching -ftab-width.
TAB_WIDTH = 3

USAGE_WORDS = {
    "DISPLAY": "DISPLAY", "COMP": "COMP", "COMPUTATIONAL": "COMP", "BINARY": "COMP",
    "COMP-4": "COMP", "COMPUTATIONAL-4": "COMP", "COMP-5": "COMP-5", "COMPUTATIONAL-5": "COMP-5",
    "COMP-3": "COMP-3", "COMPUTATIONAL-3": "COMP-3", "PACKED-DECIMAL": "COMP-3",
}
FIGURATIVE = {"SPACE": "SPACES", "SPACES": "SPACES", "ZERO": "ZEROS", "ZEROS": "ZEROS",
              "ZEROES": "ZEROS", "LOW-VALUE": "LOW-VALUES", "LOW-VALUES": "LOW-VALUES",
              "HIGH-VALUE": "HIGH-VALUES", "HIGH-VALUES": "HIGH-VALUES"}
TOKEN_RE = re.compile(r"'[^']*'|\"[^\"]*\"|[^\s'\"]+")


# --------------------------------------------------------------------------- parsing
def source_lines(path: Path, allow_continuation: bool = False):
    """Yield (line_no, text) for cols 8-72 of non-comment fixed-format lines."""
    for no, line in enumerate(path.read_bytes().decode("latin-1").splitlines(), 1):
        line = line.expandtabs(TAB_WIDTH)
        if len(line) < 7:
            continue
        indicator = line[6]
        if indicator in "*/":
            continue
        if indicator == "-" and not allow_continuation:
            raise ValueError(f"{path}:{no}: continuation lines are not supported")
        yield no, line[7:72]


def tokenize(path: Path):
    toks = []
    for no, text in source_lines(path):
        for t in TOKEN_RE.findall(text):
            if t[0] not in "'\"" and len(t) > 1 and t.endswith("."):
                toks += [(no, t[:-1]), (no, ".")]
            else:
                toks.append((no, t))
    return toks


def header_reclen(path: Path):
    for line in path.read_bytes().decode("latin-1").splitlines()[:10]:
        m = re.search(r"RECLN\s*=?\s*(\d+)", line)
        if m:
            return int(m.group(1))
    return None


def literal(tok: str):
    if tok[0] in "'\"":
        return tok[1:-1]
    up = tok.upper()
    if up in FIGURATIVE:
        return FIGURATIVE[up]
    if re.fullmatch(r"[+-]?\d+(\.\d+)?", tok):
        return tok
    raise ValueError(f"unexpected literal {tok!r}")


def parse_entries(path: Path):
    toks, entries, cur = tokenize(path), [], []
    for no, t in toks:
        if t == ".":
            if cur:
                entries.append(cur)
            cur = []
        else:
            cur.append((no, t))
    if cur:
        raise ValueError(f"{path}: unterminated entry {cur}")
    out = []
    for e in entries:
        line, level = e[0][0], e[0][1]
        if not level.isdigit():
            raise ValueError(f"{path}:{line}: expected level number, got {level!r}")
        words = [t for _, t in e[1:]]
        item = {"level": int(level), "line": line, "name": None, "pic": None, "usage": None,
                "occurs": None, "redefines": None, "value": None, "values": None}
        i = 0
        if words and words[0].upper() not in ("PIC", "PICTURE", "REDEFINES", "VALUE", "VALUES",
                                               "OCCURS", "USAGE") | USAGE_WORDS.keys():
            item["name"], i = words[0].upper(), 1
        while i < len(words):
            w = words[i].upper()
            if w in ("PIC", "PICTURE"):
                i += 1
                if words[i].upper() == "IS":
                    i += 1
                item["pic"] = words[i].upper()
                i += 1
            elif w == "USAGE":
                i += 1
                if words[i].upper() == "IS":
                    i += 1
                item["usage"] = USAGE_WORDS[words[i].upper()]
                i += 1
            elif w in USAGE_WORDS:
                item["usage"] = USAGE_WORDS[w]
                i += 1
            elif w == "REDEFINES":
                item["redefines"] = words[i + 1].upper()
                i += 2
            elif w == "OCCURS":
                item["occurs"] = int(words[i + 1])
                i += 2
                if i < len(words) and words[i].upper() == "TIMES":
                    i += 1
            elif w in ("VALUE", "VALUES"):
                i += 1
                if i < len(words) and words[i].upper() in ("IS", "ARE"):
                    i += 1
                vals = []
                while i < len(words):
                    v = words[i]
                    if v.upper() == "ALL":
                        vals.append("ALL " + literal(words[i + 1]))
                        i += 2
                    elif v.upper() in ("THRU", "THROUGH"):
                        vals[-1] = {"from": vals[-1], "thru": literal(words[i + 1])}
                        i += 2
                    else:
                        try:
                            vals.append(literal(v))
                        except ValueError:
                            break
                        i += 1
                if item["level"] == 88:
                    item["values"] = vals
                else:
                    (item["value"],) = vals
            else:
                raise ValueError(f"{path}:{line}: unsupported clause {words[i]!r} in {words}")
        out.append(item)
    return out


def analyze_pic(pic: str, usage: str):
    exp = re.sub(r"(.)\((\d+)\)", lambda m: m.group(1) * int(m.group(2)), pic)
    signed = exp.startswith("S")
    if set(exp) <= {"X"}:
        cat = "alphanumeric"
    elif set(exp) <= {"A"}:
        cat = "alphabetic"
    elif set(exp) <= set("9SVP"):
        cat = "numeric"
    else:
        cat = "numeric-edited"
    digits = scale = None
    if cat == "numeric":
        digits = exp.count("9")
        scale = exp.split("V")[1].count("9") if "V" in exp else 0
        display_len = len(exp.replace("S", "").replace("V", "").replace("P", ""))
    else:
        display_len = len(exp.replace("V", ""))
        if cat == "numeric-edited":
            digits = sum(exp.count(c) for c in "9Z")
            scale = sum(exp.split(".")[1].count(c) for c in "9Z") if "." in exp else 0
            signed = any(c in exp for c in "+-") or exp.endswith(("CR", "DB"))
    if usage in ("COMP", "COMP-5"):
        if cat != "numeric":
            raise ValueError(f"binary usage on non-numeric PIC {pic}")
        length = 2 if digits <= 4 else 4 if digits <= 9 else 8
        sign_enc = "binary two's complement, big-endian" if signed else "binary unsigned, big-endian"
    elif usage == "COMP-3":
        length = digits // 2 + 1
        sign_enc = "packed decimal, sign nibble C/D (F if unsigned)"
    else:
        length = display_len
        if cat == "numeric" and signed:
            sign_enc = "zoned decimal, trailing overpunch in last byte (zone C=+, D=-)"
        elif cat == "numeric":
            sign_enc = "zoned decimal, unsigned (zone F)"
        elif cat == "numeric-edited":
            sign_enc = "edited (sign is a printed character)" if signed else None
        else:
            sign_enc = None
    return {"category": cat, "signed": signed, "digits": digits, "scale": scale,
            "length": length, "sign_encoding": sign_enc}


def build_records(path: Path):
    entries = parse_entries(path)
    records, stack, last_data = [], [], None
    for e in entries:
        if e["level"] == 88:
            last_data["level_88"].append({"name": e["name"], "values": e["values"], "line": e["line"]})
            continue
        if e["level"] in (66, 77):
            raise ValueError(f"{path}:{e['line']}: level {e['level']} not supported")
        node = dict(e, children=[], level_88=[])
        node.pop("values")
        if e["level"] == 1:
            records.append(node)
            stack = [node]
        else:
            while stack and stack[-1]["level"] >= e["level"]:
                stack.pop()
            stack[-1]["children"].append(node)
            node["parent"] = stack[-1]
            stack.append(node)
        last_data = node
    for r in records:
        layout(r, 0, r.get("usage"))
    return records


def layout(node, offset, inherited_usage):
    node["offset"] = offset
    usage = node["usage"] or inherited_usage
    if not node["children"]:
        if not node["pic"]:
            raise ValueError(f"elementary item {node['name']} has no PIC")
        node["usage"] = usage or "DISPLAY"
        node.update(analyze_pic(node["pic"], node["usage"]))
        return
    if node["pic"]:
        raise ValueError(f"group {node['name']} has a PIC")
    cur = end_max = offset
    by_name = {}
    for c in node["children"]:
        if c["redefines"]:
            target = by_name[c["redefines"]]
            start = target["offset"]
        else:
            start = cur
        layout(c, start, usage)
        end = start + c["length"] * (c["occurs"] or 1)
        if c["redefines"]:
            tgt_end = target["offset"] + target["length"] * (target["occurs"] or 1)
            if end > tgt_end:
                raise ValueError(f"{c['name']} is larger than the item it redefines")
        else:
            cur = end
        end_max = max(end_max, end)
        if c["name"]:
            by_name[c["name"]] = c
    node.update({"category": "group", "signed": False, "digits": None, "scale": None,
                 "length": end_max - offset, "sign_encoding": None,
                 "usage": node["usage"]})


def flatten(record):
    rows = []

    def walk(node, parent_name, occurs_path, redefines_ctx):
        occ = list(occurs_path)
        if node["occurs"]:
            occ.append({"name": node["name"], "times": node["occurs"], "stride": node["length"]})
        ctx = node["name"] if node["redefines"] else redefines_ctx
        rows.append({
            "level": node["level"],
            "name": node["name"] or "FILLER",
            "filler": (node["name"] or "FILLER") == "FILLER",
            "parent": parent_name,
            "line": node["line"],
            "type": node["category"],
            "pic": node["pic"],
            "usage": node["usage"] if node["category"] != "group" else None,
            "offset": node["offset"],
            "length": node["length"],
            "total_length": node["length"] * (node["occurs"] or 1),
            "signed": node["signed"],
            "sign_encoding": node["sign_encoding"],
            "digits": node["digits"],
            "scale": node["scale"],
            "occurs": node["occurs"],
            "occurs_path": occ,
            "redefines": node["redefines"],
            "in_redefines": ctx,
            "value": node["value"],
            "level_88": [{"name": v["name"], "values": v["values"]} for v in node["level_88"]],
        })
        for c in node["children"]:
            walk(c, node["name"], occ, ctx)

    walk(record, None, [], None)
    return rows


# --------------------------------------------------------------------------- IDCAMS
def parse_idcams(src: Path):
    files = sorted(p for p in (src / "app/jcl").iterdir() if p.is_file())
    defines = []
    for jcl in files:
        lines = jcl.read_bytes().decode("latin-1").splitlines()
        text_parts, line_of = [], []
        for no, line in enumerate(lines, 1):
            if line.startswith("//") or line.startswith("/*"):
                text_parts.append(" ; ")
                line_of.append(no)
                continue
            body = line[:72].rstrip()
            if body.endswith("-"):
                body = body[:-1]
            text_parts.append(" " + body + " ")
            line_of.append(no)
        # Map character offset -> line number
        text, starts = "", []
        for part, no in zip(text_parts, line_of):
            starts.append((len(text), no))
            text += part
        for m in re.finditer(r"\bDEFINE\s+(CLUSTER|ALTERNATEINDEX|AIX|PATH|GENERATIONDATAGROUP|GDG)\b", text):
            end = len(text)
            nxt = re.search(r"\b(DEFINE|DELETE|REPRO|SET|IF|BLDINDEX|PRINT|LISTCAT)\b|;", text[m.end():])
            if nxt:
                end = m.end() + nxt.start()
            body = text[m.start():end]
            line = max(no for pos, no in starts if pos <= m.start())
            kind = {"AIX": "ALTERNATEINDEX", "GDG": "GENERATIONDATAGROUP"}.get(m.group(1), m.group(1))
            d = {"kind": kind, "jcl": f"app/jcl/{jcl.name}", "line": line,
                 "name": re.search(r"NAME\s*\(\s*([^)\s]+)", body).group(1)}
            if km := re.search(r"\bKEYS\s*\(\s*(\d+)[\s,]+(\d+)", body):
                d["keys"] = {"length": int(km.group(1)), "offset": int(km.group(2))}
            if rm := re.search(r"\bRECORDSIZE\s*\(\s*(\d+)[\s,]+(\d+)", body):
                d["recordsize"] = {"avg": int(rm.group(1)), "max": int(rm.group(2))}
            for org in ("NONINDEXED", "INDEXED", "NUMBERED", "LINEAR"):
                if re.search(rf"\b{org}\b", body):
                    d["organization"] = {"INDEXED": "KSDS", "NONINDEXED": "ESDS",
                                         "NUMBERED": "RRDS", "LINEAR": "LDS"}[org]
                    break
            if rel := re.search(r"\bRELATE\s*\(\s*([^)\s]+)", body):
                d["relate"] = rel.group(1)
            if pe := re.search(r"\bPATHENTRY\s*\(\s*([^)\s]+)", body):
                d["pathentry"] = pe.group(1)
            if re.search(r"\bNONUNIQUEKEY\b", body):
                d["unique_key"] = False
            elif re.search(r"\bUNIQUEKEY\b", body):
                d["unique_key"] = True
            if lim := re.search(r"\bLIMIT\s*\(\s*(\d+)", body):
                d["limit"] = int(lim.group(1))
            defines.append(d)
    return defines


def parse_csd(src: Path):
    files = {}
    lines = (src / "app/csd/CARDDEMO.CSD").read_bytes().decode("latin-1").splitlines()
    cur = None
    for no, line in enumerate(lines, 1):
        if m := re.search(r"DEFINE FILE\((\w+)\)", line):
            cur = m.group(1)
        elif re.search(r"DEFINE \w+\(", line):
            cur = None
        if cur and (m := re.search(r"DSNAME\(([^)]+)\)", line)):
            files.setdefault(m.group(1), []).append({"file": cur, "line": no})
    return files


# --------------------------------------------------------------------------- COPY usage
def copy_usage(src: Path):
    usage = {}
    progs = git_ls(src, "app/cbl")
    for rel in progs:
        p = src / rel
        for no, text in source_lines(p, allow_continuation=True):
            for m in re.finditer(r"\bCOPY\s+'?([A-Z0-9#@$-]+)'?", text.upper()):
                usage.setdefault(m.group(1), []).append({"program": p.stem.upper(), "file": rel, "line": no})
    return usage


def git_ls(src: Path, path: str):
    out = subprocess.run(["git", "-C", str(src), "ls-files", path], check=True,
                         capture_output=True, text=True).stdout.split()
    return out


def check_evidence(src: Path, ev):
    path, line, text = ev
    actual = (src / path).read_bytes().decode("latin-1").splitlines()[line - 1]
    norm = lambda s: re.sub(r"\s+", " ", s).strip()
    if norm(text) not in norm(actual):
        raise AssertionError(f"evidence drift: {path}:{line} does not contain {text!r}: {actual!r}")
    return {"file": path, "line": line, "text": norm(text)}


def key_fields(rows, off, length):
    exact = [r["name"] for r in rows if r["offset"] == off and r["total_length"] == length
             and not r["occurs_path"] and not r["in_redefines"]]
    overlap = [r["name"] for r in rows if r["type"] != "group" and not r["occurs_path"]
               and not r["in_redefines"] and r["offset"] < off + length
               and r["offset"] + r["total_length"] > off]
    return exact, overlap


# --------------------------------------------------------------------------- main
def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--source", required=True, type=Path, help="path to the carddemo source checkout")
    args = ap.parse_args()
    src = args.source.resolve()
    commit = subprocess.run(["git", "-C", str(src), "rev-parse", "HEAD"], check=True,
                            capture_output=True, text=True).stdout.strip()
    cpy_files = {Path(p).stem.upper(): p for p in git_ls(src, "app/cpy")}
    idcams = parse_idcams(src)
    by_dsn = {}
    for d in idcams:
        by_dsn.setdefault(d["name"], []).append(d)
    aix_for = {}
    for d in idcams:
        if d["kind"] == "ALTERNATEINDEX":
            aix_for.setdefault(d["relate"], []).append(d)
    csd = parse_csd(src)
    usage = copy_usage(src)

    LAYOUT_DIR.mkdir(parents=True, exist_ok=True)
    index = []
    for cb in LAYOUT_COPYBOOKS:
        rel = cpy_files[cb]
        path = src / rel
        recs = build_records(path)
        spec = {
            "copybook": cb,
            "source_file": rel,
            "source_repo": "ipsorakis/aws-mainframe-modernization-carddemo",
            "source_commit": commit,
            "generator": "docs/inventory/copybooks/tools/gen_layouts.py",
            "conventions": {
                "offset": "0-based byte offset from the start of the 01 record",
                "length": "bytes of ONE occurrence; total_length = length * occurs",
                "occurs_path": "OCCURS ancestors (incl. self): byte offset of element "
                               "[i1,i2..] = offset + sum(i_k * stride_k), i_k 0-based",
                "in_redefines": "name of the nearest REDEFINES item enclosing this field "
                                "(fields in different variants overlap)",
                "encoding": "EBCDIC (app/data/EBCDIC); code page not stated in the source, "
                            "cp037 assumed (inferred)",
            },
            "header_comment_reclen": header_reclen(path),
            "tab_characters": "\t" in path.read_bytes().decode("latin-1"),
            "copied_by": [{"program": u["program"], "file": u["file"], "line": u["line"]}
                          for u in usage.get(cb, [])],
            "records": [],
        }
        for r in recs:
            rows = flatten(r)
            datasets = []
            for ds in DATASETS.get((cb, r["name"]), []):
                sample = None
                if ds.get("sample"):
                    size = (src / ds["sample"]).stat().st_size
                    sample = {"file": ds["sample"], "bytes": size,
                              "records": size // r["length"], "whole_records": size % r["length"] == 0}
                entry = {"dsn": ds["dsn"], "organization": None, "cics_file": ds.get("cics_file"),
                         "sample_data": sample,
                         "evidence": [check_evidence(src, e) for e in ds["evidence"]],
                         "idcams": [], "alternate_indexes": [], "match": None, "notes": []}
                if ds.get("sequential"):
                    entry["organization"] = "sequential (no IDCAMS DEFINE)"
                    if "lrecl" in ds:
                        entry["lrecl"] = ds["lrecl"]
                        entry["match"] = r["length"] <= ds["lrecl"]
                        entry["notes"].append(
                            f"record is {r['length']} bytes, moved into the {ds['lrecl']}-byte "
                            f"FD-REPTFILE-REC (padded with spaces) before WRITE")
                for d in by_dsn.get(ds["dsn"], []):
                    entry["organization"] = d.get("organization")
                    ok_len = d["recordsize"]["max"] == r["length"] == d["recordsize"]["avg"]
                    exact, overlap = key_fields(rows, d["keys"]["offset"], d["keys"]["length"]) \
                        if "keys" in d else ([], [])
                    entry["idcams"].append({**{k: d[k] for k in ("jcl", "line", "recordsize") if k in d},
                                            "keys": d.get("keys"), "key_fields": exact,
                                            "key_overlaps": overlap, "length_match": ok_len,
                                            "key_match": bool(exact) if "keys" in d else None})
                for a in aix_for.get(ds["dsn"], []):
                    exact, overlap = key_fields(rows, a["keys"]["offset"], a["keys"]["length"])
                    entry["alternate_indexes"].append(
                        {"name": a["name"], "jcl": a["jcl"], "line": a["line"], "keys": a["keys"],
                         "unique_key": a.get("unique_key"), "key_fields": exact,
                         "key_overlaps": overlap, "key_match": bool(exact),
                         "paths": [p["name"] for p in idcams
                                   if p["kind"] == "PATH" and p.get("pathentry") == a["name"]]})
                if entry["idcams"]:
                    entry["match"] = all(i["length_match"] and i["key_match"] for i in entry["idcams"]) \
                        and all(a["key_match"] for a in entry["alternate_indexes"])
                entry["cics_files"] = csd.get(ds["dsn"], []) + [
                    c for a in aix_for.get(ds["dsn"], []) for p in idcams
                    if p["kind"] == "PATH" and p.get("pathentry") == a["name"]
                    for c in csd.get(p["name"], [])]
                datasets.append(entry)
            spec["records"].append({"name": r["name"], "line": r["line"], "length": r["length"],
                                    "datasets": datasets, "fields": rows})
        (LAYOUT_DIR / f"{cb}.json").write_text(json.dumps(spec, indent=2) + "\n")
        index.append({"copybook": cb, "source_file": rel, "in_ticket": cb in TICKET_COPYBOOKS,
                      "header_comment_reclen": spec["header_comment_reclen"],
                      "records": [{"name": r["name"], "length": r["length"],
                                   "datasets": [{k: d[k] for k in ("dsn", "organization", "match")}
                                                | {"recordsize": [i["recordsize"] for i in d["idcams"]],
                                                   "keys": [i["keys"] for i in d["idcams"]],
                                                   "key_fields": [i["key_fields"] or i["key_overlaps"]
                                                                  for i in d["idcams"]],
                                                   "length_match": [i["length_match"] for i in d["idcams"]],
                                                   "key_match": [i["key_match"] for i in d["idcams"]],
                                                   "lrecl": d.get("lrecl"),
                                                   "aix": [(a["keys"], a["key_fields"] or a["key_overlaps"],
                                                            a["key_match"]) for a in d["alternate_indexes"]],
                                                   "sample_data": d["sample_data"],
                                                   "idcams_at": [f"{i['jcl']}:{i['line']}" for i in d["idcams"]]}
                                                for d in r["datasets"]]}
                                  for r in spec["records"]],
                      "copied_by": sorted({u["program"] for u in spec["copied_by"]})})
    (LAYOUT_DIR / "_index.json").write_text(json.dumps({
        "source_commit": commit, "copybooks": index,
        "non_layout_copybooks": NON_LAYOUT_COPYBOOKS,
        "idcams_defines": idcams}, indent=2) + "\n")
    write_readme(index, idcams, usage)
    print(f"wrote {len(index)} layout specs to {LAYOUT_DIR}")


def write_readme(index, idcams, usage):
    readme = OUT_DIR / "README.md"
    text = readme.read_text() if readme.exists() else ""
    yn = {True: "yes", False: "**no**", None: "n/a"}
    rows = ["| Copybook | Record | Length | Dataset (DSN) | IDCAMS RECORDSIZE / KEYS (len off) -> key field | "
            "Length match | Key match | Programs that COPY it |",
            "|---|---|---:|---|---|---|---|---|"]
    samples = ["| Copybook | Record | Sample file | Bytes | Records | Whole records |", "|---|---|---|---:|---:|---|"]
    for c in index:
        for r in c["records"]:
            dsn, rk, lm, km = [], [], [], []
            for d in r["datasets"]:
                dsn.append(f"`{d['dsn']}`")
                parts = sorted({f"RECORDSIZE({s['avg']} {s['max']}) KEYS({k['length']} {k['offset']}) -> "
                                f"{'/'.join(kf) or '?'}" for s, k, kf in
                                zip(d["recordsize"], d["keys"], d["key_fields"])})
                parts += [f"AIX KEYS({k['length']} {k['offset']}) -> {'/'.join(kf)}" for k, kf, _ in
                          {(json.dumps(a[0]), tuple(a[1]), a[2]): a for a in d["aix"]}.values()]
                if not parts:
                    parts = [d["organization"] + (f", LRECL {d['lrecl']}" if d.get("lrecl") else "")]
                rk.append("<br>".join(parts))
                if d["length_match"]:
                    lm.append(yn[all(d["length_match"])])
                    km.append(yn[all(d["key_match"]) and all(a[2] for a in d["aix"])])
                elif d.get("lrecl"):
                    lm.append(f"fits in {d['lrecl']}")
                    km.append("n/a")
                else:
                    lm.append("n/a (no RECORDSIZE)")
                    km.append("n/a")
                sd = d["sample_data"]
                if sd:
                    samples.append(f"| {c['copybook']} | {r['name']} | `{sd['file']}` | {sd['bytes']} | "
                                   f"{sd['records']} | {yn[sd['whole_records']]} |")
            progs = ", ".join(c["copied_by"]) or "none"
            rows.append(f"| [{c['copybook']}](layouts/{c['copybook']}.json) | {r['name']} | {r['length']} "
                        f"| {'<br>'.join(dsn) or 'none (working storage only)'} | {'<br>'.join(rk) or 'n/a'} "
                        f"| {'<br>'.join(lm) or 'n/a'} | {'<br>'.join(km) or 'n/a'} | {progs} |")
    clusters = ["| DSN | Kind | Org | RECORDSIZE | KEYS (len off) | Defined in |", "|---|---|---|---|---|---|"]
    for d in idcams:
        rs = f"{d['recordsize']['avg']} {d['recordsize']['max']}" if "recordsize" in d else ""
        ks = f"{d['keys']['length']} {d['keys']['offset']}" if "keys" in d else ""
        clusters.append(f"| `{d['name']}` | {d['kind']} | {d.get('organization', '')} | {rs} | {ks} "
                        f"| {d['jcl']}:{d['line']} |")
    for marker, body in (("summary", rows), ("idcams", clusters), ("samples", samples)):
        begin, end = f"<!-- BEGIN GENERATED: {marker} -->", f"<!-- END GENERATED: {marker} -->"
        block = begin + "\n" + "\n".join(body) + "\n" + end
        if begin in text:
            text = re.sub(re.escape(begin) + r".*?" + re.escape(end), lambda _: block, text, flags=re.S)
        else:
            text += "\n" + block + "\n"
    readme.write_text(text)


if __name__ == "__main__":
    main()
