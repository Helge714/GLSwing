#!/usr/bin/env python3
"""Build a local, unsigned release candidate without modifying the development POMs."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
MODULES = {'glswing-core': 'glswing', 'glswing-lsp': 'glswing-lsp',
           'glswing-filedialogs-swt': 'glswing-filedialogs-swt'}

def run(*args, cwd):
    return subprocess.check_output(args, cwd=cwd, text=True).strip()

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--maven', default='mvn')
    parser.add_argument('--offline', action='store_true')
    parser.add_argument('--api-baseline', help='Optional installed predecessor version for japicmp')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    pom = ET.parse(root / 'pom.xml').getroot()
    development = pom.findtext('m:version', namespaces=NS)
    version = development.removesuffix('-SNAPSHOT')
    group = pom.findtext('m:groupId', namespaces=NS)
    if not re.fullmatch(r'\d+\.\d+\.\d+-SNAPSHOT', development):
        raise SystemExit('Run from a development SNAPSHOT checkout.')
    if run('git', 'status', '--porcelain', '--untracked-files=normal', cwd=root):
        raise SystemExit('Commit reviewed changes before preparing a candidate.')
    commit = run('git', 'rev-parse', 'HEAD', cwd=root)
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)  # Never replace an existing candidate.
    source = output / 'source'
    subprocess.run(['git', 'worktree', 'add', '--detach', str(source), commit], cwd=root, check=True)
    for name in run('git', 'ls-files', '*pom.xml', cwd=source).splitlines():
        path = source / name
        text = path.read_text(encoding='utf-8')
        path.write_text(text.replace(development, version), encoding='utf-8', newline='\n')
    # SCM tag describes this candidate; no tag is created by this script.
    path = source / 'pom.xml'
    text = path.read_text(encoding='utf-8')
    text = re.sub(r'<tag>[^<]*</tag>', f'<tag>v{version}</tag>', text)
    path.write_text(text, encoding='utf-8', newline='\n')
    command = [args.maven, '-B', '-Prelease', '-Dglswing.test.gl=true',
               '-pl', ','.join([*MODULES, 'glswing-consumer-smoke']), '-am', 'install']
    if args.offline:
        command.insert(1, '-o')
    with (output / 'build.log').open('w', encoding='utf-8') as log:
        result = subprocess.run(command, cwd=source, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        raise SystemExit(f'Build failed; see {output / "build.log"}')
    if args.api_baseline:
        comparison = [args.maven, '-B', '-Papi-compatibility', '-pl', ','.join(MODULES),
                      f'-Dapi.compatibility.previousVersion={args.api_baseline}', 'japicmp:cmp']
        if args.offline:
            comparison.insert(1, '-o')
        with (output / 'api-comparison.log').open('w', encoding='utf-8') as log:
            subprocess.run(comparison, cwd=source, stdout=log, stderr=subprocess.STDOUT, check=True)
        for module in MODULES:
            shutil.copytree(source / module / 'target/japicmp', output / 'api-reports' / module)
    repository = output / 'repository'
    coordinate = repository.joinpath(*group.split('.'))
    parent = coordinate / 'glswing-parent' / version
    parent.mkdir(parents=True)
    shutil.copyfile(source / 'pom.xml', parent / f'glswing-parent-{version}.pom')
    test_totals = {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0}
    for module, artifact in MODULES.items():
        directory = coordinate / artifact / version
        directory.mkdir(parents=True)
        shutil.copyfile(source / module / 'pom.xml', directory / f'{artifact}-{version}.pom')
        for classifier in ['', '-sources', '-javadoc']:
            name = f'{artifact}-{version}{classifier}.jar'
            jar = source / module / 'target' / name
            with zipfile.ZipFile(jar) as archive:
                names = archive.namelist()
                if classifier == '':
                    assert archive.read('META-INF/LICENSE') == (source / 'LICENSE').read_bytes(), name
                    assert not any(n.startswith(('org/jogamp/', 'com/jogamp/', 'org/eclipse/', 'org/apache/')) for n in names), name
                    assert not any(n.endswith(('.ttf', '.otf', '.dll', '.so', '.dylib')) for n in names), name
                elif classifier == '-sources':
                    assert any(n.endswith('.java') for n in names), name
                else:
                    assert 'index.html' in names, name
            shutil.copyfile(jar, directory / name)
        reports = source / module / 'target/surefire-reports'
        for report in reports.glob('TEST-*.xml'):
            suite = ET.parse(report).getroot()
            for key in test_totals:
                test_totals[key] += int(suite.attrib.get(key, '0'))
    assert not test_totals['failures'] and not test_totals['errors'] and test_totals['tests'] > 0, test_totals
    files = sorted(repository.rglob('*'))
    for path in files:
        if path.is_file():
            for algorithm in ['md5', 'sha1', 'sha256', 'sha512']:
                path.with_name(path.name + '.' + algorithm).write_text(
                    hashlib.new(algorithm, path.read_bytes()).hexdigest() + '\n', encoding='ascii')
    with zipfile.ZipFile(output / f'GLSwing-{version}-unsigned-maven.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(repository.rglob('*')):
            if path.is_file():
                archive.write(path, path.relative_to(repository).as_posix())
    with zipfile.ZipFile(output / f'GLSwing-{version}-source.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
        for name in run('git', 'ls-files', cwd=source).splitlines():
            # IDE-local settings are not necessary to rebuild Maven artifacts.
            if name.startswith(('.settings/', '.codex')) or name.endswith(('.launch', '.project', '.classpath')):
                continue
            archive.write(source / name, name)
    evidence = {'version': version, 'groupId': group, 'sourceCommit': commit,
                'buildKind': 'local', 'signed': False, 'uploaded': False,
                'versionTransformation': f'{development} -> {version}; SCM tag metadata only',
                'tests': test_totals, 'externalConsumer': 'Maven invoker verify in build.log',
                'apiBaseline': args.api_baseline,
                'runtimeDependenciesBundled': False,
                'artifacts': {p.relative_to(output).as_posix(): sha(p) for p in sorted(output.glob('*.zip'))},
                'remaining': ['Confirm Central namespace ownership', 'Configure signing key and Portal token',
                              'Sign and validate final bundle before authorized publication']}
    (output / 'candidate.json').write_text(json.dumps(evidence, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(evidence, indent=2))

if __name__ == '__main__':
    main()
