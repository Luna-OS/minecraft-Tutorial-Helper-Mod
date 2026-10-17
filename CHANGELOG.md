# Changelog

## [1.5.0] – Minecraft 26.3

- **Archiv mit eigenen Kopien:** Löschst du ein Projekt im Projektbereich, bleibt seine Liste im Archiv. „Öffnen“
  legt das Projekt aus der Karte wieder an, mit Liste, Link, Version und Bild. Zugewiesene Kisten kommen nicht
  zurück, die gehören zum gelöschten Projekt.
- „Aus Archiv löschen“ entfernt nur die Karte, ein Projekt dazu bleibt bestehen.
- Ein eingefügter Code landet als Karte im Archiv. Erst „Öffnen“ macht daraus ein Projekt.
- Karten zeigen „● aktiv“ für das offene Projekt und „● Projekt“, wenn es noch ein Projekt dazu gibt.
- Vorhandene Projekte werden beim ersten Start automatisch ins Archiv übernommen.

## [1.4.1] – Minecraft 26.3

- **Auto-Update unter Windows repariert:** Das Einsetzen der neuen Version beim Beenden läuft jetzt wie bei Luna
  Map über ein kleines Skript, das bis zu drei Minuten wartet, bis Minecraft und der Launcher die alte Datei
  freigeben. Vorher gab es nach 20 Sekunden auf.
- Die Einstellungen zeigen jetzt die installierte Version und den Stand des Updates. Klappt das Einsetzen einmal
  nicht, steht das dort, und beim nächsten Beenden wird es erneut versucht.
- **Wichtig:** Wer 1.3.0 oder 1.4.0 hat, installiert 1.4.1 einmal von Hand. Ab dann aktualisiert sich die Mod selbst.

## [1.4.0] – Minecraft 26.3

- **Archiv:** Alle Projekte als Karten mit dem Vorschaubild des Videos, dem Namen der Farm, der Minecraft-Version
  und der Anzahl der Materialien. Von dort lassen sie sich öffnen (Doppelklick), umbenennen, mit Version versehen
  und löschen. Erreichbar über „Archiv“ im Menü (B) oder eine eigene Taste.
- **Teilen per Code:** „Code kopieren“ legt das Projekt (Name, Link, Version, Materialliste) als kurzen Code in die
  Zwischenablage, mit einigen hundert Zeichen klein genug für eine Discord-Nachricht. „Code einfügen“ liest so
  einen Code wieder ein, auch wenn die ganze Nachricht kopiert wurde. Kisten werden nicht geteilt, sie gehören zu
  deiner Welt.
- Beim Laden eines Links merkt sich die Mod das Vorschaubild und erkennt die Minecraft-Version aus Titel oder
  Beschreibung, zum Beispiel „1.21.4“ oder „[1.20]“.

## [1.3.0] – Minecraft 26.3

- **Mehr FPS:**
  - Einstellungen und Projekte werden im Hintergrund gespeichert. Vorher wurde beim Video alle paar Sekunden im
    Spiel-Thread auf die Festplatte geschrieben.
  - Das Inventar wird nur noch neu gezählt, wenn sich etwas geändert hat. Shulkerkisten werden dabei nicht mehr
    kopiert.
  - Die Liste wird nur neu aufgebaut, wenn sich etwas ändert, statt in jedem Frame.
  - ffmpeg nutzt nur noch zwei Kerne.
- **Twitch:** Twitch-Links funktionieren jetzt, für Videos (VODs), Clips und Livestreams. Livestreams zeigen
  „● LIVE“ und starten nach einer Pause wieder beim aktuellen Stand.
- **Darkmode:** In den Einstellungen unter „Design: Hell/Dunkel“. Außerdem bleibt die Schrift auch mit
  Dark-UI-Ressourcenpaketen lesbar.
- **Auto-Update:** Die Mod sucht beim Start nach einer neuen Version auf GitHub, lädt sie herunter und tauscht sie
  beim Beenden des Spiels aus. In den Einstellungen abschaltbar.
- **Download-Fehler unter Windows behoben:** Wenn der Virenscanner eine frisch geladene Datei kurz festhält,
  versucht die Mod es mehrmals, statt mit einem abgeschnittenen Pfad als Fehler abzubrechen. Fehler werden
  vollständig angezeigt.
- Ein installiertes ffmpeg unter Version 7 wird nicht mehr benutzt, weil es Twitch-Streams nicht lesen kann. Die
  Mod bietet dann ihren eigenen Download an.

## [1.2.0] – Minecraft 26.3

- **Fenster in den Mod-Einstellungen anordnen:** Die Einstellungen zeigen eine kleine Vorschau des Bildschirms mit
  Hotbar und Fadenkreuz. Liste und Video lassen sich darin per Drag & Drop verschieben und an der Ecke unten rechts
  größer oder kleiner ziehen, auch im Hauptmenü ohne Welt. „Vollbild …“ öffnet den Editor in echter Größe.
- **Mod Menu:** Unter Fabric hat die Mod jetzt einen Einstellungen-Knopf in [Mod Menu](https://modrinth.com/mod/modmenu).
  Bei NeoForge und Forge öffnet der Knopf in der Mod-Liste dieselben Einstellungen.
- **Pfeiltasten:** Beim Anordnen verschieben die Pfeiltasten das angeklickte Fenster genau (Shift: 10 Pixel), mit
  Alt bzw. ⌥ auf dem Mac ändern sie die Größe. Praktisch mit Trackpad.
- Der Vollbild-Editor funktioniert jetzt auch aus dem Hauptmenü, mit dem Menühintergrund statt der Welt.

## [1.1.0] – Minecraft 26.3

- **Liste und Video als Fenster:** Beide erscheinen jetzt als Fenster mit Titelleiste über dem Spiel. Mit **U**
  (oder „Fenster anordnen“ im Menü) bekommst du die Maus: Fenster an der Titelleiste verschieben, an der Ecke unten
  rechts größer oder kleiner ziehen, mit ✕ schließen. Im Videofenster gibt es « ▶ » zum Zurückspulen, Abspielen und
  Vorspulen. Position und Größe werden gespeichert.
- **macOS:** Mit Homebrew oder MacPorts installierte Programme (yt-dlp, ffmpeg, Deno) werden jetzt gefunden, auch
  wenn der Launcher sie nicht im PATH hat. Von der Mod heruntergeladene Programme werden nicht mehr von Gatekeeper
  blockiert.

## [1.0.0] – Minecraft 26.3

Erste Version, für Fabric, NeoForge und Forge. Nur für den Client, auf dem Server muss nichts installiert sein.

- **Materialliste aus dem Tutorial-Link:** Bei YouTube wird die Videobeschreibung gelesen, bei anderen Links der Text
  der Webseite. Erkannt werden englische und deutsche Item-Namen. Stacks, Shulkerkisten und Doppelkisten werden
  umgerechnet.
- **Liste selbst bearbeiten:** Liste einfügen, Items über eine Suche hinzufügen, Anzahlen ändern.
- **Kisten und Shulkerkisten zählen:** Zuweisen mit **K** oder mit dem Knopf neben der geöffneten Kiste. Der Inhalt
  wird bei jedem Öffnen aktualisiert. Shulkerkisten und Bündel zählen mit ihrem Inhalt, das Inventar zählt ebenfalls
  mit.
- **Liste im HUD:** „vorhanden/benötigt“ für jedes Item, grün, gelb oder rot, Fehlendes zuerst.
- **Video im Spiel:** als Mini-Player im HUD oder groß mit Spulleiste, **J** pausiert. Benötigt yt-dlp, ffmpeg und
  Deno; die Mod lädt fehlende auf Knopfdruck herunter.
- Mehrere Projekte, Minecraft-Look, Deutsch und Englisch.

**Installation:** Lege die Jar-Datei für deinen Loader in den `mods`-Ordner. Für Fabric brauchst du zusätzlich die
[Fabric API](https://modrinth.com/mod/fabric-api).
