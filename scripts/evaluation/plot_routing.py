#!/usr/bin/env python3
"""Plot one provenance group of saved routing benchmark results; never mix road and synthetic costs."""
import argparse
import csv
import math
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=Path)
    parser.add_argument("--output", type=Path, required=True, help="PNG or SVG output path")
    parser.add_argument("--source", help="Required when the CSV contains different data origins")
    args = parser.parse_args()
    with args.csv.open(newline="") as file:
        rows = list(csv.DictReader(file))
    sources = {row["source"] for row in rows}
    if not rows or (len(sources) > 1 and not args.source):
        parser.error("Provide nonempty results from one source, or select --source explicitly.")
    source = args.source or next(iter(sources))
    rows = [row for row in rows if row["source"] == source]
    if not rows:
        parser.error("No rows match the requested source.")
    scenarios = list(dict.fromkeys(row["scenario"] for row in rows))
    methods = ("MANUAL", "NN", "NN_2OPT", "EXACT")
    index = {(row["scenario"], row["algorithm"]): row for row in rows}
    if len(index) != len(rows):
        parser.error("Duplicate scenario/algorithm rows; select one benchmark run.")
    for row in rows:
        if row["algorithm"] not in methods or not all(math.isfinite(float(row[key])) and float(row[key]) >= 0 for key in ("travel_s", "distance_m")):
            parser.error("Invalid method or non-finite/negative route cost.")
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    figure, axes = plt.subplots(1, 2, figsize=(max(10, len(scenarios) * 2.4), 4.8))
    width = .18
    colors = ("#64748b", "#0284c7", "#16a34a", "#9333ea")
    for axis, metric, label in zip(axes, ("travel_s", "distance_m"), ("Travel time (seconds)", "Road distance (metres)")):
        for offset, (method, color) in enumerate(zip(methods, colors)):
            positions, values = [], []
            for i, scenario in enumerate(scenarios):
                row = index.get((scenario, method))
                if row is not None:
                    positions.append(i + (offset - 1.5) * width)
                    values.append(float(row[metric]))
            bars = axis.bar(positions, values, width, label=method.replace("NN_2OPT", "NN + 2-opt"), color=color)
            if len(scenarios) <= 4:
                axis.bar_label(bars, fmt="%.0f", fontsize=8, padding=3)
        axis.set_xticks(range(len(scenarios)), [Path(s).stem.replace("directed-", "").replace("-synthetic", "") for s in scenarios], rotation=20 if len(scenarios) > 3 else 0)
        axis.set_ylabel(label)
        axis.grid(axis="y", alpha=.2)
        axis.set_axisbelow(True)
        axis.margins(y=.16)
    synthetic = "SYNTHETIC" in source.upper() or "SIMULATED" in source.upper()
    if synthetic:
        axes[1].set_ylabel("Synthetic distance cost (metres)")
    figure.suptitle("Routing validation — synthetic matrices" if synthetic else f"Routing comparison — {source}", fontweight="bold")
    handles, labels = axes[0].get_legend_handles_labels()
    figure.legend(handles, labels, loc="lower center", ncol=4, bbox_to_anchor=(.5, .03), frameon=False)
    figure.text(.5, .01, "Development fixtures only; no municipality performance claim." if synthetic else "Same saved directed matrices; exact results apply only to small instances. Service time is separate.", ha="center", fontsize=8)
    figure.tight_layout(rect=(0, .12, 1, .94))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    figure.savefig(args.output, dpi=200, facecolor="white")
    plt.close(figure)
    print(f"Wrote {args.output} ({len(scenarios)} scenarios, source={source})")


if __name__ == "__main__":
    main()
