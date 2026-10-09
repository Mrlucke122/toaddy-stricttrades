package me.mrlucke.toaddystricttrades.villagers.data;

import me.mrlucke.toaddystricttrades.villagers.models.VillagerTradeManager;
import org.bukkit.entity.Villager;

import java.util.Map;
import java.util.Objects;

public record LoadedConfiguration(
        TradeSettings settings,
        Map<Villager.Profession, VillagerTradeManager> tradeManagers
) {
    public LoadedConfiguration {
        Objects.requireNonNull(settings, "Settings cannot be null");
        Objects.requireNonNull(tradeManagers, "Trade managers cannot be null");

        tradeManagers = Map.copyOf(tradeManagers);
    }
}
