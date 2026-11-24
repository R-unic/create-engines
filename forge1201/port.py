#!/usr/bin/env python3
"""
Makes the Forge 1.20.1 source tree from the main build's, so the mod is written once.

  python port.py            -> build/ported/java and build/ported/resources

Steps: copy ../src/main/java, drop what the port leaves out (EXCLUDE), apply the mechanical rewrites (RULES), then let any
file in src/main/java of this folder (an overlay, hand-ported) replace the generated file of the same path. A change to
the main build reaches the port by running this again; an overlay file is the only place the port differs by hand, and
each one is a line in the file list printed at the end.
"""
import os
import re
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MAIN = os.path.join(HERE, '..', 'src', 'main')
OUT = os.path.join(HERE, 'build', 'ported')
OVERLAY = os.path.join(HERE, 'src', 'main', 'java')
BASE = 'com/createengines/'

# Left out of the port: the engine flywheel parts (asked), and the development harnesses that need porting on their own.
EXCLUDE_DIRS = [BASE + 'content/flywheel/',
                # Waves two and three: optional-mod compatibility (each needs its own 1.20.1 API) and the test harnesses
                BASE + 'compat/', BASE + 'gametest/', BASE + 'smoke/', BASE + 'client/smoke/']
EXCLUDE_FILES = [BASE + 'engine/FlywheelSpec.java']

# (pattern, replacement) applied to every file, in order
RULES = [
    # packages that moved or were renamed between NeoForge 21.1 and Forge 47
    (r'net\.neoforged\.neoforge\.common\.ModConfigSpec', 'net.minecraftforge.common.ForgeConfigSpec'),
    (r'ModConfigSpec', 'ForgeConfigSpec'),
    (r'net\.neoforged\.neoforge\.common\.NeoForge\b', 'net.minecraftforge.common.MinecraftForge'),
    (r'\bNeoForge\.EVENT_BUS', 'MinecraftForge.EVENT_BUS'),
    (r'net\.neoforged\.neoforge\.registries\.DeferredRegister', 'net.minecraftforge.registries.DeferredRegister'),
    (r'net\.neoforged\.neoforge\.registries\.', 'net.minecraftforge.registries.'),
    (r'net\.neoforged\.neoforge\.fluids\.', 'net.minecraftforge.fluids.'),
    (r'net\.neoforged\.neoforge\.event\.', 'net.minecraftforge.event.'),
    (r'net\.neoforged\.neoforge\.client\.event\.', 'net.minecraftforge.client.event.'),
    (r'net\.neoforged\.neoforge\.client\.', 'net.minecraftforge.client.'),
    (r'net\.neoforged\.neoforge\.items\.', 'net.minecraftforge.items.'),
    (r'net\.neoforged\.neoforge\.energy\.', 'net.minecraftforge.energy.'),
    (r'net\.neoforged\.neoforge\.common\.', 'net.minecraftforge.common.'),
    (r'net\.neoforged\.neoforge\.network\.', 'net.minecraftforge.network.'),
    (r'net\.neoforged\.neoforge\.', 'net.minecraftforge.'),
    (r'net\.neoforged\.bus\.api\.', 'net.minecraftforge.eventbus.api.'),
    (r'net\.neoforged\.fml\.common\.', 'net.minecraftforge.fml.common.'),
    (r'net\.neoforged\.fml\.', 'net.minecraftforge.fml.'),
    (r'net\.neoforged\.api\.distmarker\.', 'net.minecraftforge.api.distmarker.'),
    (r'@EventBusSubscriber', '@Mod.EventBusSubscriber'),
    # ids
    (r'ResourceLocation\.fromNamespaceAndPath\(', 'new ResourceLocation('),
    (r'ResourceLocation\.parse\(', 'new ResourceLocation('),
    (r'ResourceLocation\.withDefaultNamespace\(', 'new ResourceLocation("minecraft", '),
]
COMPILED = [(re.compile(p), r) for p, r in RULES]


def excluded(rel):
    return rel in EXCLUDE_FILES or any(rel.startswith(d) for d in EXCLUDE_DIRS)


def rewrite(text):
    for pattern, replacement in COMPILED:
        text = pattern.sub(replacement, text)
    return text


def main():
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    java_out = os.path.join(OUT, 'java')
    skipped, overlaid, written = [], [], 0
    root = os.path.join(MAIN, 'java')
    for folder, _, files in os.walk(root):
        for name in files:
            if not name.endswith('.java'):
                continue
            path = os.path.join(folder, name)
            rel = os.path.relpath(path, root).replace(os.sep, '/')
            if excluded(rel):
                skipped.append(rel)
                continue
            if os.path.exists(os.path.join(OVERLAY, rel)):
                overlaid.append(rel)
                continue
            target = os.path.join(java_out, rel)
            os.makedirs(os.path.dirname(target), exist_ok=True)
            with open(path, encoding='utf-8') as f:
                text = f.read()
            with open(target, 'w', encoding='utf-8', newline='\n') as f:
                f.write(rewrite(text))
            written += 1
    print(f'ported {written} files, {len(overlaid)} by hand, {len(skipped)} left out')
    for rel in overlaid:
        print('  overlay', rel)
    for rel in skipped:
        print('  left out', rel)


if __name__ == '__main__':
    sys.exit(main())
