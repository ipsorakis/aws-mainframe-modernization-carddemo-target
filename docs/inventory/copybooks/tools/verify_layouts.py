#!/usr/bin/env python3
"""Cross-check the generated layout specs against GnuCOBOL and git ls-files.

For every copybook spec in ../layouts this script
  1. compiles a stub program with cobc that COPYs the member and DISPLAYs, for every
     named data item, LENGTH OF and its byte offset (ADDRESS OF item - ADDRESS OF
     record), then compares both with the spec;
  2. checks that every file `git ls-files app/cpy` returns is either a layout spec
     or listed in gen_layouts.NON_LAYOUT_COPYBOOKS, and that every spec's source
     file is tracked;
  3. checks that the specs are up to date (re-parses the copybook and compares).

Usage:
  python3 verify_layouts.py --source ~/repos/aws-mainframe-modernization-carddemo
Requires GnuCOBOL (`sudo apt-get install -y gnucobol`). Exit code 0 = all checks pass.
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_layouts as gen  # noqa: E402

A = " " * 7   # area A (column 8)
B = " " * 11  # area B (column 12)


def stub(copybook: str, records: list[dict]) -> tuple[str, list[tuple]]:
    probes, lines = [], [
        A + "IDENTIFICATION DIVISION.",
        A + "PROGRAM-ID. VERIFY.",
        A + "DATA DIVISION.",
        A + "WORKING-STORAGE SECTION.",
        A + "01 WS-BASE USAGE POINTER.",
        A + "01 WS-BASE-N REDEFINES WS-BASE PIC S9(18) COMP-5.",
        A + "01 WS-ITEM USAGE POINTER.",
        A + "01 WS-ITEM-N REDEFINES WS-ITEM PIC S9(18) COMP-5.",
        A + "01 WS-OFF PIC 9(9).",
        A + "01 WS-LEN PIC 9(9).",
        A + f"COPY {copybook}.",
        A + "PROCEDURE DIVISION.",
    ]
    for rec in records:
        lines += [B + "SET WS-BASE TO ADDRESS OF", B + "    " + rec["name"]]
        for f in rec["fields"]:
            if f["filler"]:
                continue
            ref = [B + "    " + f["name"]]
            if f["level"] != 1:
                ref.append(B + "    OF " + rec["name"])
            if f["occurs_path"]:
                ref.append(B + "    (" + " ".join("1" * len(f["occurs_path"])) + ")")
            probes.append((rec["name"], f["name"], f["offset"], f["length"]))
            n = len(probes)
            lines += [B + "SET WS-ITEM TO ADDRESS OF"] + ref
            lines += [B + "COMPUTE WS-OFF = WS-ITEM-N - WS-BASE-N"]
            lines += [B + "MOVE LENGTH OF"] + ref + [B + "    TO WS-LEN"]
            lines += [B + f"DISPLAY 'P{n:04d} ' WS-OFF ' ' WS-LEN"]
    lines += [B + "STOP RUN."]
    return "\n".join(lines) + "\n", probes


def check_cobc(src: Path, spec: dict, work: Path) -> list[str]:
    errors = []
    cb = spec["copybook"]
    code, probes = stub(cb, spec["records"])
    prog = work / f"V{cb}.cbl"
    prog.write_text(code)
    exe = work / f"V{cb}"
    cmd = ["cobc", "-x", "-std=ibm", f"-ftab-width={gen.TAB_WIDTH}",
           "-I", str(src / "app/cpy"), "-o", str(exe), str(prog)]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode:
        return [f"{cb}: cobc failed:\n{res.stderr}"]
    out = subprocess.run([str(exe)], capture_output=True, text=True, check=True).stdout.split("\n")
    got = {}
    for line in out:
        if line.startswith("P"):
            tag, off, ln = line.split()
            got[int(tag[1:])] = (int(off), int(ln))
    for i, (rec, name, off, ln) in enumerate(probes, 1):
        if got.get(i) != (off, ln):
            errors.append(f"{cb} {rec}.{name}: spec offset/length {(off, ln)} != cobc {got.get(i)}")
    print(f"{cb:9s} cobc checked {len(probes):3d} items in {len(spec['records'])} record(s): "
          f"{'OK' if not errors else 'FAIL'}  ("
          + ", ".join(f"{r['name']}={r['length']}" for r in spec["records"]) + ")")
    return errors


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--source", required=True, type=Path)
    src = ap.parse_args().source.resolve()
    errors = []

    tracked = {Path(p).stem.upper(): p for p in gen.git_ls(src, "app/cpy")}
    classified = set(gen.LAYOUT_COPYBOOKS) | set(gen.NON_LAYOUT_COPYBOOKS)
    for stem in sorted(set(tracked) - classified):
        errors.append(f"app/cpy file not classified: {tracked[stem]}")
    for stem in sorted(classified - set(tracked)):
        errors.append(f"classified copybook not in git ls-files app/cpy: {stem}")
    specs = sorted(gen.LAYOUT_DIR.glob("*.json"))
    spec_names = {p.stem for p in specs if not p.stem.startswith("_")}
    if spec_names != set(gen.LAYOUT_COPYBOOKS):
        errors.append(f"layout specs {sorted(spec_names)} != LAYOUT_COPYBOOKS")
    print(f"git ls-files app/cpy: {len(tracked)} files = {len(gen.LAYOUT_COPYBOOKS)} layout specs "
          f"+ {len(gen.NON_LAYOUT_COPYBOOKS)} non-layout members")

    with tempfile.TemporaryDirectory() as tmp:
        for cb in gen.LAYOUT_COPYBOOKS:
            spec = json.loads((gen.LAYOUT_DIR / f"{cb}.json").read_text())
            if spec["source_file"] != tracked.get(cb):
                errors.append(f"{cb}: source_file {spec['source_file']} not tracked")
            fresh = [gen.flatten(r) for r in gen.build_records(src / spec["source_file"])]
            if fresh != [r["fields"] for r in spec["records"]]:
                errors.append(f"{cb}: spec is stale, rerun gen_layouts.py")
            errors += check_cobc(src, spec, Path(tmp))

    for e in errors:
        print("ERROR:", e)
    print("ALL CHECKS PASSED" if not errors else f"{len(errors)} error(s)")
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
