"""Looks things up inside the libraries this mod builds against, without unzipping or hunting for jar paths.

    python tools/ce_lib.py find  KineticBlockEntity            classes whose name matches (regex, case-insensitive)
    python tools/ce_lib.py api   KineticBlockEntity [-g speed] fields and method signatures (all visibilities)
    python tools/ce_lib.py src   BlockEntity -m setChanged     source of a method (Minecraft and NeoForge have source)
    python tools/ce_lib.py src   BlockEntity -g onChunkUnload  matching source lines with context
    python tools/ce_lib.py code  KineticBlockEntity -m tick    bytecode of a method (Create, Ponder... ship no source)
    python tools/ce_lib.py res   'create/models/block/shaft'   resource paths matching; add --cat to print the first
    add --jar TEXT to look only in jars whose file name holds TEXT, or to add a mod jar (--jar sable, --jar jade)

CLASS is a simple name or a full one. Versions come from gradle.properties. The class index is cached in the temp folder.
"""
import argparse
import glob
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(tempfile.gettempdir(), 'ce-lib-index.json')


def properties():
    out = {}
    with open(os.path.join(REPO, 'gradle.properties'), encoding='utf-8') as f:
        for line in f:
            if '=' in line and not line.startswith('#'):
                key, value = line.strip().split('=', 1)
                out[key] = value
    return out


def jars(wanted=None):
    p = properties()
    home = os.path.join(os.path.expanduser('~'), '.gradle', 'caches', 'modules-2', 'files-2.1')
    artifacts = os.path.join(REPO, 'build', 'moddev', 'artifacts')
    found = [os.path.join(artifacts, f'neoforge-{p["neo_version"]}{s}.jar') for s in ('-merged', '-sources',
                                                                                   '-client-extra-aka-minecraft-resources')]
    mc = p['minecraft_version']
    patterns = [
        f'com.simibubi.create/create-{mc}/{p["create_version"]}/*/*-slim.jar',
        f'net.createmod.ponder/ponder-neoforge/{p["ponder_version"]}*/*/*.jar',
        f'com.tterrag.registrate/Registrate/{p["registrate_version"]}/*/*.jar',
        f'dev.engine-room.flywheel/flywheel-neoforge*{mc}/{p["flywheel_version"]}/*/*.jar',
    ]
    if wanted:
        # Optional mods: any jar in the cache whose name holds the text
        patterns.append('*/*/*/*/*.jar')
    for pattern in patterns:
        found += glob.glob(os.path.join(home, pattern))
    found = [j for j in dict.fromkeys(found) if os.path.isfile(j)]
    if wanted:
        found = [j for j in found if wanted.lower() in os.path.basename(j).lower()]
    else:
        found = [j for j in found if not re.search(r'-javadoc|-sources', j) or 'neoforge-' in os.path.basename(j)]
    if not found:
        sys.exit('no jar matched' if wanted else 'no library jars found: run a Gradle build once')
    return found


def entries(paths):
    try:
        with open(CACHE, encoding='utf-8') as f:
            cache = json.load(f)
    except (OSError, ValueError):
        cache = {}
    changed = False
    out = {}
    for jar in paths:
        key = f'{jar}|{os.path.getmtime(jar)}'
        if key not in cache:
            with zipfile.ZipFile(jar) as z:
                cache[key] = [n for n in z.namelist() if not n.endswith('/')]
            changed = True
        out[jar] = cache[key]
    if changed:
        with open(CACHE, 'w', encoding='utf-8') as f:
            json.dump(cache, f)
    return out


def classes(index, pattern):
    regex = re.compile(pattern, re.IGNORECASE)
    for jar, names in index.items():
        for name in names:
            if name.endswith(('.class', '.java')) and '$' not in name:
                dotted = name.rsplit('.', 1)[0].replace('/', '.')
                if regex.search(dotted):
                    yield dotted, jar, name


def resolve(index, name, suffix):
    hits = {}
    for dotted, jar, entry in classes(index, re.escape(name.split('$')[0])):
        base = name.split('$')[0]
        if entry.endswith(suffix) and (dotted == base or dotted.endswith('.' + base)):
            hits.setdefault(dotted, (jar, entry))
    if not hits:
        sys.exit(f'no {suffix} entry for {name}. Try: ce_lib.py find {name}'
                 + ('  (only Minecraft and NeoForge have source: use "api" or "code")' if suffix == '.java' else ''))
    if len(hits) > 1:
        # Prefer the game, NeoForge and Create over same-named library classes
        preferred = [k for k in hits if k.startswith(('net.minecraft.', 'net.neoforged.', 'com.simibubi.'))]
        if len(preferred) != 1:
            sys.exit('several classes match, give the full name:\n  ' + '\n  '.join(sorted(hits)))
        return preferred[0], hits[preferred[0]]
    return next(iter(hits.items()))


def javap(jar, name, flags):
    tool = shutil.which('javap')
    if not tool and os.environ.get('JAVA_HOME'):
        tool = os.path.join(os.environ['JAVA_HOME'], 'bin', 'javap.exe')
    if not tool:
        sys.exit('javap not found: put a JDK on PATH or set JAVA_HOME')
    r = subprocess.run([tool, *flags, '-cp', jar, name], capture_output=True, text=True, errors='replace')
    if r.returncode:
        sys.exit(r.stderr.strip()[:1000])
    return r.stdout.splitlines()


def grep(lines, pattern, context):
    regex = re.compile(pattern, re.IGNORECASE)
    keep = set()
    for i, line in enumerate(lines):
        if regex.search(line):
            keep.update(range(max(0, i - context), min(len(lines), i + context + 1)))
    return sorted(keep)


def source_method(lines, name):
    """Line ranges of every declaration of a method, found by indentation and braces."""
    ranges = []
    declaration = re.compile(r'^(\s+)(?!return\b|new\b|else\b)[\w<>\[\], .?@]*[\w>\]]\s+' + re.escape(name) + r'\s*\(')
    for i, line in enumerate(lines):
        m = declaration.match(line)
        if not m:
            continue
        indent = m.group(1)
        end = i
        if not line.rstrip().endswith(';'):
            end = next((j for j in range(i + 1, len(lines)) if lines[j].rstrip() == indent + '}'), i)
        ranges.append((i, end))
    return ranges


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('command', choices=['find', 'api', 'src', 'code', 'res'])
    parser.add_argument('name')
    parser.add_argument('-g', '--grep')
    parser.add_argument('-m', '--method')
    parser.add_argument('-C', '--context', type=int, default=3)
    parser.add_argument('--jar')
    parser.add_argument('--cat', action='store_true')
    parser.add_argument('--limit', type=int, default=150, help='most lines to print')
    args = parser.parse_args()
    sys.stdout.reconfigure(errors='replace')
    index = entries(jars(args.jar))
    out = []

    if args.command == 'find':
        seen = {}
        for dotted, jar, _ in classes(index, args.name):
            seen.setdefault(dotted, os.path.basename(jar))
        out = [f'{k}   [{v}]' for k, v in sorted(seen.items())] or ['no match']
    elif args.command == 'res':
        regex = re.compile(args.name, re.IGNORECASE)
        hits = [(n, j) for j, names in index.items() for n in names
                if not n.endswith(('.class', '.java')) and regex.search(n)]
        if args.cat and hits:
            name, jar = next(((n, j) for n, j in hits if n.lower() == args.name.lower()), hits[0])
            with zipfile.ZipFile(jar) as z:
                out = [f'== {name} [{os.path.basename(jar)}]'] + z.read(name).decode('utf-8', 'replace').splitlines()
        else:
            out = [f'{n}   [{os.path.basename(j)}]' for n, j in hits] or ['no match']
    elif args.command == 'src':
        dotted, (jar, entry) = resolve(index, args.name, '.java')
        with zipfile.ZipFile(jar) as z:
            lines = z.read(entry).decode('utf-8', 'replace').splitlines()
        out = [f'== {dotted} ({len(lines)} lines)']
        if args.method:
            for start, end in source_method(lines, args.method):
                out += [f'{i + 1}| {lines[i]}' for i in range(start, end + 1)] + ['']
            if len(out) == 1:
                out.append(f'no declaration of {args.method}; it may be inherited (try -g, or the superclass)')
        elif args.grep:
            last = -2
            for i in grep(lines, args.grep, args.context):
                if i != last + 1:
                    out.append('--')
                out.append(f'{i + 1}| {lines[i]}')
                last = i
        else:
            out += [f'{i + 1}| {l}' for i, l in enumerate(lines)
                    if re.match(r'^\s{0,8}(public|protected|private|static)\b.*[({]', l)]
    else:
        dotted, (jar, _) = resolve(index, args.name, '.class')
        if '$' in args.name:
            dotted += '$' + args.name.split('$', 1)[1]
        if args.command == 'api':
            lines = javap(jar, dotted, ['-p'])
            out = lines if not args.grep else [lines[i] for i in grep(lines, args.grep, 0)]
        else:
            if not args.method:
                sys.exit('code needs -m METHOD (a whole class of bytecode is too long to be useful)')
            lines = javap(jar, dotted, ['-p', '-c'])
            keep = False
            for line in lines:
                if re.match(r'^  \S.*;$', line) or not line.strip():
                    keep = bool(re.search(r'[ .]' + re.escape(args.method) + r'\(', line))
                if keep:
                    # Constant pool numbers say nothing; the comment after them names the target
                    out.append(re.sub(r'#\d+(, +\d+)?\s*', '', line))
            if not out:
                out = [f'no method {args.method} in {dotted}']

    for line in out[:args.limit]:
        print(line)
    if len(out) > args.limit:
        print(f'... {len(out) - args.limit} more lines (narrow with -g or -m, or raise --limit)')


if __name__ == '__main__':
    main()
