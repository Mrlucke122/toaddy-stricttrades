package me.mrlucke.toaddystricttrades.villagers.data;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public record CustomTrade(
        @NotNull String id,
        @NotNull ItemStack cost1,
        @Nullable ItemStack cost2,
        @NotNull ItemStack result,
        @Nullable Integer maxUses
) {
    public CustomTrade {
        Objects.requireNonNull(id, "Trade ID cannot be null");
        Objects.requireNonNull(cost1, "Cost1 cannot be null for trade: " + id);
        Objects.requireNonNull(result, "Result cannot be null for trade: " + id);

        if (id.isBlank()) {
            throw new IllegalArgumentException("Trade ID cannot be blank");
        }

        validateItem(cost1, "Cost1", id);

        if (cost2 != null) {
            validateItem(cost2, "Cost2", id);
        }

        validateItem(result, "Result", id);

        if (maxUses != null && maxUses <= 0) {
            throw new IllegalArgumentException(
                    "Max uses must be greater than 0 in trade: " + id
            );
        }

        cost1 = cost1.clone();
        cost2 = cost2 != null ? cost2.clone() : null;
        result = result.clone();
    }

    private static void validateItem(
            ItemStack item,
            String field,
            String tradeId
    ) {
        if (item.getType().isAir()) {
            throw new IllegalArgumentException(
                    field + " must not be air in trade: " + tradeId
            );
        }

        int amount = item.getAmount();
        int maxStackSize = item.getType().getMaxStackSize();

        if (amount <= 0 || amount > maxStackSize) {
            throw new IllegalArgumentException(
                    field + " amount must be between 1 and "
                            + maxStackSize + " in trade: " + tradeId
            );
        }
    }

    @Override
    public ItemStack cost1() {
        return cost1.clone();
    }

    @Override
    public @Nullable ItemStack cost2() {
        return cost2 != null ? cost2.clone() : null;
    }

    @Override
    public ItemStack result() {
        return result.clone();
    }
}
