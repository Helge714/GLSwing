# Public API and Compatibility

[Documentation index](README.md) · [Installation](../README.md#installation) · [Release notes](../CHANGELOG.md)

GLSwing treats its documented public Java packages as the supported library API.
This page defines that boundary and the compatibility check used for releases
after `0.1.0`.

## Contents

- [Supported API](#supported-api)
- [Implementation packages](#implementation-packages)
- [Compatibility policy](#compatibility-policy)
- [Running the compatibility check](#running-the-compatibility-check)
- [Reviewing intentional changes](#reviewing-intentional-changes)

## Supported API

Public and protected types and members in the following artifacts form the
supported API unless their package is listed as an implementation package:

- `com.vingame:glswing`
- `com.vingame:glswing-lsp`
- `com.vingame:glswing-filedialogs-swt`

Generated Javadoc is the member-level reference. The conceptual guides describe
object relationships, lifecycle rules, configuration formats, and recommended
usage. The examples artifact demonstrates the API but is not itself a supported
library API.

## Implementation packages

The following packages are public only because GLSwing's implementation spans
multiple Java packages. Applications must not import them:

- `com.vingame.glswing.internal`
- `com.vingame.glswing.rendering.internal`
- `com.vingame.glswing.rendering.batch`

These packages are excluded from generated Javadoc and automated compatibility
checks. Their types and signatures may change without notice. No supported
public top-level API currently exposes a type from these packages.

## Compatibility policy

Beginning with `0.1.0`, each release is compared with the intended predecessor.
The build rejects binary- or source-incompatible changes to supported public and
protected members. Synthetic compiler artifacts are ignored.

An incompatible change requires an explicit API decision. During the `0.x`
series, an accepted breaking change belongs in a new minor version and must be
described prominently in the release notes. Patch releases must remain source
and binary compatible.

The automated check supplements API review. It cannot determine behavioral
compatibility, thread-safety changes, altered persistence formats, or changes to
theme, input-map, and localization keys; those remain part of release review.

## Running the compatibility check

Build the current source and compare it with a published predecessor:

```bash
./mvnw -Papi-compatibility \
  -Dapi.compatibility.previousVersion=0.1.0 \
  verify
```

On Windows:

```powershell
.\mvnw.cmd -Papi-compatibility `
  -Dapi.compatibility.previousVersion=0.1.0 `
  verify
```

Replace `0.1.0` with the release that the current version must remain compatible
with. The comparison covers the core, LSP, and SWT artifacts. Reports are written
to each module's `target/japicmp` directory.

Version `0.1.0` establishes the first baseline, so it has no earlier artifact to
compare. Run the profile for every later release candidate.

## Reviewing intentional changes

Do not suppress a failure merely to make a build pass. Confirm whether the
affected member belongs to the supported API, document the migration path, and
choose a version that communicates the compatibility impact. Exclusions belong
only to implementation packages, not individual supported members.
