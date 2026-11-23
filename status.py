#!/usr/bin/env python3
"""
Expiry-Kit status — versions.txt ka readable dashboard.

Teen sections:
  1) RECENT UPDATES — last N din me update hui apps (git blame se), din ke saath
  2) DUE SOON       — 25d+ purani lines (cycle lagbhag khatam, abhi update karo)
  3) OTHER APPS     — bich ka baaki sab

Har line: app name, version, kitne din pehle update hui, cycle ke kitne din bache.

Usage:
  python3 status.py
  CYCLE=15 python3 status.py            # custom cycle days
  RECENT_DAYS=7 python3 status.py       # recent window (default 3)
  DUE_DAYS=25 python3 status.py         # due threshold (default 25, 0 = band)
"""

import os
import re
import subprocess
import sys
from datetime import datetime, timezone

CYCLE = int(os.environ.get("CYCLE", "30"))
RECENT_DAYS = int(os.environ.get("RECENT_DAYS", "3"))
DUE_DAYS = int(os.environ.get("DUE_DAYS", "25"))
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


def row_line(row) -> str:
    name, pkg, ver, days_i, left, pct = row
    label = (name or pkg)[:22]
    d = f"{days_i}d ago" if days_i >= 0 else "?d ago"
    l = f"{left}d left"
    warn = "  ⚠️ UPDATE DUE" if left == 0 else ""
    return f"  {label:<22} v{ver:<10} {d:>8}  {bar(pct)}  {l}{warn}"


def print_section(title: str, rows) -> None:
    print(f"  {title}")
    print("  " + "─" * 66)
    if not rows:
        print("  (koi app nahi)")
    for r in rows:
        print(row_line(r))


def main() -> int:
    # script chaho repo folder se chalao ya kahin se — khud repo dhundh lega
    here = os.path.dirname(os.path.abspath(__file__))
    repo = here if os.path.isdir(os.path.join(here, ".git")) else \
        os.path.expanduser("~/tmp/Expiry-Kit")
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

    # sections — teeno mutually exclusive
    recent = [r for r in rows if 0 <= r[3] <= RECENT_DAYS]
    due = [r for r in rows if DUE_DAYS > 0 and r[3] >= DUE_DAYS]
    others = [r for r in rows
              if not (0 <= r[3] <= RECENT_DAYS) and not (DUE_DAYS > 0 and r[3] >= DUE_DAYS)]

    recent.sort(key=lambda r: (r[3], (r[0] or r[1]).lower()))          # naya pehle
    due.sort(key=lambda r: (-r[3], (r[0] or r[1]).lower()))            # sabse purana pehle
    others.sort(key=lambda r: (r[3] < 0, -max(r[3], 0), (r[0] or r[1]).lower()))

    print(f"\n  EXPIRY-KIT  ·  today {now:%Y-%m-%d}  ·  cycle {CYCLE}d"
          f"  ·  recent {RECENT_DAYS}d"
          + (f"  ·  due {DUE_DAYS}d+" if DUE_DAYS > 0 else ""))
    if recent:
        print()
        print_section(f"RECENT UPDATES  ({len(recent)})", recent)
    if due:
        print()
        print_section(f"DUE SOON  ≥{DUE_DAYS}d old  ({len(due)})", due)
    if others:
        print()
        print_section(f"OTHER APPS  ({len(others)})", others)
    if not (recent or due or others):
        print("  (versions.txt me koi app line nahi)")
    print("  " + "─" * 66)
    print(f"  total apps: {len(rows)}   ·   recent: {len(recent)}"
          f"   ·   due: {len(due)}   ·   file: versions.txt (main)\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
