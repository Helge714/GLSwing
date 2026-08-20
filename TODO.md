# GLSwingNext – TODO

Diese Datei sammelt bewusst zurückgestellte Aufgaben. Aktive Vorhaben gehören
nicht automatisch hierher; ein Punkt wird entfernt, sobald er umgesetzt oder
bewusst verworfen wurde.

## Eingabe und Fokus

- Vollständige Tastatursteuerung und Fokus-Traversierung ergänzen.
  - `Tab` und `Shift+Tab` wechseln zwischen fokussierbaren Elementen.
  - `Enter` und `Leertaste` bedienen Buttons und Checkboxen.
  - Pfeiltasten steuern Slider, Menüs, ComboBoxen und Tabs.
  - `Escape` schließt Editor, Popup oder Dialog.
  - Fokusreihenfolge beziehungsweise optionalen `tabIndex` festlegen.
  - Fokus beim Entfernen, Verstecken oder Deaktivieren eines Elements sicher
    weitergeben.
  - Theme-konfigurierbaren Fokusring anbieten.

## Datenansichten

- `GLScrollPane` im allgemeinen GUI-Showcase vollständig demonstrieren und
  Resize, Mausrad, Scrollbar-Dragging sowie verschachteltes Clipping testen.
- Generische `GLList<T>` entwickeln.
  - Single- und später Multi-Selection unterstützen.
  - Austauschbaren `GLListCellRenderer<T>` vorsehen.
  - Große Datenmengen durch Virtualisierung auf sichtbare Einträge begrenzen.
- Auf dem Listen- und Auswahlmodell später `GLTree<T>` und `GLTable` aufbauen.

## Rendering und Farbräume

- GPU-residente statische Instanz-/Meshbuffer als Ergänzung zum dynamischen
  Sprite-Instancing entwerfen.
  - Unveränderte Instanzdaten nur bei Erstellung oder gezieltem Update
    hochladen und anschließend ohne CPU-Neuaufbau zeichnen.
  - Ressourcenlebensdauer, partielles Update, Recording-Integration, Picking
    und Kontextverlust eindeutig regeln.
  - Die API allgemein halten; Tile-Chunks, Terrain und World-Streaming bleiben
    Anwendungs- oder Erweiterungslogik und werden nicht Teil des Kernmoduls.

- 2.5D-Licht nach realer Nutzung gezielt erweitern.
  - Mehrere Richtungs-, Punkt- oder Spotlichter erst nach einem Benchmark als
    Forward-, Forward+- oder separaten Lichtpass entwerfen.
  - Optional specular/roughness/emissive Maps ergänzen, ohne den einfachen
    `GLMaterial2D`-Pfad mit ungenutzten Kosten zu belasten.
  - Sortierhilfen oder Order-independent Transparency nur bei echtem Bedarf
    untersuchen; der aktuelle `BLEND`-Modus bleibt bewusst back-to-front.
  - Falls Anwendungen Depth nachbearbeiten müssen, eine samplebare
    Depth-Textur-Option für `GLRenderTarget` ergänzen; derzeit sind
    Depth-Attachments absichtlich günstige Renderbuffer.

- UI-Materialsystem auf Basis des Universal-UI-Shaders entwickeln.
  - Shader und immutable Uniform-/Stilwerte in einem wiederverwendbaren
    `GLUIMaterial` bündeln.
  - Benachbarte Instanzen mit demselben Material ohne zusätzlichen Flush
    gemeinsam zeichnen und Materialwechsel als klare Batchgrenze behandeln.
  - Prüfen, welche Materialparameter als UI-Instanzdaten oder über einen
    GPU-Puffer transportiert werden können, damit unterschiedliche Looks mit
    demselben Basis-Shader im selben Drawcall bleiben.
  - Zusammenspiel mit Themes, Recordings, Picking und dem vollständigen
    `setUIShader`-Vertrag festlegen.
  - Eine einfache Material-API beibehalten; Shader-/Uniformzustände bleiben
    der flexible Low-Level-Weg.
- Allgemeine GPU-Farbrauminterpolation für Vertexverläufe entwickeln.
  - Für beliebige Dreiecke und Polygone mindestens `SRGB`, `LINEAR_RGB` und
    `OKLAB` unterstützen.
  - Den Interpolationsmodus möglichst als Vertexattribut transportieren, damit
    ein Wechsel das Batching nicht durch zusätzliche Flushes unterbricht.
  - Farbattribute vor der Rasterizer-Interpolation in den gewählten Farbraum
    überführen und im Fragmentshader wieder nach sRGB konvertieren.
  - Eindimensionale Verläufe zusätzlich über Endfarben und einen
    Verlaufsparameter `t` abbilden, damit zyklische `HSV`- und
    `OKLCH`-Interpolation eindeutig möglich ist.
  - Zusätzliche Vertexbandbreite, Fragmentshader-Kosten, Gamut-Clipping und die
    Kompatibilität mit eigenen Filled-Shadern messen und dokumentieren.

## Später prüfen

- Barrierefreiheits-Schnittstellen für GPU-gerenderte Controls untersuchen.
- Einheitliche Tastenkürzel und Mnemonics für Menüs konzipieren.
