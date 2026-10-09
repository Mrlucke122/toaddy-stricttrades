package me.mrlucke.toaddystricttrades.villagers.data;

import org.bukkit.entity.Villager;

import java.util.Objects;
import java.util.Set;

public record TradeSettings(
        boolean mixVanillaTrades,
        ProfessionFilterMode professionMode,
        Set<Villager.Profession> professions,
        Set<Integer> allowedLevels,
        int maxVanillaTradesPerLevel,
        double vanillaTradeChance,
        boolean prioritizeCustom,
        float priceMultiplier,
        boolean disableExpReward,
        boolean disableDiscount
) {
    public TradeSettings {
        Objects.requireNonNull(professionMode, "Profession filter mode cannot be null");
        Objects.requireNonNull(professions, "Profession filter cannot be null");
        Objects.requireNonNull(allowedLevels, "Allowed levels cannot be null");

        if (maxVanillaTradesPerLevel < 0) {
            throw new IllegalArgumentException(
                    "max-vanilla-trades-per-level cannot be negative"
            );
        }

        if (!Double.isFinite(vanillaTradeChance)
                || vanillaTradeChance < 0.0
                || vanillaTradeChance > 1.0) {
            throw new IllegalArgumentException(
                    "vanilla-trade-chance must be between 0.0 and 1.0"
            );
        }

        if (!Float.isFinite(priceMultiplier) || priceMultiplier < 0.0f) {
            throw new IllegalArgumentException(
                    "price-multiplier must be a finite number >= 0.0"
            );
        }

        for (Integer level : allowedLevels) {
            if (level == null || level < 1 || level > 5) {
                throw new IllegalArgumentException(
                        "allowed-levels must contain only values from 1 to 5"
                );
            }
        }

        professions = Set.copyOf(professions);
        allowedLevels = Set.copyOf(allowedLevels);
    }

    public boolean allowsVanilla(
            Villager.Profession profession,
            int level
    ) {
        if (!mixVanillaTrades || !allowedLevels.contains(level)) {
            return false;
        }

        return switch (professionMode) {
            case OFF -> true;
            case WHITELIST -> professions.contains(profession);
            case BLACKLIST -> !professions.contains(profession);
        };
    }
}
