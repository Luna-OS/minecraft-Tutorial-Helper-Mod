package com.lunaos.tutorialtracker.video;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lunaos.tutorialtracker.TutorialTracker;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The programs the video player runs: yt-dlp (finds the video's stream), ffmpeg (decodes it) and Deno (yt-dlp needs a
 * JavaScript runtime for YouTube). Ones already installed on the system are used as they are; the rest can be
 * downloaded into {@code tutorialtracker/bin} in the game folder, only when the player agrees to.
 */
public final class VideoTools {

    public enum Tool {
        YT_DLP("yt-dlp"),
        FFMPEG("ffmpeg"),
        DENO("deno");

        public final String command;

        Tool(String command) {
            this.command = command;
        }
    }

    /** A download in progress, read by the screens. */
    public static final class Progress {
        public volatile String stage = "";
        public volatile long done;
        public volatile long total;
        public volatile boolean finished;
        public volatile String error;
        /** The file was held by another program (on Windows, mostly the virus scanner looking at a new download). */
        public volatile boolean locked;

        public float fraction() {
            return this.total > 0 ? Math.min(1.0f, this.done / (float) this.total) : 0.0f;
        }
    }

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    private static final boolean ARM = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT).matches("aarch64|arm64");

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(20))
        .build();

    private static Path binDir;
    private static volatile Progress running;

    private VideoTools() {
    }

    public static void init(Path dir) {
        binDir = dir;
    }

    public static Path binDir() {
        return binDir;
    }

    public static Path ytDlp() {
        return find(Tool.YT_DLP);
    }

    public static Path ffmpeg() {
        return find(Tool.FFMPEG);
    }

    public static Path deno() {
        return find(Tool.DENO);
    }

    /** yt-dlp and ffmpeg are enough to play; Deno makes YouTube work reliably. */
    public static boolean ready() {
        return ytDlp() != null && ffmpeg() != null;
    }

    public static boolean allPresent() {
        return ready() && deno() != null;
    }

    /** Our own copy first, then the system's PATH. */
    public static Path find(Tool tool) {
        String name = WINDOWS ? tool.command + ".exe" : tool.command;
        if (binDir != null) {
            Path own = binDir.resolve(name);
            if (Files.isRegularFile(own)) {
                return own;
            }
        }
        for (String entry : searchPath()) {
            if (entry.isBlank()) {
                continue;
            }
            try {
                Path candidate = Path.of(entry.trim().replace("\"", "")).resolve(name);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return candidate;
                }
            } catch (RuntimeException ignored) {
                // A malformed PATH entry.
            }
        }
        return null;
    }

    /**
     * The PATH, plus where package managers put programs. The launcher on macOS starts the game with a bare PATH
     * (/usr/bin:/bin:...), so Homebrew's and MacPorts' programs would not be found otherwise.
     */
    private static List<String> searchPath() {
        List<String> entries = new ArrayList<>();
        String path = System.getenv("PATH");
        if (path != null) {
            entries.addAll(List.of(path.split(java.io.File.pathSeparator)));
        }
        if (!WINDOWS) {
            String home = System.getProperty("user.home", "");
            entries.addAll(List.of("/opt/homebrew/bin", "/usr/local/bin", "/opt/local/bin", "/usr/bin",
                home + "/.deno/bin", home + "/.local/bin", home + "/bin"));
        }
        return entries;
    }

    public static boolean isOwnCopy(Path tool) {
        return tool != null && binDir != null && tool.startsWith(binDir);
    }

    /** Megabytes the missing tools roughly take to download, for the question before downloading. */
    public static int downloadMegabytes() {
        int total = 0;
        if (ytDlp() == null) {
            total += WINDOWS ? 18 : MAC ? 37 : 40;
        }
        if (ffmpeg() == null) {
            total += WINDOWS ? 115 : MAC ? 30 : 150;
        }
        if (deno() == null) {
            total += MAC ? 39 : 42;
        }
        return total;
    }

    public static Progress running() {
        return running;
    }

    /** Downloads whatever is missing, in the background. */
    public static synchronized Progress installMissing() {
        if (running != null && !running.finished) {
            return running;
        }
        Progress progress = new Progress();
        running = progress;
        Thread thread = new Thread(() -> {
            try {
                Files.createDirectories(binDir);
                removeLeftovers();
                if (ytDlp() == null) {
                    installYtDlp(progress);
                }
                if (ffmpeg() == null) {
                    installFfmpeg(progress);
                }
                if (deno() == null) {
                    installDeno(progress);
                }
            } catch (Exception e) {
                TutorialTracker.LOGGER.warn("Downloading the video tools failed", e);
                fail(progress, e);
            } finally {
                progress.finished = true;
            }
        }, "TutorialTracker tool download");
        thread.setDaemon(true);
        thread.start();
        return progress;
    }

    /** yt-dlp has to keep up with YouTube; its standalone build updates itself. */
    public static synchronized Progress updateYtDlp() {
        if (running != null && !running.finished) {
            return running;
        }
        Progress progress = new Progress();
        progress.stage = "yt-dlp";
        running = progress;
        Thread thread = new Thread(() -> {
            try {
                Path ytDlp = ytDlp();
                if (ytDlp == null) {
                    throw new IOException("yt-dlp is not installed");
                }
                if (isOwnCopy(ytDlp)) {
                    // Downloading the latest build again works everywhere; "-U" needs write access next to it.
                    installYtDlp(progress);
                } else {
                    Result result = run(List.of(ytDlp.toString(), "-U"), 120);
                    if (result.exitCode != 0) {
                        throw new IOException(lastLine(result.stderr));
                    }
                }
            } catch (Exception e) {
                TutorialTracker.LOGGER.warn("Updating yt-dlp failed", e);
                fail(progress, e);
            } finally {
                progress.finished = true;
            }
        }, "TutorialTracker yt-dlp update");
        thread.setDaemon(true);
        thread.start();
        return progress;
    }

    private static void installYtDlp(Progress progress) throws IOException, InterruptedException {
        progress.stage = "yt-dlp";
        String asset;
        if (WINDOWS) {
            asset = ARM ? "yt-dlp_arm64.exe" : "yt-dlp.exe";
        } else if (MAC) {
            asset = "yt-dlp_macos";
        } else {
            asset = ARM ? "yt-dlp_linux_aarch64" : "yt-dlp_linux";
        }
        Path target = binDir.resolve(WINDOWS ? "yt-dlp.exe" : "yt-dlp");
        Path temp = download("https://github.com/yt-dlp/yt-dlp/releases/latest/download/" + asset, progress);
        moveInto(temp, target);
        makeRunnable(target);
    }

    private static void installFfmpeg(Progress progress) throws IOException, InterruptedException {
        progress.stage = "ffmpeg";
        Path target = binDir.resolve(WINDOWS ? "ffmpeg.exe" : "ffmpeg");
        if (WINDOWS) {
            Path zip = download("https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip", progress);
            unzipEntry(zip, "/bin/ffmpeg.exe", target);
            deleteQuietly(zip);
        } else if (MAC) {
            Path zip = download("https://ffmpeg.martin-riedl.de/redirect/latest/macos/" + (ARM ? "arm64" : "amd64")
                + "/snapshot/ffmpeg.zip", progress);
            unzipEntry(zip, "ffmpeg", target);
            deleteQuietly(zip);
        } else {
            Path archive = download("https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-"
                + (ARM ? "linuxarm64" : "linux64") + "-gpl.tar.xz", progress);
            // Java reads no .xz; every desktop Linux has tar for it.
            Path unpack = Files.createTempDirectory(binDir, "ffmpeg");
            try {
                Result result = run(List.of("tar", "-xJf", archive.toString(), "-C", unpack.toString()), 300);
                if (result.exitCode != 0) {
                    throw new IOException("tar: " + lastLine(result.stderr));
                }
                try (Stream<Path> files = Files.walk(unpack)) {
                    Path found = files.filter(p -> p.getFileName().toString().equals("ffmpeg")
                        && p.getParent().getFileName().toString().equals("bin")).findFirst()
                        .orElseThrow(() -> new IOException("ffmpeg not found in the archive"));
                    moveInto(found, target);
                }
            } finally {
                deleteTree(unpack);
                deleteQuietly(archive);
            }
        }
        makeRunnable(target);
    }

    private static void installDeno(Progress progress) throws IOException, InterruptedException {
        progress.stage = "deno";
        String triple;
        if (WINDOWS) {
            triple = "x86_64-pc-windows-msvc";
        } else if (MAC) {
            triple = (ARM ? "aarch64" : "x86_64") + "-apple-darwin";
        } else {
            triple = (ARM ? "aarch64" : "x86_64") + "-unknown-linux-gnu";
        }
        Path target = binDir.resolve(WINDOWS ? "deno.exe" : "deno");
        Path zip = download("https://github.com/denoland/deno/releases/latest/download/deno-" + triple + ".zip", progress);
        unzipEntry(zip, WINDOWS ? "deno.exe" : "deno", target);
        deleteQuietly(zip);
        makeRunnable(target);
    }

    /**
     * A process with the wider search path: yt-dlp looks for Deno (and ffmpeg) on its PATH, which on macOS would
     * otherwise be the launcher's bare one.
     */
    public static ProcessBuilder processBuilder(List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(command);
        java.util.LinkedHashSet<String> entries = new java.util.LinkedHashSet<>();
        if (binDir != null) {
            entries.add(binDir.toString());
        }
        for (String entry : searchPath()) {
            if (!entry.isBlank()) {
                entries.add(entry);
            }
        }
        String key = builder.environment().keySet().stream().filter(k -> k.equalsIgnoreCase("PATH")).findFirst()
            .orElse("PATH");
        builder.environment().put(key, String.join(java.io.File.pathSeparator, entries));
        return builder;
    }

    /**
     * Marks a downloaded program as executable. On macOS it also drops the "downloaded from the internet" mark, which
     * would otherwise have Gatekeeper refuse to start it from the game without telling anyone.
     */
    private static void makeRunnable(Path target) {
        target.toFile().setExecutable(true);
        if (MAC) {
            try {
                run(List.of("/usr/bin/xattr", "-d", "com.apple.quarantine", target.toString()), 10);
            } catch (Exception ignored) {
                // Not marked, or no xattr: nothing to remove.
            }
        }
    }

    private static Path download(String url, Progress progress) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "TutorialTracker (Minecraft mod)")
            .timeout(Duration.ofMinutes(30))
            .GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        progress.done = 0;
        progress.total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        Path temp = Files.createTempFile(binDir, "download", ".part");
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temp)) {
            byte[] buffer = new byte[1 << 16];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
                progress.done += n;
            }
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        return temp;
    }

    /** Extracts the entry whose path ends with {@code suffix} (or is exactly it). */
    private static void unzipEntry(Path zip, String suffix, Path target) throws IOException, InterruptedException {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            String wanted = suffix.replaceFirst("^/", "");
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                if (!entry.isDirectory() && (name.equals(wanted) || name.endsWith("/" + wanted))) {
                    Path temp = target.resolveSibling(target.getFileName() + ".part");
                    Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                    moveInto(temp, target);
                    return;
                }
            }
        }
        throw new IOException(suffix + " not found in the download");
    }

    /**
     * Puts a finished download in place. On Windows a file just written is often held for a moment by the virus
     * scanner (or the old program is still running), and the move fails with "in use": it is tried again for a few
     * seconds before giving up.
     */
    private static void moveInto(Path from, Path to) throws IOException, InterruptedException {
        for (int attempt = 1; ; attempt++) {
            try {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (java.nio.file.FileSystemException e) {
                if (attempt >= 10) {
                    throw e;
                }
                Thread.sleep(500L * Math.min(attempt, 4));
            }
        }
    }

    /**
     * Cleans up a downloaded archive. Failing to is no reason to report the install as failed: the program is in
     * place by then. A file the virus scanner still holds goes when the game closes, or on the next download.
     */
    private static void deleteQuietly(Path file) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Files.deleteIfExists(file);
                return;
            } catch (IOException e) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        file.toFile().deleteOnExit();
    }

    /** Half-finished downloads from an earlier try. */
    private static void removeLeftovers() {
        try (Stream<Path> files = Files.list(binDir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".part")).forEach(VideoTools::deleteQuietly);
        } catch (IOException ignored) {
            // Nothing to clean.
        }
    }

    /** A message a player can read: the file's name and what went wrong, not a path across the whole screen. */
    private static void fail(Progress progress, Exception e) {
        if (e instanceof java.nio.file.FileSystemException fs) {
            String file = fs.getFile() == null ? "" : Path.of(fs.getFile()).getFileName().toString();
            String reason = fs.getReason() != null ? fs.getReason()
                : e instanceof java.nio.file.AccessDeniedException ? "access denied"
                : e instanceof java.nio.file.NoSuchFileException ? "not found" : e.getClass().getSimpleName();
            progress.error = file.isEmpty() ? reason : file + ": " + reason;
            progress.locked = !(e instanceof java.nio.file.NoSuchFileException);
            return;
        }
        progress.error = e.getMessage() == null ? e.toString() : e.getMessage();
    }

    private static void deleteTree(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // Temporary files only.
        }
    }

    record Result(int exitCode, String stdout, String stderr) {
    }

    static Result run(List<String> command, int timeoutSeconds) throws IOException, InterruptedException {
        Process process = processBuilder(command).start();
        process.getOutputStream().close();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread errReader = new Thread(() -> {
            try (InputStream err = process.getErrorStream()) {
                err.transferTo(stderr);
            } catch (IOException ignored) {
                // Process ended.
            }
        }, "TutorialTracker stderr");
        errReader.setDaemon(true);
        errReader.start();
        byte[] out;
        try (InputStream in = process.getInputStream()) {
            out = in.readAllBytes();
        }
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException(command.get(0) + " timed out");
        }
        errReader.join(2000);
        return new Result(process.exitValue(), new String(out, StandardCharsets.UTF_8),
            stderr.toString(StandardCharsets.UTF_8));
    }

    static String lastLine(String text) {
        String[] lines = text.strip().split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].strip();
            if (!line.isEmpty() && !line.startsWith("WARNING")) {
                return line.length() > 200 ? line.substring(0, 200) : line;
            }
        }
        return lines.length > 0 ? lines[lines.length - 1].strip() : "";
    }

    /** yt-dlp's common arguments: no playlists, quiet, and our Deno when there is one. */
    static List<String> ytDlpCommand(String... args) {
        List<String> command = new ArrayList<>();
        command.add(ytDlp().toString());
        command.add("--no-playlist");
        command.add("--no-warnings");
        command.add("--no-progress");
        // A Deno on the PATH is found by yt-dlp itself; ours has to be named (and older yt-dlp lack the option).
        Path deno = deno();
        if (isOwnCopy(deno)) {
            command.add("--js-runtimes");
            command.add("deno:" + deno);
        }
        command.addAll(List.of(args));
        return command;
    }

    /** yt-dlp's JSON for a video without the streams: title, description, duration. Null when it fails. */
    public static JsonObject describe(String url) {
        try {
            Result result = run(ytDlpCommand("-J", "--skip-download", url), 90);
            if (result.exitCode == 0) {
                JsonElement element = JsonParser.parseString(result.stdout);
                return element.isJsonObject() ? element.getAsJsonObject() : null;
            }
            TutorialTracker.LOGGER.warn("yt-dlp could not read {}: {}", url, lastLine(result.stderr));
        } catch (Exception e) {
            TutorialTracker.LOGGER.warn("yt-dlp could not read {}", url, e);
        }
        return null;
    }
}
