# GLSwing 0.2.0 — release candidate

- Shared keyboard command routing across focus, hovered containers and a default
  command target; shared Find UI for text, code and Markdown.
- More compact graph arrangement, preserved camera position on resize and
  improved node title sizing and diagnostic badges.
- Borderless themes retain connection selection/deletion and valid rendering.
- Reset name follows live language changes, even for menus created before a
  node is attached. Consumer-side translation workarounds are unnecessary.
- Code language hooks support completion, formatting, semantic text emphasis
  and improved parameter hints. Curve controls reveal handles on interaction.
- Cached drawings recover after renderer recreation. Linux dialogs prefer
  opaque surfaces; physical Linux/macOS graphics acceptance remains separate.
- Original SVG demo artwork replaces an image without a recorded license.
  Optional library JARs include the project license and have Sources/Javadoc.

Java 17 and JOGL 2.6.0 remain required. Applications should rebuild and test
against 0.2.0. API checks against the locally installed 0.1.0 artifacts pass;
those artifacts are local builds, not an independently verified published
baseline. In particular the Core baseline records commit `a224af0` with a dirty
worktree. The comparison is useful regression evidence, not proof of compatibility
with the Git tag or every historical 0.1.0 build. No new binary-compatibility
guarantee or document-format change is introduced by this release preparation.

This is a local, unsigned candidate. Central namespace verification, signing and
explicit publication remain outstanding. A live external JDT language-server
integration test is opt-in and was not run; its unit tests were run.
