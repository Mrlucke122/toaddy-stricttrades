package me.mrlucke.toaddystricttrades.villagers.data;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record TradeLevel(
        int levelNumber,
        int randomTradesMin,
        int randomTradesMax,
        @NotNull List<CustomTrade> trades
) {
    public TradeLevel {
        Objects.requireNonNull(
                trades,
                "Trades list cannot be null for level: " + levelNumber
        );

        if (levelNumber < 1 || levelNumber > 5) {
            throw new IllegalArgumentException(
                    "Level number must be between 1 and 5: " + levelNumber
            );
        }

        if (randomTradesMin < 0 || randomTradesMax < 0) {
            throw new IllegalArgumentException(
                    "Trade limits cannot be negative for level " + levelNumber
            );
        }

        if (randomTradesMax < randomTradesMin) {
            throw new IllegalArgumentException(
                    "Invalid trade limits for level " + levelNumber
                            + ": min=" + randomTradesMin
                            + ", max=" + randomTradesMax
            );
        }

        List<CustomTrade> copiedTrades = List.copyOf(trades);

        if (randomTradesMax > copiedTrades.size()) {
            throw new IllegalArgumentException(
                    "random-trades-max=" + randomTradesMax
                            + " exceeds available trades ("
                            + copiedTrades.size()
                            + ") for level " + levelNumber
            );
        }

        Set<String> ids = new HashSet<>();

        for (CustomTrade trade : copiedTrades) {
            if (!ids.add(trade.id())) {
                throw new IllegalArgumentException(
                        "Duplicate trade ID '" + trade.id()
                                + "' in level " + levelNumber
                );
            }
        }

        trades = copiedTrades;
    }
}
