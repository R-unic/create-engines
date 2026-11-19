"""Lists the live mutants of a PIT report (build/reports/pitest/mutations.xml), grouped by class and method.

Usage: python tools/pit_survivors.py [path/to/mutations.xml] [--lines]

Verification track item 1: killing survivors is how the mutation ratchet (src/smoke/mutation-ratchet.txt) comes down. This reads
the report PIT already wrote; it runs nothing.
"""
import sys
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    path = args[0] if args else 'build/reports/pitest/mutations.xml'
    show_lines = '--lines' in sys.argv
    live = defaultdict(list)
    total = Counter()
    for m in ET.parse(path).getroot().iter('mutation'):
        status = m.get('status')
        total[status] += 1
        if status not in ('SURVIVED', 'NO_COVERAGE'):
            continue
        cls = m.findtext('mutatedClass').rsplit('.', 1)[-1]
        method = m.findtext('mutatedMethod')
        mutator = m.findtext('mutator').rsplit('.', 1)[-1]
        line = m.findtext('lineNumber')
        desc = m.findtext('description')
        live[(cls, method)].append((int(line), mutator, desc, status))
    print({k: v for k, v in total.items()})
    by_class = Counter()
    for (cls, _), items in live.items():
        by_class[cls] += len(items)
    for cls, n in by_class.most_common():
        print(f'{n:4d}  {cls}')
        for (c, method), items in sorted(live.items(), key=lambda kv: -len(kv[1])):
            if c != cls:
                continue
            print(f'      {len(items):3d}  {method}')
            if show_lines:
                for line, mutator, desc, status in sorted(items):
                    print(f'            L{line}  {status[:2]}  {mutator}: {desc}')


main()
