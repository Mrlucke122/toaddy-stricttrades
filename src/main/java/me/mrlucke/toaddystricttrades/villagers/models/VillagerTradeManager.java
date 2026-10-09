package me.mrlucke.toaddystricttrades.villagers.models;

import me.mrlucke.toaddystricttrades.villagers.data.TradeLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public record VillagerTradeManager(Map<Integer, TradeLevel> tradeLevels) {

    public VillagerTradeManager(Map<Integer, TradeLevel> tradeLevels) {
        Objects.requireNonNull(tradeLevels, "Trade levels cannot be null");
        this.tradeLevels = Map.copyOf(tradeLevels);
    }

    public @Nullable TradeLevel getLevel(int level) {
        return tradeLevels.get(level);
    }
}
