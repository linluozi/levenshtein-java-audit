#!/usr/bin/env python3
"""生成基准数据 JSON。所有数字由本脚本实跑得出，不手写。

用法: python run_bench.py
输出: data/bench.json
"""
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
CLASSES = os.path.join(ROOT, "classes")
JAVA = "java"


def java_cmd(*args):
    return [JAVA, "-Dfile.encoding=UTF-8", "-cp", CLASSES] + list(args)


def compile_probes():
    """编译 audit/*.java 到 classes/。仓库不提交 class 文件，所以每次自己编。"""
    os.makedirs(CLASSES, exist_ok=True)
    sources = sorted(
        os.path.join(ROOT, "audit", name)
        for name in os.listdir(os.path.join(ROOT, "audit"))
        if name.endswith(".java")
    )
    if not sources:
        raise SystemExit("audit/ has no .java files")
    result = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", CLASSES] + sources,
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    if result.returncode != 0:
        raise SystemExit(f"javac failed:\n{result.stdout}\n{result.stderr}")
    print(f"compiled {len(sources)} probes -> {CLASSES}", file=sys.stderr)


def run(*args):
    # JVM 在 Windows 下会用 GBK 往 stderr 写诊断信息，必须容错解码
    out = subprocess.run(java_cmd(*args), capture_output=True, text=True,
                         encoding="utf-8", errors="replace")
    if out.returncode != 0:
        raise RuntimeError(f"java {' '.join(args)} failed: {(out.stderr or '')[:400]}")
    return out.stdout or ""


def parse_ratio(line):
    """P1 | P vs Q | iters=2/23804 | P=1.2us Q=0.5us | P/Q = 12.345 [1.0, 2.0]"""
    m = re.search(r"([A-Za-z0-9]+) vs ([A-Za-z0-9]+).*?= ([\d.]+) \[([\d.]+), ([\d.]+)\]", line)
    if not m:
        raise ValueError(f"cannot parse: {line}")
    return {
        "arm_x": m.group(1),
        "arm_y": m.group(2),
        "ratio": float(m.group(3)),
        "lo": float(m.group(4)),
        "hi": float(m.group(5)),
    }


def median_ratio(case, x, y, repeats=3):
    vals = []
    for _ in range(repeats):
        line = [l for l in run("audit.Two", case, x, y).splitlines() if " vs " in l][-1]
        vals.append(parse_ratio(line))
    vals.sort(key=lambda d: d["ratio"])
    return vals[len(vals) // 2]


def main():
    compile_probes()
    os.makedirs(os.path.join(ROOT, "data"), exist_ok=True)
    data = {}

    print("== 1. 跨 case 污染实验 ==", file=sys.stderr)
    clean, polluted = [], []
    for _ in range(12):
        out = run("audit.Pollute2", "clean")
        clean.append(float(re.search(r"P2=([\d.]+)us", out).group(1)))
        out = run("audit.Pollute2", "p1-opt")
        polluted.append(float(re.search(r"P2=([\d.]+)us", out).group(1)))
    clean.sort()
    polluted.sort()
    slow = [v for v in polluted if v > 400]
    fast = [v for v in polluted if v <= 400]
    data["pollution"] = {
        "clean": clean,
        "after_p1": polluted,
        "clean_median": clean[len(clean) // 2],
        "after_p1_median": polluted[len(polluted) // 2],
        "slow_mode_count": len(slow),
        "slow_mode_range": [min(slow), max(slow)] if slow else None,
        "unaffected_count": len(fast),
        "ratio_when_slow": (min(slow) / clean[len(clean) // 2]) if slow else None,
        "ratio_when_slow_max": (max(slow) / clean[len(clean) // 2]) if slow else None,
    }
    print(f"  clean {data['pollution']['clean_median']:.1f}us  "
          f"polluted: {len(slow)}/{len(polluted)} slow "
          f"({min(slow):.0f}-{max(slow):.0f}us), {len(fast)} unaffected", file=sys.stderr)

    print("== 2. 拆臂归因 ==", file=sys.stderr)
    arms = {}
    for case in ["P1", "P2", "P3"]:
        arms[case] = {
            "refactor": median_ratio(case, "A", "A2"),
            "swap": median_ratio(case, "A2", "B"),
            "trim": median_ratio(case, "A2", "C"),
            "total": median_ratio(case, "A", "D"),
        }
        print(f"  {case}: trim {arms[case]['trim']['ratio']:.2f}x  "
              f"swap {arms[case]['swap']['ratio']:.3f}x", file=sys.stderr)
    data["arms"] = arms

    print("== 3. 真实负载 ==", file=sys.stderr)
    real_cases = [
        ("R1", "file path", "long shared prefix and suffix"),
        ("R2", "URL", "one digit differs"),
        ("R3", "english sentence", "one word differs"),
        ("R5", "english word pair", "implementation / documentation"),
        ("R6", "chinese, 3 chars", "one character differs"),
        ("R7", "chinese, 4 chars", "all four differ"),
        ("R8", "version string", "one patch level differs"),
        ("R10", "name + city row", "city differs"),
        ("T1", "log line", "similar"),
        ("T2", "log line", "dissimilar"),
        ("P3", "128x128", "no shared edges (control)"),
    ]
    real = []
    for cid, label, note in real_cases:
        r = median_ratio(cid, "P", "Q")
        real.append({"id": cid, "label": label, "note": note, "ratio": r["ratio"],
                     "lo": r["lo"], "hi": r["hi"]})
        print(f"  {cid:4s} {r['ratio']:8.2f}x", file=sys.stderr)
    data["real"] = real

    print("== 4. DP 格数（确定性） ==", file=sys.stderr)
    cells_out = run("audit.Cells")
    cells = []
    for line in cells_out.splitlines()[1:]:
        parts = line.split()
        if len(parts) < 6:
            continue
        cells.append({
            "label": " ".join(parts[:-5]),
            "upstream_cells": int(parts[-5]),
            "new_cells": int(parts[-4]),
            "upstream_row": int(parts[-2]),
            "new_row": int(parts[-1]),
        })
    data["cells"] = cells
    print(f"  {len(cells)} cases", file=sys.stderr)

    print("== 5. 喂毒 ==", file=sys.stderr)
    poison = {"original": [], "fixed": []}
    for line in run("audit.Poison").splitlines():
        m = re.match(r"\[\s*(\d+)\]\s+(.*?)\s+->\s+(.*)", line)
        if m:
            poison["original"].append({"id": int(m.group(1)), "desc": m.group(2).strip(), "result": m.group(3).strip()})
    for line in run("audit.Poison2").splitlines():
        m = re.match(r"\[\s*(\d+)\]\s+(.*?)\s+->\s+(.*)", line)
        if m:
            poison["fixed"].append({"id": int(m.group(1)), "desc": m.group(2).strip(), "result": m.group(3).strip()})
    data["poison"] = poison
    caught = sum(1 for r in poison["original"] if r["id"] != 0 and r["result"].lower().startswith("caught"))
    total = sum(1 for r in poison["original"] if r["id"] != 0)
    print(f"  原包 {total} 投 / 抓到 {caught}；修复版 {len(poison['fixed'])} 投", file=sys.stderr)

    path = os.path.join(ROOT, "data", "bench.json")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print(f"\nwrote {path}", file=sys.stderr)


if __name__ == "__main__":
    main()
