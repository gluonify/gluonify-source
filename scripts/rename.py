#!/usr/bin/env python3
"""Renomme le projet pour en faire VOTRE service : groupId, artifactId, paquet Java, nom de l'exécutable, audience du jeton.

Usage : python3 scripts/rename.py <groupId> <artifactId> [<paquet Java>]
  ex. : python3 scripts/rename.py com.acme shop-api com.acme.shop

- groupId / artifactId : coordonnées Maven (minuscules, chiffres, tirets, points) ;
- paquet Java : par défaut <groupId>.<artifactId sans tirets> ;
- l'artifactId devient aussi : le nom de l'exécutable (Dockerfile.build), le titre OpenAPI et l'audience attendue des jetons Charm (« POST /v1/tokens » avec "audience": "<artifactId>").

Le script ne touche qu'à ce dépôt, ne demande rien, et peut être relancé (il repart des noms actuels lus dans pom.xml). Vérifiez ensuite : mvn test.
"""
import os
import re
import shutil
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
TEXT = ('.java', '.xml', '.properties', '.md', '.json', '.js', '.vue', '.html', '.sh', '.yml', '.yaml', '')  # '' : Dockerfile.build et autres sans extension
SKIP_DIRS = {'.git', 'target', 'node_modules', 'dist'}


def fail(msg):
    print('erreur : ' + msg, file=sys.stderr)
    sys.exit(1)


def current(pom):
    g = re.search(r'<modelVersion>[^<]*</modelVersion>\s*(?:<!--.*?-->\s*)?<groupId>([^<]+)</groupId>\s*<artifactId>([^<]+)</artifactId>', pom, re.S)
    if not g:
        fail('groupId/artifactId introuvables dans pom.xml')
    return g.group(1), g.group(2)


def main():
    if len(sys.argv) not in (3, 4):
        print(__doc__)
        sys.exit(2)
    group, artifact = sys.argv[1], sys.argv[2]
    if not re.fullmatch(r'[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)*', group):
        fail('groupId invalide (minuscules, chiffres, points) : ' + group)
    if not re.fullmatch(r'[a-z][a-z0-9-]*', artifact):
        fail('artifactId invalide (minuscules, chiffres, tirets) : ' + artifact)
    package = sys.argv[3] if len(sys.argv) == 4 else group + '.' + artifact.replace('-', '')
    if not re.fullmatch(r'[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)*', package):
        fail('paquet Java invalide : ' + package)

    pom_path = os.path.join(ROOT, 'pom.xml')
    old_group, old_artifact = current(open(pom_path, encoding='utf-8').read())
    # paquet actuel : celui de SourceConfig (le fichier de configuration de l'application)
    old_package = None
    for d, _, files in os.walk(os.path.join(ROOT, 'src', 'main', 'java')):
        if 'SourceConfig.java' in files or any(f.endswith('Config.java') for f in files) and old_package is None:
            m = re.search(r'^package ([\w.]+);', open(os.path.join(d, [f for f in files if f.endswith('Config.java')][0]), encoding='utf-8').read(), re.M)
            if m:
                old_package = m.group(1)
                break
    if not old_package:
        fail('paquet Java actuel introuvable')

    # 1. contenu des fichiers texte
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

    # 2. dossiers des paquets Java (main et test)
    for tree in ('main', 'test'):
        base = os.path.join(ROOT, 'src', tree, 'java')
        old_dir, new_dir = os.path.join(base, *old_package.split('.')), os.path.join(base, *package.split('.'))
        if os.path.isdir(old_dir) and old_dir != new_dir:
            os.makedirs(os.path.dirname(new_dir), exist_ok=True)
            shutil.move(old_dir, new_dir)
            # supprime les dossiers vides laissés par l'ancien paquet
            parent = os.path.dirname(old_dir)
            while parent != base and os.path.isdir(parent) and not os.listdir(parent):
                os.rmdir(parent)
                parent = os.path.dirname(parent)
    print(f'renommé : {old_group}:{old_artifact} ({old_package}) -> {group}:{artifact} ({package}) ; {changed} fichier(s) modifié(s)')
    print('étape suivante : mvn test')


if __name__ == '__main__':
    main()
