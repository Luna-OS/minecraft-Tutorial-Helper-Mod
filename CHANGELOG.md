# Changelog

## [1.5.1] – Minecraft 26.3

- **Fixed the game crashing when opening the archive:** Large preview pictures (Twitch delivers 2560×1440) crashed
  the game while being decoded. Pictures are now loaded the way Minecraft loads its own, very large ones are skipped,
  and from Twitch the mod fetches small preview pictures (640×360).
- README and changelog are now in English.

## [1.5.0] – Minecraft 26.3

- **Archive with its own copies:** If you delete a project in the projects area, its list stays in the archive.
  "Open" creates the project again from the card, with list, link, version and picture. Assigned chests do not come
  back; they belonged to the deleted project.
- "Remove from archive" removes only the card; a project of it stays.
- A pasted code becomes a card in the archive. Only "Open" turns it into a project.
- Cards show "● active" for the open project and "● project" while a project of it still exists.
- Existing projects are added to the archive automatically on the first start.

## [1.4.1] – Minecraft 26.3

- **Fixed auto-update on Windows:** Swapping in the new version when the game closes now works like in Luna Map,
  through a small script that waits up to three minutes for Minecraft and the launcher to release the old file.
  Before, it gave up after 20 seconds.
- The settings now show the installed version and the state of the update. If swapping it in fails, it says so there,
  and it is tried again the next time the game closes.
- **Important:** If you have 1.3.0 or 1.4.0, install 1.4.1 once by hand. From then on the mod updates itself.

## [1.4.0] – Minecraft 26.3

- **Archive:** All projects as cards with the video's preview picture, the name of the farm, the Minecraft version and
  the number of materials. From there they can be opened (double-click), renamed, given a version and deleted.
  Reachable through "Archive" in the menu (B) or its own key.
- **Sharing by code:** "Copy code" puts the project (name, link, version, material list) on the clipboard as a short
  code; at a few hundred characters it is small enough for a Discord message. "Paste code" reads such a code back in,
  even when the whole message was copied. Chests are not shared, they belong to your world.
- When loading a link, the mod remembers the preview picture and detects the Minecraft version from the title or
  description, for example "1.21.4" or "[1.20]".

## [1.3.0] – Minecraft 26.3

- **More FPS:**
  - Settings and projects are saved in the background. Before, during a video the game thread wrote to disk every few
    seconds.
  - The inventory is only counted again when something changed. Shulker boxes are no longer copied for it.
  - The list is only rebuilt when something changes, instead of every frame.
  - ffmpeg uses only two cores.
- **Twitch:** Twitch links now work, for videos (VODs), clips and live streams. Live streams show "● LIVE" and start
  at the current point again after a pause.
- **Dark mode:** In the settings under "Design: Light/Dark". Text also stays readable with dark UI resource packs.
- **Auto-update:** At startup the mod looks for a new version on GitHub, downloads it and swaps it in when the game
  closes. Can be turned off in the settings.
- **Fixed download errors on Windows:** When the virus scanner briefly holds a freshly downloaded file, the mod tries
  several times instead of failing with a cut-off path as the error. Errors are shown in full.
- An installed ffmpeg below version 7 is no longer used, because it cannot read Twitch streams. The mod then offers
  its own download.

## [1.2.0] – Minecraft 26.3

- **Arrange the windows in the mod settings:** The settings show a small preview of the screen with hotbar and
  crosshair. The list and video can be moved there by drag and drop and made larger or smaller at the bottom right
  corner, even in the main menu without a world. "Full screen …" opens the editor at real size.
- **Mod Menu:** On Fabric the mod now has a settings button in [Mod Menu](https://modrinth.com/mod/modmenu). On
  NeoForge and Forge the button in the mod list opens the same settings.
- **Arrow keys:** While arranging, the arrow keys move the clicked window exactly (Shift: 10 pixels), and with Alt or
  ⌥ on a Mac they change its size. Handy with a trackpad.
- The full screen editor now also works from the main menu, with the menu background instead of the world.

## [1.1.0] – Minecraft 26.3

- **List and video as windows:** Both now appear as windows with a title bar over the game. With **U** (or "Arrange
  windows" in the menu) you get the mouse: move windows by their title bar, drag the bottom right corner to make them
  larger or smaller, close them with ✕. The video window has « ▶ » to rewind, play and fast-forward. Position and
  size are saved.
- **macOS:** Programs installed with Homebrew or MacPorts (yt-dlp, ffmpeg, Deno) are now found, even when the
  launcher does not have them in its PATH. Programs downloaded by the mod are no longer blocked by Gatekeeper.

## [1.0.0] – Minecraft 26.3

First version, for Fabric, NeoForge and Forge. Client only; nothing has to be installed on the server.

- **Material list from the tutorial link:** For YouTube the video description is read, for other links the text of
  the web page. English and German item names are recognised. Stacks, shulker boxes and double chests are converted.
- **Edit the list yourself:** Paste a list, add items through a search, change amounts.
- **Count chests and shulker boxes:** Assign them with **K** or with the button next to the open chest. The contents
  are updated each time it is opened. Shulker boxes and bundles count with their contents, and the inventory counts
  as well.
- **List in the HUD:** "have/needed" for each item, green, yellow or red, missing items first.
- **Video in the game:** as a mini player in the HUD or full size with a seek bar, **J** pauses. Needs yt-dlp, ffmpeg
  and Deno; the mod downloads missing ones at the click of a button.
- Several projects, Minecraft look, English and German.

**Installation:** Put the jar for your loader in the `mods` folder. For Fabric you also need the
[Fabric API](https://modrinth.com/mod/fabric-api).
