"""Applies many exact text replacements across files in one go. Replaces one-off patch scripts.

    python tools/ce_patch.py CHANGES.patch [--dry-run] [--root DIR]

CHANGES.patch (write it to the scratchpad with the Write tool; no escaping, quotes and apostrophes are plain text):

    @@file src/main/java/com/createengines/engine/sim/Wear.java
    @@old
    	public static final double SEIZE_AFTER = 20;
    @@new
    	public static final double SEIZE_AFTER = 120;
    @@old count=3
    isSeized()
    @@new
    showsSeized()
    @@end

@@old takes count=N (the exact number of matches expected, default 1) or count=all. Several old/new pairs may follow one
@@file; @@end closes the file's list (the next @@file also does). Text is whole lines, except a block of a single line,
which also matches inside a line. Line endings (CRLF or LF) and a BOM are kept.

All or nothing: if any pair does not match, no file is written, and the closest line in the file is shown. A pair whose
new text is already there is skipped, so a patch can be run again.
"""
import argparse
import difflib
import os
import sys


def parse(text):
    changes = []  # (path, old, new, count)
    path = mode = count = None
    old, new = [], []

    def flush():
        nonlocal old, new, mode
        if mode == 'new':
            a, b = '\n'.join(old), '\n'.join(new)
            if len(old) > 1 or len(new) > 1:
                a += '\n'
                b += '\n' if new else ''
            if not a.strip('\n'):
                raise ValueError(f'empty @@old for {path}')
            changes.append((path, a, b, count))
        elif mode == 'old':
            raise ValueError(f'@@old without @@new for {path}')
        old, new, mode = [], [], None

    for number, line in enumerate(text.replace('\r\n', '\n').removesuffix('\n').split('\n'), 1):
        if line.startswith('@@file '):
            flush()
            path = line[7:].strip()
        elif line == '@@old' or line.startswith('@@old '):
            flush()
            if not path:
                raise ValueError(f'line {number}: @@old before any @@file')
            option = line[5:].strip()
            count = 1
            if option:
                key, _, value = option.partition('=')
                if key != 'count':
                    raise ValueError(f'line {number}: unknown option {option}')
                count = value if value == 'all' else int(value)
            mode = 'old'
        elif line == '@@new':
            if mode != 'old':
                raise ValueError(f'line {number}: @@new without @@old')
            mode = 'new'
        elif line == '@@end':
            flush()
        elif mode == 'old':
            old.append(line)
        elif mode == 'new':
            new.append(line)
        elif line.strip():
            raise ValueError(f'line {number}: text outside a block: {line[:60]}')
    flush()
    return changes


def closest(text, old):
    first = next((l for l in old.split('\n') if l.strip()), '').strip()
    lines = text.split('\n')
    match = difflib.get_close_matches(first, [l.strip() for l in lines], n=1, cutoff=0.4)
    if not match:
        return '  no similar line in the file'
    at = next(i for i, l in enumerate(lines) if l.strip() == match[0])
    window = lines[at:at + min(len(old.split('\n')) + 1, 8)]
    return '\n'.join(f'  {at + i + 1}| {l}' for i, l in enumerate(window))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('patch')
    parser.add_argument('--root', default=os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    sys.stdout.reconfigure(errors='replace')
    with open(args.patch, encoding='utf-8-sig') as f:
        try:
            changes = parse(f.read())
        except ValueError as problem:
            sys.exit(f'bad patch file: {problem}')
    if not changes:
        sys.exit('the patch file holds no changes')

    files, endings, problems, notes = {}, {}, [], []
    applied = {}
    for path, old, new, count in changes:
        full = os.path.normpath(os.path.join(args.root, path))
        if full not in files:
            if not os.path.isfile(full):
                problems.append(f'{path}: no such file')
                continue
            with open(full, 'rb') as f:
                raw = f.read().decode('utf-8')
            endings[full] = '\r\n' if '\r\n' in raw else '\n'
            files[full] = raw.replace('\r\n', '\n')
            applied[full] = [path, 0]
        text = files[full]
        found = text.count(old)
        # Already applied: the new text is there, and any match of the old text lies inside it (an insertion)
        if new and new != old and new in text and found == (text.count(new) * new.count(old)):
            notes.append(f'{path}: already applied, skipped: {old.strip()[:60]!r}')
            continue
        wanted = found if count == 'all' else count
        if found == 0 or found != wanted:
            problems.append(f'{path}: expected {count} match(es), found {found}: {old.strip()[:80]!r}\n'
                            + (closest(text, old) if found == 0 else '  add context lines, or set count=N'))
            continue
        files[full] = text.replace(old, new)
        applied[full][1] += found

    if problems:
        print('NOTHING WRITTEN. Fix these and run again:')
        print('\n'.join(problems))
        sys.exit(1)
    for full, text in files.items():
        path, n = applied[full]
        if n and not args.dry_run:
            with open(full, 'wb') as f:
                f.write(text.replace('\n', endings[full]).encode('utf-8'))
        print(f'{"would change" if args.dry_run else "changed"} {path}: {n} replacement(s)' if n else f'unchanged {path}')
    for note in notes:
        print(note)


if __name__ == '__main__':
    main()
