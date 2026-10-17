package com.lunaos.tutorialtracker.project;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A project as a short line of text, to send to friends over Discord or anywhere else and to paste back in: name,
 * link, Minecraft version, preview picture (only when it cannot be worked out from the link) and the material list,
 * compressed. Chests are not part of it, they belong to one world. A list of twenty materials comes to a few hundred
 * characters.
 */
public final class ShareCode {

    /** What a project shared this way carries. */
    public record Shared(String name, String url, String version, String thumbnail, Map<String, Integer> materials) {
    }

    private static final String PREFIX = "TT1:";
    private static final Pattern CODE = Pattern.compile("TT1:([A-Za-z0-9_-]{8,})");
    private static final Pattern ITEM = Pattern.compile("[a-z0-9_.-]+(:[a-z0-9_./-]+)?");
    /** Nothing a real list needs comes close; a pasted code that unpacks to more is not ours. */
    private static final int MAX_TEXT = 64 * 1024;

    private ShareCode() {
    }

    /** The code, with the project's name and version in front so people see in the chat what it is. */
    public static String export(Project project, String derivedThumbnail) {
        StringBuilder text = new StringBuilder();
        text.append(clean(project.name)).append('\n')
            .append(clean(project.url)).append('\n')
            .append(clean(project.version)).append('\n');
        boolean first = true;
        for (Project.Material material : project.materials) {
            if (!first) {
                text.append(';');
            }
            first = false;
            String item = material.item.startsWith("minecraft:") ? material.item.substring(10) : material.item;
            text.append(item).append('*').append(material.needed);
        }
        if (!project.thumbnail.isBlank() && !project.thumbnail.equals(derivedThumbnail)) {
            text.append('\n').append(clean(project.thumbnail));
        }
        String code = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(deflate(text.toString()));
        String label = project.name.isBlank() ? "Tutorial Tracker" : "Tutorial Tracker: " + clean(project.name);
        if (!project.version.isBlank()) {
            label += " (" + clean(project.version) + ")";
        }
        return label + " " + code;
    }

    /** The first code found anywhere in a text (a whole chat message pasted in will do), or null. */
    public static Shared find(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = CODE.matcher(text);
        while (matcher.find()) {
            Shared shared = decode(matcher.group(1));
            if (shared != null) {
                return shared;
            }
        }
        return null;
    }

    private static Shared decode(String code) {
        String text;
        try {
            text = inflate(Base64.getUrlDecoder().decode(code));
        } catch (IllegalArgumentException | DataFormatException e) {
            return null;
        }
        if (text == null) {
            return null;
        }
        String[] lines = text.split("\n", -1);
        if (lines.length < 4) {
            return null;
        }
        Map<String, Integer> materials = new LinkedHashMap<>();
        for (String entry : lines[3].split(";")) {
            int star = entry.lastIndexOf('*');
            if (star <= 0) {
                continue;
            }
            String item = entry.substring(0, star);
            if (!ITEM.matcher(item).matches()) {
                continue;
            }
            try {
                int count = Integer.parseInt(entry.substring(star + 1));
                if (count > 0 && count <= 10_000_000) {
                    materials.merge(item.contains(":") ? item : "minecraft:" + item, count, Integer::sum);
                }
            } catch (NumberFormatException ignored) {
                // Not a count: skipped.
            }
        }
        String url = lines[1].strip();
        if (!url.isEmpty() && !url.matches("(?i)^https?://\\S+$")) {
            url = "";
        }
        String thumbnail = lines.length > 4 ? lines[4].strip() : "";
        if (!thumbnail.matches("(?i)^https?://\\S+$")) {
            thumbnail = "";
        }
        return new Shared(limit(lines[0], 200), url, limit(lines[2], 20), thumbnail, materials);
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').strip();
    }

    private static String limit(String value, int length) {
        String stripped = value.strip();
        return stripped.length() > length ? stripped.substring(0, length) : stripped;
    }

    private static byte[] deflate(String text) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        deflater.setInput(text.getBytes(StandardCharsets.UTF_8));
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return out.toByteArray();
    }

    /** The text, or null when it unpacks to more than any list could need. */
    private static String inflate(byte[] data) throws DataFormatException {
        Inflater inflater = new Inflater(true);
        // Raw deflate data wants one byte more than it holds (see Inflater).
        inflater.setInput(java.util.Arrays.copyOf(data, data.length + 1));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                out.write(buffer, 0, n);
                if (out.size() > MAX_TEXT) {
                    return null;
                }
            }
        } finally {
            inflater.end();
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
