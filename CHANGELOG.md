# Changelog

## 0.1.0 – 2026-08-21

Erster öffentlicher Preview-Release von GLSwing. Die API befindet sich noch in
aktiver Entwicklung und kann sich in folgenden `0.x`-Versionen verändern.

### Rendering

- Gebatchte 2D-Pipeline für Flächen, Linien, Texturen, MSDF-Text und UI.
- Transformationen, Projektionen, Kamera, Picking und Recordings.
- Pfade, Bezierkurven, gestrichelte Strokes und SVG-Import.
- Verschachtelte Layer, Blendmodi, Effekte und eigene Shader/Uniforms.
- RenderTargets, Feedback-Buffer, Atlanten und adaptive SVG-Rasterung.
- 2.5D-Sprites mit Depth-/Normalmaps, Licht, Instancing und residenten Puffern.

### UI

- `GLFrame`, direkter Canvas und einbettbares `GLSwingPanel`.
- Container, Layouts, Damage-Tracking und Z-Order.
- Buttons, Checkboxen, Slider, Texteditoren, ComboBoxen und Menüs.
- Tabs, Scrollbereiche, Dialoge, Themes und Lokalisierung.
- Node-Editor mit Ports, Verbindungen, Auswahl und Undo/Redo.

### Build und Plattformen

- Java 17 als Laufzeit- und API-Mindestversion.
- Maven-Multiprojekt mit getrenntem Core- und Examples-Artefakt.
- JOGL 2.6.0 und plattformspezifische Natives über Maven Central.
- Maven Wrapper, Source-JAR, Javadoc-JAR und automatisierte Smoke-Tests.
- Veröffentlichung unter der Apache License 2.0.
