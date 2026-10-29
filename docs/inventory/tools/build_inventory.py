#!/usr/bin/env python3
"""Build the CardDemo source inventory (BNP-2).

Scans a local checkout of ipsorakis/aws-mainframe-modernization-carddemo and writes
inventory.csv, dependencies.csv and the generated blocks of README.md under
docs/inventory/. Python 3 standard library only.

Usage:
    python3 docs/inventory/tools/build_inventory.py <source-checkout> [--out DIR]
    python3 docs/inventory/tools/build_inventory.py <source-checkout> --check

--check only verifies coverage: every git-tracked file in the scanned folders must
have exactly one row in inventory.csv (exit code 1 otherwise).
"""

import argparse
import collections
import csv
import io
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

SCANNED_FOLDERS = [
    # folder, inventory type
    ("app/cbl", "program"),
    ("app/cpy", "copybook"),
    ("app/bms", "bms"),
    ("app/jcl", "jcl"),
    ("app/scheduler", "scheduler"),
    ("app/csd", "csd"),
    ("app/cpy-bms", "copybook"),
    ("app/data", "data"),
    ("app/proc", "proc"),
    ("app/ctl", "control"),
    ("app/asm", "assembler"),
    ("app/maclib", "macro"),
    ("app/catlg", "catalog"),
]

# Counts and claims written in Jira BNP-2 (compared against the checkout).
TICKET_COUNTS = {
    "app/cbl": 31,
    "app/cpy": 30,
    "app/bms": 17,
    "app/jcl": 38,
    "app/scheduler": 2,
}

INVENTORY_COLUMNS = ["path", "type", "name", "category", "purpose", "lines", "bytes"]
DEPENDENCY_COLUMNS = ["from", "to", "kind", "from_type", "to_type", "record_copybook", "detail", "source"]

BOILERPLATE = re.compile(
    r"copyright|all rights reserved|licen[cs]e|warrant|apache|may not use|obtain a copy|"
    r"required by applicable|\"as is\"|either express|^ver:|cardddemo_v|carddemo_v",
    re.I,
)
SDSF_CEMT = re.compile(r"CEMT\s+SET\s+FIL\(\s*([A-Z0-9#@$]+)\s*\)\s+(OPE|CLO)", re.I)
CICS_FILE_COMMANDS = {"READ", "WRITE", "REWRITE", "DELETE", "STARTBR", "READNEXT", "READPREV", "ENDBR", "RESETBR", "UNLOCK"}
CICS_FILE_KIND = {
    "READ": "CICS READ", "STARTBR": "CICS READ", "READNEXT": "CICS READ", "READPREV": "CICS READ",
    "RESETBR": "CICS READ", "ENDBR": "CICS READ", "UNLOCK": "CICS READ",
    "WRITE": "CICS WRITE", "REWRITE": "CICS REWRITE", "DELETE": "CICS DELETE",
}
SYSTEM_DDS = {"STEPLIB", "JOBLIB", "SYSOUT", "SYSPRINT", "SYSUDUMP", "CEEDUMP", "SYSABOUT", "SYSDBOUT"}


# --------------------------------------------------------------------------- files
def list_files(src):
    """Return the sorted repo-relative paths of every file to inventory.

    Lists the files git tracks under SCANNED_FOLDERS in the source checkout `src`
    (`git ls-files`, so untracked build output is ignored). Paths use forward
    slashes, e.g. "app/cbl/COSGN00C.cbl". Tracked files deleted from the working
    tree are still returned; main() and check_coverage() report them as missing.
    If `src` is not a git checkout (or git is unavailable), falls back to walking
    the folders on disk.
    """
    folders = [f for f, _ in SCANNED_FOLDERS]
    try:
        out = subprocess.run(["git", "-C", src, "ls-files", "-z", "--"] + folders,
                             check=True, capture_output=True).stdout.decode()
        files = [p for p in out.split("\0") if p]
    except (OSError, subprocess.CalledProcessError):
        files = []
        for folder in folders:
            for root, _, names in os.walk(os.path.join(src, folder)):
                for n in names:
                    files.append(os.path.relpath(os.path.join(root, n), src).replace(os.sep, "/"))
    return sorted(files)


def folder_of(path):
    for folder, ftype in SCANNED_FOLDERS:
        if path.startswith(folder + "/"):
            return folder, ftype
    raise ValueError(path)


def read_text(src, path):
    with open(os.path.join(src, path), "rb") as fh:
        return fh.read().decode("latin-1")


def stem(path):
    return os.path.splitext(os.path.basename(path))[0].upper()


def source_commit(src):
    try:
        return subprocess.run(["git", "-C", src, "rev-parse", "HEAD"], check=True,
                              capture_output=True).stdout.decode().strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


# --------------------------------------------------------------------------- COBOL
TOKEN_RE = re.compile(r"'[^']*'|\"[^\"]*\"|[A-Za-z0-9][A-Za-z0-9#@$-]*|[().=]")


class Cobol:
    """Fixed-format COBOL source (program or copybook) as a token stream."""

    def __init__(self, path, text):
        self.path = path
        self.name = stem(path)
        self.lines = text.splitlines()
        self.tokens = []  # (token, lineno)
        self.comments = []  # (lineno, text)
        for no, line in enumerate(self.lines, 1):
            if len(line) > 6 and line[6] in "*/":
                self.comments.append((no, line[7:72].strip()))
                continue
            code = line[7:72] if len(line) > 7 else ""
            for m in TOKEN_RE.finditer(code):
                tok = m.group(0)
                if tok[0] not in "'\"":
                    tok = tok.upper()
                self.tokens.append((tok, no))
        self.toks = [t for t, _ in self.tokens]

    def line(self, i):
        return self.tokens[i][1]

    @staticmethod
    def lit(tok):
        return tok[0] in "'\"" and tok[1:-1].strip() or None

    def index_of(self, *seq):
        n = len(seq)
        for i in range(len(self.toks) - n + 1):
            if tuple(self.toks[i:i + n]) == seq:
                return i
        return None

    def copies(self):
        out = []
        for i, t in enumerate(self.toks[:-1]):
            if t == "COPY":
                nxt = self.toks[i + 1]
                out.append(((self.lit(nxt) or nxt).upper(), self.line(i), i))
        return out

    def level01_names(self):
        names = []
        for i, t in enumerate(self.toks[:-1]):
            if t in ("01", "1") and (i == 0 or self.toks[i - 1] == ".") and re.match(r"[A-Z]", self.toks[i + 1]):
                names.append(self.toks[i + 1])
        return names

    def values(self):
        """data-name -> set of literal VALUEs (non-88 items)."""
        vals = collections.defaultdict(set)
        cur, level = None, None
        for i, t in enumerate(self.toks[:-1]):
            if re.fullmatch(r"\d{1,2}", t) and (i == 0 or self.toks[i - 1] == "."):
                level, cur = t, self.toks[i + 1]
            elif t in ("VALUE", "VALUES") and level not in ("88",) and cur and cur != "FILLER":
                nxt = self.toks[i + 1]
                if nxt == "IS" and i + 2 < len(self.toks):
                    nxt = self.toks[i + 2]
                v = self.lit(nxt)
                if v:
                    vals[cur].add(v.upper())
        return vals

    def moves(self):
        """target -> list of (source token, line)."""
        mv = collections.defaultdict(list)
        for i, t in enumerate(self.toks[:-3]):
            if t != "MOVE":
                continue
            j = i + 1
            src = self.toks[j]
            if src == "(":
                continue
            j += 1
            if self.toks[j] == "(":  # skip reference modification / subscript on source
                depth = 0
                while j < len(self.toks):
                    depth += self.toks[j] == "("
                    depth -= self.toks[j] == ")"
                    j += 1
                    if depth == 0:
                        break
            if j < len(self.toks) and self.toks[j] == "TO" and j + 1 < len(self.toks):
                mv[self.toks[j + 1]].append((src, self.line(i)))
        return mv

    def exec_cics(self):
        """yield (command, {option: value tokens}, line)."""
        i = 0
        while i < len(self.toks) - 1:
            if self.toks[i] == "EXEC" and self.toks[i + 1] == "CICS":
                start = i
                j = i + 2
                body = []
                while j < len(self.toks) and self.toks[j] != "END-EXEC":
                    body.append(self.toks[j])
                    j += 1
                yield self._parse_cics(body) + (self.line(start),)
                i = j
            i += 1

    @staticmethod
    def _parse_cics(body):
        cmd = []
        k = 0
        while k < len(body) and body[k] != "(" and (k + 1 >= len(body) or body[k + 1] != "("):
            cmd.append(body[k])
            k += 1
        opts = {}
        while k < len(body):
            name = body[k]
            k += 1
            if k < len(body) and body[k] == "(":
                depth, val = 0, []
                while k < len(body):
                    depth += body[k] == "("
                    depth -= body[k] == ")"
                    val.append(body[k])
                    k += 1
                    if depth == 0:
                        break
                opts[name] = val[1:-1]
            else:
                opts.setdefault(name, [])
        return " ".join(cmd), opts


def header_purpose_program(cob):
    lines = [(no, txt) for no, txt in cob.comments if no <= 40]
    for idx, (no, txt) in enumerate(lines):
        m = re.match(r"\*?\s*function\s*:\s*(.*)", txt, re.I)
        if m:
            parts = [m.group(1).strip(" *")]
            for no2, nxt in lines[idx + 1:]:
                if no2 != no + 1:
                    break
                body = nxt.strip(" *")
                if not body or re.match(r"[A-Za-z ]+:", body) or BOILERPLATE.search(body):
                    break
                parts.append(body)
                no = no2
            return " ".join(p for p in parts if p)
    for _, txt in lines:
        body = txt.strip(" *")
        if body and not BOILERPLATE.search(body) and not re.match(r"(program|application|type)\s*:", body, re.I):
            return body
    return ""


def header_purpose_copybook(cob):
    first_code = cob.tokens[0][1] if cob.tokens else 10 ** 9
    for no, txt in cob.comments:
        if no > first_code:
            break
        body = txt.strip(" *-")
        if body and not BOILERPLATE.search(body) and not re.fullmatch(r"[\w-]+\.CPY", body, re.I):
            # header lines can continue the description on following comment lines
            return body
    names = cob.level01_names()
    if names:
        return "No header description; defines 01 " + ", ".join(names)
    for no, txt in cob.comments:
        body = txt.strip(" *-")
        if body and not BOILERPLATE.search(body):
            return "No header description; procedure-division code (" + body + ")"
    return "No header description"


# --------------------------------------------------------------------------- JCL
class Jcl:
    def __init__(self, path, text):
        self.path = path
        self.member = stem(path)
        self.statements = []  # dict(name, op, operands, line, data)
        self.comments = []
        cur = None
        data_target = None
        for no, raw in enumerate(text.splitlines(), 1):
            line = raw[:72].rstrip()
            if line.startswith("//*"):
                self.comments.append((no, line[3:].strip()))
                continue
            if line.startswith("/*"):
                data_target = None
                continue
            if not line.startswith("//"):
                if data_target is not None:
                    data_target["data"].append(raw.rstrip())
                continue
            data_target = None
            body = line[2:]
            if cur is not None and cur["operands"].endswith(","):
                cur["operands"] += self._first_field(body.strip())
                continue
            if not body.strip():
                continue
            if body[0] == " ":
                name, rest = "", body.strip()
            else:
                name, _, rest = body.partition(" ")
                rest = rest.strip()
            op, _, operands = rest.partition(" ")
            cur = {"name": name, "op": op, "operands": self._first_field(operands.strip()),
                   "line": no, "data": []}
            self.statements.append(cur)
            if op == "DD" and re.match(r"(\*|DATA)(,|$)", cur["operands"]):
                data_target = cur

    @staticmethod
    def _first_field(s):
        out, q = [], False
        for ch in s:
            if ch == "'":
                q = not q
            if ch == " " and not q:
                break
            out.append(ch)
        return "".join(out)

    def job_name(self):
        for st in self.statements:
            if st["op"] in ("JOB", "PROC"):
                return st["name"]
        return self.member

    def job_card_text(self):
        for st in self.statements:
            if st["op"] == "JOB":
                m = re.search(r"'([^']*)'", st["operands"])
                return m.group(1).strip() if m else ""
        return ""

    def steps(self):
        """list of dict(step, kind, target, line, dds: list of (ddname, operands, line, data))."""
        steps, cur, last_dd = [], None, None
        for st in self.statements:
            if st["op"] == "EXEC":
                m = re.match(r"(?:(PGM|PROC)=)?([A-Z0-9#@$&.]+)", st["operands"])
                kind = (m.group(1) or "PROC") if m else "?"
                cur = {"step": st["name"], "kind": kind, "target": m.group(2) if m else "",
                       "line": st["line"], "dds": []}
                steps.append(cur)
            elif st["op"] == "DD":
                ddname = st["name"] or last_dd
                last_dd = ddname
                if cur is None:
                    cur = {"step": "(job)", "kind": "", "target": "", "line": st["line"], "dds": []}
                    steps.append(cur)
                cur["dds"].append((ddname, st["operands"], st["line"], st["data"]))
        return steps


def dd_dsn(operands):
    m = re.search(r"(?:DSN|DSNAME)=([^,]+)", operands)
    return m.group(1) if m else None


def dd_disp(operands):
    m = re.search(r"DISP=(\([^)]*\)|[A-Z]+)", operands)
    return m.group(1) if m else ""


def base_dsn(dsn):
    return re.sub(r"\(.*\)$", "", dsn)


def short_dsn(dsn):
    return base_dsn(dsn).replace("AWS.M2.CARDDEMO.", "")


IDCAMS_DEFINE = re.compile(r"DEFINE\s+(CLUSTER|ALTERNATEINDEX|AIX|PATH|GENERATIONDATAGROUP|GDG)\s*\(\s*NAME\s*\(\s*([A-Z0-9.#@$]+)\s*\)")


def idcams(data_lines):
    text = " ".join(l[:72] for l in data_lines).upper().replace(" -", " ")
    out = []
    for m in IDCAMS_DEFINE.finditer(text):
        nxt = re.search(r"\b(DEFINE|DELETE|REPRO|IF|BLDINDEX|LISTCAT)\b", text[m.end():])
        block = text[m.end(): m.end() + nxt.start()] if nxt else text[m.end():]
        attrs = []
        for pat in (r"KEYS\s*\(\s*\d+\s+\d+\s*\)", r"RECORDSIZE\s*\(\s*\d+\s+\d+\s*\)",
                    r"\b(INDEXED|NONINDEXED|NUMBERED|LINEAR)\b", r"RELATE\s*\(\s*[A-Z0-9.#@$]+\s*\)",
                    r"PATHENTRY\s*\(\s*[A-Z0-9.#@$]+\s*\)", r"LIMIT\s*\(\s*\d+\s*\)", r"\b(SCRATCH|NOSCRATCH)\b", r"\bUPGRADE\b",
                    r"\b(NONUNIQUEKEY|UNIQUEKEY)\b"):
            a = re.search(pat, block)
            if a:
                attrs.append(re.sub(r"\s+", " ", a.group(0)))
        kind = {"ALTERNATEINDEX": "AIX", "GENERATIONDATAGROUP": "GDG"}.get(m.group(1), m.group(1))
        out.append(("IDCAMS DEFINE " + kind, m.group(2), " ".join(attrs)))
    for m in re.finditer(r"\bDELETE\s+([A-Z0-9.#@$]+)", text):
        out.append(("IDCAMS DELETE", m.group(1), ""))
    return out


# --------------------------------------------------------------------------- main build
class Builder:
    def __init__(self, src):
        self.src = src
        self.files = list_files(src)
        self.edges = []
        self.rows = []
        self.ext_programs = self._extension_members("cbl")
        self.ext_jcl = self._extension_members("jcl")
        self.cobol = {}
        self.copybooks = {}
        for p in self.files:
            folder, ftype = folder_of(p)
            if folder in ("app/cbl", "app/cpy", "app/cpy-bms") and not p.endswith(".gitkeep"):
                cob = Cobol(p, read_text(src, p))
                (self.cobol if folder == "app/cbl" else self.copybooks)[cob.name] = cob
        self.programs = set(self.cobol)
        self.asm = {stem(p) for p in self.files if p.startswith("app/asm/")}
        self.jcl_members = {stem(p) for p in self.files if p.startswith("app/jcl/")}
        self.procs = {stem(p) for p in self.files if p.startswith("app/proc/")}
        self.bms = {stem(p) for p in self.files if p.startswith("app/bms/")}
        self.record_index = {}
        for name, cob in self.copybooks.items():
            for rec in cob.level01_names():
                self.record_index.setdefault(rec, name)
        self.online = {n for n, c in self.cobol.items() if "CICS" in c.toks and c.index_of("EXEC", "CICS") is not None}

    def _extension_members(self, sub):
        out = {}
        app = os.path.join(self.src, "app")
        for d in sorted(os.listdir(app)) if os.path.isdir(app) else []:
            if d.startswith("app-") and os.path.isdir(os.path.join(app, d, sub)):
                for n in os.listdir(os.path.join(app, d, sub)):
                    out.setdefault(stem(n), d)
        return out

    def edge(self, frm, to, kind, from_type, to_type, record="", detail="", source=""):
        self.edges.append({"from": frm, "to": to, "kind": kind, "from_type": from_type, "to_type": to_type,
                           "record_copybook": record, "detail": detail, "source": source})

    def program_type(self, name):
        if name in self.programs:
            return "program"
        if name in self.asm:
            return "assembler"
        if name in self.ext_programs:
            return "program (not in app/cbl; in app/%s)" % self.ext_programs[name]
        if name.startswith("CEE"):
            return "LE runtime service"
        return "external"

    # ---------------------------------------------------------------- COBOL edges
    def resolve(self, cob, var, values, moves, seen=None):
        """Return (literals, dynamic_sources) a variable can hold."""
        seen = seen or set()
        if var in seen:
            return set(), set()
        seen.add(var)
        lits, dyn = set(values.get(var, ())), set()
        for src, _ in moves.get(var, ()):
            lit = Cobol.lit(src)
            if lit:
                lits.add(lit.upper())
            elif src in seen:
                continue
            elif re.match(r"[A-Z]", src):
                l2, d2 = self.resolve(cob, src, values, moves, seen)
                lits |= l2
                dyn |= d2
                if not l2 and not d2:
                    dyn.add(src)
            if src.startswith("CDEMO-") and src != var:
                dyn.add(src)
        return lits, dyn

    def table_values(self, cob, field):
        """Program names held in a menu-table copybook that defines `field`."""
        out = {}
        for cb, _, _ in cob.copies():
            book = self.copybooks.get(cb)
            if book and field in book.toks:
                for i, t in enumerate(book.toks[:-4]):
                    if t == "X" and book.toks[i + 1] == "(" and book.toks[i + 2] == "08" and book.toks[i + 4] == "VALUE":
                        v = Cobol.lit(book.toks[i + 5])
                        if v:
                            out[v.upper()] = cb
        return out

    def cobol_edges(self, cob):
        prog = cob.name
        values = cob.values()
        moves = cob.moves()
        copied = [c for c, _, _ in cob.copies()]
        for cb in copied:
            for k, v in self.copybooks.get(cb, Cobol("x", "")).values().items():
                values.setdefault(k, set()).update(v)
        local_records = {}
        for cb in copied:
            book = self.copybooks.get(cb)
            if book:
                for rec in book.level01_names():
                    local_records[rec] = cb

        def rec_cb(name):
            return local_records.get(name) or ""

        src_ref = lambda line: "%s:%d" % (cob.path, line)
        ptype = "program"

        for cb, line, _ in cob.copies():
            folder = "copybook" if cb in self.copybooks else "external copybook (CICS/IBM supplied)"
            if cb in self.copybooks and self.copybooks[cb].path.startswith("app/cpy-bms/"):
                folder = "copybook (BMS symbolic map)"
            self.edge(prog, cb, "COPY", ptype, folder, source=src_ref(line))

        for i, t in enumerate(cob.toks[:-1]):
            if t == "CALL":
                lit = Cobol.lit(cob.toks[i + 1])
                if lit:
                    self.edge(prog, lit.upper(), "CALL", ptype, self.program_type(lit.upper()),
                              source=src_ref(cob.line(i)))

        def targets(opt_tokens):
            if not opt_tokens:
                return {}, set(), ""
            lit = Cobol.lit(opt_tokens[0])
            if lit:
                return {lit.upper(): "literal"}, set(), ""
            var = opt_tokens[0]
            lits, dyn = self.resolve(cob, var, values, moves)
            res = {v: "resolved from %s" % var for v in lits}
            if not lits and len(opt_tokens) > 1:
                for v, cb in self.table_values(cob, var).items():
                    res[v] = "resolved from menu table %s in %s" % (var, cb)
            return res, dyn, var

        for cmd, opts, line in cob.exec_cics():
            verb = cmd.split()[0] if cmd else ""
            if verb == "XCTL" or verb == "LINK":
                res, dyn, var = targets(opts.get("PROGRAM"))
                for tgt, how in sorted(res.items()):
                    if tgt != prog:
                        self.edge(prog, tgt, verb, ptype, self.program_type(tgt), detail=how, source=src_ref(line))
                for d in sorted(dyn):
                    self.edge(prog, "(dynamic: %s)" % d, verb, ptype, "runtime value",
                              detail="PROGRAM(%s) also takes its value from %s (COMMAREA)" % (var, d),
                              source=src_ref(line))
            elif verb == "RETURN" and "TRANSID" in opts:
                res, _, _ = targets(opts["TRANSID"])
                for tgt, how in sorted(res.items()):
                    self.edge(prog, tgt, "RETURN-TRANSID", ptype, "transaction", detail=how, source=src_ref(line))
            elif verb in ("SEND", "RECEIVE") and "MAPSET" in opts:
                res, _, _ = targets(opts["MAPSET"])
                mres, _, _ = targets(opts.get("MAP"))
                for tgt, how in sorted(res.items()):
                    self.edge(prog, tgt, "%s MAP" % verb, ptype, "bms" if tgt in self.bms else "bms (missing)",
                              detail="MAP(%s) %s" % ("/".join(sorted(mres)) or "?", how), source=src_ref(line))
            elif verb in CICS_FILE_COMMANDS and ("DATASET" in opts or "FILE" in opts):
                res, _, _ = targets(opts.get("DATASET") or opts.get("FILE"))
                rec = ""
                for o in ("INTO", "FROM"):
                    if opts.get(o):
                        rec = rec_cb(opts[o][0])
                for tgt, how in sorted(res.items()):
                    self.edge(prog, tgt, CICS_FILE_KIND[verb], ptype, "cics-file", record=rec,
                              detail="EXEC CICS %s; %s" % (verb, how), source=src_ref(line))
            elif verb == "WRITEQ" and "QUEUE" in opts:
                res, _, _ = targets(opts["QUEUE"])
                for tgt, how in sorted(res.items()):
                    self.edge(prog, tgt, "WRITEQ %s" % (cmd.split()[1] if len(cmd.split()) > 1 else ""), ptype,
                              "transient data queue", detail=how, source=src_ref(line))

        # JCL submitted from program literals (CORPT00C writes JCL to the JOBS TDQ)
        for i, t in enumerate(cob.toks):
            lit = Cobol.lit(t)
            if lit:
                m = re.search(r"EXEC\s+PROC=([A-Z0-9#@$]+)", lit)
                if m:
                    self.edge(prog, m.group(1), "SUBMIT JCL (EXEC PROC=)", ptype,
                              "proc" if m.group(1) in self.procs else "proc (missing)",
                              detail="in-program JCL literal", source=src_ref(cob.line(i)))

        # Batch file I/O via SELECT/FD
        selects, orgs = {}, {}
        for i, t in enumerate(cob.toks[:-3]):
            if t == "SELECT":
                f = cob.toks[i + 1]
                j = i + 2
                if cob.toks[j] == "OPTIONAL":
                    j += 1
                if cob.toks[j] == "ASSIGN":
                    j += 1
                    if cob.toks[j] == "TO":
                        j += 1
                    selects[f] = (cob.toks[j], cob.line(i))
                    k = j
                    while k < len(cob.toks) and cob.toks[k] not in ("SELECT", "DATA", "FD"):
                        if cob.toks[k] == "ORGANIZATION":
                            org = cob.toks[k + 1] if cob.toks[k + 1] != "IS" else cob.toks[k + 2]
                            orgs[f] = org
                        k += 1
        if not selects:
            return
        fd_records, fd_copy = collections.defaultdict(set), collections.defaultdict(set)
        cur = None
        proc_start = cob.index_of("PROCEDURE", "DIVISION") or len(cob.toks)
        for i in range(proc_start):
            t = cob.toks[i]
            if t == "FD" and i + 1 < len(cob.toks):
                cur = cob.toks[i + 1]
            elif t == "SECTION" and i > 0 and cob.toks[i - 1] in ("WORKING-STORAGE", "LINKAGE", "LOCAL-STORAGE"):
                cur = None
            elif cur and t in ("01", "1") and cob.toks[i - 1] == ".":
                fd_records[cur].add(cob.toks[i + 1])
            elif cur and t == "COPY":
                cb = cob.toks[i + 1]
                fd_copy[cur].add(cb)
                for rec in (self.copybooks[cb].level01_names() if cb in self.copybooks else []):
                    fd_records[cur].add(rec)
        rec_to_file = {r: f for f, rs in fd_records.items() for r in rs}
        ops = collections.defaultdict(lambda: collections.defaultdict(list))  # file -> kind -> lines
        recs = collections.defaultdict(set)
        opens = collections.defaultdict(set)
        toks = cob.toks
        i = proc_start
        while i < len(toks) - 1:
            t = toks[i]
            if t == "OPEN":
                mode, j = None, i + 1
                while j < len(toks):
                    if toks[j] in ("INPUT", "OUTPUT", "I-O", "EXTEND"):
                        mode = toks[j]
                    elif toks[j] in selects and mode:
                        opens[toks[j]].add(mode)
                    else:
                        break
                    j += 1
            elif t in ("READ", "DELETE", "START") and toks[i + 1] in selects:
                f = toks[i + 1]
                ops[f]["READ" if t in ("READ", "START") else "DELETE"].append(cob.line(i))
                for j in range(i + 2, min(i + 6, len(toks) - 1)):
                    if toks[j] == "INTO" and rec_cb(toks[j + 1]):
                        recs[f].add(rec_cb(toks[j + 1]))
            elif t in ("WRITE", "REWRITE") and toks[i + 1] in rec_to_file:
                f = rec_to_file[toks[i + 1]]
                ops[f][t].append(cob.line(i))
                if rec_cb(toks[i + 1]):
                    recs[f].add(rec_cb(toks[i + 1]))
                if toks[i + 2] == "FROM" and rec_cb(toks[i + 3]):
                    recs[f].add(rec_cb(toks[i + 3]))
            i += 1
        for f, (dd, line) in sorted(selects.items()):
            rec = "|".join(sorted(recs[f] | fd_copy[f]))
            detail = "SELECT %s ASSIGN TO %s%s; OPEN %s" % (
                f, dd, (" ORGANIZATION %s" % orgs[f]) if f in orgs else "", "/".join(sorted(opens[f])) or "-")
            kinds = ops[f] or {}
            if not kinds:
                self.edge(prog, dd, "OPEN only (no READ/WRITE)" if opens[f] else "SELECT only (never opened)",
                          ptype, "dd", rec, detail, src_ref(line))
            for kind, lines in sorted(kinds.items()):
                self.edge(prog, dd, kind, ptype, "dd", rec, detail, " ".join(src_ref(l) for l in lines))

    # ---------------------------------------------------------------- JCL edges
    def jcl_edges(self, path):
        j = Jcl(path, read_text(self.src, path))
        frm = j.member
        ftype = "proc" if path.startswith("app/proc/") else "jcl"
        ref = lambda line: "%s:%d" % (path, line)
        for st in j.steps():
            if st["kind"] == "PGM":
                self.edge(frm, st["target"], "EXEC PGM=", ftype,
                          self.program_type(st["target"]) if st["target"] in self.programs or st["target"] in self.ext_programs
                          else "system utility", detail="step %s" % st["step"], source=ref(st["line"]))
            elif st["kind"] == "PROC":
                self.edge(frm, st["target"], "EXEC PROC=", ftype,
                          "proc" if st["target"] in self.procs else "proc (missing)",
                          detail="step %s" % st["step"], source=ref(st["line"]))
            intrdr = any("INTRDR" in ops for _, ops, _, _ in st["dds"])
            for dd, ops, line, data in st["dds"]:
                dsn = dd_dsn(ops)
                if dsn:
                    self.edge("%s.%s.%s" % (frm, st["step"], dd), dsn, "JCL DD->DSN", ftype + " DD", "dataset",
                              detail="DISP=%s" % dd_disp(ops) if dd_disp(ops) else "", source=ref(line))
                    m = re.match(r"[A-Z0-9.#@$]+\(([A-Z0-9#@$]+)\)$", dsn)
                    if intrdr and m and dd == "SYSUT1":
                        self.edge(frm, m.group(1), "SUBMIT via INTRDR", ftype,
                                  "jcl" if m.group(1) in self.jcl_members else "jcl (missing)",
                                  detail="IEBGENER %s -> INTRDR" % dsn, source=ref(line))
                if data and st["target"] == "IDCAMS":
                    for kind, name, attrs in idcams(data):
                        self.edge(frm, name, kind, ftype, "dataset", detail=attrs, source=ref(line))
                if data and st["target"] == "SDSF":
                    for k, l in enumerate(data):
                        for m in SDSF_CEMT.finditer(l):
                            self.edge(frm, m.group(1).upper(), "CEMT SET FILE " + ("OPEN" if m.group(2).upper() == "OPE" else "CLOSE"),
                                      ftype, "cics-file", source=ref(line + 1 + k))
        return j

    # ---------------------------------------------------------------- CSD / scheduler
    def csd_edges(self, path):
        text = read_text(self.src, path)
        defs = []
        cur = None
        for no, line in enumerate(text.splitlines(), 1):
            m = re.match(r"\s*DEFINE\s+(\w+)\((\w+)\)", line)
            if m:
                cur = {"type": m.group(1), "name": m.group(2), "line": no, "attrs": line}
                defs.append(cur)
            elif cur:
                cur["attrs"] += " " + line.strip()
        for d in defs:
            ref = "%s:%d" % (path, d["line"])
            if d["type"] == "FILE":
                m = re.search(r"DSNAME\(([^)]+)\)", d["attrs"])
                if m:
                    self.edge(d["name"], m.group(1), "CSD FILE DSNAME", "cics-file", "dataset", source=ref)
            elif d["type"] == "TRANSACTION":
                m = re.search(r"\bPROGRAM\((\w+)\)", d["attrs"])
                if m:
                    self.edge(d["name"], m.group(1), "CSD TRANSACTION PROGRAM", "transaction",
                              self.program_type(m.group(1)) if m.group(1) in self.programs else
                              "program (CSD only, no source in app/cbl)", source=ref)
        return defs

    def scheduler_edges(self, path):
        text = read_text(self.src, path)
        summary = {}
        if path.endswith(".controlm"):
            root = ET.fromstring(text)
            folders, jobs = 0, 0
            for folder in root.iter():
                if folder.tag not in ("FOLDER", "SMART_FOLDER", "SUB_FOLDER"):
                    continue
                folders += 1
                fname = folder.get("FOLDER_NAME")
                produced = {}
                fjobs = [j for j in folder if j.tag == "JOB"]
                for job in fjobs:
                    jobs += 1
                    mem = job.get("MEMNAME") or job.get("JOBNAME")
                    for oc in job.findall("OUTCOND"):
                        if oc.get("SIGN") == "+":
                            produced[oc.get("NAME")] = mem
                    self.edge("Control-M:" + fname, mem, "CONTROL-M JOB", "scheduler",
                              "jcl" if mem in self.jcl_members else
                              ("jcl (in app/%s)" % self.ext_jcl[mem] if mem in self.ext_jcl else "jcl (missing)"),
                              detail="%s; TIMETO=%s DAYS=%s" % (job.get("DESCRIPTION", ""), job.get("TIMETO", ""), job.get("DAYS", "")),
                              source=path)
                for job in fjobs:
                    mem = job.get("MEMNAME") or job.get("JOBNAME")
                    for ic in job.findall("INCOND"):
                        pred = produced.get(ic.get("NAME"))
                        self.edge(pred or "(condition %s)" % ic.get("NAME"), mem, "CONTROL-M INCOND", "jcl", "jcl",
                                  detail="folder %s; condition %s" % (fname, ic.get("NAME")), source=path)
            summary = {"folders": folders, "jobs": jobs}
        else:
            cur, in_trig, entries = None, False, 0
            for no, line in enumerate(text.splitlines(), 1):
                m = re.match(r"\s*1LJOB,JOB=(\S+?),", line)
                if m:
                    cur, in_trig = m.group(1), False
                    entries += 1
                    continue
                if "TRIGGERED JOBS" in line:
                    in_trig = True
                    continue
                m = re.match(r"\s+JOB=(\S+)\s+SCHID=(\d+)", line)
                if in_trig and cur and m:
                    tgt = m.group(1)
                    self.edge(cur, tgt, "CA-7 TRIGGER", "jcl",
                              "jcl" if tgt in self.jcl_members else
                              ("jcl (in app/%s)" % self.ext_jcl[tgt] if tgt in self.ext_jcl else "jcl (missing)"),
                              detail="SCHID=%s" % m.group(2), source="%s:%d" % (path, no))
            summary = {"entries": entries}
        return summary

    # ---------------------------------------------------------------- inventory rows
    def build(self):
        jcls = {}
        csd_defs = []
        sched = {}
        for name in sorted(self.cobol):
            self.cobol_edges(self.cobol[name])
        for name in sorted(self.copybooks):
            cob = self.copybooks[name]
            for cb, line, _ in cob.copies():
                self.edge(name, cb, "COPY", "copybook", "copybook" if cb in self.copybooks else "external copybook",
                          source="%s:%d" % (cob.path, line))
        for p in self.files:
            if p.startswith(("app/jcl/", "app/proc/")):
                jcls[p] = self.jcl_edges(p)
            elif p.startswith("app/csd/") and not p.endswith(".gitkeep"):
                csd_defs = self.csd_edges(p)
            elif p.startswith("app/scheduler/"):
                sched[p] = self.scheduler_edges(p)
        self.csd_defs = csd_defs
        self.jcls = jcls

        copy_users = collections.defaultdict(set)
        for e in self.edges:
            if e["kind"] == "COPY" and e["from_type"] == "program":
                copy_users[e["to"]].add(e["from"])
        exec_by_jcl = {e["to"] for e in self.edges if e["kind"] == "EXEC PGM="}
        called_by = collections.defaultdict(set)
        for e in self.edges:
            if e["kind"] == "CALL":
                called_by[e["to"]].add(e["from"])
        self.called_by = called_by

        def program_category(name, purpose):
            if name in self.online:
                return "online"
            if "UTILITY" in purpose.upper() or (called_by.get(name, set()) & self.online):
                return "utility"
            return "batch"

        self.categories = {}
        for name, cob in self.cobol.items():
            self.categories[name] = program_category(name, header_purpose_program(cob))

        dsn_users = collections.defaultdict(set)
        for e in self.edges:
            if e["kind"] == "JCL DD->DSN":
                dsn_users[e["to"]].add(e["from"].split(".")[0])

        for p in self.files:
            folder, ftype = folder_of(p)
            raw = open(os.path.join(self.src, p), "rb").read()
            text = raw.decode("latin-1")
            lines = "" if folder == "app/data" and "/EBCDIC/" in p else str(text.count("\n"))
            name = os.path.basename(p) if folder == "app/scheduler" else stem(p)
            if p.endswith(".gitkeep"):
                self.rows.append([p, "placeholder", ".gitkeep", "n/a", "Empty placeholder that keeps the folder in git", lines, len(raw)])
                continue
            if folder == "app/cbl":
                cob = self.cobol[name]
                purpose = header_purpose_program(cob) or "No header description"
                cat = self.categories[name]
            elif folder in ("app/cpy", "app/cpy-bms"):
                cob = self.copybooks[name]
                users = copy_users.get(name, set())
                if folder == "app/cpy-bms":
                    purpose = "BMS symbolic map for mapset %s (01 %s)" % (name, ", ".join(cob.level01_names()))
                else:
                    purpose = header_purpose_copybook(cob)
                cats = {self.categories[u] for u in users}
                cat = "unused" if not users else ("online+batch" if {"online", "batch"} <= cats or ("utility" in cats and len(cats) > 1)
                                                  else sorted(cats)[0])
            elif folder == "app/bms":
                purpose = header_purpose_copybook(Cobol(p, "      " + "\n      ".join(text.splitlines())))
                purpose = next((l.strip(" *") for l in text.splitlines()[:5] if l.startswith("*") and l.strip(" *")
                                and not BOILERPLATE.search(l)), "")
                cat = "online"
            elif folder in ("app/jcl", "app/proc"):
                j = self.jcls[p]
                card = j.job_card_text()
                comment = next((c.strip(" *-`") for _, c in j.comments if c.strip(" *-`") and not BOILERPLATE.search(c)
                                and not c.upper().startswith("SET PARMS")), "")
                purpose = card if (" " in card and card.upper() != j.member) else (comment or card)
                runs = {e["to"] for e in self.edges if e["from"] == j.member and e["kind"] == "EXEC PGM=" and e["to"] in self.programs}
                procs = {e["to"] for e in self.edges if e["from"] == j.member and e["kind"] == "EXEC PROC="}
                if any(e["from"] in procs and e["kind"] == "EXEC PGM=" and e["to"] in self.programs for e in self.edges):
                    runs.add("proc")
                cat = "batch" if runs else "utility"
                if runs - {"proc"}:
                    purpose += "; runs " + ", ".join(sorted(runs - {"proc"}))
                if j.job_name() != j.member:
                    purpose += " [%s statement name %s differs from member name]" % ("PROC" if folder == "app/proc" else "JOB", j.job_name())
            elif folder == "app/scheduler":
                s = sched[p]
                purpose = ("Control-M job definitions: %(folders)d folders, %(jobs)d job entries" % s) if "folders" in s \
                    else "CA-7 job listing (LJOB LIST=ALL): %(entries)d job entries with triggers" % s
                cat = "batch"
            elif folder == "app/csd":
                counts = collections.Counter(d["type"] for d in self.csd_defs)
                purpose = "CICS CSD resource definitions (group CARDDEMO): " + ", ".join(
                    "%d %s" % (v, k) for k, v in sorted(counts.items()))
                cat = "online"
            elif folder == "app/data":
                base = os.path.basename(p)
                if "/EBCDIC/" in p:
                    used = sorted(dsn_users.get(base, ()))
                    purpose = "EBCDIC seed/extract dataset %s (%d bytes)%s" % (
                        base, len(raw), ("; referenced by JCL " + ", ".join(used)) if used else "; not referenced by any app/jcl DD")
                    name = base
                else:
                    recs = text.splitlines()
                    lens = sorted({len(r) for r in recs})
                    purpose = "ASCII seed data: %d records, record length %s (ASCII copy of an EBCDIC dataset, inferred from name)" % (
                        len(recs), "/".join(map(str, lens[:3])) + ("..." if len(lens) > 3 else ""))
                    name = base
                cat = "n/a"
            elif folder == "app/asm":
                m = re.search(r"^(\w+)\s+(CSECT|START)", text, re.M)
                purpose = "No header description; assembler %s %s, CALLed by %s" % (
                    m.group(1) if m else name, m.group(2) if m else "", ", ".join(sorted(called_by.get(name, ()))) or "nothing")
                cat = "utility"
            elif folder == "app/maclib":
                m = re.search(r"^\s+MACRO\s*\n\s*(?:&\w+\s+)?(\w+)|^(\w+)\s+DSECT", text, re.M)
                purpose = "No header description; assembler %s" % ("macro " + m.group(1) if m and m.group(1) else "DSECT " + (m.group(2) if m else name))
                cat = "utility"
            elif folder == "app/ctl":
                purpose = "IDCAMS control statements: " + " ".join(l.strip() for l in text.splitlines() if l.strip() and not l.strip().startswith("/*"))
                cat = "utility"
            elif folder == "app/catlg":
                purpose = "IDCAMS LISTCAT LEVEL(AWS.M2.CARDDEMO) ALL output (catalog listing)"
                cat = "n/a"
            else:
                purpose, cat = "", "n/a"
            self.rows.append([p, ftype, name, cat, re.sub(r"\s+", " ", purpose).strip(), lines, len(raw)])

        merged = collections.OrderedDict()
        for e in self.edges:
            k = (e["from"], e["to"], e["kind"], e["from_type"], e["to_type"])
            m = merged.setdefault(k, {c: [] for c in DEPENDENCY_COLUMNS})
            for c in ("record_copybook", "detail", "source"):
                for v in e[c].split("|") if c == "record_copybook" else [e[c]]:
                    if v and v not in m[c]:
                        m[c].append(v)
        self.edges = []
        for (frm, to, kind, ft, tt), m in merged.items():
            self.edges.append({"from": frm, "to": to, "kind": kind, "from_type": ft, "to_type": tt,
                               "record_copybook": "|".join(sorted(m["record_copybook"])),
                               "detail": "; ".join(m["detail"]), "source": " ".join(m["source"])})
        self.edges.sort(key=lambda e: (e["from_type"], e["from"], e["kind"], e["to"]))


# --------------------------------------------------------------------------- outputs
def write_csv(path, header, rows):
    with open(path, "w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh, lineterminator="\n")
        w.writerow(header)
        w.writerows(rows)


def check_coverage(src, out_dir):
    expected = set(list_files(src))
    with open(os.path.join(out_dir, "inventory.csv"), encoding="utf-8") as fh:
        paths = [r["path"] for r in csv.DictReader(fh)]
    for p in missing_on_disk(src, expected):
        print("COVERAGE FAIL: tracked by git but missing on disk: %s" % p, file=sys.stderr)
    dupes = sorted(p for p, n in collections.Counter(paths).items() if n > 1)
    missing = sorted(expected - set(paths))
    extra = sorted(set(paths) - expected)
    for label, items in (("missing from inventory.csv", missing), ("not in source checkout", extra), ("duplicated", dupes)):
        for p in items:
            print("COVERAGE FAIL: %s: %s" % (label, p), file=sys.stderr)
    by_folder = collections.Counter(folder_of(p)[0] for p in expected)
    print("coverage: %d/%d files inventoried (%s)" % (len(expected) - len(missing), len(expected),
          ", ".join("%s=%d" % (f, by_folder[f]) for f, _ in SCANNED_FOLDERS)))
    return not (missing or extra or dupes or missing_on_disk(src, expected))


def missing_on_disk(src, paths):
    return sorted(p for p in paths if not os.path.isfile(os.path.join(src, p)))


def md_cell(items, limit=None):
    items = list(items)
    if limit and len(items) > limit:
        items = items[:limit] + ["+%d more" % (len(items) - limit)]
    return ", ".join(items) if items else "-"


def generated_markdown(b, src):
    out = io.StringIO()
    p = lambda s="": out.write(s + "\n")
    edges = b.edges
    commit = source_commit(src)

    # counts
    counts = collections.Counter(folder_of(r[0])[0] for r in b.rows)
    real = collections.Counter(folder_of(r[0])[0] for r in b.rows if r[1] != "placeholder")
    p("Source commit scanned: `%s` (`ipsorakis/aws-mainframe-modernization-carddemo`, `main`)." % commit)
    p()
    p("| Folder | Files (git ls-files) | Excluding .gitkeep | Ticket BNP-2 | Match |")
    p("|---|---|---|---|---|")
    for f, _ in SCANNED_FOLDERS:
        t = TICKET_COUNTS.get(f)
        p("| `%s/` | %d | %d | %s | %s |" % (f, counts[f], real[f], t if t is not None else "not listed",
                                           "yes" if t == real[f] else ("n/a" if t is None else "**no**")))
    p("| **Total** | %d | %d | | |" % (sum(counts.values()), sum(real.values())))
    p()
    kinds = collections.Counter(e["kind"] for e in edges)
    p("`dependencies.csv` holds %d edges: %s." % (len(edges), ", ".join("%s %d" % (k, v) for k, v in sorted(kinds.items()))))
    p()

    # program table
    dd_dsn_by_prog = collections.defaultdict(lambda: collections.defaultdict(set))
    step_prog = {}
    for jpath, j in b.jcls.items():
        for st in j.steps():
            if st["kind"] == "PGM" and st["target"] in b.programs:
                for dd, ops, _, _ in st["dds"]:
                    d = dd_dsn(ops)
                    if d:
                        dd_dsn_by_prog[st["target"]][dd.split(".")[-1]].add(short_dsn(d))
                step_prog.setdefault(st["target"], set()).add(j.member)
    for callee, callers in b.called_by.items():  # subroutines see their caller's DDs
        for c in callers:
            for dd, dsns in dd_dsn_by_prog.get(c, {}).items():
                dd_dsn_by_prog[callee][dd] |= dsns
    trans_by_prog = collections.defaultdict(set)
    for e in edges:
        if e["kind"] == "CSD TRANSACTION PROGRAM":
            trans_by_prog[e["to"]].add(e["from"])
    p("### Programs")
    p()
    p("Files: online programs show CICS file names (CSD); batch programs show `DDNAME=DSN` from the JCL steps that run them "
      "(DSN prefix `AWS.M2.CARDDEMO.` dropped). R=read, W=write, U=rewrite, D=delete.")
    p()
    p("| Program | Category | Entry (CSD transaction / JCL) | Copybooks used | Files read/written | Transfers control to (XCTL / CALL / LINK) |")
    p("|---|---|---|---|---|---|")
    letter = {"CICS READ": "R", "READ": "R", "CICS WRITE": "W", "WRITE": "W", "CICS REWRITE": "U", "REWRITE": "U",
              "CICS DELETE": "D", "DELETE": "D"}
    for name in sorted(b.programs):
        mine = [e for e in edges if e["from"] == name]
        cbs = sorted({e["to"] for e in mine if e["kind"] == "COPY" and e["to_type"].startswith("copybook")})
        files = collections.defaultdict(set)
        for e in mine:
            if e["kind"] in letter:
                files[e["to"]].add(letter[e["kind"]])
        fcell = []
        for f in sorted(files):
            ops = "".join(x for x in "RWUD" if x in files[f])
            if e_is_dd(mine, f):
                dsns = sorted(dd_dsn_by_prog[name].get(f, ())) or ["no JCL DD"]
                fcell.append("%s=%s (%s)" % (f, "/".join(dsns), ops))
            else:
                fcell.append("%s (%s)" % (f, ops))
        xfer = sorted({("%s" % e["to"]) + (" (CALL)" if e["kind"] == "CALL" else "") for e in mine
                       if e["kind"] in ("XCTL", "LINK", "CALL") and not e["to"].startswith("(dynamic")})
        if any(e["to"].startswith("(dynamic") for e in mine):
            xfer.append("caller via COMMAREA")
        entry = sorted(trans_by_prog.get(name, ())) + sorted(step_prog.get(name, ()))
        if not entry and b.called_by.get(name):
            entry = ["CALLed by " + "/".join(sorted(b.called_by[name]))]
        p("| `%s` | %s | %s | %s | %s | %s |" % (name, b.categories[name], md_cell(entry) if entry else "**none found**",
                                               md_cell(cbs), md_cell(fcell), md_cell(xfer)))
    p()

    # data files table
    p("### Data files")
    p()
    p("VSAM and sequential datasets referenced by IDCAMS DEFINEs, the CSD, or the DD statements of steps that run a CardDemo "
      "program. GDG generations and members are folded into their base name.")
    p()
    p("| Dataset (DSN) | Defined by (IDCAMS) | Definition attributes | CICS file (CSD) | Record copybook(s) | Online programs | Batch programs (DD) |")
    p("|---|---|---|---|---|---|---|")
    datasets = collections.defaultdict(lambda: {"def": set(), "attrs": set(), "cics": set(), "rec": set(), "online": set(), "batch": set()})
    for e in edges:
        if e["kind"].startswith("IDCAMS DEFINE"):
            d = datasets[e["to"]]
            d["def"].add("%s (%s)" % (e["from"], e["kind"].replace("IDCAMS DEFINE ", "")))
            if e["detail"]:
                d["attrs"].add(e["detail"])
        elif e["kind"] == "CSD FILE DSNAME":
            datasets[e["to"]]["cics"].add(e["from"])
    cics_to_dsn = {e["from"]: e["to"] for e in edges if e["kind"] == "CSD FILE DSNAME"}
    for e in edges:
        if e["from_type"] == "program" and e["to_type"] == "cics-file" and e["to"] in cics_to_dsn:
            d = datasets[cics_to_dsn[e["to"]]]
            d["online"].add("%s(%s)" % (e["from"], letter.get(e["kind"], "?")))
            if e["record_copybook"]:
                d["rec"].add(e["record_copybook"])
    for jpath, j in b.jcls.items():
        for st in j.steps():
            if st["kind"] != "PGM" or st["target"] not in b.programs:
                continue
            progs = [st["target"]] + sorted(k for k, v in b.called_by.items() if st["target"] in v)
            for dd, ops, _, _ in st["dds"]:
                dsn = dd_dsn(ops)
                if not dsn or dd in SYSTEM_DDS:
                    continue
                for prog in progs:
                    for e in edges:
                        if e["from"] == prog and e["to"] == dd and e["to_type"] == "dd":
                            d = datasets[base_dsn(dsn)]
                            d["batch"].add("%s(%s)" % (prog, letter.get(e["kind"], "?")))
                            for r in e["record_copybook"].split("|"):
                                if r:
                                    d["rec"].add(r)
    for dsn in sorted(datasets):
        d = datasets[dsn]
        if not (d["online"] or d["batch"] or d["cics"] or d["def"]):
            continue
        p("| `%s` | %s | %s | %s | %s | %s | %s |" % (dsn, md_cell(sorted(d["def"])), md_cell(sorted(d["attrs"])),
                                                    md_cell(sorted(d["cics"])), md_cell(sorted(d["rec"])),
                                                    md_cell(merge_ops(d["online"])), md_cell(merge_ops(d["batch"]))))
    p()

    # mermaid
    p("### Dependency diagrams")
    p()
    p("Online: CICS programs and the CICS files they use (solid = read, thick = write/rewrite/delete). Menu/XCTL flow is in `dependencies.csv` (kind `XCTL`).")
    p()
    p("```mermaid")
    p("flowchart LR")
    files = sorted({e["to"] for e in edges if e["from_type"] == "program" and e["to_type"] == "cics-file"})
    for f in files:
        p("  F_%s[(%s)]" % (f, f))
    pairs = collections.defaultdict(set)
    for e in edges:
        if e["from_type"] == "program" and e["to_type"] == "cics-file":
            pairs[(e["from"], e["to"])].add("R" if e["kind"] == "CICS READ" else "W")
    for (prog, f), ops in sorted(pairs.items()):
        if "W" in ops:
            p("  %s ==> F_%s" % (prog, f))
        else:
            p("  F_%s --> %s" % (f, prog))
    p("```")
    p()
    p("Batch: programs and the datasets bound to their DDs by the JCL that runs them (arrow into a program = read, out of a program = write).")
    p()
    p("```mermaid")
    p("flowchart LR")
    bpairs = collections.defaultdict(set)
    for dsn, d in datasets.items():
        for item in d["batch"]:
            prog, ops = item.split("(")
            bpairs[(prog, short_dsn(dsn))] |= set(ops.rstrip(")"))
    nodes = sorted({ds for _, ds in bpairs})
    for n in nodes:
        p("  D_%s[(%s)]" % (re.sub(r"[^A-Za-z0-9]", "_", n), n))
    for (prog, ds), ops in sorted(bpairs.items()):
        nid = "D_" + re.sub(r"[^A-Za-z0-9]", "_", ds)
        if "R" in ops:
            p("  %s --> %s" % (nid, prog))
        if ops & set("WUD"):
            p("  %s ==> %s" % (prog, nid))
    p("```")
    p()

    # computed findings
    p("### Findings computed from the source")
    p()
    find = []
    csd_progs = {d["name"] for d in b.csd_defs if d["type"] == "PROGRAM"}
    csd_maps = {d["name"] for d in b.csd_defs if d["type"] == "MAPSET"}
    for prog in sorted(csd_progs - b.programs):
        find.append("CSD defines PROGRAM(%s) but there is no source for it in `app/cbl/`%s." % (
            prog, "".join("; transaction %s points to it" % e["from"] for e in edges
                          if e["kind"] == "CSD TRANSACTION PROGRAM" and e["to"] == prog)))
    for prog in sorted(b.online - csd_progs):
        find.append("Online program %s has no CSD PROGRAM definition." % prog)
    for m in sorted(b.bms - csd_maps):
        find.append("BMS mapset %s has no CSD MAPSET definition." % m)
    not_run = sorted(n for n in b.programs if b.categories[n] == "batch" and n not in step_prog and not b.called_by.get(n))
    for n in not_run:
        find.append("Batch program %s is not run by any JCL or PROC in `app/jcl/` / `app/proc/`." % n)
    for e in edges:
        if e["kind"] in ("XCTL", "CALL", "LINK") and e["to_type"].startswith("program (not in app/cbl"):
            find.append("%s transfers control (%s) to %s, which is not in `app/cbl/` but exists in `app/%s/` (%s)." % (
                e["from"], e["kind"], e["to"], b.ext_programs[e["to"]], e["detail"]))
    sched_missing = sorted({(e["to"], e["to_type"]) for e in edges if e["kind"] in ("CONTROL-M JOB", "CA-7 TRIGGER")
                            and e["to_type"] != "jcl"})
    for mem, t in sched_missing:
        find.append("Scheduler references job %s with no member in `app/jcl/` %s." % (mem, t.replace("jcl ", "")))
    unscheduled = sorted(b.jcl_members - {e["to"] for e in edges if e["kind"] in ("CONTROL-M JOB", "CA-7 TRIGGER")}
                         - {e["from"] for e in edges if e["kind"] == "CA-7 TRIGGER"})
    find.append("JCL members not referenced by either scheduler (%d): %s." % (len(unscheduled), ", ".join(unscheduled)))
    for name in sorted(b.programs):
        if name not in step_prog:
            continue
        for e in edges:
            if e["from"] == name and e["to_type"] == "dd" and not dd_dsn_by_prog[name].get(e["to"]):
                find.append("%s uses DD %s (%s) but no JCL step that runs it (%s) has a %s DD." % (
                    name, e["to"], e["kind"], ", ".join(sorted(step_prog[name])), e["to"]))
    unused = sorted(r[2] for r in b.rows if r[1] == "copybook" and r[3] == "unused")
    if unused:
        find.append("Copybooks not COPYed by any program in `app/cbl/`: %s." % ", ".join(unused))
    for r in b.rows:
        if "differs from member name" in r[4]:
            find.append("`%s`: %s." % (r[0], r[4][r[4].index("[") + 1:-1]))
    upper = sorted(r[0] for r in b.rows if re.search(r"\.(CBL|JCL|CPY)$", r[0]) and not r[0].startswith("app/cpy-bms"))
    find.append("Upper-case extensions: %s." % ", ".join("`%s`" % u for u in upper))
    for f in find:
        p("- " + f)
    return out.getvalue()


def e_is_dd(edges, name):
    return any(e["to"] == name and e["to_type"] == "dd" for e in edges)


def merge_ops(items):
    acc = collections.defaultdict(set)
    for it in items:
        prog, ops = it.split("(")
        acc[prog] |= set(ops.rstrip(")"))
    return ["%s(%s)" % (k, "".join(x for x in "RWUD" if x in v)) for k, v in sorted(acc.items())]


def update_readme(path, generated):
    begin, end = "<!-- BEGIN GENERATED: build_inventory.py -->", "<!-- END GENERATED: build_inventory.py -->"
    with open(path, encoding="utf-8") as fh:
        text = fh.read()
    if begin not in text or end not in text:
        raise SystemExit("%s lacks the generated-block markers" % path)
    head, rest = text.split(begin, 1)
    _, tail = rest.split(end, 1)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(head + begin + "\n" + generated + end + tail)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("source", help="path to a checkout of aws-mainframe-modernization-carddemo")
    ap.add_argument("--out", default=os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")),
                    help="output directory (default: docs/inventory)")
    ap.add_argument("--check", action="store_true", help="only run the coverage check against an existing inventory.csv")
    args = ap.parse_args(argv)
    if not os.path.isdir(os.path.join(args.source, "app")):
        ap.error("%s does not look like the CardDemo source repo (no app/ folder)" % args.source)
    if args.check:
        return 0 if check_coverage(args.source, args.out) else 1
    gone = missing_on_disk(args.source, list_files(args.source))
    if gone:
        for p in gone:
            print("ERROR: tracked by git but missing on disk: %s" % p, file=sys.stderr)
        return 1
    b = Builder(args.source)
    b.build()
    write_csv(os.path.join(args.out, "inventory.csv"), INVENTORY_COLUMNS, b.rows)
    write_csv(os.path.join(args.out, "dependencies.csv"), DEPENDENCY_COLUMNS,
              [[e[c] for c in DEPENDENCY_COLUMNS] for e in b.edges])
    readme = os.path.join(args.out, "README.md")
    if os.path.exists(readme):
        update_readme(readme, generated_markdown(b, args.source))
    print("wrote %d inventory rows, %d dependency edges" % (len(b.rows), len(b.edges)))
    return 0 if check_coverage(args.source, args.out) else 1


if __name__ == "__main__":
    sys.exit(main())
