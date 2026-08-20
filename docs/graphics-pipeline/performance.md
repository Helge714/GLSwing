# Batching, Performance und Lebensdauer

[Zur API-Landkarte](../graphics-pipeline.md) · [Picking](picking.md)

GLSwing optimiert die CPU-Aufbereitung und die Zahl der GPU-Drawcalls, kann aber
die tatsächliche Pixelarbeit, Host-Präsentation oder VSync nicht wegoptimieren.
Dieses Kapitel erklärt die wichtigsten Grenzen und Messwerte.

## Renderfluss

```text
GLContainer-Hierarchie
  -> Damage/Culling
  -> Recording-Replay oder Immediate-Aufbau
  -> Filled / Texture / Text / Universal UI
  -> Layer- und RenderTarget-Pässe
  -> Farbausgabe + Picking-Snapshot
  -> GLCanvas oder GLSwingPanel-Präsentation
```

## Was einen Batch beendet

- Wechsel zwischen Filled, Texture, Text und Universal-UI,
- Shaderwechsel oder Uniformänderung,
- Projection-/View-Wechsel,
- Clip-, Layer- oder RenderTarget-Wechsel,
- Änderung der Color-/Pickingausgaben,
- `clear()` oder `flush()`,
- Vertex-/Instanzkapazität oder Texturslotgrenze.

Model-Transforms allein müssen nicht flushen. Die CPU transformiert
Geometrie/Instanzen passend, bevor sie im Batch landet.

## Standardpipeline gegen UI-Pipeline

Die Standardpipeline ist flexibler und unterstützt eigene Shader pro Typ. Bei
einem klassischen Buttonmuster `Fill -> Text -> Fill -> Text` müsste sie die
sichtbare Reihenfolge mit Typwechseln bewahren. Der Universal-UI-Batch codiert
Flächen, MSDF-Glyphen und Bilder dagegen als kompatible Instanzen und hält viele
Controls in wenigen Drawcalls.

Verwende die UI-Pipeline nicht pauschal für beliebige Kunstgrafik: Ihre
analytischen Formen sind absichtlich spezialisiert und ignorieren eigene
Filled-/Texture-/Textshader.

## Recording

Recording reduziert CPU-Kosten für stabile Geometrie erheblich, besonders bei
Paths, runden/dashed Strokes und Text. Replay bleibt batchfreundlich. Eine
Geometrieänderung benötigt `invalidateRecording`; reine Transform-/Farbwechsel
können oft `GLRenderParameters` nutzen.

Screen-Space-Strokes bleiben viewabhängig. Ihre Geometrie wird bei geänderter
Model-/Viewmatrix einmal neu kompiliert und anschließend gecacht.

## Atlanten und Slots

Kleine statische Bilder gemeinsam in Atlaspages zu speichern reduziert
Texturwechsel. Der UI-Batch besitzt 16 Texturslots; eine Page mit vielen Icons
belegt nur einen. Große, dynamische und Repeat-Bilder bleiben Standalone.

Adaptive RGBA-Icons rasterisieren fehlende Größen im Hintergrund und laden
höchstens kontrolliert neue Varianten in den Renderer, damit Zoom keinen langen
Frame blockiert.

## Layerkosten

Layerarbeit skaliert vor allem mit der bearbeiteten Pixelregion:

- ohne Effekt: ein zusätzlicher Compositepass,
- einfacher Effekt: ein zusätzlicher Pass,
- Blur/DropShadow/InnerShadow: zwei zusätzliche Passes je Effekt,
- jeder weitere Effekt verarbeitet das Ergebnis des vorherigen.

Deshalb immer `beginLayer(x,y,w,h)` verwenden, wenn Bounds bekannt sind.
`beginLayer()` sollte eine bewusste Ausnahme bleiben.

## RenderTargets

Ein Target mit 2048×2048 bearbeitet unabhängig von seiner späteren
Darstellungsgröße Millionen Pixel. Wähle die kleinste sachlich ausreichende
Auflösung. Ping-Pong-Simulationen bleiben auf der GPU; synchrones Readback im
Framepfad vermeiden.

## Damage und Culling

`GLContainer` prüft visuelle Bounds gegen die Damage-Region und spielt nur
relevante eigene Inhalte ab. `visualOverflow` berücksichtigt Schatten und
andere Außenpixel. Innerhalb eines großen eigenen Renderhooks kann
`intersectsRedrawBounds` zusätzliche teure Teilbereiche überspringen.

Damage-Tracking reduziert Arbeit bei statischen Oberflächen; eine hohe
theoretische FPS ist nicht sein einziges Ziel. Wenn kein sichtbarer Inhalt
dirty ist, muss die Bibliothek nicht künstlich Frames erzeugen.

## Canvas gegen `GLSwingPanel`

- Direkter `GLCanvas`: OpenGL präsentiert unmittelbar im heavyweight Canvas;
  höchste und stabilste Renderingrate.
- `GLSwingPanel`: integriert sich in Swing-Painting und dessen Scheduling;
  praktisch für gemischte Oberflächen, aber zusätzliche Präsentationskosten.
- `GLFrame`: Komforthost um die direkte Canvasvariante.

Ein weiterer FBO beseitigt die Swing-/AWT-Präsentationsgrenze nicht automatisch.

## Renderauflösung und HiDPI

`setRenderResolutionScale` am Host reduziert/erhöht die physische
Renderauflösung. Logische Koordinaten bleiben stabil. Niedrigere Skalen sparen
Pixelarbeit, können aber Text, Kanten und Effekte weicher machen. Eingebaute
Effekte und adaptive Icons berücksichtigen DeviceScale.

## Messen

Am Host stehen typischerweise bereit:

| Messwert | Aussage |
|---|---|
| `getFramesPerSecond()` | Präsentierte Frames pro Sekunde |
| `getAverageFrameTimeMillis()` | Gemittelte End-to-End-Framezeit |
| `getLastDrawCallCount()` | Drawcalls des letzten Frames |
| `getFrameProfile()` | Detaillierte CPU-/GPU-/Präsentationsphasen |
| `getActualAntialiasingSamples()` | Tatsächlich gewähltes MSAA |
| `getActualSwapInterval()` | Tatsächliches VSync-Verhalten |
| Surface-/Renderpixelgröße | HiDPI- und Resolution-Scale-Kosten |

FPS allein ist nicht ausreichend: VSync, Swing-Scheduling und Fensterzustand
können die Rate deckeln, obwohl CPU und GPU freie Zeit besitzen.

## Ressourcenlebensdauer

| Typ | Besitzerregel |
|---|---|
| `GLImage`, `GLIcon` | CPU-Ressource; Renderer verwaltet seine GPU-Auflösung |
| `GLTexture`, `ShaderProgram`, eigener `GLFont` | Im passenden GL-Disposehook freigeben |
| `GLRecording` | Gehört seinem Renderer; besitzt referenzierte Ressourcen nicht |
| `GLRenderTarget`, `GLFeedbackBuffer` | Mit `close()` beenden; renderergebunden |

Niemals Low-Level-GL-Ressourcen auf einem beliebigen AWT-Thread erstellen oder
löschen, wenn dort kein passender GL-Kontext current ist.

## Praktische Checkliste

1. Stabile komplexe Geometrie recorden.
2. Standardcontrols über den UI-Batch zeichnen.
3. Kleine statische Bilder der Atlasautomatik überlassen.
4. Shader-/Uniformwechsel gruppieren.
5. Layer auf echte Bounds begrenzen und Effektketten bewusst ordnen.
6. RenderTargets nur aktualisieren, wenn ein neuer Zustand nötig ist.
7. GPU-Readbacks im interaktiven Framepfad vermeiden.
8. Mit Frameprofil und Drawcalls messen, nicht nur mit FPS urteilen.
