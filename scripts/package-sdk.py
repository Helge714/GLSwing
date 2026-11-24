#!/usr/bin/env python3
"""Package reviewed local GLSwing artifacts for manual use; never uploads anything."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

POWERSHELL = r'''param([string]$Maven = 'mvn', [string]$RepositoryPath)
$ErrorActionPreference = 'Stop'
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'sdk.json') -Raw | ConvertFrom-Json
$artifacts = @('glswing-parent', 'glswing', 'glswing-lsp', 'glswing-filedialogs-swt')
foreach ($artifact in $artifacts) {
    $name = "$artifact-$($manifest.version)"
    $pom = Join-Path $PSScriptRoot "poms/$name.pom"
    $arguments = @('-B', 'org.apache.maven.plugins:maven-install-plugin:3.1.3:install-file', "-DpomFile=$pom", '-DgeneratePom=false')
    if ($artifact -eq 'glswing-parent') { $arguments += "-Dfile=$pom" }
    else {
        $arguments += "-Dfile=$(Join-Path $PSScriptRoot "lib/$name.jar")"
        $arguments += "-Dsources=$(Join-Path $PSScriptRoot "sources/$name-sources.jar")"
        $arguments += "-Djavadoc=$(Join-Path $PSScriptRoot "javadoc/$name-javadoc.jar")"
    }
    if ($RepositoryPath) { $arguments += "-DlocalRepositoryPath=$([IO.Path]::GetFullPath($RepositoryPath))" }
    & $Maven @arguments
    if ($LASTEXITCODE -ne 0) { throw "Could not install $artifact" }
}
Write-Host "Installed GLSwing $($manifest.version) locally. No account or publishing credentials needed."
'''

SHELL = r'''#!/bin/sh
set -eu
sdk_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
maven=${MAVEN:-mvn}
version=$(cat "$sdk_dir/VERSION")
for artifact in glswing-parent glswing glswing-lsp glswing-filedialogs-swt; do
    name="$artifact-$version"
    pom="$sdk_dir/poms/$name.pom"
    set -- -B org.apache.maven.plugins:maven-install-plugin:3.1.3:install-file "-DpomFile=$pom" -DgeneratePom=false
    if [ "$artifact" = glswing-parent ]; then
        set -- "$@" "-Dfile=$pom"
    else
        set -- "$@" "-Dfile=$sdk_dir/lib/$name.jar" "-Dsources=$sdk_dir/sources/$name-sources.jar" "-Djavadoc=$sdk_dir/javadoc/$name-javadoc.jar"
    fi
    if [ -n "${GLSWING_LOCAL_REPOSITORY:-}" ]; then
        set -- "$@" "-DlocalRepositoryPath=$GLSWING_LOCAL_REPOSITORY"
    fi
    "$maven" "$@"
done
printf 'Installed GLSwing %s locally. No account or publishing credentials needed.\n' "$version"
'''

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--candidate', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    candidate = a.candidate.resolve()
    evidence = json.loads((candidate / 'candidate.json').read_text())
    version, group = evidence['version'], evidence['groupId']
    for name, expected in evidence['artifacts'].items():
        assert digest(candidate / name) == expected, f'Candidate changed: {name}'
    output = a.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    folder = output / f'GLSwing-{version}'
    folder.mkdir()
    coordinate = candidate.joinpath('repository', *group.split('.'))
    for artifact in ['glswing-parent', 'glswing', 'glswing-lsp', 'glswing-filedialogs-swt']:
        name = f'{artifact}-{version}'
        copies = [(coordinate / artifact / version / f'{name}.pom', f'poms/{name}.pom')]
        if artifact != 'glswing-parent':
            copies += [(coordinate / artifact / version / f'{name}{suffix}.jar', f'{directory}/{name}{suffix}.jar')
                       for suffix, directory in [('', 'lib'), ('-sources', 'sources'), ('-javadoc', 'javadoc')]]
        for source, relative in copies:
            target = folder / relative
            target.parent.mkdir(exist_ok=True)
            shutil.copyfile(source, target)
    source = candidate / 'source'
    for name, path in [('LICENSE', 'LICENSE'), ('THIRD-PARTY.md', 'docs/THIRD-PARTY.md')]:
        shutil.copyfile(source / path, folder / name)
    (folder / 'VERSION').write_text(version + '\n', encoding='ascii')
    (folder / 'install-local.ps1').write_text(POWERSHELL, encoding='utf-8', newline='\n')
    (folder / 'install-local.sh').write_text(SHELL, encoding='utf-8', newline='\n')
    (folder / 'README.md').write_text(f'''# GLSwing {version}

GLSwing is a GPU-accelerated Java UI and node-editor library.
Requires Java 17+, Maven for the local-install option, and a desktop OpenGL driver.
No Sonatype account or publishing token is needed to use this package.

## Option 1: Install locally for Maven/IDE use

Unzip the package first. In PowerShell run:

```powershell
./install-local.ps1
```

On Linux/macOS run `sh ./install-local.sh`. The scripts install the parent POM,
three libraries, Sources and Javadoc in your local Maven repository. They may
download Maven's install plugin. They do not publish or upload anything.
Then add this dependency to your project:

```xml
<dependency>
  <groupId>{group}</groupId>
  <artifactId>glswing</artifactId>
  <version>{version}</version>
</dependency>
```

Maven downloads JOGL, GlueGen and their platform natives from their public
repositories. These third-party dependencies are not included in this ZIP.
Internet is needed on first resolution; no GLSwing-specific online repository
is needed after local installation. Optional modules use the same group/version:
`glswing-lsp` adds language-server integration, `glswing-filedialogs-swt` native
file dialogs. Add only the modules your application needs.

Build from the separate source ZIP instead with `./mvnw install` (Windows:
`./mvnw.cmd install`) if you prefer rebuilding the library.

## Option 2: Add JARs directly

Add `lib/glswing-{version}.jar` to your project's classpath. This library is not
a standalone application: double-clicking the JAR does not launch a demo.
Also add JOGL/GlueGen 2.6.0 and their native JARs for the target OS/architecture;
the exact declarations are in `poms/glswing-{version}.pom` and its parent.
Optional GLSwing modules require their own transitive dependencies as declared
in their POMs. Keep those dependencies' original licenses/notices when shipping
them with your application. The local Maven option handles dependency selection.

Attach the matching `sources/` and `javadoc/` JARs in your IDE for navigation and
API help. Java imports remain `com.vingame.glswing.*`; the new Maven group is
`{group}`. See `LICENSE` and `THIRD-PARTY.md` for redistribution scope.

The package is locally built and unsigned. Physical Linux/macOS acceptance is
separate from the automated Windows/offscreen GPU checks. `sdk.json` records
the build provenance. `SHA256SUMS` covers every file in the extracted SDK.
''', encoding='utf-8', newline='\n')
    manifest = {'version': version, 'groupId': group, 'sourceCommit': evidence['sourceCommit'],
                'buildKind': evidence['buildKind'], 'signed': False,
                'tests': evidence['tests'], 'runtimeDependenciesBundled': False}
    (folder / 'sdk.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    files = sorted(f for f in folder.rglob('*') if f.is_file())
    (folder / 'SHA256SUMS').write_text(''.join(f'{digest(f)}  {f.relative_to(folder).as_posix()}\n' for f in files), encoding='ascii')
    archive = output / f'GLSwing-{version}-SDK.zip'
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
        for f in sorted(folder.rglob('*')):
            if f.is_file(): z.write(f, f.relative_to(output).as_posix())
    shutil.copyfile(candidate / f'GLSwing-{version}-source.zip', output / f'GLSwing-{version}-source.zip')
    (output / 'SHA256SUMS').write_text(''.join(f'{digest(f)}  {f.name}\n' for f in sorted(output.glob('*.zip'))), encoding='ascii')
    print(json.dumps({'sdk': str(archive), 'sha256': digest(archive)}, indent=2))

if __name__ == '__main__':
    main()
