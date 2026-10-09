package me.mrlucke.toaddystricttrades.villagers.tools;

import me.mrlucke.toaddystricttrades.villagers.data.CustomTrade;
import me.mrlucke.toaddystricttrades.villagers.data.TradeLevel;
import me.mrlucke.toaddystricttrades.villagers.models.VillagerTradeManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TradeConfigLoader {

    public static final String TRADE_FOLDER = "trades";

    private static final List<String> PROFESSIONS = List.of(
            "ARMORER",
            "BUTCHER",
            "CARTOGRAPHER",
            "CLERIC",
            "FARMER",
            "FISHERMAN",
            "FLETCHER",
            "LEATHERWORKER",
            "LIBRARIAN",
            "MASON",
            "SHEPHERD",
            "TOOLSMITH",
            "WEAPONSMITH"
    );

    private final Plugin plugin;

    public TradeConfigLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public Map<Villager.Profession, VillagerTradeManager> load()
            throws IOException, InvalidConfigurationException {

        File tradeFolder = new File(
                plugin.getDataFolder(),
                TRADE_FOLDER
        );

        if (!tradeFolder.exists() && !tradeFolder.mkdirs()) {
            throw new IOException(
                    "Failed to create trade config directory: "
                            + tradeFolder.getAbsolutePath()
            );
        }

        Map<Villager.Profession, VillagerTradeManager> result =
                new HashMap<>();

        for (String professionName : PROFESSIONS) {
            Villager.Profession profession =
                    resolveProfession(professionName);

            String fileName = professionName + ".yml";
            File file = new File(tradeFolder, fileName);

            if (!file.exists()) {
                plugin.saveResource(
                        TRADE_FOLDER + "/" + fileName,
                        false
                );
            }

            if (!file.isFile()) {
                throw new IOException(
                        "Trade configuration is not a file: "
                                + file.getAbsolutePath()
                );
            }

            YamlConfiguration config = new YamlConfiguration();
            config.load(file);

            Map<Integer, TradeLevel> levels = parseLevels(
                    config,
                    fileName
            );

            result.put(
                    profession,
                    new VillagerTradeManager(levels)
            );
        }

        return Map.copyOf(result);
    }

    private static Villager.Profession resolveProfession(
            String professionName
    ) {
        NamespacedKey key = NamespacedKey.minecraft(
                professionName.toLowerCase(Locale.ROOT)
        );

        Villager.Profession profession =
                Registry.VILLAGER_PROFESSION.get(key);

        if (profession == null) {
            throw new IllegalStateException(
                    "Villager profession not found: minecraft:" + key.getKey()
            );
        }

        return profession;
    }

    private static Map<Integer, TradeLevel> parseLevels(
            YamlConfiguration config,
            String fileName
    ) {
        Map<Integer, TradeLevel> levels = new HashMap<>();
        Set<String> tradeIds = new HashSet<>();

        ConfigurationSection levelsSection =
                config.getConfigurationSection("levels");

        if (levelsSection == null) {
            return Map.of();
        }

        for (String levelKey : levelsSection.getKeys(false)) {
            ConfigurationSection levelSection =
                    requireLevelSection(
                            levelsSection,
                            levelKey,
                            fileName
                    );

            int levelNumber =
                    parseLevelNumber(levelKey, fileName);

            TradeLevel tradeLevel =
                    parseLevel(
                            levelSection,
                            levelNumber,
                            levelKey,
                            fileName,
                            tradeIds
                    );

            if (levels.put(levelNumber, tradeLevel) != null) {
                throw invalid(
                        fileName + ": levels." + levelKey,
                        "duplicate villager level: " + levelNumber
                );
            }
        }

        return Map.copyOf(levels);
    }

    private static ConfigurationSection requireLevelSection(
            ConfigurationSection levelsSection,
            String levelKey,
            String fileName
    ) {
        ConfigurationSection levelSection =
                levelsSection.getConfigurationSection(levelKey);

        if (levelSection == null) {
            throw invalid(
                    fileName + ": levels." + levelKey,
                    "level entry must be a configuration section"
            );
        }

        return levelSection;
    }

    private static TradeLevel parseLevel(
            ConfigurationSection levelSection,
            int levelNumber,
            String levelKey,
            String fileName,
            Set<String> tradeIds
    ) {
        String context =
                fileName + ": levels." + levelKey;

        int min = requireInt(
                levelSection,
                "random-trades-min",
                context
        );

        int max = requireInt(
                levelSection,
                "random-trades-max",
                context
        );

        List<CustomTrade> trades =
                parseTrades(
                        levelSection,
                        context,
                        tradeIds
                );

        try {
            return new TradeLevel(
                    levelNumber,
                    min,
                    max,
                    trades
            );
        } catch (IllegalArgumentException exception) {
            throw invalid(
                    context,
                    exception.getMessage(),
                    exception
            );
        }
    }

    private static List<CustomTrade> parseTrades(
            ConfigurationSection levelSection,
            String context,
            Set<String> tradeIds
    ) {
        List<?> rawTrades =
                levelSection.getList(TRADE_FOLDER);

        if (rawTrades == null) {
            return List.of();
        }

        List<CustomTrade> trades =
                new ArrayList<>(rawTrades.size());

        for (int index = 0; index < rawTrades.size(); index++) {
            trades.add(
                    parseTradeEntry(
                            rawTrades.get(index),
                            index,
                            context,
                            tradeIds
                    )
            );
        }

        return List.copyOf(trades);
    }

    private static CustomTrade parseTradeEntry(
            Object rawTrade,
            int index,
            String context,
            Set<String> tradeIds
    ) {
        String tradeContext =
                context + "." + TRADE_FOLDER + "[" + index + "]";

        if (!(rawTrade instanceof Map<?, ?> tradeMap)) {
            throw invalid(
                    tradeContext,
                    "trade entry must be a YAML map"
            );
        }

        CustomTrade trade =
                parseTrade(
                        tradeMap,
                        tradeContext
                );

        if (!tradeIds.add(trade.id())) {
            throw invalid(
                    tradeContext,
                    "duplicate trade ID: " + trade.id()
            );
        }

        return trade;
    }

    private static int parseLevelNumber(
            String raw,
            String fileName
    ) {
        try {
            int level = Integer.parseInt(raw);

            if (level < 1 || level > 5) {
                throw new NumberFormatException("out of range");
            }

            return level;
        } catch (NumberFormatException exception) {
            throw invalid(
                    fileName + ": levels." + raw,
                    "villager level must be an integer from 1 to 5",
                    exception
            );
        }
    }

    private static CustomTrade parseTrade(
            Map<?, ?> map,
            String context
    ) {
        String id =
                requireString(
                        map,
                        "id",
                        context
                );

        ItemStack cost1 =
                parseItem(
                        requireMap(
                                map,
                                "cost-1",
                                context
                        ),
                        context + ".cost-1"
                );

        ItemStack cost2 = null;

        if (map.containsKey("cost-2")) {
            cost2 =
                    parseItem(
                            requireMap(
                                    map,
                                    "cost-2",
                                    context
                            ),
                            context + ".cost-2"
                    );
        }

        ItemStack result =
                parseItem(
                        requireMap(
                                map,
                                "result",
                                context
                        ),
                        context + ".result"
                );

        Integer maxUses = null;

        if (map.containsKey("max-uses")) {
            maxUses =
                    requireInt(
                            map.get("max-uses"),
                            context + ".max-uses"
                    );
        }

        try {
            return new CustomTrade(
                    id,
                    cost1,
                    cost2,
                    result,
                    maxUses
            );
        } catch (IllegalArgumentException exception) {
            throw invalid(
                    context,
                    exception.getMessage(),
                    exception
            );
        }
    }

    private static ItemStack parseItem(
            Map<?, ?> map,
            String context
    ) {
        String materialName =
                requireString(
                        map,
                        "material",
                        context
                );

        Material material;

        try {
            material =
                    Material.valueOf(
                            materialName
                                    .trim()
                                    .toUpperCase(Locale.ROOT)
                    );
        } catch (IllegalArgumentException exception) {
            throw invalid(
                    context + ".material",
                    "unknown material: " + materialName,
                    exception
            );
        }

        int amount =
                requireInt(
                        map.get("amount"),
                        context + ".amount"
                );

        if (amount > material.getMaxStackSize()) {
            throw invalid(
                    context + ".amount",
                    "amount " + amount
                            + " exceeds maximum stack size "
                            + material.getMaxStackSize()
            );
        }

        try {
            return new ItemStack(
                    material,
                    amount
            );
        } catch (IllegalArgumentException exception) {
            throw invalid(
                    context,
                    "invalid item stack: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private static Map<?, ?> requireMap(
            Map<?, ?> parent,
            String key,
            String context
    ) {
        Object value = parent.get(key);

        if (!(value instanceof Map<?, ?> map)) {
            throw invalid(
                    context + "." + key,
                    "must be a YAML map"
            );
        }

        return map;
    }

    private static String requireString(
            Map<?, ?> parent,
            String key,
            String context
    ) {
        Object value = parent.get(key);

        if (!(value instanceof String string)
                || string.isBlank()) {
            throw invalid(
                    context + "." + key,
                    "must be a non-empty string"
            );
        }

        return string;
    }

    private static int requireInt(
            ConfigurationSection section,
            String key,
            String context
    ) {
        return requireInt(
                section.get(key),
                context + "." + key
        );
    }

    private static int requireInt(
            Object value,
            String context
    ) {
        if (!(value instanceof Number number)) {
            throw invalid(
                    context,
                    "must be an integer"
            );
        }

        double numericValue =
                number.doubleValue();

        if (!Double.isFinite(numericValue)
                || numericValue != Math.rint(numericValue)
                || numericValue < Integer.MIN_VALUE
                || numericValue > Integer.MAX_VALUE) {
            throw invalid(
                    context,
                    "must be an integer"
            );
        }

        return (int) numericValue;
    }

    private static IllegalArgumentException invalid(
            String context,
            String message
    ) {
        return new IllegalArgumentException(
                "Invalid trade configuration at "
                        + context
                        + ": "
                        + message
        );
    }

    private static IllegalArgumentException invalid(
            String context,
            String message,
            Throwable cause
    ) {
        return new IllegalArgumentException(
                "Invalid trade configuration at "
                        + context
                        + ": "
                        + message,
                cause
        );
    }
}