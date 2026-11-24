# Showcase theme bundle

These files are intentionally stored outside the application classpath, under `glswing-examples/resources`. Run
the showcase from the repository root and use **Theme → Colors** and **Theme →
Metrics** to demonstrate independent filesystem-based theme composition.

`showcase-bundle.gltheme` combines:

- `showcase-colors.gltheme`, including the external font declaration;
- `showcase-metrics.gltheme`, containing independent geometry settings;
- bundle-local ComboBox gradient overrides.

The deliberately distinctive, unmodified VT323 font and its complete SIL Open
Font License are in `fonts/`. The exaggerated metrics are intended for visual
verification rather than as production UI defaults.

The dark, light, and orange/VT323 color fragments can each be combined with
either the standard or exaggerated metrics fragment. Changing one selection
does not alter the other.
