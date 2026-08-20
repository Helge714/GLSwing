# Undo/Redo mit `GLSaveManager`

[UI-Übersicht](../ui-framework.md) · [Komponenten](components.md) ·
[Controls](controls.md) · [Node-System](nodes.md)

## Inhalt

- [Einrichten](#einrichten)
- [Status und Befehle](#status-und-befehle)
- [Bereits ausgeführte Änderung aufzeichnen](#bereits-ausgeführte-änderung-aufzeichnen)
- [Änderung ausführen und aufzeichnen](#änderung-ausführen-und-aufzeichnen)
- [Transaktionen](#transaktionen)
- [Tastenkürzel](#tastenkürzel)
- [Automatisch integrierte Komponenten](#automatisch-integrierte-komponenten)
- [Speicherstatus](#speicherstatus)

Der frühere gedankliche „URManager“ heißt in der aktuellen API
`GLSaveManager`: Er verwaltet Undo/Redo und zusätzlich den Speicherstatus eines
Dokuments. Die History gehört in der Regel an den Root-Host, sodass alle
Controls und Nodes desselben Dokuments dieselbe Zeitlinie benutzen.

## Einrichten

```java
GLSaveManager history = new GLSaveManager(200);
frame.setGLSaveManager(history);

history.addListener(manager -> {
    undoButton.setEnabled(manager.canUndo());
    redoButton.setEnabled(manager.canRedo());
    updateWindowTitle(manager.isDirty());
});
```

Ohne Argument gilt `DEFAULT_HISTORY_LIMIT` (`100`). Das Limit ist mindestens
eins und lässt sich mit `setHistoryLimit(...)` ändern. Alte Einträge werden bei
Bedarf am Anfang verworfen.

`GLFrame`, `GLSwingContainerCanvas` und `GLSwingPanel` bieten
`setGLSaveManager(...)`. Kinder erreichen die Instanz über
`getGLSaveManager()` ihres Parent-Baums.

## Status und Befehle

| API | Bedeutung |
|---|---|
| `canUndo()` / `canRedo()` | Ob ein Schritt verfügbar ist. |
| `getUndoName()` / `getRedoName()` | Anzeigename des nächsten Schritts oder `null`. |
| `undo()` / `redo()` | Führt aus und meldet Erfolg als `boolean`. |
| `isApplying()` | `true`, während Undo oder Redo den Modellzustand verändert. |
| `isInTransaction()` | Ob gerade eine zusammengesetzte Änderung aufgenommen wird. |
| `isDirty()` | Ob die aktuelle Position vom markierten Speicherstand abweicht. |
| `markSaved()` | Markiert die aktuelle Position als gespeichert. |
| `reset()` | Löscht die History und betrachtet den aktuellen Zustand als gespeichert. |

`undo()` und `redo()` sind während einer offenen Transaktion nicht erlaubt.
Auch `markSaved()` und `reset()` verlangen einen abgeschlossenen konsistenten
Zustand.

## Bereits ausgeführte Änderung aufzeichnen

`record(...)` wird verwendet, wenn die Anwendung den neuen Wert bereits gesetzt
hat:

```java
Color oldColor = model.getColor();
Color newColor = chosenColor;
model.setColor(newColor);

history.record("Change color",
        () -> model.setColor(oldColor),
        () -> model.setColor(newColor));
```

Alternativ kapselt `GLUndoableEdit.of(name, undo, redo)` denselben Vertrag.
Während `isApplying()` wahr ist, ignoriert der Manager neue Record-Aufrufe. Ein
Undo-Callback erzeugt daher nicht versehentlich weitere History-Einträge.

Callbacks sollten den vollständigen Modellzustand und das nötige UI-Repaint
wiederherstellen. Flüchtige lokale Variablen werden am besten als unveränderliche
Vorher-/Nachher-Snapshots erfasst.

## Änderung ausführen und aufzeichnen

`perform(...)` ist die kompakte Variante, wenn der Manager die Vorwärtsänderung
jetzt ausführen soll:

```java
history.perform("Rename layer",
        () -> layer.setName(newName),
        () -> layer.setName(oldName));
```

Der `change`-Callback ist zugleich die Redo-Aktion. Schlägt die Ausführung mit
einer Exception fehl, wird kein gültiger neuer History-Schritt angenommen.

## Transaktionen

Mehrere Einträge lassen sich zu genau einem Undo-Schritt bündeln. Das
`AutoCloseable`-Muster stellt auch bei Exceptions das korrekte Ende sicher:

```java
try (GLSaveManager.Transaction ignored =
        history.beginTransaction("Move selection")) {
    move(first, dx, dy);
    move(second, dx, dy);
}
```

Transaktionen dürfen verschachtelt werden. Die innere Transaktion wird als Teil
der äußeren gesammelt; nur die äußerste erzeugt einen sichtbaren History-Schritt.
Leere Transaktionen erzeugen keinen Eintrag. Handles müssen in umgekehrter
Öffnungsreihenfolge geschlossen werden.

Undo läuft innerhalb eines zusammengesetzten Eintrags rückwärts, Redo vorwärts.
Dadurch werden voneinander abhängige Änderungen in natürlicher Reihenfolge
zurückgenommen.

## Tastenkürzel

```java
@Override
public void keyPressed(KeyEvent event) {
    if (history.handleKeyPressed(event))
        return;
    // weitere Shortcuts
}
```

`handleKeyPressed(...)` unterstützt plattformgerecht Strg/Cmd+Z,
Strg/Cmd+Shift+Z und Strg/Cmd+Y und konsumiert behandelte Events.
`isHistoryShortcut(...)` prüft nur, ob ein Event zu dieser Familie gehört.

Die verwalteten GLSwing-Hosts binden diese Shortcuts bereits in ihre
Eingabekette ein. Vor Undo/Redo wird ein fokussierter Texteditor nach Möglichkeit
committet, damit Textzustand und Dokument-History konsistent bleiben.

## Automatisch integrierte Komponenten

Folgende Benutzerinteraktionen verwenden den geerbten Manager bereits:

- erfolgreicher Commit eines `GLTextEditor`, sofern `historyEnabled` aktiv ist,
- Slider-Drag, Texteingabe und Reset,
- gemeinsame Wertänderungen einer `GLSliderGroup`,
- Node-Titelbearbeitung, Verschieben und Skalieren,
- Verbindungen sowie Löschen und Bewegen einer Node-Auswahl.

Programmatische Setter sind im Allgemeinen keine Benutzeraktion und werden
nicht automatisch aufgezeichnet. Das verhindert, dass Laden, Synchronisieren
oder Animationen die History fluten. Solche Änderungen werden bei Bedarf
explizit mit `record`, `perform` oder einer Transaktion beschrieben.

## Speicherstatus

Nach erfolgreichem Speichern:

```java
saveDocument();
history.markSaved();
```

`isDirty()` vergleicht die aktuelle History-Position mit diesem Checkpoint. Ein
Undo zurück zum gespeicherten Stand macht das Dokument wieder sauber, ein Redo
erneut dirty. Wird der markierte Stand durch das History-Limit abgeschnitten,
bleibt die Information konservativ: Der aktuelle Zustand wird nicht irrtümlich
als gespeichert gemeldet.
