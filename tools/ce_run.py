"""Runs Gradle for this repo and prints only the verdict. The full output goes to a log file.

    python tools/ce_run.py unit [--tests '*WearTest']
    python tools/ce_run.py gametest --copy NAME
    python tools/ce_run.py unit gametest data --copy NAME
    python tools/ce_run.py gate --copy gate240
    python tools/ce_run.py parse gametest            (re-read the last run's results, run nothing)

Words: compile, unit, gametest, data, pitest, smoke, check, build, gate. Any other word is passed to Gradle as a task name.

--copy NAME   mirror this checkout into ../create-engines-NAME and run there (no shared build/ or run/ with other sessions).
              The copy's GameTest world and config are deleted first, so tests see shipped defaults and a clean world.
              Only a folder this script created (it holds .ce-copy) is ever mirrored over.
--tests F     JUnit filter, for the unit step only.
--max N       most failures to print per step (default 12).

--offline is always added, and --rerun for test, GameTest, audit and pitest tasks, so nothing is skipped as UP-TO-DATE.
Exit code 0 only if every step passed. A GameTest step passes only if its log says "All N required tests passed".
"""
import argparse
import filecmp
import glob
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MARKER = '.ce-copy'
ALIASES = {
    'compile': ['compileJava', 'compileTestJava'],
    'unit': ['test'],
    'gametest': ['runGameTestServer'],
    'data': ['runData'],
    'pitest': ['pitest'],
    'smoke': ['smoke', '--continue'],
    'check': ['check'],
    'build': ['build'],
}
# docs/STABILITY.md 2.1 and 2.3, plus the compatibility GameTest rows. runData runs last, on an emptied src/generated.
GATE = ['smoke', 'check', 'runGameTestServerPollution', 'runGameTestServerSable', 'runGameTestServerTfmg',
        'runGameTestServerEnergy', 'build', 'jarServer', 'pitest', 'data']
RERUN = re.compile(r'^(test|pitest|runSmokeAudit\w*|runGameTestServer\w*)$')
GAMETEST_DIRS = {'runGameTestServer': 'run/gametest', 'smoke': 'run/gametest',
                 'runGameTestServerPollution': 'run/gametest-pollution', 'runGameTestServerSable': 'run/gametest-sable',
                 'runGameTestServerTfmg': 'run/gametest-tfmg', 'runGameTestServerPetrochem': 'run/gametest-petrochem',
                 'runGameTestServerEnergy': 'run/gametest-energy'}


def clip(text, width=300):
    text = ' '.join(text.split())
    return text if len(text) <= width else text[:width] + ' ...'


def read_lines(path, encoding='utf-8'):
    with open(path, encoding=encoding, errors='replace') as f:
        return f.read().replace('\x00', '').splitlines()


def sync_copy(name):
    if not re.fullmatch(r'[A-Za-z0-9._-]+', name):
        sys.exit('--copy takes a short name such as "wear", not a path')
    dest = os.path.join(os.path.dirname(REPO), 'create-engines-' + name)
    if os.path.exists(dest):
        if os.path.exists(os.path.join(dest, '.git')):
            sys.exit(f'{dest} is a git checkout or worktree, refusing to mirror over it')
        if os.listdir(dest) and not os.path.exists(os.path.join(dest, MARKER)):
            sys.exit(f'{dest} exists and was not made by this script (no {MARKER}). /MIR would delete files there. '
                     'Use another --copy name.')
    os.makedirs(dest, exist_ok=True)
    open(os.path.join(dest, MARKER), 'w').close()
    r = subprocess.run(['robocopy', REPO, dest, '/MIR', '/XD', 'run', 'build', '.gradle', '.git', '__pycache__',
                        '/XF', MARKER, '/NFL', '/NDL', '/NJH', '/NJS', '/NP'], capture_output=True, text=True)
    if r.returncode >= 8:  # robocopy: 0-7 are success
        sys.exit(f'robocopy failed ({r.returncode}):\n{r.stdout[-2000:]}')
    # Items pile up in a reused GameTest world until a later run runs out of memory, and a failed config sweep leaves
    # extreme values behind
    for run_dir in set(GAMETEST_DIRS.values()):
        for leftover in ('world', 'config'):
            shutil.rmtree(os.path.join(dest, run_dir, leftover), ignore_errors=True)
    return dest


def other_java(root):
    """Process ids of java processes whose command line names this folder (a client, a server, a test run)."""
    script = ("Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | "
              "ForEach-Object { \"$($_.ProcessId) $($_.CommandLine)\" }")
    try:
        out = subprocess.run(['powershell', '-NoProfile', '-Command', script], capture_output=True, text=True,
                             timeout=60).stdout
    except (OSError, subprocess.TimeoutExpired):
        return []
    pattern = re.compile(re.escape(root).replace(r'\\', r'[\\/]+') + r'(?![\w.-])', re.IGNORECASE)
    return [line.split(' ', 1)[0] for line in out.splitlines() if pattern.search(line)]


def compile_errors(root, lines, limit):
    found, seen = [], set()
    for i, line in enumerate(lines):
        if not re.search(r'\.java:\d+: error:', line):
            continue
        # Gradle prints each error again in its summary, indented
        head = line.strip().replace(root + os.sep, '')
        if head in seen or len(found) >= limit:
            continue
        seen.add(head)
        block = [clip(head, 400)]
        for extra in lines[i + 1:i + 6]:
            if re.search(r'\.java:\d+:|^\s*\d+ errors?|^\s*Note:|^> Task|^FAILURE|^\* ', extra):
                break
            block.append('    ' + extra.strip()[:200])
        found.append('\n'.join(block))
    return found


def unit_results(root, since, limit):
    total = failed = skipped = 0
    shown = []
    for path in glob.glob(os.path.join(root, 'build', 'test-results', 'test', '*.xml')):
        if os.path.getmtime(path) < since:
            continue
        try:
            suite = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        total += int(suite.get('tests', 0))
        skipped += int(suite.get('skipped', 0))
        for case in suite.iter('testcase'):
            for problem in case.findall('failure') + case.findall('error'):
                failed += 1
                if len(shown) < limit:
                    name = case.get('classname', '').rsplit('.', 1)[-1] + '.' + case.get('name', '')
                    trace = (problem.text or '').splitlines()
                    where = next((t.strip() for t in trace if 'com.createengines' in t and t.strip().startswith('at ')), '')
                    shown.append(f'  FAIL {name}\n       {clip(problem.get("message") or (trace[0] if trace else ""))}'
                                 + (f'\n       {where}' if where else ''))
    return total, failed, skipped, shown


def gametest_results(root, task, since, limit):
    log = os.path.join(root, GAMETEST_DIRS[task], 'logs', 'latest.log')
    if not os.path.exists(log) or os.path.getmtime(log) < since:
        return False, [f'  no fresh GameTest log at {log}: the server never ran']
    lines = read_lines(log, 'latin-1')
    passed = next((m.group(1) for l in lines if (m := re.search(r'All (\d+) required tests passed', l))), None)
    out = []
    names = []
    for i, line in enumerate(lines):
        m = re.search(r'LogTestReporter/?\]?:? (\S+) failed at [^!]*! (.*)', line)
        if m:
            message = m.group(2)
            if message.rstrip().endswith(':'):
                # The details are on the lines that follow, up to the next log entry
                for extra in lines[i + 1:i + 4]:
                    if re.match(r'\[\d\d\w{3}\d{4} ', extra):
                        break
                    message += ' ' + extra
            names.append(f'  FAIL {m.group(1)}: {clip(message)}')
    out += names[:limit]
    if len(names) > limit:
        out.append(f'  ... and {len(names) - limit} more failed tests')
    if passed:
        out.insert(0, f'  All {passed} required tests passed')
    else:
        count = next((m.group(1) for l in lines if (m := re.search(r'(\d+) required tests failed', l))), None)
        out.insert(0, f'  {count} required tests failed' if count else
                   '  never finished: no "All N required tests passed" line (crash, or another server held the world lock)')
    if any('OutOfMemoryError' in l for l in lines):
        out.append('  OutOfMemoryError in the log: delete this run folder\'s world and rerun')
    return passed is not None, out


def report(root, task, lines, since, limit):
    """Returns (extra reason to fail, text lines)."""
    bad, out = False, []
    errors = compile_errors(root, lines, limit)
    if errors:
        out += errors
    if task in ('test', 'check', 'build', 'smoke'):
        total, failed, skipped, shown = unit_results(root, since, limit)
        if total:
            out.append(f'  unit: {total} run, {failed} failed, {skipped} skipped')
            out += shown
        elif task == 'test' and not errors:
            bad = True
            out.append('  no unit test results were written')
    if task in GAMETEST_DIRS and not errors:
        ok, text = gametest_results(root, task, since, limit)
        bad |= not ok
        out += text
    out += ['  ' + clip(l) for l in lines if l.startswith('PIT: ')]
    # Lines from the verify steps and the audit
    out += ['  ' + clip(l) for l in lines
            if re.match(r'\s*(FAIL|WARN)\b(?!:\s*$)', l) and 'StatusConsoleListener' not in l][:limit]
    return bad, out


def what_went_wrong(lines):
    out, keep = [], 0
    for line in lines:
        if line.startswith('* What went wrong'):
            keep = 6
        elif line.startswith('* Try'):
            keep = 0
        if keep:
            out.append('  ' + clip(line))
            keep -= 1
    return out[:18]


def generated_diff(root, limit):
    """Compares the copy's freshly generated files with the checkout's."""
    a, b = os.path.join(REPO, 'src', 'generated'), os.path.join(root, 'src', 'generated')
    changed = []
    names = set()
    for base in (a, b):
        for folder, dirs, files in os.walk(base):
            dirs[:] = [d for d in dirs if d != '.cache']
            names.update(os.path.relpath(os.path.join(folder, f), base) for f in files)
    for rel in sorted(names):
        x, y = os.path.join(a, rel), os.path.join(b, rel)
        if not os.path.exists(x):
            changed.append('only generated now: ' + rel)
        elif not os.path.exists(y):
            changed.append('committed but no longer generated: ' + rel)
        elif not filecmp.cmp(x, y, shallow=False):
            with open(x, 'rb') as f, open(y, 'rb') as g:
                if f.read().replace(b'\r\n', b'\n') != g.read().replace(b'\r\n', b'\n'):
                    changed.append('differs: ' + rel)
    shown = ['  ' + c for c in changed[:limit]]
    if len(changed) > limit:
        shown.append(f'  ... {len(changed)} in total')
    return changed, shown


def run_step(root, word, args, log_dir):
    tasks = list(ALIASES.get(word, [word]))
    task = tasks[0]
    command = [os.path.join(root, 'gradlew.bat' if os.name == 'nt' else 'gradlew')]
    for t in tasks:
        command.append(t)
        if RERUN.match(t):
            command.append('--rerun')
        if t == 'test' and args.tests:
            command += ['--tests', args.tests]
    command += ['--offline', '--console=plain']
    fresh_data = task == 'runData' and root != REPO
    if fresh_data:
        # Datagen skips a file whose output matches its cache, so a stale file only shows from an empty folder
        shutil.rmtree(os.path.join(root, 'src', 'generated'), ignore_errors=True)
    log = os.path.join(log_dir, word + '.log')
    started = time.time()
    with open(log, 'wb') as sink:
        code = subprocess.run(command, cwd=root, stdout=sink, stderr=subprocess.STDOUT).returncode
    minutes = (time.time() - started) / 60
    lines = read_lines(log)
    bad, detail = report(root, task, lines, started - 2, args.max)
    if fresh_data and code == 0:
        changed, shown = generated_diff(root, args.max)
        detail.append(f'  src/generated: {len(changed)} files differ from the checkout')
        detail += shown
        bad |= bool(changed)
    broken = bool(compile_errors(root, lines, 1))
    if code != 0:
        if not broken:
            detail += what_went_wrong(lines)
        if any(re.search(r'Unable to delete directory|NoClassDefFoundError: com/createengines', l) for l in lines):
            detail.append('  looks like another run is using this build/ folder: use --copy NAME')
    ok = code == 0 and not bad
    print(f'{"PASS" if ok else "FAIL"} {word}  ({minutes:.1f} min, gradle exit {code})  log: {log}')
    for line in detail:
        print(line)
    sys.stdout.flush()
    return ok, broken


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('words', nargs='+')
    parser.add_argument('--copy')
    parser.add_argument('--tests')
    parser.add_argument('--max', type=int, default=12)
    parser.add_argument('--logs', help='folder for logs (default: a new folder under the temp directory)')
    args = parser.parse_args()
    sys.stdout.reconfigure(errors='replace')

    if args.words[0] == 'parse':
        root = os.path.join(os.path.dirname(REPO), 'create-engines-' + args.copy) if args.copy else REPO
        ok = True
        for word in args.words[1:] or ['unit', 'gametest']:
            task = ALIASES.get(word, [word])[0]
            bad, detail = report(root, task, [], 0, args.max)
            print(f'{word} (last results on disk, nothing was run; their age is not checked)')
            print('\n'.join(detail) or '  nothing found')
            ok &= not bad
        sys.exit(0 if ok else 1)

    words = []
    for word in args.words:
        words += GATE if word == 'gate' else [word]
    if 'gate' in args.words and not args.copy:
        sys.exit('the gate runs in a private copy: add --copy NAME')

    root = sync_copy(args.copy) if args.copy else REPO
    busy = other_java(root)
    if busy:
        print(f'WARNING: java processes {", ".join(busy)} are using {root}. They can corrupt this run '
              '(do not kill another session\'s process; use --copy NAME).')
    log_dir = args.logs or tempfile.mkdtemp(prefix='ce-run-')
    os.makedirs(log_dir, exist_ok=True)
    print(f'running in {root}', flush=True)
    results = []
    for n, word in enumerate(words):
        ok, broken = run_step(root, word, args, log_dir)
        results.append((word, ok))
        if broken and words[n + 1:]:
            print(f'the code does not compile: not running {", ".join(words[n + 1:])}')
            results += [(w, False) for w in words[n + 1:]]
            break
    failed = [w for w, ok in results if not ok]
    print(f'RESULT: {"FAIL (" + ", ".join(failed) + ")" if failed else "PASS"}  [{len(results)} step(s)]')
    sys.exit(1 if failed else 0)


if __name__ == '__main__':
    main()
