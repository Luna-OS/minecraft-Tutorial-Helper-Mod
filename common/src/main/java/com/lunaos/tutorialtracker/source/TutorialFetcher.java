package com.lunaos.tutorialtracker.source;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lunaos.tutorialtracker.video.VideoTools;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches the text of a tutorial: for a YouTube video its title and description (where the material list usually
 * is), for any other page its visible text.
 */
public final class TutorialFetcher {

    public record Tutorial(String title, String text, String videoId) {
    }

    /** The error message when YouTube answers with its bot check instead of the page. */
    public static final String BLOCKED = "youtube_blocked";

    private static final Pattern YOUTUBE_ID = Pattern.compile(
        "(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|shorts/|live/|embed/|v/)|youtu\\.be/|youtube-nocookie\\.com/embed/)"
            + "([A-Za-z0-9_-]{11})");
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36";

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    private TutorialFetcher() {
    }

    public static String youtubeId(String url) {
        if (url == null) {
            return null;
        }
        Matcher matcher = YOUTUBE_ID.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Adds https:// to a link typed without it. */
    public static String normalizeUrl(String url) {
        String trimmed = url.trim();
        if (!trimmed.isEmpty() && !trimmed.matches("(?i)^https?://.*")) {
            trimmed = "https://" + trimmed;
        }
        return trimmed;
    }

    public static CompletableFuture<Tutorial> fetch(String rawUrl) {
        String url = normalizeUrl(rawUrl);
        return CompletableFuture.supplyAsync(() -> {
            try {
                String videoId = youtubeId(url);
                if (videoId != null) {
                    return fetchYouTube(url, videoId);
                }
                return fetchPage(url);
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("interrupted", e);
            }
        });
    }

    private static Tutorial fetchYouTube(String url, String videoId) throws IOException, InterruptedException {
        IOException failure = null;
        try {
            String html = get("https://www.youtube.com/watch?v=" + videoId + "&hl=en");
            JsonObject player = extractJson(html, "ytInitialPlayerResponse");
            if (player != null && player.has("videoDetails")) {
                JsonObject details = player.getAsJsonObject("videoDetails");
                String title = string(details, "title");
                String description = string(details, "shortDescription");
                if (!description.isEmpty() || !title.isEmpty()) {
                    return new Tutorial(title, description, videoId);
                }
            }
        } catch (IOException e) {
            failure = e;
        }
        // The page can come back as a consent or bot check; yt-dlp knows its way around those.
        if (VideoTools.ytDlp() != null) {
            JsonObject info = VideoTools.describe(url);
            if (info != null) {
                return new Tutorial(string(info, "title"), string(info, "description"), videoId);
            }
        }
        if (failure != null && !BLOCKED.equals(failure.getMessage())) {
            throw failure;
        }
        throw new IOException(BLOCKED, failure);
    }

    private static Tutorial fetchPage(String url) throws IOException, InterruptedException {
        String html = get(url);
        if (html == null) {
            // Not a page (a video file, say): nothing to read a list from, but it can still be played.
            String path = URI.create(url).getPath();
            String name = path == null ? url : path.substring(path.lastIndexOf('/') + 1);
            return new Tutorial(name, "", null);
        }
        String title = "";
        Matcher titleMatch = Pattern.compile("(?is)<title[^>]*>(.*?)</title>").matcher(html);
        if (titleMatch.find()) {
            title = decodeEntities(titleMatch.group(1)).strip();
        }
        return new Tutorial(title, htmlToText(html), null);
    }

    /** Pages larger than this are cut off: a material list is never that far down. */
    private static final int MAX_PAGE_BYTES = 8 << 20;

    /** The page as text, or null when the link is not a text page at all. */
    private static String get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9,de;q=0.8")
            // Skips the EU cookie consent page in front of YouTube.
            .header("Cookie", "SOCS=CAI; CONSENT=YES+cb")
            .GET()
            .build();
        HttpResponse<java.io.InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (java.io.InputStream body = response.body()) {
            if (response.uri().getHost() != null && response.uri().getHost().endsWith("google.com")
                && response.uri().getPath().startsWith("/sorry")) {
                throw new IOException(BLOCKED);
            }
            if (response.statusCode() == 429) {
                throw new IOException(BLOCKED);
            }
            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode());
            }
            String type = response.headers().firstValue("Content-Type").orElse("text/html").toLowerCase(java.util.Locale.ROOT);
            if (!type.startsWith("text/") && !type.contains("html") && !type.contains("xml") && !type.contains("json")) {
                return null;
            }
            return new String(body.readNBytes(MAX_PAGE_BYTES), StandardCharsets.UTF_8);
        }
    }

    /** The JSON object assigned to {@code name} in a page's script, found by matching its braces. */
    static JsonObject extractJson(String html, String name) {
        int at = html.indexOf(name + " = {");
        int offset = name.length() + 3;
        if (at < 0) {
            at = html.indexOf(name + "={");
            offset = name.length() + 1;
        }
        if (at < 0) {
            return null;
        }
        int start = at + offset;
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < html.length(); i++) {
            char c = html.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    try {
                        JsonElement element = JsonParser.parseString(html.substring(start, i + 1));
                        return element.isJsonObject() ? element.getAsJsonObject() : null;
                    } catch (RuntimeException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    /** The readable text of a page, one line per paragraph, list item or table row. */
    static String htmlToText(String html) {
        String text = html.replaceAll("(?is)<(script|style|noscript|svg|head)[^>]*>.*?</\\1>", " ");
        text = text.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replaceAll("(?i)</(p|div|tr|h[1-6]|ul|ol|table|section|article)>", "\n");
        text = text.replaceAll("(?i)<(li)[^>]*>", "\n- ");
        text = text.replaceAll("(?i)</t[dh]>", " ");
        text = text.replaceAll("(?s)<[^>]+>", " ");
        text = decodeEntities(text);
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\n")) {
            String stripped = line.replaceAll("[ \\t\\u00a0]+", " ").strip();
            if (!stripped.isEmpty()) {
                out.append(stripped).append('\n');
            } else if (!out.isEmpty() && out.charAt(out.length() - 1) == '\n'
                && (out.length() < 2 || out.charAt(out.length() - 2) != '\n')) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static String decodeEntities(String text) {
        Matcher numeric = Pattern.compile("&#(x?)([0-9a-fA-F]+);").matcher(text);
        StringBuilder out = new StringBuilder();
        while (numeric.find()) {
            int code;
            try {
                code = Integer.parseInt(numeric.group(2), numeric.group(1).isEmpty() ? 10 : 16);
            } catch (NumberFormatException e) {
                code = '?';
            }
            numeric.appendReplacement(out, Matcher.quoteReplacement(new String(Character.toChars(code))));
        }
        numeric.appendTail(out);
        return out.toString().replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&");
    }
}
