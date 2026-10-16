# Changelog

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
