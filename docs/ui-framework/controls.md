# Controls

[UI-Übersicht](../ui-framework.md) · [Komponenten](components.md) ·
[Layouts](layout.md) · [Themes](themes-localization.md) ·
[History](history.md)

## Inhalt

- [Gemeinsames Control-Modell](#gemeinsames-control-modell)
- [Schnellübersicht](#schnellübersicht)
- [Label, Button und Checkbox](#label-button-und-checkbox)
- [Slider](#slider)
- [Texteditor](#texteditor)
- [ComboBox](#combobox)
- [Popupmenüs und Menüleiste](#popupmenüs-und-menüleiste)
- [Tabs](#tabs)
- [ScrollPane und ScrollBar](#scrollpane-und-scrollbar)

## Gemeinsames Control-Modell

Interaktive Standardkomponenten erben von `GLControl`. Die Klasse verwaltet
Enabled-, Hover-, Pressed- und Fokuszustand, Pointer-Capture sowie Tastaturziele.
Die Darstellung leitet sich aus `GLControlState` ab:

- `NORMAL`
- `HOVERED`
- `PRESSED`
- `DISABLED`

`setEnabled(false)` beendet aktive Interaktionen und verhindert neue Eingaben.
`setFocusable(boolean)` steuert, ob das Control GL-Tastaturfokus erhalten kann.
Subklassen reagieren über die geschützten `control...`-Hooks auf Zustands-,
Maus-, Fokus- und Tastaturereignisse; die finale Ereignisverwaltung von
`GLControl` sollte dabei nicht umgangen werden.

Jeder Standardtyp besitzt einen unveränderlichen Style-Record. `setGLStyle(...)`
setzt eine lokale Darstellung, `clearGLStyle()` kehrt zum geerbten Theme zurück.

## Schnellübersicht

| Control | Erzeugung | Zentrale API |
|---|---|---|
| `GLLabel` | `new GLLabel(text)` | `get/setText`, `get/set/clearGLStyle` |
| `GLButton` | `new GLButton(text)` | ActionListener, ActionCommand, `doClick()` |
| `GLCheckBox` | `new GLCheckBox(text[,selected])` | `get/setSelected`, ActionListener, `doClick()` |
| `GLSlider` | `fixed(...)`, `free(...)` | Wert, Bereich, Drag-Raten, Editor, Reset-Menü |
| `GLSliderGroup` | `new GLSliderGroup(sliders...)` | Verbundene vertikale Slider |
| `GLTextEditor` | `new GLTextEditor([text])` | Caret, Auswahl, Filter, Validierung, Commit/Cancel |
| `GLComboBox<T>` | leer oder Liste | Auswahl, Sortierung, Untermenüs, Popupregeln |
| `GLMenuBar` | `new GLMenuBar()` | Menüs hinzufügen und schließen |
| `GLPopupMenu` | `new GLPopupMenu()` | Items, Submenus, Titel, Overflow, Positionierung |
| `GLTabbedPane` | `new GLTabbedPane()` | Tabs, Auswahl, Aktivierung |
| `GLScrollPane` | leer oder Content | Viewport, Policies und Offset |
| `GLScrollBar` | horizontal/vertikal | Wert, Extent, Maximum, Inkremente |
| `GLLine` | siehe Konstruktoren | leichtes Linien-/Separator-Control |

Text, Padding und Größen verwenden logische Pixel. Zu breiter Text wird bei den
dafür vorgesehenen Controls gekürzt; reservierte Bereiche wie Sliderwert,
ComboBox-Pfeil oder Popup-Scrollbar behalten Priorität.

## Label, Button und Checkbox

```java
GLLabel status = new GLLabel("Nicht verbunden");

GLButton connect = new GLButton("Verbinden");
connect.setActionCommand("connect");
connect.addActionListener(event -> status.setText("Verbunden"));

GLCheckBox preview = new GLCheckBox("Vorschau", true);
preview.addActionListener(event -> setPreviewVisible(preview.isSelected()));
```

`doClick()` löst dieselbe semantische Aktion wie ein gültiger Mausklick aus.
`GLButton` und `GLCheckBox` sind standardmäßig nicht fokussierbar; bei Bedarf
wird dies explizit mit `setFocusable(true)` aktiviert.

`GLLine` ist für Separatoren und einfache UI-Linien gedacht. Die Orientierungen
`HORIZONTAL`, `VERTICAL`, `DIAGONAL_DOWN` und `DIAGONAL_UP` erzeugen typische
Verläufe; `setRelativeEndpoints(x1,y1,x2,y2)` erlaubt eigene, nicht geklemmte
Endpunkte relativ zu Breite und Höhe. Farbe und `GLStroke` sind austauschbar.
Das Control selbst ist nicht pickbar. Für frei gezeichnete oder komplexe Linien
dient dagegen `GLContext2D` mit `GLStroke`.

## Slider

### Fixed und Free

```java
GLSlider opacity = GLSlider.fixed("Deckkraft", 0, 1, 0.8);
opacity.setStep(0.01);
opacity.setValueFormatter(v -> String.format("%.0f %%", v * 100));

GLSlider exposure = GLSlider.free("Belichtung", 0);
exposure.setDragSensitivity(0.1);
exposure.setPreciseDragFactor(0.1); // Shift
exposure.setCoarseDragFactor(10);   // Strg/Ctrl
```

`FIXED` begrenzt den Wert auf `minimum .. maximum` und visualisiert ihn als
Fortschrittsfüllung. `FREE` besitzt keinen Wertebereich und bietet zusätzlich
Pfeilflächen. Seine Drag-Sensitivität ist eine Wertänderung pro horizontalem
logischem Bildschirm-Pixel. Shift multipliziert sie mit dem präzisen Faktor,
Strg/Ctrl mit dem groben Faktor.

`step` quantisiert Wertänderungen. `increment()` und `decrement()` ändern um
diesen Schritt. `setDefaultValue(...)` definiert das Ziel von `reset()`.
Rechtsklick öffnet das Kontextmenü mit dem lokalisierten Reset-Eintrag;
`getContextMenu()` erlaubt zusätzliche anwendungsspezifische Einträge.

Ein Klick auf den Wert startet den eingebetteten Texteditor.
`startValueEditing()`, `cancelValueEditing()` und `isValueEditing()` steuern ihn
programmatisch. Erfolgreiche Benutzeränderungen werden mit dem History-Manager
des Hosts zusammengeführt; reine programmatische `setValue(...)`-Aufrufe sind
nicht automatisch Undo-Schritte.

### Slidergruppen

`GLSliderGroup` fasst vertikal angeordnete Slider zu einer optischen und
interaktiven Einheit zusammen:

```java
GLSlider r = GLSlider.free("Rot", 0);
GLSlider g = GLSlider.free("Grün", 0);
GLSlider b = GLSlider.free("Blau", 0);
GLSliderGroup rgb = new GLSliderGroup(r, g, b);
```

Die Gruppe teilt Borders und äußere Rundungen. Beim zunächst vertikalen Ziehen
lassen sich mehrere Slider sammeln; der anschließende horizontale Drag setzt
alle ausgewählten Werte gemeinsam. Ein Klick ohne Wert-Drag öffnet verbundene
Editoren. Der gemeinsame Vorgang wird als ein Undo-Schritt aufgezeichnet.
`setRange(...)` setzt den Bereich aller kompatiblen Fixed-Slider.

## Texteditor

`GLTextEditor` ist ein wiederverwendbarer einzeiliger Editor mit Caret,
Selektion, Zwischenablage, horizontalem Scrollen und Edge-Fades.

```java
GLTextEditor editor = new GLTextEditor("12.5");
editor.setInputFilter(GLTextInputFormats.DECIMAL_NUMBER);
editor.setInputVerifier(text -> {
    try {
        return Double.isFinite(Double.parseDouble(text));
    } catch (NumberFormatException ignored) {
        return false;
    }
});
editor.setValidationFailureMode(
        GLTextEditor.ValidationFailureMode.RESTORE_LAST_VALID);
editor.addActionListener(event -> apply(editor.getText()));
```

Filter und Verifier lösen verschiedene Aufgaben:

| API | Zeitpunkt | Zweck |
|---|---|---|
| `setInputFilter(Predicate<String>)` | Bei jeder Zwischenänderung | Verbietet Zeichenfolgen, die nicht einmal ein gültiger Zwischenzustand sind. |
| `setInputVerifier(Predicate<String>)` | Beim Commit | Entscheidet, ob der fertige Wert gültig ist. |

`GLTextInputFormats.ANY` akzeptiert alles. `DECIMAL_NUMBER` akzeptiert auch
sinnvolle Zwischenstände wie ein einzelnes Minuszeichen; die abschließende
Validierung bleibt Aufgabe des Verifiers.

Bei `KEEP_INVALID` bleibt fehlerhafter Text zur Korrektur sichtbar.
`RESTORE_LAST_VALID` stellt den letzten gültigen Text wieder her, beendet den
Editierzustand und zeigt für `validationFeedbackDuration` Millisekunden den
Fehlerzustand. `commit()` gibt den Erfolg zurück, `cancel()` stellt den Zustand
vor der Bearbeitung wieder her.

Standardmäßig schreibt ein erfolgreicher Commit eine History-Änderung. Mit
`setHistoryEnabled(false)` kann ein zusammengesetztes Control diese Aufzeichnung
selbst übernehmen und doppelte Schritte vermeiden.

`setContainerShape(GLCornerRadii, borderWidth)` passt den Editor an gruppierte
oder eingebettete Formen an, etwa an Slidergruppen.

## ComboBox

```java
GLComboBox<Quality> quality = new GLComboBox<>(List.of(
        Quality.LOW, Quality.MEDIUM, Quality.HIGH));
quality.setItemTextProvider(Quality::displayName);
quality.setSelectedItem(Quality.HIGH);
quality.addActionListener(event -> setQuality(quality.getSelectedItem()));

GLComboBox<Quality>.SubMenu presets = quality.addSubMenu("Vorgaben");
presets.addItem(Quality.LOW).addItem(Quality.HIGH);
```

Items lassen sich hinzufügen, einfügen, entfernen und mit
`sortItems(comparator)` sortieren. `setItemTextProvider(...)` trennt Modellwert
und Anzeige. Untermenüs sind rekursiv und wählen weiterhin einen Wert derselben
ComboBox.

Das Popup prüft verfügbaren Platz, öffnet nach unten oder oben und verwendet bei
Bedarf Scrollbars. `setPopupMatchesComboWidth(true)` begrenzt seine Root-Breite
auf die ComboBox. `setPopupTitle(...)` setzt einen festen Titel oberhalb des
scrollbaren Inhalts.

`GLPopupTextOverflow` bestimmt den Umgang mit zu breitem Popuptext:

- `EXPAND`: Breitenvorgabe bei Bedarf überschreiten.
- `ELLIPSIS`: Text mit Auslassungszeichen kürzen.
- `HORIZONTAL_SCROLL`: horizontalen Scrollbereich verwenden.

## Popupmenüs und Menüleiste

```java
GLPopupMenu file = new GLPopupMenu();
file.setTitle("Datei");
file.setTitlePosition(GLPopupTitlePosition.TOP);
file.addItem("Neu", event -> createDocument());
file.addSeparator();

GLPopupMenu export = new GLPopupMenu();
export.addItem("PNG", event -> exportPng());
file.addSubMenu("Exportieren", export);

GLMenuBar bar = new GLMenuBar();
bar.addMenu("Datei", file);
```

Ein `GLMenuItem` unterstützt Text, Accelerator-Anzeigetext, ActionCommand,
Listener, `doClick()` und ein optionales Submenu. Accelerator-Text ist zunächst
nur Darstellung; die globale Tastenkombination bindet die Anwendung selbst.

`GLPopupMenu#show(host,x,y)` erwartet Host-Koordinaten. Kontextmenüs werden an
der Mausposition geöffnet. Popup und Submenus werden als oberste Overlays
gerendert, an die sichtbare Fläche geklemmt und bei Platzmangel vertikal oder
horizontal scrollbar. Der optionale Titel an `TOP` oder `BOTTOM` bleibt beim
Scrollen sichtbar.

`setImmediateCpuPickingEnabled(true)` ist eine gezielte Alternative für Menüs,
wenn Hover ohne den üblichen GPU-Picking-Snapshot im selben Event sichtbar sein
muss. Standardmäßig bleibt das gemeinsame GPU-Picking aktiv.

## Tabs

```java
GLTabbedPane tabs = new GLTabbedPane();
int sceneTab = tabs.addTab("Szene", scene);
tabs.addTab("Eigenschaften", properties);
tabs.setEnabledAt(sceneTab, true);
tabs.setSelectedIndex(sceneTab);
tabs.addSelectionListener((source, oldIndex, newIndex) -> updateTitle());
```

Nur der ausgewählte Inhalt ist sichtbar und interaktiv. Titel können geändert,
Tabs entfernt und einzelne Tabs deaktiviert werden. Ist die Tabzeile breiter als
der verfügbare Raum, scrollt sie per Mausrad; überlange Titel werden gekürzt.

## ScrollPane und ScrollBar

```java
GLScrollPane scroll = new GLScrollPane(largeContent);
scroll.setVerticalScrollBarPolicy(
        GLScrollPane.ScrollBarPolicy.AS_NEEDED);
scroll.setHorizontalScrollBarPolicy(
        GLScrollPane.ScrollBarPolicy.NEVER);
scroll.setScrollUnit(28);
scroll.scrollRectToVisible(x, y, width, height);
```

Der Viewport clippt Rendering und Picking seiner Inhalte. Die Policies
`AS_NEEDED`, `ALWAYS` und `NEVER` gelten getrennt je Achse. Offset und
Scroll-Einheit sind logische Pixel.

Das zugrunde liegende `GLScrollBar`-Modell verwendet:

- `value`: aktuelle Scrollposition,
- `extent`: sichtbare Länge,
- `maximum`: gesamte Inhaltslänge,
- `maximumValue = max(0, maximum - extent)`.

`setValues(value,extent,maximum)` aktualisiert das Modell atomar. Unit- und
Block-Inkrement steuern kleine beziehungsweise seitenweise Schritte.
