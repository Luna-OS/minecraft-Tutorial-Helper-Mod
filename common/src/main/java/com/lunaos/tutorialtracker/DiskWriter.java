package com.lunaos.tutorialtracker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Writes the settings and projects off the game thread, so a slow disk never costs a frame. What is written is
 * turned into text on the game thread (so it is a consistent snapshot) and handed over; if a file is saved again
 * before the last save reached the disk, only the newest text is written. Whatever is still waiting when the game
 * closes is written then.
 */
public final class DiskWriter {

    /** The newest text for each file not written yet, or {@link #DELETE} for a file to remove. */
    private static final Map<Path, Object> PENDING = new ConcurrentHashMap<>();
    private static final Object DELETE = new Object();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "TutorialTracker save");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(DiskWriter::flush, "TutorialTracker save on exit"));
    }

    private DiskWriter() {
    }

    public static void write(Path file, String text) {
        schedule(file, text);
    }

    public static void delete(Path file) {
        schedule(file, DELETE);
    }

    private static void schedule(Path file, Object content) {
        // Only the first change since the last write queues a job; later ones just replace the text it will write.
        if (PENDING.put(file, content) == null) {
            EXECUTOR.execute(() -> writeNow(file));
        }
    }

    /** Writes everything still waiting, on the calling thread. */
    public static synchronized void flush() {
        for (Path file : PENDING.keySet()) {
            writeNow(file);
        }
    }

    private static synchronized void writeNow(Path file) {
        Object content = PENDING.remove(file);
        if (content == null) {
            return;
        }
        try {
            if (content == DELETE) {
                Files.deleteIfExists(file);
                return;
            }
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, (String) content, StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            TutorialTracker.LOGGER.warn("Could not write {}", file, e);
        }
    }
}
