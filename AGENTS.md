# Repository working agreements

## Versioning

- Consider version impact on every change without waiting for a reminder. EasyKnack
  and GLSwing are independently versioned; do not keep their numbers in lockstep.
- Keep the product version in Maven as the single source of truth. Derive display
  versions and diagnostic metadata from generated build information, not literals.
- Assess the cumulative changes since the last release: compatible fixes require
  a patch release, compatible features a minor release, and incompatible public
  contracts a major release once stable. During 0.x development use minor releases
  for breaking changes and explicitly document them; do not declare 1.0 automatically.
- Maintain the next development version autonomously when the scope warrants it.
  Once that version already covers a change, do not increment it again for every
  edit, commit or compilation. If no release baseline exists, retain the current
  development version and record the intended first release rather than guessing.
- Documentation-only changes and tests alone do not require a product-version bump.
- Record user-visible changes and compatibility implications in concise English
  release notes, separate from user documentation and implementation history.
- Package builds should generate build identity automatically: CI run number and
  retry identity for official builds; an explicitly local identity for local builds;
  Git commit and dirty-worktree status for both. Never invent CI numbers or imply
  that a local counter is globally unique. Eclipse incremental compilation must not
  increment the product version or modify tracked version files.
- Preferred display: `0.2.0 — Build 147 (d48b02c)`; SemVer build metadata uses `+`,
  while `-` denotes prerelease identifiers. Retain Maven SNAPSHOT conventions for
  development artifacts. Keep installer version constraints separate from display.
- Keep document-format, plugin ABI and audio-host protocol versions separate from
  the product version; change them only when the corresponding contract changes.
- Never overwrite an existing released artifact with different contents. Release
  tags such as `v0.2.0`, publishing and pushing remain actions requiring user scope;
  ordinary version maintenance does not itself authorize a release.
- Package metadata generation is implemented in glswing-core at prepare-package;
  release CI and installer integration remain pending. See the EasyKnack build-identity notes.

## Documentation

- User documentation is English, task-oriented and independent of Eclipse.
- Keep developer setup, historical decisions and debugging accounts separate from
  user instructions. Node details belong in a linked reference, not the quick start.
- Preserve manual edits and keep GLSwing an external Maven dependency.

## Commits

- Commit completed, coherent changes regularly and autonomously after relevant
  checks pass. Prefer one focused commit per logical feature or fix; do not wait
  for a reminder or accumulate unrelated completed work into a large commit.
- Review the diff and stage explicit files or hunks. Preserve user edits and
  existing staged work; do not sweep unrelated changes into a commit. Split mixed
  changes where safe, and leave uncertain pre-existing work untouched.
- Use concise English commit messages describing the change and its purpose.
  Keep EasyKnack and GLSwing commits separate, with required library changes
  committed before their consumer changes when practical.
- Do not commit generated binaries, caches, local settings or secrets. Record
  relevant validation and disclose any checks that could not be completed.
- Local commits are authorized by this agreement. Pushing, creating release tags
  and publishing artifacts still require explicit user scope.
