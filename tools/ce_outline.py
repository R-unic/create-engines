"""Shows where things are in a big file, so only the needed lines are read.

    python tools/ce_outline.py EngineController                      declarations with line ranges (Java)
    python tools/ce_outline.py EngineController -m tick updateWear   just those methods' line ranges
    python tools/ce_outline.py EngineController -g battery           declarations whose body mentions a word, with hit lines
    python tools/ce_outline.py docs/ROADMAP.md                       headings with line ranges (Markdown)

FILE is a path, or a class name looked up under src/. Then read the range with the Read tool (offset and limit).
"""
import argparse
import glob
import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TYPE = re.compile(r'\b(class|interface|record|enum)\s+(\w+)')
NOT_METHOD = {'if', 'for', 'while', 'switch', 'catch', 'synchronized', 'return', 'new', 'else', 'do', 'try'}


def find(name):
    if os.path.isfile(name):
        return name
    if os.path.isfile(os.path.join(REPO, name)):
        return os.path.join(REPO, name)
    hits = glob.glob(os.path.join(REPO, 'src', '**', os.path.basename(name).removesuffix('.java') + '.java'),
                     recursive=True)
    if len(hits) != 1:
        sys.exit(f'{len(hits)} files match {name}' + ''.join('\n  ' + h for h in hits))
    return hits[0]


def blank(source):
    """The source with comments and string contents blanked, line numbers kept, so braces can be counted."""
    pattern = re.compile(r'//[^\n]*|/\*.*?\*/|"""(?:\\.|.)*?"""|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])+\'', re.DOTALL)
    return pattern.sub(lambda m: re.sub(r'[^\n]', ' ', m.group(0)), source)


def java_outline(source):
    """(start line, end line, depth, label, name) for every type and method."""
    clean = blank(source)
    found, stack = [], []
    head_start = 0
    parens = 0
    for i, ch in enumerate(clean):
        if ch == '(':
            parens += 1
        elif ch == ')':
            parens -= 1
        elif ch == '{':
            head = clean[head_start:i]
            entry = None
            stripped = re.sub(r'@\w+(\([^()]*\))?', ' ', head).strip()
            in_type = not stack or (stack[-1] is not None and stack[-1][4])
            if parens == 0 and stripped:
                kind = TYPE.search(stripped.split('(')[0])
                method = re.search(r'(\w+)\s*\(', stripped)
                name = None
                if kind:
                    name = kind.group(2)
                elif in_type and method and method.group(1) not in NOT_METHOD and '=' not in stripped.split('(')[0]:
                    name = method.group(1)
                if name:
                    # The declaration starts at the head's first line that is not blank or an annotation
                    offset = len(head) - len(head.lstrip())
                    start = clean.count('\n', 0, head_start + offset) + 1
                    label = ' '.join(stripped.split())
                    entry = [start, None, sum(1 for s in stack if s), label[:130], bool(kind), name]
            stack.append(entry)
            head_start = i + 1
        elif ch == '}':
            if stack:
                entry = stack.pop()
                if entry:
                    entry[1] = clean.count('\n', 0, i) + 1
                    found.append(entry)
            head_start = i + 1
        elif ch == ';' and parens == 0:
            head_start = i + 1
    found.sort(key=lambda e: e[0])
    return [(e[0], e[1], e[2], e[3], e[5]) for e in found]


def markdown_outline(lines):
    heads, fenced = [], False
    for i, line in enumerate(lines, 1):
        if line.startswith('```'):
            fenced = not fenced
        m = None if fenced else re.match(r'(#{1,6})\s+(.*)', line)
        if m:
            heads.append((i, len(m.group(1)), m.group(2)))
    out = []
    for k, (start, level, title) in enumerate(heads):
        end = next((h[0] - 1 for h in heads[k + 1:] if h[1] <= level), len(lines))
        out.append((start, end, level - 1, title[:130], title))
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('file')
    parser.add_argument('-m', '--method', nargs='+', help='only declarations (or headings) with these names')
    parser.add_argument('-g', '--grep', help='only declarations holding a line that matches this regex')
    args = parser.parse_args()
    sys.stdout.reconfigure(errors='replace')
    path = find(args.file)
    with open(path, encoding='utf-8-sig', errors='replace') as f:
        source = f.read()
    lines = source.split('\n')
    markdown = path.endswith('.md')
    items = markdown_outline(lines) if markdown else java_outline(source)
    print(f'{os.path.relpath(path, REPO)}  ({len(lines)} lines)')
    if args.method:
        wanted = [w.lower() for w in args.method]
        items = [i for i in items if any((w in i[4].lower()) if markdown else w == i[4].lower() for w in wanted)]
    hits = {}
    if args.grep:
        regex = re.compile(args.grep, re.IGNORECASE)
        matched = [n for n, line in enumerate(lines, 1) if regex.search(line)]
        for n in matched:
            # The smallest declaration that holds the line
            holders = [i for i in items if i[0] <= n <= i[1]]
            key = min(holders, key=lambda i: i[1] - i[0]) if holders else None
            hits.setdefault(key, []).append(n)
        if None in hits:
            print(f'  outside any declaration: lines {", ".join(map(str, hits[None][:20]))}')
        items = [i for i in items if i in hits]
    for item in items:
        start, end, depth, label, _ = item
        note = f'   hits: {", ".join(map(str, hits[item][:12]))}' if item in hits else ''
        print(f'{start:5}-{end:<5} {"  " * depth}{label}{note}')
    if not items:
        print('  nothing matched')


if __name__ == '__main__':
    main()
