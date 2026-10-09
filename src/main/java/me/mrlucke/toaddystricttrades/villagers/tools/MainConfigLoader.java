package me.mrlucke.toaddystricttrades.villagers.tools;

import me.mrlucke.toaddystricttrades.villagers.data.ProfessionFilterMode;
import me.mrlucke.toaddystricttrades.villagers.data.TradeSettings;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MainConfigLoader {

    public static final String CONFIG_FILE = "config.yml";

    private final Plugin plugin;

    public MainConfigLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public TradeSettings load()
            throws IOException, InvalidConfigurationException {

        File file = new File(
                plugin.getDataFolder(),
                CONFIG_FILE
        );

        if (!file.isFile()) {
            throw new IOException(
                    "Missing configuration file: "
                            + file.getAbsolutePath()
            );
        }

        YamlConfiguration config = new YamlConfiguration();
        config.load(file);

        return parse(config);
    }

    private static TradeSettings parse(
            YamlConfiguration config
    ) {
        ConfigurationSection trades =
                requireSection(
                        config,
                        "trades"
                );

        ConfigurationSection mixVanillaSettings =
                requireSection(
                        trades,
                        "mix-vanilla-settings"
                );

        ConfigurationSection professions =
                requireSection(
                        mixVanillaSettings,
                        "professions"
                );

        ConfigurationSection limits =
                requireSection(
                        mixVanillaSettings,
                        "limits"
                );

        ConfigurationSection modifiers =
                requireSection(
                        mixVanillaSettings,
                        "modifiers"
                );

        boolean mixVanillaTrades =
                requireBoolean(
                        trades,
                        "mix-vanilla-trades"
                );

        ProfessionFilterMode mode =
                parseMode(
                        professions.get("mode"),
                        "trades.mix-vanilla-settings.professions.mode"
                );

        Set<Villager.Profession> professionSet =
                parseProfessions(
                        professions.getList("list"),
                        "trades.mix-vanilla-settings.professions.list"
                );

        Set<Integer> allowedLevels =
                parseIntegerSet(
                        mixVanillaSettings.getList("allowed-levels"),
                        "trades.mix-vanilla-settings.allowed-levels"
                );

        int maxVanillaTradesPerLevel =
                requireInt(
                        limits,
                        "max-vanilla-trades-per-level"
                );

        double vanillaTradeChance =
                requireNumber(
                        limits,
                        "vanilla-trade-chance"
                ).doubleValue();

        validateVanillaTradeChance(vanillaTradeChance);

        boolean prioritizeCustom =
                requireBoolean(
                        limits,
                        "prioritize-custom"
                );

        float priceMultiplier =
                requireNumber(
                        modifiers,
                        "price-multiplier"
                ).floatValue();

        if (!Float.isFinite(priceMultiplier)
                || priceMultiplier < 0.0F) {
            throw new IllegalArgumentException(
                    "Configuration value '"
                            + modifiers.getCurrentPath()
                            + ".price-multiplier' must be a finite number >= 0"
            );
        }

        boolean disableExpReward =
                requireBoolean(
                        modifiers,
                        "disable-exp-reward"
                );

        boolean disableDiscount =
                requireBoolean(
                        modifiers,
                        "disable-discount"
                );

        return new TradeSettings(
                mixVanillaTrades,
                mode,
                professionSet,
                allowedLevels,
                maxVanillaTradesPerLevel,
                vanillaTradeChance,
                prioritizeCustom,
                priceMultiplier,
                disableExpReward,
                disableDiscount
        );
    }

    private static ProfessionFilterMode parseMode(
            Object raw,
            String path
    ) {
        String modeName = switch (raw) {
            case Boolean booleanValue -> {
                if (!booleanValue) {
                    yield "OFF";
                }

                throw invalid(
                        path,
                        "must be OFF, BLACKLIST or WHITELIST"
                );
            }

            case String string when !string.isBlank() ->
                    string.trim();

            default -> throw invalid(
                    path,
                    "must be OFF, BLACKLIST or WHITELIST"
            );
        };

        try {
            return ProfessionFilterMode.valueOf(
                    modeName.toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw invalid(
                    path,
                    "must be OFF, BLACKLIST or WHITELIST",
                    exception
            );
        }
    }

    private static Set<Villager.Profession> parseProfessions(
            List<?> rawList,
            String path
    ) {
        if (rawList == null) {
            throw invalid(
                    path,
                    "configuration list is missing"
            );
        }

        Set<Villager.Profession> result =
                new HashSet<>();

        for (Object raw : rawList) {
            if (!(raw instanceof String value)
                    || value.isBlank()) {
                throw invalid(
                        path,
                        "every value must be a profession name"
                );
            }

            String normalized =
                    value.trim().toLowerCase(Locale.ROOT);

            NamespacedKey key =
                    NamespacedKey.minecraft(normalized);

            Villager.Profession profession =
                    Registry.VILLAGER_PROFESSION.get(key);

            if (profession == null) {
                throw invalid(
                        path,
                        "unknown villager profession '"
                                + value
                                + "'"
                );
            }

            result.add(profession);
        }

        return Set.copyOf(result);
    }

    private static Set<Integer> parseIntegerSet(
            List<?> rawList,
            String path
    ) {
        if (rawList == null) {
            throw invalid(
                    path,
                    "configuration list is missing"
            );
        }

        Set<Integer> result =
                new HashSet<>();

        for (Object raw : rawList) {
            if (!(raw instanceof Number number)) {
                throw invalid(
                        path,
                        "every value must be an integer"
                );
            }

            result.add(
                    numberToInt(
                            number,
                            path
                    )
            );
        }

        return Set.copyOf(result);
    }

    private static ConfigurationSection requireSection(
            ConfigurationSection parent,
            String path
    ) {
        ConfigurationSection section =
                parent.getConfigurationSection(path);

        if (section == null) {
            throw invalid(
                    buildPath(parent, path),
                    "configuration section is missing"
            );
        }

        return section;
    }

    private static String requireString(
            ConfigurationSection section,
            String path
    ) {
        Object value =
                section.get(path);

        if (!(value instanceof String string)
                || string.isBlank()) {
            throw invalid(
                    buildPath(section, path),
                    "must be a non-empty string"
            );
        }

        return string;
    }

    private static boolean requireBoolean(
            ConfigurationSection section,
            String path
    ) {
        Object value =
                section.get(path);

        if (!(value instanceof Boolean booleanValue)) {
            throw invalid(
                    buildPath(section, path),
                    "must be true or false"
            );
        }

        return booleanValue;
    }

    private static Number requireNumber(
            ConfigurationSection section,
            String path
    ) {
        Object value =
                section.get(path);

        if (!(value instanceof Number number)) {
            throw invalid(
                    buildPath(section, path),
                    "must be a number"
            );
        }

        return number;
    }

    private static int requireInt(
            ConfigurationSection section,
            String path
    ) {
        return numberToInt(
                requireNumber(
                        section,
                        path
                ),
                buildPath(section, path)
        );
    }

    private static int numberToInt(
            Number number,
            String path
    ) {
        double value =
                number.doubleValue();

        if (!Double.isFinite(value)
                || value != Math.rint(value)
                || value < Integer.MIN_VALUE
                || value > Integer.MAX_VALUE) {
            throw invalid(
                    path,
                    "must be an integer"
            );
        }

        return (int) value;
    }

    private static void validateVanillaTradeChance(
            double value
    ) {
        if (!Double.isFinite(value)
                || value < 0.0
                || value > 1.0) {
            throw new IllegalArgumentException(
                    "Configuration value "
                            + "'trades.mix-vanilla-settings.limits."
                            + "vanilla-trade-chance' "
                            + "must be between 0.0 and 1.0"
            );
        }
    }

    private static String buildPath(
            ConfigurationSection section,
            String path
    ) {
        String currentPath =
                section.getCurrentPath();

        if (currentPath == null || currentPath.isBlank()) {
            return path;
        }

        return currentPath + "." + path;
    }

    private static IllegalArgumentException invalid(
            String path,
            String message
    ) {
        return new IllegalArgumentException(
                "Configuration value '"
                        + path
                        + "' "
                        + message
        );
    }

    private static IllegalArgumentException invalid(
            String path,
            String message,
            Throwable cause
    ) {
        return new IllegalArgumentException(
                "Configuration value '"
                        + path
                        + "' "
                        + message,
                cause
        );
    }
}