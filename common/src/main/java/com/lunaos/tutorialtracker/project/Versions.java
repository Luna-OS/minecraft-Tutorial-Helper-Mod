package com.lunaos.tutorialtracker.project;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the Minecraft version a tutorial is for in its title or description ("1.21.4", "[1.20]", "26.3"). */
public final class Versions {

    /**
     * Java Edition versions from 1.8 on, and the new numbering from 26 on. Not part of a longer number or word, so
     * "1.5k cobblestone", "1.99" and "26.3k subscribers" do not count.
     */
    private static final Pattern VERSION = Pattern.compile(
        "(?<![\\w.])(1\\.(?:[89]|1\\d|2\\d)(?:\\.\\d{1,2})?|(?:2[6-9]|3\\d)\\.\\d{1,2}(?:\\.\\d{1,2})?)(?![\\w.]|\\.\\d)");
    /** A description line that talks about the version. */
    private static final Pattern VERSION_LINE =
        Pattern.compile("minecraft|\\bmc\\b|java|bedrock|version|works? (in|on)|tested", Pattern.CASE_INSENSITIVE);

    private Versions() {
    }

    /** The version, or empty when neither the title nor a line about versions names one. */
    public static String guess(String title, String description) {
        String found = first(title);
        if (found != null) {
            return found;
        }
        if (description != null) {
            for (String line : description.split("\n")) {
                if (VERSION_LINE.matcher(line).find()) {
                    found = first(line);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return "";
    }

    private static String first(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = VERSION.matcher(text.toLowerCase(Locale.ROOT));
        return matcher.find() ? matcher.group(1) : null;
    }
}
