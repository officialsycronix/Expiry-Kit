#!/usr/bin/env python3
"""
Expiry-Kit status — versions.txt ka readable dashboard.

Har app line ke liye dikhata hai:
  - package, version, app name
  - kitne din pehle line update hui (git blame se)
  - cycle ke kitne din bache (default 30 din)

Usage (project folder me):
  python3 status.py
  CYCLE=15 python3 status.py   # custom cycle days
"""

import os
import re
import subprocess
import sys
from datetime import datetime, timezone

CYCLE = int(os.environ.get("CYCLE", "30"))
PKG_RE = re.compile(r"^([A-Za-z0-9._]+)\s*=\s*(\d+)")


def sh(*args: str) -> str:
    return subprocess.check_output(args, text=True, stderr=subprocess.DEVNULL)


def git_blame_dates(path: str) -> dict[int, int]:
    """line_no (1-based) -> unix ts"""
    out = sh("git", "blame", "--porcelain", "--", path)
    dates: dict[int, int] = {}
    ts = None
    line_no = 0
    for ln in out.splitlines():
        if ln.startswith("author-time "):
            ts = int(ln.split(" ", 1)[1])
        elif ln.startswith("\t"):
            line_no += 1
            if ts is not None:
                dates[line_no] = ts
    return dates


def bar(pct: int, width: int = 12) -> str:
    pct = max(0, min(100, pct))
    filled = round(width * pct / 100)
    return "█" * filled + "░" * (width - filled)


def main() -> int:
    # script chaho repo folder se chalao ya kahin se — khud repo dhundh lega
    here = os.path.dirname(os.path.abspath(__file__))
    repo = here if os.path.isdir(os.path.join(here, ".git")) else \
        "/data/data/com.termux/files/tmp/opencode/Expiry-Kit"
    if not os.path.isdir(os.path.join(repo, ".git")):
        print("error: Expiry-Kit repo nahi mila")
        return 1
    os.chdir(repo)

    try:
        sh("git", "fetch", "-q", "origin", "main")
        sh("git", "merge", "--ff-only", "-q", "origin/main")
    except subprocess.CalledProcessError:
        print("warn: git pull failed (offline?) — purana local data dikh raha hai")

    try:
        blame = git_blame_dates("versions.txt")
        text = sh("git", "show", "HEAD:versions.txt")
    except subprocess.CalledProcessError as e:
        print("error: versions.txt nahi mila:", e)
        return 1

    now = datetime.now(timezone.utc)
    rows = []
    for idx, line in enumerate(text.splitlines(), 1):
        raw = line.strip()
        if not raw or raw.startswith("#"):
            continue
        m = PKG_RE.match(raw)
        if not m:
            continue
        pkg, ver = m.group(1), int(m.group(2))
        name = raw.split("#", 1)[1].strip() if "#" in raw else ""
        ts = blame.get(idx)
        days = (now.timestamp() - ts) / 86400 if ts else None
        days_i = int(days) if days is not None else -1
        left = max(0, CYCLE - days_i) if days_i >= 0 else CYCLE
        pct = int(100 * days_i / CYCLE) if CYCLE > 0 and days_i >= 0 else 0
        rows.append((name, pkg, ver, days_i, left, pct))

    print(f"\n  EXPIRY-KIT  ·  today {now:%Y-%m-%d}  ·  cycle {CYCLE}d")
    print("  " + "─" * 66)
    if not rows:
        print("  (versions.txt me koi app line nahi)")
    for name, pkg, ver, days_i, left, pct in rows:
        label = (name or pkg)[:22]
        d = f"{days_i}d ago" if days_i >= 0 else "?d ago"
        l = f"{left}d left"
        warn = "  ⚠️ UPDATE DUE" if left == 0 else ""
        print(f"  {label:<22} v{ver:<10} {d:>8}  {bar(pct)}  {l}{warn}")
    print("  " + "─" * 66)
    print(f"  total apps: {len(rows)}   ·   file: versions.txt (main)\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
