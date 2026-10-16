package com.lunaos.tutorialtracker.materials;

import com.lunaos.tutorialtracker.TutorialTracker;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Every item under the names a tutorial might use for it: the English name (most videos are English), the name in
 * the game's current language (German videos for German players), the registry name, and a few words tutorials
 * use loosely ("planks", "iron", "slime"). Rebuilt when the language changes.
 */
public final class ItemNameIndex implements MaterialParser.ItemNames {

    /** Loose words that stand for one specific item. Checked before anything else. */
    private static final String[][] ALIASES = {
        {"planks", "oak_planks"}, {"wood planks", "oak_planks"}, {"wooden planks", "oak_planks"},
        {"logs", "oak_log"}, {"log", "oak_log"}, {"wood", "oak_log"}, {"holz", "oak_log"}, {"bretter", "oak_planks"},
        {"iron", "iron_ingot"}, {"eisen", "iron_ingot"}, {"gold", "gold_ingot"}, {"copper", "copper_ingot"},
        {"kupfer", "copper_ingot"}, {"netherite", "netherite_ingot"}, {"diamonds", "diamond"},
        {"slime", "slime_block"}, {"slimes", "slime_block"}, {"schleim", "slime_block"},
        {"honey", "honey_block"}, {"water", "water_bucket"}, {"wasser", "water_bucket"}, {"lava", "lava_bucket"},
        {"redstone", "redstone"}, {"dust", "redstone"}, {"comparator", "comparator"}, {"comparators", "comparator"},
        {"repeater", "repeater"}, {"repeaters", "repeater"}, {"komparator", "comparator"}, {"verstärker", "repeater"},
        {"torch", "torch"}, {"redstone torches", "redstone_torch"},
        {"rails", "rail"}, {"schienen", "rail"}, {"powered rails", "powered_rail"},
        {"trapdoor", "oak_trapdoor"}, {"trapdoors", "oak_trapdoor"}, {"falltür", "oak_trapdoor"},
        {"fence", "oak_fence"}, {"fences", "oak_fence"}, {"zaun", "oak_fence"}, {"fence gate", "oak_fence_gate"},
        {"sign", "oak_sign"}, {"signs", "oak_sign"}, {"schild", "oak_sign"}, {"door", "oak_door"},
        {"button", "stone_button"}, {"buttons", "stone_button"}, {"knopf", "stone_button"},
        {"pressure plate", "stone_pressure_plate"}, {"druckplatte", "stone_pressure_plate"},
        {"slab", "stone_slab"}, {"slabs", "stone_slab"}, {"stufe", "stone_slab"}, {"stufen", "stone_slab"},
        {"stairs", "stone_stairs"}, {"treppe", "stone_stairs"}, {"carpet", "white_carpet"}, {"teppich", "white_carpet"},
        {"wool", "white_wool"}, {"wolle", "white_wool"}, {"concrete", "white_concrete"}, {"beton", "white_concrete"},
        {"bed", "red_bed"}, {"bett", "red_bed"}, {"boat", "oak_boat"}, {"boot", "oak_boat"},
        {"glass", "glass"}, {"pane", "glass_pane"}, {"panes", "glass_pane"}, {"leaves", "oak_leaves"},
        {"sapling", "oak_sapling"}, {"saplings", "oak_sapling"}, {"terracotta", "terracotta"},
        {"shulker", "shulker_box"}, {"shulker boxes", "shulker_box"}, {"shulkers", "shulker_box"},
        {"tnt", "tnt"}, {"pearls", "ender_pearl"}, {"bonemeal", "bone_meal"}, {"string", "string"},
        {"lead", "lead"}, {"leads", "lead"}, {"name tag", "name_tag"}, {"nametag", "name_tag"},
        {"cobble", "cobblestone"}, {"cobbled deepslate", "cobbled_deepslate"}, {"stone brick", "stone_bricks"},
        {"hoppers", "hopper"}, {"hopper minecarts", "hopper_minecart"}, {"chest minecart", "chest_minecart"},
        {"daylight sensor", "daylight_detector"}, {"lightning rod", "lightning_rod"},
    };

    private static ItemNameIndex instance;
    private static String instanceLanguage;

    private final Map<String, Item> byName = new HashMap<>();
    /** For the add-item search: each item with the names it can be found by. */
    private final List<Entry> entries = new ArrayList<>();

    public record Entry(Item item, String displayName, String searchText) {
    }

    public static synchronized ItemNameIndex get() {
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (instance == null || !language.equals(instanceLanguage)) {
            instance = new ItemNameIndex();
            instanceLanguage = language;
        }
        return instance;
    }

    private ItemNameIndex() {
        Map<String, String> english = loadEnglish();
        Language current = Language.getInstance();

        for (String[] alias : ALIASES) {
            Item item = item(alias[1]);
            if (item != null) {
                this.byName.putIfAbsent(MaterialParser.normalize(alias[0]), item);
            }
        }
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                items.add(item);
            }
        }
        // English and registry names first: those are what most tutorials use. The current language fills in.
        for (Item item : items) {
            String englishName = english.get(item.getDescriptionId());
            if (englishName != null) {
                this.byName.putIfAbsent(MaterialParser.normalize(englishName), item);
            }
        }
        for (Item item : items) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            this.byName.putIfAbsent(MaterialParser.normalize(id.getPath()), item);
        }
        for (Item item : items) {
            String localName = current.getOrDefault(item.getDescriptionId(), "");
            if (!localName.isEmpty()) {
                this.byName.putIfAbsent(MaterialParser.normalize(localName), item);
            }
        }
        for (Item item : items) {
            String display = new ItemStack(item).getHoverName().getString();
            String englishName = english.getOrDefault(item.getDescriptionId(), "");
            String path = BuiltInRegistries.ITEM.getKey(item).getPath();
            this.entries.add(new Entry(item, display,
                (display + " " + englishName + " " + path.replace('_', ' ')).toLowerCase(Locale.ROOT)));
        }
        this.entries.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
    }

    private static Map<String, String> loadEnglish() {
        Map<String, String> out = new HashMap<>();
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager()
                .getResource(Identifier.withDefaultNamespace("lang/en_us.json"));
            if (resource.isPresent()) {
                try (InputStream in = resource.get().open()) {
                    Language.loadFromJson(in, out::put);
                }
            }
        } catch (Exception e) {
            TutorialTracker.LOGGER.warn("Could not read the English item names", e);
        }
        return out;
    }

    public static Item item(String id) {
        Identifier key = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (key == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.getValue(key);
        return item == Items.AIR ? null : item;
    }

    /** Item ids are looked up for every stack counted, many times a second: made once per item. */
    private static final Map<Item, String> IDS = new ConcurrentHashMap<>();

    public static String id(Item item) {
        return IDS.computeIfAbsent(item, i -> BuiltInRegistries.ITEM.getKey(i).toString());
    }

    @Override
    public String lookup(String normalizedName) {
        Item item = this.byName.get(normalizedName);
        return item == null ? null : id(item);
    }

    @Override
    public int maxStackSize(String itemId) {
        Item item = item(itemId);
        return item == null ? 64 : Math.max(1, item.getDefaultMaxStackSize());
    }

    /** Items whose name (current language or English) or id contains every word of the query. */
    public List<Item> search(String query, int limit) {
        String[] words = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<Item> out = new ArrayList<>();
        for (Entry entry : this.entries) {
            boolean all = true;
            for (String word : words) {
                if (!word.isEmpty() && !entry.searchText().contains(word)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                out.add(entry.item());
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }
}
