# GLSwingNext-Dokumentation

Die README im Projektstamm ist der kurze Einstieg. Ausführliche, versionierte
Anleitungen liegen hier, damit die öffentliche API nicht zwischen Demos und
langen Featurelisten gesucht werden muss.

## Inhalt

- [Handbücher](#handbücher)
- [Ausführbare Referenzen](#ausführbare-referenzen)

## Handbücher

### UI-Framework

Die [vollständige UI-API-Landkarte](ui-framework.md) führt von Hosts und
`GLContainer` bis zum Node-Editor:

| Kapitel | Inhalt |
|---|---|
| [Komponenten und Hosts](ui-framework/components.md) | Lifecycle, Bounds, Z-Order, Damage, `GLFrame`, Canvas, Panel und Dialoge |
| [Layouts und Größen](ui-framework/layout.md) | Freies Layout, Grid, Constraints, Messen und eigene Layouts |
| [Controls](ui-framework/controls.md) | Alle Standardcontrols, Menüs, Tabs, Scrollen und Eingabe |
| [Themes und Sprachen](ui-framework/themes-localization.md) | Styles, externe Theme-Dateien, Farben, Live-Reload und Locale-Fallback |
| [Undo/Redo](ui-framework/history.md) | `GLSaveManager`, Transaktionen, Shortcuts und automatische Integration |
| [Node-System](ui-framework/nodes.md) | Nodes, Ports, Verbindungen, Auswahl, Ansicht, Styles und History |

### Grafikpipeline

Beginne mit der [vollständigen `GLContext2D`-API-Landkarte](graphics-pipeline.md).
Die Detailkapitel bauen in dieser Reihenfolge darauf auf:

| Kapitel | Inhalt |
|---|---|
| [Context konfigurieren](graphics-pipeline/context-configuration.md) | Zeichenstil aus Farbe und Stroke, Koordinatensysteme, Einheiten, Clip und Outputs |
| [Zeichenbefehle](graphics-pipeline/drawing.md) | Alle Draw-/Fill-Methoden, Overloads und klare Trennung von Immediate, Recording, UI und Nicht-UI |
| [Recording](graphics-pipeline/recording.md) | Aufnahme, Replay, Parameterregionen, Caches und Referenzen |
| [Shader](graphics-pipeline/shaders.md) | Alle Shaderlayouts, Uniformtypen und Custom-Layer-Shader |
| [Depth und 2.5D-Materialien](graphics-pipeline/depth-materials.md) | Depth-State, Sprites, Color-/Depth-/Normalmaps, Alpha und Licht |
| [Layer und Effekte](graphics-pipeline/layers-effects.md) | Compositing, Blendmodi und jeder eingebaute Effekt einzeln |
| [RenderTargets](graphics-pipeline/render-targets.md) | Persistente FBOs, Feedback und Ping-Pong-Simulationen |
| [Ressourcen](graphics-pipeline/resources.md) | Bilder, Atlanten, Icons, Text, Pfade und SVG |
| [Picking](graphics-pipeline/picking.md) | IDs, MouseTargets, lokale Koordinaten, Events und Snapshotstrategie |
| [Performance](graphics-pipeline/performance.md) | Flushgrenzen, UI-Batch, Layerkosten, Messwerte und Lebensdauer |

Jedes Kapitel beginnt mit Querverweisen und verwendet dieselben Begriffe aus
der API-Landkarte. Tabellen sind die Referenz; Beispiele zeigen den empfohlenen
Einsatz und wiederholen nicht die vollständige Methodensammlung.

## Ausführbare Referenzen

Die Klassen in
`glswing-examples/src/main/java/com/vingame/glswing/demo` ergänzen das
Handbuch:

- `DrawingApiDemo`, `PathDemo`, `SvgImportDemo`
- `CustomShaderDemo`, `RecordingParameterDemo`, `LayerDemo`
- `ImageAtlasDemo`, `DepthSpriteDemo`, `SpritePipelineBenchmarkDemo`,
  `SpritePipelineBenchmark`, `PickingDemo`, `GameOfLifeDemo`
- `GuiShowcaseDemo`, `CanvasGuiShowcaseDemo`, `GLFrameDemo`
- `NodeEditorDemo`

Die Dokumentation beschreibt die jeweils aktuelle API. In der derzeitigen
Entwicklungsphase gibt es bewusst keine Kompatibilitätsschicht für ältere
Zwischenstände.
