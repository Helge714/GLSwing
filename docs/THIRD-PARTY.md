# Standalone redistribution inventory

Review scope: the parent POM and the unshaded `glswing`, `glswing-lsp`, and
`glswing-filedialogs-swt` JARs, their Sources/Javadoc JARs and tracked source
distribution. This inventory is technical evidence, not a warranty of title.

## Project content

The project is offered under Apache-2.0 (`LICENSE`). Every runtime library JAR
includes that text at `META-INF/LICENSE`; the source distribution retains the
root license. Core resources contain shaders, themes, translations and fifteen
SVG icons under the repository license. The old foreign `test_icon.svg` had
no recorded provenance/license and was replaced with original geometric demo
artwork during this preparation; its old bytes are not part of the candidate.
Historical Git commits still contain the old image and are outside this
current-tree artifact audit. The review found no separately credited
third-party code/license headers in the three library modules. This source scan
does not independently establish authorship of each icon or algorithm; imported
material must retain its original notices if subsequently identified.

No fonts, native binaries or copied dependency classes are included in the three
runtime JARs. The source-only showcase includes `VT323-Regular.ttf` and its
adjacent `OFL.txt`: copyright 2011, The VT323 Project Authors; SIL Open Font
License 1.1. Keep both files together. This font is not in the Core runtime.

## Dependencies resolved separately by Maven

| Library | Direct use | License family / evidence to preserve if bundled |
| --- | --- | --- |
| JOGL 2.6.0 and GlueGen 2.6.0 | Core | JogAmp BSD-style main terms plus component-specific third-party notices in upstream distributions; do not describe the complete native distribution as BSD-only. |
| Eclipse LSP4J 1.0.0 | LSP module | EPL-2.0; inspect the exact resolved JAR/POM notices and transitive JSON-RPC/Gson dependencies if redistributed. |
| Apache Commons Compress 1.28.0 | LSP module | Apache-2.0 with upstream LICENSE/NOTICE and transitive dependencies. |
| Eclipse SWT 3.130.0 | Optional native dialogs | EPL-2.0 and applicable platform fragment notices. The consumer OS/architecture selects the fragment. |

These are Maven dependencies, not embedded in the standalone library JARs.
Applications which create a fat JAR, installer or dependency bundle must audit
the actual resolved runtime closure, include applicable licenses/notices and
satisfy any corresponding source obligations. The Core's Apache license does
not relicense these dependencies. Preserve upstream NOTICE content where
applicable; a new NOTICE file is not inherently required merely for Apache-2.0.

The optional JDT Language Server manager downloads Eclipse JDT LS 1.60.0 only
when installation is explicitly invoked; no server distribution is embedded in
the LSP JAR or candidate ZIP. A product bundling that server must retain the
server's own legal files and audit the complete server distribution separately.
The runtime Java installation is supplied by the consumer; no JRE is bundled.

## Candidate verification

`scripts/prepare-release.py` checks each runtime JAR for the exact project
license, absence of embedded dependency packages, fonts and native libraries,
and presence of real Sources/Javadoc content. It records hashes, test totals,
commit and local-build identity. `build.log` retains module tests and isolated
consumer verification. The source ZIP preserves the showcase font license.

Public sources checked during preparation (2026-11-24):

- [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [Eclipse EPL-2.0](https://www.eclipse.org/legal/epl-2.0/)
- [VT323 upstream font license](https://github.com/google/fonts/blob/main/ofl/vt323/OFL.txt)
- Exact dependency POMs and legal resources in the locally resolved Maven artifacts.

Before uploading, confirm namespace ownership and rights to publish the project
content. This check is separate from the already performed EasyKnack installer
audit, which has a different and substantially larger redistribution scope.
