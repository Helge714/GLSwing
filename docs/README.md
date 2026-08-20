# GLSwingNext-Dokumentation

Die README im Projektstamm ist der kurze Einstieg. Ausführliche, versionierte
Anleitungen liegen hier, damit die öffentliche API nicht zwischen Demos und
langen Featurelisten gesucht werden muss.

## Handbücher

### Grafikpipeline

Beginne mit der [vollständigen `GLContext2D`-API-Landkarte](graphics-pipeline.md).
Die Detailkapitel bauen in dieser Reihenfolge darauf auf:

| Kapitel | Inhalt |
|---|---|
| [Context konfigurieren](graphics-pipeline/context-configuration.md) | Farbe, Transform, Projection, Kamera, Stroke, Toleranzen, Clip und Outputs |
| [Zeichenbefehle](graphics-pipeline/drawing.md) | Alle Draw-/Fill-Methoden, Overloads und klare Trennung von Immediate, Recording, UI und Nicht-UI |
| [Recording](graphics-pipeline/recording.md) | Aufnahme, Replay, Parameterregionen, Caches und Referenzen |
| [Shader](graphics-pipeline/shaders.md) | Alle Shaderlayouts, Uniformtypen und Custom-Layer-Shader |
| [Layer und Effekte](graphics-pipeline/layers-effects.md) | Compositing, Blendmodi und jeder eingebaute Effekt einzeln |
| [RenderTargets](graphics-pipeline/render-targets.md) | Persistente FBOs, Feedback und Ping-Pong-Simulationen |
| [Ressourcen](graphics-pipeline/resources.md) | Bilder, Atlanten, Icons, Text, Pfade und SVG |
| [Picking](graphics-pipeline/picking.md) | IDs, MouseTargets, lokale Koordinaten, Events und Snapshotstrategie |
| [Performance](graphics-pipeline/performance.md) | Flushgrenzen, UI-Batch, Layerkosten, Messwerte und Lebensdauer |

Jedes Kapitel beginnt mit Querverweisen und verwendet dieselben Begriffe aus
der API-Landkarte. Tabellen sind die Referenz; Beispiele zeigen den empfohlenen
Einsatz und wiederholen nicht die vollständige Methodensammlung.

## Ausführbare Referenzen

Die Klassen in `src/com/vingame/glswing/demo` ergänzen das Handbuch:

- `DrawingApiDemo`, `PathDemo`, `SvgImportDemo`
- `CustomShaderDemo`, `RecordingParameterDemo`, `LayerDemo`
- `ImageAtlasDemo`, `PickingDemo`, `GameOfLifeDemo`

Die Dokumentation beschreibt die jeweils aktuelle API. In der derzeitigen
Entwicklungsphase gibt es bewusst keine Kompatibilitätsschicht für ältere
Zwischenstände.
