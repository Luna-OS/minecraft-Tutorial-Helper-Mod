package com.lunaos.tutorialtracker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Keeps the mod up to date from its GitHub releases. When the game starts it looks for a newer release for the same
 * Minecraft version and loader, downloads that jar next to the running one (as {@code .jar.pending}, which no loader
 * picks up) and swaps the two when the game closes: the running jar cannot be replaced while it is in use, and on
 * Windows not even deleted. A swap that does not happen (the game crashed) is done at the next close instead. Nothing
 * is done in a development setup, where the mod is not a jar.
 */
public final class Updater {

    public enum State {
        /** Not a jar (development), or switched off. */
        OFF,
        CHECKING,
        UP_TO_DATE,
        DOWNLOADING,
        /** Downloaded: goes in when the game closes. */
        READY,
        FAILED
    }

    private static final String RELEASES =
        "https://api.github.com/repos/Luna-OS/minecraft-Tutorial-Helper-Mod/releases?per_page=20";
    private static final Pattern TAG = Pattern.compile("v?(\\d+(?:\\.\\d+)*)\\+(.+)");
    private static final boolean WINDOWS =
        System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    private static volatile State state = State.OFF;
    private static volatile String newVersion = "";
    private static volatile String error = "";
    private static String loader;
    private static String version;
    private static String minecraft;
    private static Path ownJar;
    /** The downloaded jar waiting to go in, and the name it gets then. */
    private static Path pending;
    private static Path target;
    private static boolean swapHooked;
    /** The last close could not put the update in (the old jar stayed locked). */
    private static volatile String lastFailure;
    private static boolean announced;

    private Updater() {
    }

    public static State state() {
        return state;
    }

    public static String newVersion() {
        return newVersion;
    }

    public static String error() {
        return error;
    }

    public static String version() {
        return version == null ? "?" : version;
    }

    /** Set when the last close could not put an update in. */
    public static String lastFailure() {
        return lastFailure;
    }

    /**
     * Called by each loader at start with its name ("fabric", "neoforge", "forge"), the mod's version and the jar
     * the mod was loaded from (null when it is not known).
     */
    public static void init(String loaderName, String modVersion, Path jar, boolean check) {
        loader = loaderName;
        version = modVersion;
        minecraft = SharedConstants.getCurrentVersion().name();
        readLastResult();
        TutorialTracker.LOGGER.info("Tutorial Tracker updater: {} {} for Minecraft {}, jar {}", loaderName, modVersion,
            minecraft, jar);
        if (jar == null || !Files.isRegularFile(jar) || !jar.getFileName().toString().endsWith(".jar")) {
            state = State.OFF;
            TutorialTracker.LOGGER.info("Tutorial Tracker updater off: not running from a jar");
            return;
        }
        ownJar = jar.toAbsolutePath();
        findEarlierDownload();
        if (state != State.READY && check) {
            check();
        }
    }

    /** A download from an earlier session that never went in (the game did not close normally). */
    private static void findEarlierDownload() {
        try (Stream<Path> files = Files.list(ownJar.getParent())) {
            String prefix = "tutorial-tracker-" + loader + "-";
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (!name.startsWith(prefix) || !name.endsWith(".jar.pending")) {
                    continue;
                }
                String jarName = name.substring(0, name.length() - ".pending".length());
                String fileVersion = jarName.substring(prefix.length(), jarName.length() - ".jar".length());
                Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)*)\\+(.+)").matcher(fileVersion);
                if (matcher.matches() && newer(matcher.group(1), version)
                    && matcher.group(2).equals(minecraftVersion())) {
                    ready(file, file.resolveSibling(jarName), matcher.group(1));
                    return;
                }
                // Older than what runs, or for another Minecraft: of no use.
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            TutorialTracker.LOGGER.warn("Could not look for an earlier update download", e);
        }
    }

    /** Looks for a newer version, and downloads it when there is one. In the background. */
    public static synchronized void check() {
        if (ownJar == null || state == State.CHECKING || state == State.DOWNLOADING || state == State.READY) {
            return;
        }
        state = State.CHECKING;
        Thread thread = new Thread(() -> {
            try {
                run();
            } catch (Exception | LinkageError e) {
                TutorialTracker.LOGGER.warn("Update check failed", e);
                error = e.getMessage() == null ? e.toString() : e.getMessage();
                state = State.FAILED;
            }
        }, "TutorialTracker update");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private static void run() throws IOException, InterruptedException {
        String minecraft = minecraftVersion();
        JsonArray releases = JsonParser.parseString(get(RELEASES)).getAsJsonArray();
        // The newest release for this Minecraft version; releases come newest first, but versions decide.
        JsonObject best = null;
        String bestVersion = version;
        for (JsonElement element : releases) {
            JsonObject release = element.getAsJsonObject();
            if (bool(release, "draft") || bool(release, "prerelease")) {
                continue;
            }
            Matcher matcher = TAG.matcher(string(release, "tag_name"));
            if (matcher.matches() && matcher.group(2).equals(minecraft) && newer(matcher.group(1), bestVersion)) {
                best = release;
                bestVersion = matcher.group(1);
            }
        }
        if (best == null) {
            state = State.UP_TO_DATE;
            TutorialTracker.LOGGER.info("Tutorial Tracker {} is up to date", version);
            return;
        }
        String jarName = "tutorial-tracker-" + loader + "-" + bestVersion + "+" + minecraft + ".jar";
        JsonObject asset = null;
        for (JsonElement element : best.getAsJsonArray("assets")) {
            if (jarName.equals(string(element.getAsJsonObject(), "name"))) {
                asset = element.getAsJsonObject();
            }
        }
        if (asset == null) {
            // A release without a jar for this loader: nothing to install.
            state = State.UP_TO_DATE;
            return;
        }
        state = State.DOWNLOADING;
        newVersion = bestVersion;
        Path folder = ownJar.getParent();
        Path download = folder.resolve(jarName + ".download");
        Path waiting = folder.resolve(jarName + ".pending");
        try {
            download(string(asset, "browser_download_url"), download);
            verify(download, asset);
            Files.move(download, waiting, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(download);
        }
        ready(waiting, folder.resolve(jarName), bestVersion);
        TutorialTracker.LOGGER.info("Tutorial Tracker {} downloaded, goes in when the game closes", bestVersion);
    }

    /** Size and checksum as GitHub lists them, and a look inside: it has to be this mod. */
    private static void verify(Path file, JsonObject asset) throws IOException {
        long size = asset.has("size") ? asset.get("size").getAsLong() : -1;
        if (size >= 0 && Files.size(file) != size) {
            throw new IOException("download incomplete");
        }
        String digest = string(asset, "digest");
        if (digest.startsWith("sha256:")) {
            try (InputStream in = Files.newInputStream(file)) {
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[1 << 16];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    sha.update(buffer, 0, n);
                }
                if (!HexFormat.of().formatHex(sha.digest()).equalsIgnoreCase(digest.substring(7))) {
                    throw new IOException("checksum does not match");
                }
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IOException(e);
            }
        }
        try (ZipFile zip = new ZipFile(file.toFile())) {
            if (zip.getEntry("com/lunaos/tutorialtracker/TutorialTracker.class") == null) {
                throw new IOException("not a Tutorial Tracker jar");
            }
        }
    }

    private static synchronized void ready(Path waiting, Path finalName, String readyVersion) {
        pending = waiting;
        target = finalName;
        newVersion = readyVersion;
        state = State.READY;
        if (!swapHooked) {
            swapHooked = true;
            Runtime.getRuntime().addShutdownHook(new Thread(Updater::swap, "TutorialTracker update install"));
        }
    }

    /**
     * When the game closes: the old jar out, the new one in. The old jar always goes first, and the new one only goes
     * in once it is gone: two copies of the mod would keep the game from starting. On Windows the running jar stays
     * locked until the game has fully ended, so a small batch script, started on its own with "start", waits for
     * that and does the same (as Luna Map does). How it went is written to a file read at the next start.
     */
    private static void swap() {
        if (pending == null || !Files.exists(pending)) {
            return;
        }
        try {
            if (WINDOWS) {
                swapOnWindows();
            } else {
                Files.deleteIfExists(ownJar);
                Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(resultFile(), "installed " + newVersion);
            }
        } catch (Exception e) {
            // Left as it is: the pending jar is tried again at the next close.
            System.err.println("Tutorial Tracker: could not install the update: " + e);
        }
    }

    /**
     * Tries to delete the old jar every two seconds for up to three minutes (the game, the launcher or a virus
     * scanner may still hold it), and only then moves the new one in.
     */
    private static void swapOnWindows() throws IOException {
        Path script = Files.createTempFile("tutorialtracker-update", ".bat");
        String oldJar = bat(ownJar);
        String result = bat(resultFile());
        String text = "@echo off\r\n"
            + "chcp 65001 >nul\r\n"
            + "set /a n=0\r\n"
            + ":loop\r\n"
            + "ping -n 3 127.0.0.1 >nul\r\n"
            + "del /f /q \"" + oldJar + "\" >nul 2>&1\r\n"
            + "set /a n+=1\r\n"
            + "if exist \"" + oldJar + "\" if %n% lss 90 goto loop\r\n"
            + "if exist \"" + oldJar + "\" (\r\n"
            + "  >\"" + result + "\" echo locked\r\n"
            + ") else (\r\n"
            + "  move /y \"" + bat(pending) + "\" \"" + bat(target) + "\" >nul\r\n"
            + "  >\"" + result + "\" echo installed " + newVersion + "\r\n"
            + ")\r\n"
            + "del \"%~f0\"\r\n";
        Files.writeString(script, text, java.nio.charset.StandardCharsets.UTF_8);
        new ProcessBuilder("cmd", "/c", "start", "\"\"", "/min", script.toString())
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
    }

    /** A path for a batch file: absolute, with % doubled so cmd leaves it alone. */
    private static String bat(Path path) {
        return path.toAbsolutePath().toString().replace("%", "%%");
    }

    /** Where the last close says how installing went. */
    private static Path resultFile() throws IOException {
        Path folder = TutorialTracker.dataDir();
        Files.createDirectories(folder);
        return folder.resolve("update-result.txt");
    }

    /** What the last close reported, logged and kept for the settings. */
    private static void readLastResult() {
        try {
            Path file = resultFile();
            if (!Files.exists(file)) {
                return;
            }
            String text = Files.readString(file).strip();
            Files.deleteIfExists(file);
            if (text.startsWith("installed")) {
                TutorialTracker.LOGGER.info("Tutorial Tracker update {} was installed", text.substring(9).strip());
            } else {
                lastFailure = text;
                TutorialTracker.LOGGER.warn("Tutorial Tracker update could not be installed: {}", text);
            }
        } catch (IOException e) {
            TutorialTracker.LOGGER.warn("Could not read the last update result", e);
        }
    }

    /** Once in a world: says that an update is ready, so nobody wonders about the new version later. */
    public static void announce(Minecraft minecraft) {
        if (state == State.READY && !announced && minecraft.player != null) {
            announced = true;
            TutorialTracker.message(Component.translatable("message.tutorialtracker.update_ready", newVersion));
        }
    }

    private static String minecraftVersion() {
        return minecraft;
    }

    /** Whether version a is newer than b, comparing numbers part by part (1.10.0 is newer than 1.9.2). */
    static boolean newer(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.replaceAll("[^0-9.].*$", "").split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? parse(x[i]) : 0;
            int q = i < y.length ? parse(y[i]) : 0;
            if (p != q) {
                return p > q;
            }
        }
        return false;
    }

    private static int parse(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "TutorialTracker (Minecraft mod)")
            .header("Accept", "application/vnd.github+json")
            .GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static void download(String url, Path to) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMinutes(5))
            .header("User-Agent", "TutorialTracker (Minecraft mod)")
            .GET().build();
        HttpResponse<Path> response = HTTP.send(request, HttpResponse.BodyHandlers.ofFile(to));
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }
}
