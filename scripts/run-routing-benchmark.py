#!/usr/bin/env python3
"""Replay saved routing matrices through the production Java algorithms."""
import argparse
import os
from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("fixtures", nargs="?", type=Path,
                        default=ROOT / "evaluation/routing/fixtures")
    parser.add_argument("output", nargs="?", type=Path,
                        default=ROOT / "evaluation/routing/results.csv")
    args = parser.parse_args()

    fixtures = args.fixtures.resolve()
    output = args.output.resolve()
    if not fixtures.is_dir():
        parser.error(f"Fixture directory does not exist: {fixtures}")
    output.parent.mkdir(parents=True, exist_ok=True)

    wrapper = ROOT / "BACK" / ("mvnw.cmd" if os.name == "nt" else "mvnw")
    command = [str(wrapper), "-q", "-f", str(ROOT / "BACK/pom.xml"),
               "-Dtest=RoutingBenchmarkTest", f"-Droute.fixtures={fixtures}",
               f"-Droute.output={output}", "test"]
    if os.name == "nt":
        command = ["cmd", "/d", "/c", *command]
    subprocess.run(command, cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
