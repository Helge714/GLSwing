# GLSwingNext-Dokumentation

Die README im Projektstamm ist der kurze Einstieg. Ausführliche, versionierte
Anleitungen liegen hier, damit die öffentliche API nicht zwischen Demos und
langen Featurelisten gesucht werden muss.

## Handbücher

- [Grafikpipeline und `GLContext2D`](graphics-pipeline.md) – Rendering,
  Zustände, Formen, Pfade, Text, Bilder, Atlanten, Shader, Picking, Recording,
  Layer und RenderTargets.

## Ausführbare Referenzen

Die Klassen in `src/com/vingame/glswing/demo` ergänzen das Handbuch:

- `DrawingApiDemo`, `PathDemo`, `SvgImportDemo`
- `CustomShaderDemo`, `RecordingParameterDemo`, `LayerDemo`
- `ImageAtlasDemo`, `PickingDemo`, `GameOfLifeDemo`

Die Dokumentation beschreibt die jeweils aktuelle API. In der derzeitigen
Entwicklungsphase gibt es bewusst keine Kompatibilitätsschicht für ältere
Zwischenstände.
