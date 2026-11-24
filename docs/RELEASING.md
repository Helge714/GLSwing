# Releasing GLSwing

The Maven parent owns the version. Development stays on `0.2.0-SNAPSHOT`;
the next intended release is `0.2.0`. The Core, LSP and SWT library artifacts
and their parent POM are published together. Examples and consumer tests are
source material, not Central library artifacts. EasyKnack is independently versioned.

## Local candidate (no credentials, upload or tag)

Commit reviewed changes, then run with JDK 17+ (JDK 25 is currently validated),
Python 3.9+ and Maven 3.9.11 available:

```sh
python scripts/prepare-release.py --output target/release-candidate-0.2.0
```

Use `--maven /path/to/mvn` or `--offline` when appropriate. Output must not
exist. The script creates a detached Git worktree, derives the release version
from the parent POM, transforms the reactor POMs there, builds and runs the Core
GPU tests and isolated Maven consumer, checks runtime JAR licenses/content,
and creates unsigned Maven and source ZIPs with checksums and candidate evidence.
The development checkout remains on SNAPSHOT. `source/` is retained for review.
Local build identity records the original commit and the version-transformation
dirty state; this is not an official CI build. `candidate.json` records the change.
No released artifact is overwritten. Do not distribute the unsigned ZIP as
a Central-ready bundle. Do not modify artifacts after signing.

When done, use `git worktree remove <candidate>/source` before deleting the
candidate directory. Keep the candidate ZIPs/evidence until release acceptance.

## Before publication

1. Review [the redistribution inventory](THIRD-PARTY.md), candidate evidence,
   source changes and release notes. Preserve matching sources and license files.
2. Verify the Central namespace for `com.vingame`; the build does not establish
   domain ownership. If unavailable, decide and update all Maven coordinates
   consistently before producing the final artifacts. Java package names can stay.
3. Update README installation examples to the final released version only when
   that version is actually available. Review API/document compatibility since
   0.1.0; 0.x minor releases may break APIs. Do not advertise 0.2.0 as a drop-in
   binary replacement without an API comparison and consumer verification.
4. Prepare a clean release commit with final Maven versions and SCM tag metadata;
   obtain authorization before pushing or creating `v0.2.0`.
5. Configure a PGP key and a Central Portal token outside the repository. Never
   commit keys, passphrases or token-bearing settings files.

The optional `central` profile supplies the Central publisher and GPG signing.
The `release` profile supplies Javadoc JARs; Sources JARs are always attached.
Use `-pl glswing-core,glswing-lsp,glswing-filedialogs-swt -am` to publish only
the libraries and parent. Runtime dependencies stay transitive, not shaded.

With a signing key configured, the default is **bundle only**:

```sh
mvn -Prelease,central -pl glswing-core,glswing-lsp,glswing-filedialogs-swt -am clean deploy
```

`central.skipUpload=true` is the default. This produces a signed bundle without
uploading. Inspect it and verify signatures against the intended public key.
After explicit publication authorization, setting `-Dcentral.skipUpload=false`
uploads for Portal validation. `autoPublish=false` leaves final publication manual.
Use a Maven `settings.xml` server with id `central` and Portal token credentials.
Follow current Sonatype instructions for key export/passphrase configuration.

This repository does not yet have a configured Central account, verified namespace,
signing identity or official release CI. The local candidate does not claim those.

## Sources

- [Central requirements](https://central.sonatype.org/publish/requirements/)
- [Namespace verification](https://central.sonatype.org/register/namespace/)
- [Central Maven publisher, including skipUpload](https://central.sonatype.org/publish/publish-portal-maven/)
- [Apache-2.0 redistribution terms](https://www.apache.org/licenses/LICENSE-2.0)
