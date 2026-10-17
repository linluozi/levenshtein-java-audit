#!/usr/bin/env python
"""Generate every figure in assets/ from data/bench.json.

Rules followed (each from a past mistake):

* **Plot ratios / speed-ups, never absolute milliseconds across cases.**
  Absolute wall-clock drifts by up to 4x across cases inside one process.
* **Draw speed-up, not ratio.** ``speedup = 1 / ratio`` keeps "long bar = win"
  readable; a raw ratio axis makes 0.98x look like a long bar on a log scale.
* **Label the round count and the observed range** on every timing figure, so a
  single run cannot be mistaken for a result.
* **Deterministic quantities (grid cells, row length, poison outcomes) get
  their own figures** — they carry no timing noise at all.

Figures
-------
``pollution.png``        same function, same process, 4x slower after another case
``attribution.png``      where the speed-up actually comes from
``real-workloads.png``   speed-up by shared-edge ratio, on real strings
``grid-cells.png``       deterministic: DP cells and row length
``poison.png``           which mutations the shipped test suite catches

Run:
    python assets/make_figures.py
"""

from __future__ import annotations

import json
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "assets"
DATA = ROOT / "data" / "bench.json"

INK = "#2b2119"
PAPER = "#faf6f0"
AMBER = "#c8892b"
TERRACOTTA = "#b4552f"
OLIVE = "#6b6b3a"
MUTED = "#8a7a68"
GRID = "#ddd2c4"

plt.rcParams.update(
    {
        "figure.facecolor": PAPER,
        "axes.facecolor": PAPER,
        "axes.edgecolor": INK,
        "axes.labelcolor": INK,
        "text.color": INK,
        "xtick.color": INK,
        "ytick.color": INK,
        "font.size": 10,
        "axes.grid": True,
        "grid.color": GRID,
        "grid.linewidth": 0.6,
        "axes.spines.top": False,
        "axes.spines.right": False,
        "savefig.facecolor": PAPER,
        # 图内只有几个中文样例串（苹果手机 / 苹果耳机），配一个 CJK 回退字体
        "font.sans-serif": ["DejaVu Sans", "Microsoft YaHei", "Noto Sans SC"],
        "axes.unicode_minus": False,
    }
)

DATA_D = json.loads(DATA.read_text(encoding="utf-8"))


# ---------------------------------------------------------------------------
# 1. 跨 case 污染
# ---------------------------------------------------------------------------


def fig_pollution() -> None:
    p = DATA_D["pollution"]
    clean = p["clean"]
    dirty = p["after_p1"]

    fig, ax = plt.subplots(figsize=(6.4, 4.0))
    xs = [0, 1]
    medians = [p["clean_median"], p["after_p1_median"]]

    for i, (vals, colour) in enumerate([(clean, OLIVE), (dirty, TERRACOTTA)]):
        jitter = np.linspace(-0.11, 0.11, len(vals))
        ax.scatter(np.full(len(vals), xs[i]) + jitter, vals, s=34, color=colour,
                   alpha=0.75, edgecolor=INK, linewidth=0.5, zorder=3)
        ax.hlines(medians[i], xs[i] - 0.22, xs[i] + 0.22, color=INK, linewidth=2.2, zorder=4)
        ax.text(xs[i], medians[i] * 1.06, f"{medians[i]:.0f} µs", ha="center",
                va="bottom", fontsize=10, fontweight="bold", color=INK)

    ax.annotate("", xy=(0.5, medians[1] * 0.99), xytext=(0.5, medians[0] * 0.99),
                arrowprops=dict(arrowstyle="<->", color=MUTED, linewidth=1.2))
    ax.text(0.53, (medians[0] * medians[1]) ** 0.5,
            f"{medians[1] / medians[0]:.1f}× slower",
            fontsize=11, color=TERRACOTTA, fontweight="bold", va="center")

    ax.set_xticks(xs)
    ax.set_xticklabels(["fresh process\n(P2 only)",
                        "same process,\nafter running P1"])
    ax.set_xlim(-0.45, 1.45)
    ax.set_ylabel("P2 call time (µs)")
    ax.set_ylim(0, max(dirty) * 1.18)
    ax.set_title(
        "The same 8 000 × 24 call, measured two ways\n"
        f"n = {len(clean)} fresh processes per column; black line = median",
        fontsize=11,
    )
    # 污染是双峰的：要么完全没事，要么掉进慢档。这一点必须写在图上。
    slow = [v for v in dirty if v > 400]
    if slow:
        ax.text(0.98, 0.05,
                f"{len(slow)} of {len(dirty)} polluted runs land in the slow mode "
                f"({min(slow):.0f}-{max(slow):.0f} µs);\n"
                f"the other {len(dirty) - len(slow)} match the fresh-process figure",
                transform=ax.transAxes, ha="right", va="bottom", fontsize=8.5,
                color=MUTED, style="italic")
    fig.tight_layout()
    fig.savefig(ASSETS / "pollution.png", dpi=150)
    plt.close(fig)


# ---------------------------------------------------------------------------
# 2. 拆臂归因
# ---------------------------------------------------------------------------


def fig_attribution() -> None:
    arms = DATA_D["arms"]
    cases = [("P1", "4 097 chars,\nlong shared edges"),
             ("P2", "8 000 × 24,\nno shared edges"),
             ("P3", "128 × 128,\nno shared edges")]
    steps = [("refactor", "tidy only", OLIVE),
             ("swap", "+ shorter-first", MUTED),
             ("trim", "+ trim edges", AMBER)]

    fig, ax = plt.subplots(figsize=(7.6, 4.2))
    width = 0.24
    for i, (key, label, colour) in enumerate(steps):
        vals, los, his = [], [], []
        for cid, _ in cases:
            a = arms[cid][key]
            vals.append(a["ratio"])
            los.append(a["ratio"] - a["lo"])
            his.append(a["hi"] - a["ratio"])
        x = np.arange(len(cases)) + (i - 1) * width
        bars = ax.bar(x, vals, width, color=colour, label=label,
                      edgecolor=INK, linewidth=0.6, zorder=3,
                      yerr=[los, his], error_kw=dict(ecolor=INK, lw=0.8, capsize=2))
        for b, v in zip(bars, vals):
            txt = f"{v:.2f}×" if v < 100 else f"{v:,.0f}×"
            ax.text(b.get_x() + b.get_width() / 2, b.get_height() * 1.25, txt,
                    ha="center", va="bottom", fontsize=8.5, color=INK)

    ax.axhline(1.0, color=MUTED, linestyle="--", linewidth=1.0, zorder=2)
    ax.set_yscale("log")
    ax.set_ylim(0.5, 3e5)
    ax.set_xticks(np.arange(len(cases)))
    ax.set_xticklabels([c[1] for c in cases])
    ax.set_ylabel("speed-up vs. the previous arm (log)")
    ax.legend(loc="upper left", frameon=False, fontsize=9, ncol=3)
    ax.set_title(
        "The gain comes from trimming edges, not from swapping arguments\n"
        "each bar = one arm vs. the arm before it; median of 3 runs, whiskers = range",
        fontsize=10,
    )
    fig.tight_layout()
    fig.savefig(ASSETS / "attribution.png", dpi=150)
    plt.close(fig)


# ---------------------------------------------------------------------------
# 3. 真实负载
# ---------------------------------------------------------------------------


def fig_real_workloads() -> None:
    real = [r for r in DATA_D["real"] if r["id"] != "P3"]
    rows = sorted(real, key=lambda r: r["ratio"])

    fig, ax = plt.subplots(figsize=(8.4, 5.2))
    ys = np.arange(len(rows))
    for y, r in zip(ys, rows):
        colour = TERRACOTTA if r["ratio"] < 3 else (AMBER if r["ratio"] < 20 else OLIVE)
        ax.barh(y, r["ratio"], height=0.6, color=colour, edgecolor=INK,
                linewidth=0.6, zorder=3)
        ax.hlines(y, r["lo"], r["hi"], color=INK, linewidth=1.4, zorder=4)
        txt = f"{r['ratio']:.2f}x" if r["ratio"] < 10 else f"{r['ratio']:.0f}x"
        ax.text(r["hi"] * 1.08, y, txt, va="center", fontsize=9,
                fontweight="bold" if r["ratio"] >= 20 else "normal")

    ax.set_yticks(ys)
    ax.set_yticklabels([f"{r['label']}  ·  {r['note']}" for r in rows], fontsize=9)
    ax.axvline(1.0, color=MUTED, linestyle="--", linewidth=1.0)
    ax.set_xscale("log")
    ax.set_xlim(0.9, 260)
    ax.set_xlabel("speed-up vs. upstream (log)")
    ax.set_title(
        "Real strings: the gain tracks how much of the input is a shared edge\n"
        "median of 3 interleaved runs; whiskers = range",
        fontsize=10,
    )
    fig.tight_layout()
    fig.savefig(ASSETS / "real-workloads.png", dpi=150)
    plt.close(fig)


# ---------------------------------------------------------------------------
# 4. DP 格数（确定性）
# ---------------------------------------------------------------------------


def fig_grid_cells() -> None:
    cells = [c for c in DATA_D["cells"] if c["upstream_cells"] > 1]
    cells.sort(key=lambda c: c["upstream_cells"])

    fig, ax = plt.subplots(figsize=(7.8, 4.6))
    ys = np.arange(len(cells))
    h = 0.36
    ax.barh(ys + h / 2, [c["upstream_cells"] for c in cells], height=h,
            color=MUTED, edgecolor=INK, linewidth=0.6, label="upstream", zorder=3)
    ax.barh(ys - h / 2, [max(c["new_cells"], 1) for c in cells], height=h,
            color=AMBER, edgecolor=INK, linewidth=0.6, label="with trimming", zorder=3)

    for y, c in zip(ys, cells):
        ratio = c["upstream_cells"] / max(c["new_cells"], 1)
        txt = "no change" if ratio < 1.05 else (
            f"{ratio:,.0f}x fewer" if ratio >= 10 else f"{ratio:.1f}x fewer")
        ax.text(c["upstream_cells"] * 1.15, y, txt, va="center", fontsize=8.5,
                color=MUTED if ratio < 1.05 else INK)

    ax.set_yticks(ys)
    ax.set_yticklabels([c["label"] for c in cells], fontsize=8.5)
    ax.set_xscale("log")
    ax.set_xlim(0.7, 1e8)
    ax.set_xlabel("DP cells visited (log)")
    ax.legend(loc="lower right", frameon=False, fontsize=9)
    ax.set_title(
        "Deterministic: how many grid cells each input actually touches\n"
        "counted, not timed - no measurement noise in this figure",
        fontsize=10,
    )
    fig.tight_layout()
    fig.savefig(ASSETS / "grid-cells.png", dpi=150)
    plt.close(fig)


# ---------------------------------------------------------------------------
# 5. 喂毒
# ---------------------------------------------------------------------------


def _short(desc: str, limit: int = 34) -> str:
    desc = desc.replace("★", "").strip()
    return desc if len(desc) <= limit else desc[: limit - 1] + "…"


def fig_poison() -> None:
    orig = DATA_D["poison"]["original"]
    fixed = DATA_D["poison"]["fixed"]

    # id 0 是对照组（不改，必须通过），不计入毒株
    mutants = [r for r in orig if r["id"] != 0]
    groups = {"caught": [], "equivalent": [], "blind": []}
    for row in mutants:
        if row["result"].startswith("抓到"):
            groups["caught"].append(row)
        elif row["id"] in (17, 18, 19, 20):
            groups["blind"].append(row)
        else:
            groups["equivalent"].append(row)

    fig, axes = plt.subplots(1, 2, figsize=(11.4, 4.8),
                             gridspec_kw={"width_ratios": [1, 1.25]})

    ax = axes[0]
    sizes = [len(groups["caught"]), len(groups["equivalent"]), len(groups["blind"])]
    labels = ["caught", "equivalent\n(result still right)", "genuine blind spot"]
    colours = [OLIVE, MUTED, TERRACOTTA]
    bars = ax.bar(range(3), sizes, color=colours, edgecolor=INK, linewidth=0.6, zorder=3)
    for b, v in zip(bars, sizes):
        ax.text(b.get_x() + b.get_width() / 2, v + 0.25, str(v), ha="center",
                fontsize=13, fontweight="bold")
    ax.set_xticks(range(3))
    ax.set_xticklabels(labels, fontsize=9)
    ax.set_ylabel("mutants")
    ax.set_ylim(0, max(sizes) + 2.2)
    ax.set_title(f"The shipped suite: {len(mutants)} mutants, "
                 f"{len(groups['blind'])} never noticed", fontsize=11)

    # 右：修复版新增的结构断言抓住了什么
    ax = axes[1]
    key_rows = [r for r in fixed if r["id"] in (0, 1, 2, 3, 10, 11)]
    ys = np.arange(len(key_rows))
    for y, r in zip(ys, key_rows):
        caught = r["result"].startswith("抓到")
        ax.barh(y, 1, color=TERRACOTTA if caught else OLIVE, alpha=0.9,
                edgecolor=INK, linewidth=0.6, zorder=3)
        ax.text(0.03, y, ("caught  " if caught else "passes  ") + _short(r["desc"], 40),
                va="center", fontsize=9, color=PAPER if caught else INK,
                fontweight="bold" if caught else "normal")
    ax.set_xlim(0, 1)
    ax.set_ylim(-0.7, len(key_rows) - 0.3)
    ax.set_yticks([])
    ax.set_xticks([])
    ax.grid(False)
    ax.set_title("With structural assertions added\n"
                 "the two \"optimisation removed\" mutants are caught",
                 fontsize=11)

    fig.tight_layout()
    fig.savefig(ASSETS / "poison.png", dpi=150)
    plt.close(fig)


if __name__ == "__main__":
    ASSETS.mkdir(exist_ok=True)
    fig_pollution()
    fig_attribution()
    fig_real_workloads()
    fig_grid_cells()
    fig_poison()
    print("wrote 5 figures to", ASSETS)
