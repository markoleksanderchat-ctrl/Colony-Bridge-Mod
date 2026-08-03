package com.colonybridge.market;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

public final class OnlineIssuerMapper {
    public static final Set<String> TICKERS = Set.of("STPL", "MRCR", "PEPR", "FISH", "IRON", "GDSM",
            "WAXC", "SKIN", "CORD", "MSON", "CARP", "JOIN", "COOP", "GLAZ", "BRIK", "DYER",
            "HABD", "BOWY", "FLET", "CUTL", "ARMR", "APOT", "BAKR", "BUTR", "POUL", "FRUT",
            "SALT", "SHIP", "SCRV", "HORN", "LORN", "FOND", "ADVT", "WIRE", "MUSC", "GARD",
            "COLL", "UPHL");
    public static final String CATALOG_CHECKSUM = "A5093542FE72BC8425FC859465814B0BDC34505BF1BC646E9A785DF008378DA1";
    private static final Map<String, String> ITEM_TICKERS = loadItemTickers();
    public static final int CATALOG_SIZE = ITEM_TICKERS.size();

    private OnlineIssuerMapper() {
    }

    public static String issuerFor(String itemId, Set<String> tags) {
        if (!MarketItemIds.isVanilla(itemId)) throw new IllegalArgumentException("Only vanilla items can be mapped.");
        String catalogTicker = ITEM_TICKERS.get(itemId);
        if (catalogTicker != null) return catalogTicker;
        String path = itemId.substring("minecraft:".length());
        Set<String> safeTags = tags == null ? Set.of() : tags;

        if (contains(path, "music_disc", "jukebox", "note_block", "goat_horn")) return "MUSC";
        if (contains(path, "redstone", "piston", "observer", "dispenser", "dropper", "repeater", "comparator",
                "daylight_detector", "tripwire", "sculk", "target", "lightning_rod")) return "WIRE";
        if (contains(path, "coal", "charcoal", "torch", "furnace", "smoker", "blast_furnace", "campfire")) return "COLL";
        if (safeTags.contains("#end") || safeTags.contains("#nether") || contains(path, "trial_key", "ominous_bottle",
                "heavy_core", "mace", "breeze_rod", "wind_charge", "echo_shard", "recovery_compass")) return "ADVT";
        if (contains(path, "potion", "brewing_stand", "fermented_spider_eye", "glistering_melon", "rabbit_foot",
                "phantom_membrane", "spider_eye", "slime_ball")) return "APOT";
        if (contains(path, "helmet", "chestplate", "leggings", "shield", "smithing_template") || path.endsWith("_boots")) return "ARMR";
        if (contains(path, "pickaxe", "shovel", "hoe", "shears", "flint_and_steel", "brush")
                || path.endsWith("_sword") || path.endsWith("_axe")) return "CUTL";
        if (contains(path, "crossbow") || path.endsWith("bow")) return "BOWY";
        if (contains(path, "arrow", "firework_rocket", "firework_star", "flint", "feather")) return "FLET";
        if (contains(path, "boat", "raft")) return "SHIP";
        if (contains(path, "minecart", "rail", "hopper", "anvil", "cauldron", "bell", "chain")) return "FOND";
        if (contains(path, "saddle", "horse_armor", "fishing_rod", "carrot_on_a_stick", "warped_fungus_on_a_stick", "lead")) return "LORN";
        if (contains(path, "book", "paper", "map", "compass", "clock", "name_tag")) return "SCRV";
        if (contains(path, "bone", "scute", "shell", "tooth", "skull", "head")) return "HORN";
        if (safeTags.contains("#ocean") || contains(path, "kelp", "sponge", "coral", "sea_pickle", "turtle_egg")) return "SALT";
        if (contains(path, "fish", "salmon", "cod", "pufferfish", "tropical_fish", "ink_sac")) return "FISH";
        if (contains(path, "raw_beef", "cooked_beef", "porkchop", "mutton", "rotten_flesh", "stew")) return "BUTR";
        if (contains(path, "chicken", "rabbit", "egg")) return "POUL";
        if (contains(path, "bread", "cake", "cookie", "pie", "baked_potato")) return "BAKR";
        if (contains(path, "apple", "berries", "melon", "pumpkin", "cocoa", "sugar_cane", "sugar")) return "FRUT";
        if (safeTags.contains("#crops") || contains(path, "wheat", "carrot", "potato", "beetroot", "hay_block")) return "PEPR";
        if (safeTags.contains("#farmable") || contains(path, "sapling", "leaves", "flower", "moss", "vine", "fungus", "roots", "seeds")) return "GARD";
        if (contains(path, "bed", "painting", "item_frame", "armor_stand", "flower_pot")) return "UPHL";
        if (path.equals("white_wool")) return "STPL";
        if (contains(path, "dye", "banner", "carpet", "glazed_terracotta") || path.endsWith("_wool")) return "DYER";
        if (contains(path, "candle", "honeycomb", "honey_bottle")) return "WAXC";
        if (contains(path, "leather", "rabbit_hide")) return "SKIN";
        if (contains(path, "glass", "beacon")) return "GLAZ";
        if (contains(path, "brick", "terracotta", "concrete", "mud", "pottery", "decorated_pot")) return "BRIK";
        if (contains(path, "barrel", "chest", "shulker_box", "bucket", "bowl", "bundle")) return "COOP";
        if (contains(path, "door", "trapdoor", "stairs", "slab", "fence", "gate", "sign")) return "JOIN";
        if (contains(path, "log", "wood", "planks", "bamboo")) return "CARP";
        if (safeTags.contains("#precious") || contains(path, "gold", "diamond", "emerald", "lapis", "amethyst")) return "GDSM";
        if (contains(path, "iron", "copper", "tin")) return "IRON";
        if (safeTags.contains("#building") || contains(path, "stone", "cobble", "deepslate", "sand", "gravel", "dirt")) return "MSON";
        if (contains(path, "string", "pattern", "cloth")) return "HABD";
        if (safeTags.contains("#food")) return "PEPR";
        if (safeTags.contains("#combat")) return "CUTL";
        if (safeTags.contains("#crafted")) return "HABD";
        return "STPL";
    }

    static Map<String, String> catalog() {
        return ITEM_TICKERS;
    }

    private static Map<String, String> loadItemTickers() {
        try (var stream = OnlineIssuerMapper.class.getResourceAsStream("/data/colonybridge/market/online-item-issuers.json")) {
            if (stream == null) throw new IllegalStateException("Royal Exchange item issuer catalog is missing.");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject items = root.getAsJsonObject("items");
            int expected = root.get("itemCount").getAsInt();
            Map<String, String> mapped = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : items.entrySet()) {
                String ticker = entry.getValue().getAsString();
                if (!MarketItemIds.isVanilla(entry.getKey()) || !TICKERS.contains(ticker)) {
                    throw new IllegalStateException("Royal Exchange item issuer catalog contains an invalid entry.");
                }
                mapped.put(entry.getKey(), ticker);
            }
            if (mapped.size() != expected) throw new IllegalStateException("Royal Exchange item issuer catalog count is invalid.");
            Set<String> usedTickers = Set.copyOf(mapped.values());
            if (!usedTickers.equals(TICKERS)) throw new IllegalStateException("Royal Exchange issuer coverage is invalid.");
            String checksum = checksum(mapped);
            if (!CATALOG_CHECKSUM.equals(checksum)) {
                throw new IllegalStateException("Royal Exchange item issuer catalog checksum is invalid: " + checksum);
            }
            return Map.copyOf(mapped);
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    static String checksum(Map<String, String> mapped) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        mapped.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '=');
            digest.update(entry.getValue().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
        });
        return HexFormat.of().withUpperCase().formatHex(digest.digest());
    }

    private static boolean contains(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
