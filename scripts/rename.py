#!/usr/bin/env python3
"""Renames the project to make it YOUR service: groupId, artifactId, Java package, executable name, token audience.

Usage: python3 scripts/rename.py <groupId> <artifactId> [<Java package>]
  e.g.: python3 scripts/rename.py com.acme shop-api com.acme.shop

- groupId / artifactId: Maven coordinates (lowercase, digits, hyphens, dots);
- Java package: defaults to <groupId>.<artifactId without hyphens>;
- the artifactId also becomes: the executable name (Dockerfile.build), the OpenAPI title and the audience expected in Charm tokens ("POST /v1/tokens" with "audience": "<artifactId>").

The script only touches this repository, asks nothing, and can be re-run (it starts from the current names read in pom.xml). Then check: mvn test.
"""
import os
import re
import shutil
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
TEXT = ('.java', '.xml', '.properties', '.md', '.json', '.js', '.vue', '.html', '.sh', '.yml', '.yaml', '')  # '': Dockerfile.build and others without an extension
SKIP_DIRS = {'.git', 'target', 'node_modules', 'dist'}


def fail(msg):
    print('error: ' + msg, file=sys.stderr)
    sys.exit(1)


def current(pom):
    g = re.search(r'<modelVersion>[^<]*</modelVersion>\s*(?:<!--.*?-->\s*)?<groupId>([^<]+)</groupId>\s*<artifactId>([^<]+)</artifactId>', pom, re.S)
    if not g:
        fail('groupId/artifactId not found in pom.xml')
    return g.group(1), g.group(2)


def main():
    if len(sys.argv) not in (3, 4):
        print(__doc__)
        sys.exit(2)
    group, artifact = sys.argv[1], sys.argv[2]
    if not re.fullmatch(r'[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)*', group):
        fail('invalid groupId (lowercase, digits, dots): ' + group)
    if not re.fullmatch(r'[a-z][a-z0-9-]*', artifact):
        fail('invalid artifactId (lowercase, digits, hyphens): ' + artifact)
    package = sys.argv[3] if len(sys.argv) == 4 else group + '.' + artifact.replace('-', '')
    if not re.fullmatch(r'[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)*', package):
        fail('invalid Java package: ' + package)

    pom_path = os.path.join(ROOT, 'pom.xml')
    old_group, old_artifact = current(open(pom_path, encoding='utf-8').read())
    # current package: that of SourceConfig (the application's configuration file)
    old_package = None
    for d, _, files in os.walk(os.path.join(ROOT, 'src', 'main', 'java')):
        if 'SourceConfig.java' in files or any(f.endswith('Config.java') for f in files) and old_package is None:
            m = re.search(r'^package ([\w.]+);', open(os.path.join(d, [f for f in files if f.endswith('Config.java')][0]), encoding='utf-8').read(), re.M)
            if m:
                old_package = m.group(1)
                break
    if not old_package:
        fail('current Java package not found')

    # 1. contents of text files
    changed = 0
    for d, dirs, files in os.walk(ROOT):
        dirs[:] = [x for x in dirs if x not in SKIP_DIRS]
        for f in files:
            if os.path.splitext(f)[1] not in TEXT or f in ('package-lock.json',):
                continue
            p = os.path.join(d, f)
            try:
                s = open(p, encoding='utf-8').read()
            except UnicodeDecodeError:
                continue
            n = s.replace(old_package, package)
            if f == 'pom.xml':
                n = n.replace('<groupId>' + old_group + '</groupId>', '<groupId>' + group + '</groupId>', 1)
            n = n.replace(old_artifact, artifact)
            if n != s:
                open(p, 'w', encoding='utf-8').write(n)
                changed += 1

    # 2. Java package folders (main and test)
    for tree in ('main', 'test'):
        base = os.path.join(ROOT, 'src', tree, 'java')
        old_dir, new_dir = os.path.join(base, *old_package.split('.')), os.path.join(base, *package.split('.'))
        if os.path.isdir(old_dir) and old_dir != new_dir:
            os.makedirs(os.path.dirname(new_dir), exist_ok=True)
            shutil.move(old_dir, new_dir)
            # remove empty folders left by the old package
            parent = os.path.dirname(old_dir)
            while parent != base and os.path.isdir(parent) and not os.listdir(parent):
                os.rmdir(parent)
                parent = os.path.dirname(parent)
    print(f'renamed: {old_group}:{old_artifact} ({old_package}) -> {group}:{artifact} ({package}) ; {changed} file(s) modified')
    print('next step: mvn test')


if __name__ == '__main__':
    main()
