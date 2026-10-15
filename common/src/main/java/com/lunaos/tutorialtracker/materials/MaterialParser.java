package com.lunaos.tutorialtracker.materials;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a material list out of free text, usually a video description: lines such as "64x Hopper", "Hopper x 64",
 * "Hoppers: 64", "3 stacks of cobblestone", "- 2 Shulker of Glass" or "10 Trichter". Plain Java so it can be tested
 * without the game; item names are resolved through {@link ItemNames}.
 */
public final class MaterialParser {

    /** Resolves a normalised name ("sticky piston") to an item id ("minecraft:sticky_piston"). */
    public interface ItemNames {
        /** The item id for a normalised name, or null. */
        String lookup(String normalizedName);

        int maxStackSize(String itemId);
    }

    public record Result(Map<String, Integer> items, List<String> unmatched) {
    }

    private static final Pattern URL = Pattern.compile("(https?://|www\\.)\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIMESTAMP_START = Pattern.compile("^[\\s\\p{Punct}]*\\d{1,2}:\\d{2}(:\\d{2})?\\b");
    private static final Pattern TIMESTAMP = Pattern.compile("\\b\\d{1,2}:\\d{2}(:\\d{2})?\\b");
    /** Game versions (1.21.4, 1.20, 26.3): never amounts. A unit after the number makes it one (1.5 stacks). */
    private static final Pattern VERSION = Pattern.compile(
        "\\b\\d+(\\.\\d+){2,}\\b|\\b(1\\.\\d{2}|2\\d\\.\\d)(\\.x)?\\+?(?!\\d|\\s*(stacks?|st|sb|dc|shulker|k)\\b)");
    private static final Pattern SKIP_LINE = Pattern.compile(
        "(/\\s*h(ou)?r?\\b|per hour|pro stunde|/\\s*std|rates?\\b|\\bdrops?\\b|\\bfps\\b|\\btps\\b|subscribe|abonnier|"
            + "patreon|discord|twitter|instagram|tiktok|version|edition|coordinates|koordinaten|music|musik|"
            + "\\bmod(s|pack)?\\b|world download|\\bxp\\b|\\bexp\\b|experience|erfahrung|seed\\s*:)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER = Pattern.compile(
        "(materials?|materialien|material\\s*list|materialliste|items? needed|required items|resources|ressourcen|"
            + "you('ll| will)? need|what you need|things you need|needed items|benötigt|benoetigt|du brauchst|"
            + "ihr braucht|was ihr braucht|was du brauchst|zutaten|shopping list|einkaufsliste|cost|kosten|bill of materials)",
        Pattern.CASE_INSENSITIVE);
    /** A number with an optional "x" before or after it and an optional "k" (thousand). */
    private static final Pattern NUMBER = Pattern.compile(
        "(?<![\\p{L}\\d.,])[x×]?\\s?(\\d{1,7}(?:[.,]\\d+)?)\\s?(k\\b)?\\s?[x×]?(?!\\d|st\\b|nd\\b|rd\\b|th\\b)",
        Pattern.CASE_INSENSITIVE);

    private static final Map<String, Integer> UNIT_STACKS = new LinkedHashMap<>();

    static {
        for (String unit : new String[]{"stack", "stacks", "st", "stapel"}) {
            UNIT_STACKS.put(unit, 1);
        }
        for (String unit : new String[]{"sb", "sbs", "shulker", "shulkers", "shulkerbox", "shulkerboxes", "shulkerkiste",
            "shulkerkisten"}) {
            UNIT_STACKS.put(unit, 27);
        }
        for (String unit : new String[]{"dc", "dcs", "doublechest", "doublechests", "doppelkiste", "doppelkisten"}) {
            UNIT_STACKS.put(unit, 54);
        }
    }

    /** Words around an amount that are not part of the item name. */
    private static final java.util.Set<String> FILLER = java.util.Set.of("of", "x", "von", "mit", "full", "voll", "volle",
        "voller", "a", "an", "any", "some", "about", "around", "approx", "ca", "circa", "etwa", "ungefähr", "roughly",
        "total", "insgesamt", "gesamt", "pcs", "pieces", "stück", "stk", "each", "je", "jeweils", "and", "und", "plus",
        "the", "die", "der", "das", "den", "type", "types", "kind", "beliebige", "beliebig", "or", "oder", "more", "mehr",
        "needed", "benötigt", "required", "items", "item", "blocks", "block", "for", "für", "im");

    private static final int MAX_AMOUNT = 1_000_000;
    private static final int MAX_UNMATCHED = 40;

    private final ItemNames names;

    public MaterialParser(ItemNames names) {
        this.names = names;
    }

    public Result parse(String text) {
        List<String> lines = splitLines(text);

        // A "Materials:" section, when the text has one, is the list; quantities elsewhere (chapters, rates, other
        // builds) are then left out.
        Result section = parseSection(lines);
        if (section != null && !section.items().isEmpty()) {
            return section;
        }
        return parseLines(lines);
    }

    private Result parseSection(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.length() > 80 || !HEADER.matcher(line).find() || parseLine(line, null) != null) {
                continue;
            }
            Map<String, Integer> items = new LinkedHashMap<>();
            List<String> unmatched = new ArrayList<>();
            int misses = 0;
            for (int j = i + 1; j < lines.size(); j++) {
                String next = lines.get(j);
                if (next.isBlank()) {
                    if (!items.isEmpty()) {
                        // A blank line ends the list, unless the list carries on right after it.
                        if (j + 1 < lines.size() && parseLine(lines.get(j + 1), null) != null) {
                            continue;
                        }
                        break;
                    }
                    continue;
                }
                Parsed parsed = parseLine(next, unmatched);
                if (parsed == null) {
                    if (!items.isEmpty() && ++misses >= 2) {
                        break;
                    }
                    continue;
                }
                misses = 0;
                parsed.addTo(items);
            }
            if (!items.isEmpty()) {
                return new Result(items, unmatched);
            }
        }
        return null;
    }

    private Result parseLines(List<String> lines) {
        Map<String, Integer> items = new LinkedHashMap<>();
        List<String> unmatched = new ArrayList<>();
        for (String line : lines) {
            Parsed parsed = parseLine(line, unmatched);
            if (parsed != null) {
                parsed.addTo(items);
            }
        }
        return new Result(items, unmatched);
    }

    private static List<String> splitLines(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : text.replace("\r", "").split("\n")) {
            String line = URL.matcher(raw).replaceAll(" ").strip();
            // Several entries on one line: "64 Hoppers, 32 Chests, 10 Rails".
            String[] parts = line.split("\\s*[,;|•·]\\s*|\\s+[-–]\\s+(?=\\d)|\\s+(?:and|und|&)\\s+(?=\\d+\\s*[x×]?\\s*\\p{L}{3})");
            int withNumber = 0;
            for (String part : parts) {
                if (NUMBER.matcher(part).find()) {
                    withNumber++;
                }
            }
            if (parts.length > 1 && withNumber > 1) {
                for (String part : parts) {
                    out.add(part.strip());
                }
            } else {
                out.add(line);
            }
        }
        return out;
    }

    private record Parsed(String itemId, int amount) {
        void addTo(Map<String, Integer> items) {
            items.merge(this.itemId, this.amount, (a, b) -> Math.min(MAX_AMOUNT, a + b));
        }
    }

    /** One entry, or null when the line has no amount or no known item. Lines with an amount but no item are noted. */
    private Parsed parseLine(String original, List<String> unmatched) {
        if (original.isBlank() || TIMESTAMP_START.matcher(original).find() || SKIP_LINE.matcher(original).find()) {
            return null;
        }
        String line = TIMESTAMP.matcher(original).replaceAll(" ");
        line = VERSION.matcher(line).replaceAll(" ");
        line = line.toLowerCase(Locale.ROOT).replace('×', 'x');

        // Every amount on the line is tried in turn: in "Layer 2: 16 slime blocks" the first number is not the one.
        List<int[]> numbers = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(line);
        while (matcher.find()) {
            numbers.add(new int[]{matcher.start(), matcher.end(), matcher.start(1), matcher.end(1),
                matcher.group(2) != null ? 1 : 0});
        }
        if (numbers.isEmpty()) {
            return null;
        }
        for (int i = 0; i < numbers.size(); i++) {
            int previousEnd = i > 0 ? numbers.get(i - 1)[1] : 0;
            int nextStart = i + 1 < numbers.size() ? numbers.get(i + 1)[0] : line.length();
            Parsed parsed = tryAmount(line, numbers.get(i), previousEnd, nextStart);
            if (parsed != null) {
                return parsed;
            }
        }
        if (unmatched != null && unmatched.size() < MAX_UNMATCHED) {
            String shown = original.strip();
            if (shown.length() > 2 && shown.length() <= 120 && shown.chars().anyMatch(Character::isLetter)) {
                unmatched.add(shown);
            }
        }
        return null;
    }

    /** The entry for one amount on a line, its name taken from the words up to the neighbouring amounts. */
    private Parsed tryAmount(String line, int[] number, int previousEnd, int nextStart) {
        double value;
        try {
            value = Double.parseDouble(line.substring(number[2], number[3]).replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
        if (number[4] == 1) {
            value *= 1000;
        }
        String before = line.substring(previousEnd, number[0]);
        String after = line.substring(number[1], Math.max(number[1], nextStart));
        // "1: place the hoppers" or "(2)": numbering, not an amount of what follows.
        boolean afterUsable = !after.stripLeading().matches("^[:.)\\]].*");

        List<String> afterWords = words(after);
        int stacks = 0;
        int unitIndex = -1;
        if (!afterWords.isEmpty() && UNIT_STACKS.containsKey(afterWords.get(0))) {
            unitIndex = 0;
        } else if (afterWords.size() > 1 && afterWords.get(0).equals("x") && UNIT_STACKS.containsKey(afterWords.get(1))) {
            unitIndex = 1;
        }
        String unit = null;
        if (unitIndex >= 0) {
            String candidate = afterWords.get(unitIndex);
            // "2 shulker boxes" are boxes, "2 shulkers of glass" is glass: a container unit needs an "of" after it.
            boolean containerUnit = UNIT_STACKS.get(candidate) > 1;
            int next = unitIndex + 1;
            if (containerUnit && next < afterWords.size() && afterWords.get(next).startsWith("box")) {
                next++;
            }
            boolean followedByOf = next < afterWords.size() && (afterWords.get(next).equals("of")
                || afterWords.get(next).equals("voll") || afterWords.get(next).equals("mit")
                || afterWords.get(next).equals("full"));
            boolean shortUnit = candidate.equals("sb") || candidate.equals("sbs") || candidate.startsWith("dc")
                || candidate.startsWith("double") || candidate.startsWith("doppel");
            if (!containerUnit || followedByOf || shortUnit) {
                unit = candidate;
                stacks = UNIT_STACKS.get(candidate);
                afterWords = new ArrayList<>(afterWords.subList(next, afterWords.size()));
            }
        }
        // "2 stacks + 5" or "2 stacks and 5": a few more on top of the stacks.
        int extra = 0;
        if (unit != null) {
            Matcher plus = Pattern.compile("^\\s*\\p{L}*\\s*(\\+|plus|and|und)\\s*(\\d{1,3})(?!\\d)")
                .matcher(line.substring(number[1]));
            if (plus.find()) {
                extra = Integer.parseInt(plus.group(2));
                // The name then follows the second amount: "2 stacks + 5 redstone".
                String rest = line.substring(number[1] + plus.end());
                Matcher following = NUMBER.matcher(rest);
                if (following.find()) {
                    rest = rest.substring(0, following.start());
                }
                List<String> restWords = words(rest);
                if (!restWords.isEmpty()) {
                    afterWords = restWords;
                }
            }
        }
        if (unit == null && value != Math.rint(value)) {
            // "1.5 hoppers" means nothing; without a unit a decimal is a version or a rate.
            return null;
        }

        // The item name is on either side of the amount: "64 Hopper" or "Hopper: 64".
        String itemId = null;
        if (afterUsable) {
            List<String> nameWords = clean(afterWords);
            if (!nameWords.isEmpty()) {
                itemId = find(nameWords);
            }
        }
        if (itemId == null && unit == null) {
            List<String> beforeWords = clean(words(before));
            if (!beforeWords.isEmpty()) {
                itemId = find(beforeWords);
            }
        }
        if (itemId == null) {
            return null;
        }
        long amount;
        if (unit != null) {
            amount = Math.round(value * stacks * this.names.maxStackSize(itemId)) + extra;
        } else {
            amount = Math.round(value);
        }
        if (amount <= 0 || amount > MAX_AMOUNT) {
            return null;
        }
        return new Parsed(itemId, (int) amount);
    }

    private static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        for (String word : normalize(text).split(" ")) {
            if (!word.isEmpty()) {
                out.add(word);
            }
        }
        return out;
    }

    private static List<String> clean(List<String> words) {
        List<String> out = new ArrayList<>();
        for (String word : words) {
            if (!FILLER.contains(word) && !word.matches("\\d+")) {
                out.add(word);
            }
        }
        return out;
    }

    /** The longest run of words that names an item; the earliest one wins among runs of the same length. */
    private String find(List<String> words) {
        int max = Math.min(6, words.size());
        for (int length = max; length >= 1; length--) {
            for (int start = 0; start + length <= words.size(); start++) {
                String id = lookupVariants(words.subList(start, start + length));
                if (id != null) {
                    return id;
                }
            }
        }
        return null;
    }

    private String lookupVariants(List<String> words) {
        String joined = String.join(" ", words);
        String id = this.names.lookup(joined);
        if (id != null) {
            return id;
        }
        // Plurals, English and German: hoppers, torches, berries, shelves, Truhen, Kolben...
        String last = words.get(words.size() - 1);
        String head = words.size() > 1 ? String.join(" ", words.subList(0, words.size() - 1)) + " " : "";
        for (String singular : singulars(last)) {
            id = this.names.lookup(head + singular);
            if (id != null) {
                return id;
            }
        }
        // "slime blocks" -> "slime block", "glass panes" -> "glass pane": also try the first word in singular.
        if (words.size() > 1) {
            for (String singular : singulars(words.get(0))) {
                List<String> copy = new ArrayList<>(words);
                copy.set(0, singular);
                id = this.names.lookup(String.join(" ", copy));
                if (id != null) {
                    return id;
                }
            }
        }
        return null;
    }

    static List<String> singulars(String word) {
        List<String> out = new ArrayList<>(4);
        if (word.length() < 3) {
            return out;
        }
        if (word.endsWith("ies")) {
            out.add(word.substring(0, word.length() - 3) + "y");
        }
        if (word.endsWith("ves")) {
            out.add(word.substring(0, word.length() - 3) + "f");
        }
        if (word.endsWith("es")) {
            out.add(word.substring(0, word.length() - 2));
        }
        if (word.endsWith("s")) {
            out.add(word.substring(0, word.length() - 1));
        }
        if (word.endsWith("en")) {
            out.add(word.substring(0, word.length() - 2));
        }
        if (word.endsWith("n") || word.endsWith("e")) {
            out.add(word.substring(0, word.length() - 1));
        }
        if (word.endsWith("er")) {
            out.add(word.substring(0, word.length() - 2));
        }
        return out;
    }

    /** Lower case, letters and digits only, single spaces. The item index uses the same form. */
    public static String normalize(String text) {
        String lower = text.toLowerCase(Locale.ROOT).replace('_', ' ').replace("'", "");
        StringBuilder out = new StringBuilder(lower.length());
        boolean space = true;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
                space = false;
            } else if (!space) {
                out.append(' ');
                space = true;
            }
        }
        int end = out.length();
        if (end > 0 && out.charAt(end - 1) == ' ') {
            out.setLength(end - 1);
        }
        return out.toString();
    }
}
