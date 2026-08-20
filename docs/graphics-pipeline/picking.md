# Picking und Mausziele

[Zur API-Landkarte](../graphics-pipeline.md) · [Ressourcen](resources.md) · [Weiter: Performance](performance.md)

GLSwing rendert neben Farbe optional einen Picking-FBO. Jeder pickbare Pixel
enthält eine intern auflösbare GPU-ID. Die öffentliche API bietet zwei Modelle:
eine eigene Integer-ID oder ein direktes `GLMouseTarget`.

## Numerische Pick-ID

```java
g.setPickId(17);
g.fillCircle(80, 80, 30, Color.ORANGE);
g.clearPickId();
```

Alternativ besitzen viele Standard-Zeichenmethoden einen `int pickId`-Overload:

```java
g.fillRect(20, 20, 100, 40, Color.BLUE, 17);
```

IDs müssen nichtnegativ sein; `0`/`NO_PICK_ID` bedeutet kein ID-Ziel. Die
Zuordnung von ID zu Domänenobjekt verwaltet die Anwendung. Hosts bieten
`pick(x,y)` für die ID und `pickResult(x,y)` für das vollständige Ergebnis.

## Direktes `GLMouseTarget`

```java
GLMouseTarget target = new GLMouseTarget() {
    @Override
    public void mousePressed(GLMouseEvent event) {
        System.out.println(event.getLocalX() + ", " + event.getLocalY());
    }
};

g.setMouseTarget(target, 40, 25);
g.fillRect(40, 25, 180, 60, Color.BLUE);
g.clearMouseTarget();
```

`setMouseTarget` ersetzt die aktive Pick-ID; `setPickId` ersetzt das MouseTarget.
Mit explizitem Ursprung sind lokale Koordinaten relativ zu diesem Punkt. Ohne
Ursprung bestimmt der erste folgende Zeichenbefehl den Ursprung aus seiner
linken oberen Bounds-Ecke beziehungsweise seinem ersten Punkt; er bleibt bis
zum Wechsel/Clear stabil.

`GLContainer` implementiert `GLMouseTarget` und setzt sich beim Rendering
automatisch mit lokalem Ursprung `(0,0)`, solange
`isMouseTargetEnabled()` nicht `false` liefert.

## Ereignisse

`GLMouseTarget` besitzt Defaultmethoden für:

- `mouseEntered`, `mouseExited`, `mouseMoved`,
- `mousePressed`, `mouseDragged`, `mouseReleased`,
- `mouseCaptureLost`, `mouseClicked`,
- `mouseWheelMoved(GLMouseWheelEvent)`.

`GLMouseEvent` stellt bereit:

| Daten | Methoden |
|---|---|
| Original-AWT-Event | `getOriginalEvent` |
| Treffer | `getPickResult`, `getTarget` |
| Screenposition | `getScreenX`, `getScreenY` |
| Lokale Position | `hasLocalPosition`, `getLocalX`, `getLocalY` |
| Dragdelta | `getDeltaX`, `getDeltaY` |
| Maustaste/Clicks/Modifier/Zeit | `getButton`, `getClickCount`, `getModifiersEx`, `getWhen` |
| Eventfluss | `consume`, `isConsumed` |
| Pointer | `requestPointerWarp`, `getPointerController` |

`GLMouseWheelEvent` ergänzt `getWheelRotation`, `getPreciseWheelRotation`,
`getScrollAmount`, `getScrollType`.

## `GLPickResult`

```java
GLPickResult hit = canvas.pickResult(mouseX, mouseY);
if (hit.isHit()) {
    int id = hit.getId();
    GLMouseTarget object = hit.getTarget();
}
```

Methoden:

- `none`, `isHit`, `hasId`, `getId`,
- `hasTarget`, `getTarget`, `getTarget(Class<T>)`,
- `getScreenX/Y`, `hasLocalPosition`, `getLocalX/Y`,
- `toScreen(localX,localY)`,
- `at(newScreenX,newScreenY)` mit derselben eingefrorenen Transform,
- `retarget(newTarget)` für Event-Bubbling zu einem Parent.

Lokale Koordinaten entstehen durch die beim Drawcall gespeicherte inverse
Transform. Bei einer nicht invertierbaren Transform ist
`hasLocalPosition()` false.

## Maus-Capture

Nach `mousePressed` kann der Inputdispatcher das getroffene Ziel für den Drag
capturen. Dadurch gehen Drag/Release weiter an dasselbe Objekt, auch wenn der
Cursor seine sichtbare Geometrie verlässt. Wird das Ziel entfernt oder die
Capture anderweitig ungültig, folgt `mouseCaptureLost`.

`requestPointerWarp(x,y)` unterstützt endlose Draggesten wie den FREE-Slider.
Die Hostimplementierung unterdrückt das daraus entstehende künstliche
Mausevent. Der `GLPointerController` erlaubt außerdem Cursoränderungen über den
jeweiligen Fensterbackend.

## Textur-Alpha

```java
g.setTexturePickAlphaThreshold(0.2f);
```

Für Texture-Batches wird `textureAlpha * tintAlpha >= threshold` geprüft. Die
Grenze gilt für `drawTexture`, `drawImage` und `drawRenderTarget` sowie für
texturgebundene Instanzen des UI-Batches wie `drawUIImage` und Icons. Filled,
Standardtext und rein analytische UI-Geometrie sind nicht betroffen.

## Color und Picking getrennt schreiben

```java
g.setRenderOutputs(true, false);
drawDecorationOnly(g);

g.setRenderOutputs(false, true);
g.addUIHitArea(20, 20, 260, 50);

g.setRenderOutputs(true, true);
```

Zustandswechsel flushen und sind recordbar. Beide Outputs sind zu Framebeginn
aktiv. `addUIHitArea` schreibt keine sichtbare Farbe.

## Asynchroner Snapshot

Der normale Hoverpfad liest den bereits verfügbaren Picking-Snapshot statt die
GPU bei jeder Mausbewegung synchron anzuhalten. Dadurch kann ein Treffer bei
einer View-/Geometrieänderung höchstens kurz hinterherhinken, die Framerate und
gefühlte Latenz sind aber deutlich besser. Explizite Hostabfragen verwenden
ebenfalls die aktuelle Snapshotlogik.

Wenn eine Strukturänderung sofort eine neue Pickingdarstellung benötigt, sorgt
die Component-/Host-Infrastruktur über Repaint und Picking-Invalidierung für
den Neuaufbau. `repaintAndRebuildPicking(source)` ist der gezielte Hostweg.

## Recording und Referenzen

Pick-IDs werden als Werte gespeichert. MouseTargets werden in Recordings per
`WeakReference` gehalten: Ein Recording verhindert nicht, dass ein sonst nicht
mehr erreichbares Objekt vom Garbage Collector entfernt wird. Beim Replay ist
ein eingesammeltes Ziel einfach nicht mehr interaktiv.

## Grenzen und typische Fehler

- Pick-ID/MouseTarget nach temporärem Einsatz löschen oder bewusst ersetzen.
- Nach `setRenderOutputs(false,true)` den Normalzustand restaurieren.
- Bei mehreren sichtbaren Treffern gewinnt der zuletzt darüber gezeichnete
  Pixel; Z-Order ist daher automatisch berücksichtigt.
- Ein Layer-Schatten/Glow ist nicht pickbar, weil Effekte den Picking-FBO nicht
  nachträglich verändern.
- Lokale Koordinaten beziehen sich auf den gesetzten/automatischen Ursprung,
  nicht automatisch auf beliebige Objektbounds.
