#!/usr/bin/env python3
"""Summarises a javac log from the port: where the errors are and what they are about. Usage: errors.py LOG"""
import collections
import re
import sys

text = open(sys.argv[1], encoding='utf-8', errors='replace').read()
errs = re.findall(r'ported[\\/]java[\\/]com[\\/]createengines[\\/]([^:]+):(\d+): error: (.*)', text)
norm = lambda p: p.replace('\\', '/')
print(len(errs), 'errors in', len({norm(e[0]) for e in errs}), 'files')
for k, v in collections.Counter(norm(e[0]).split('/')[0] for e in errs).most_common():
    print('  ', v, k)
print('messages:')
for k, v in collections.Counter(re.sub(r'\s+', ' ', e[2])[:80] for e in errs).most_common(12):
    print('  ', v, k)
print('packages that do not exist:')
for k, v in collections.Counter(re.findall(r'error: package ([\w.]+) does not exist', text)).most_common(40):
    print('  ', v, k)
print('missing symbols:')
for k, v in collections.Counter(re.findall(r'symbol:\s+(?:class|variable|method) (\w+)', text)).most_common(40):
    print('  ', v, k)
