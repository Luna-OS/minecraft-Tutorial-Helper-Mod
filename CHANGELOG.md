# Changelog

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
