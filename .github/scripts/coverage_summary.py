import csv
import sys
from collections import defaultdict

report = sys.argv[1] if len(sys.argv) > 1 else "coverage-report/target/site/jacoco-aggregate/jacoco.csv"
totals = defaultdict(lambda: [0, 0, 0, 0])
with open(report) as f:
    for row in csv.DictReader(f):
        module = row["GROUP"].split("/")[-1]
        totals[module][0] += int(row["LINE_MISSED"])
        totals[module][1] += int(row["LINE_COVERED"])
        totals[module][2] += int(row["BRANCH_MISSED"])
        totals[module][3] += int(row["BRANCH_COVERED"])


def percent(missed, covered):
    total = missed + covered
    return f"{covered / total * 100:.1f}%" if total else "—"


overall = [sum(values[i] for values in totals.values()) for i in range(4)]
print("## Test coverage")
print()
print("| Module | Lines | Branches |")
print("|---|---|---|")
for module, values in sorted(totals.items()):
    print(f"| {module} | {percent(values[0], values[1])} | {percent(values[2], values[3])} |")
print(f"| **Total** | **{percent(overall[0], overall[1])}** | **{percent(overall[2], overall[3])}** |")
