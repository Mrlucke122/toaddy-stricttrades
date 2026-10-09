package me.mrlucke.toaddystricttrades.villagers.listeners;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import io.papermc.paper.event.player.PlayerTradeEvent;
import me.mrlucke.toaddystricttrades.villagers.data.CustomTrade;
import me.mrlucke.toaddystricttrades.villagers.data.TradeLevel;
import me.mrlucke.toaddystricttrades.villagers.data.TradeSettings;
import me.mrlucke.toaddystricttrades.villagers.models.VillagerTradeManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class VillagerTradeListener implements Listener {

    private static final int DEFAULT_MAX_USES = 12;

    private final JavaPlugin plugin;

    private volatile TradeSettings settings;
    private volatile Map<Villager.Profession, VillagerTradeManager> managers;

    private final NamespacedKey generatedLevelKey;
    private final NamespacedKey customCountKey;
    private final NamespacedKey customFirstKey;
    private final NamespacedKey vanillaLevelKey;
    private final NamespacedKey vanillaCountKey;

    private final Map<UUID, Boolean> scheduledReconciliations = new HashMap<>();

    public VillagerTradeListener(
            JavaPlugin plugin,
            TradeSettings settings,
            Map<Villager.Profession, VillagerTradeManager> managers
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.managers = Map.copyOf(managers);

        this.generatedLevelKey = new NamespacedKey(
                plugin,
                "generated_trade_level"
        );

        this.customCountKey = new NamespacedKey(
                plugin,
                "generated_custom_trade_count"
        );

        this.customFirstKey = new NamespacedKey(
                plugin,
                "generated_custom_first"
        );

        this.vanillaLevelKey = new NamespacedKey(
                plugin,
                "vanilla_trade_level"
        );

        this.vanillaCountKey = new NamespacedKey(
                plugin,
                "vanilla_trade_count"
        );
    }

    public void updateConfiguration(
            TradeSettings settings,
            Map<Villager.Profession, VillagerTradeManager> managers
    ) {
        this.settings = settings;
        this.managers = Map.copyOf(managers);
    }

    @EventHandler
    public void onEntityAddToWorld(EntityAddToWorldEvent event) {
        Entity entity = event.getEntity();

        if (!(entity instanceof Villager villager)) {
            return;
        }

        scheduleReconcile(villager, false);
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onCareerChange(VillagerCareerChangeEvent event) {
        Villager villager = event.getEntity();

        clearGeneratedState(villager);
        clearVanillaState(villager);

        scheduleReconcile(villager, false);
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onAcquireTrade(VillagerAcquireTradeEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        TradeSettings currentSettings = settings;
        int level = villager.getVillagerLevel();

        if (!currentSettings.allowsVanilla(
                villager.getProfession(),
                level
        )) {
            event.setCancelled(true);
            return;
        }

        prepareVanillaCounter(
                villager,
                level
        );

        int currentVanillaCount = getInt(
                villager,
                vanillaCountKey,
                0
        );

        if (currentVanillaCount
                >= currentSettings.maxVanillaTradesPerLevel()) {
            event.setCancelled(true);
            return;
        }

        if (!rollChance(
                currentSettings.vanillaTradeChance()
        )) {
            event.setCancelled(true);
            return;
        }

        MerchantRecipe recipe =
                new MerchantRecipe(event.getRecipe());

        applyVanillaModifiers(
                recipe,
                currentSettings
        );

        event.setRecipe(recipe);

        setInt(
                villager,
                vanillaCountKey,
                currentVanillaCount + 1
        );

        scheduleReconcile(villager, true);
    }

    @EventHandler(
            priority = EventPriority.NORMAL,
            ignoreCancelled = true
    )
    public void onPlayerTrade(PlayerTradeEvent event) {
        if (!(event.getMerchant() instanceof Villager villager)) {
            return;
        }

        if (getGeneratedLevel(villager)
                != villager.getVillagerLevel()) {
            scheduleReconcile(villager, true);
        }
    }

    public void applyTrades(Villager villager) {
        synchronize(
                villager,
                true,
                false
        );
    }

    public void reloadLoadedVillagers() {
        for (var world : Bukkit.getWorlds()) {
            for (Villager villager :
                    world.getEntitiesByClass(Villager.class)) {

                synchronize(
                        villager,
                        false,
                        true
                );
            }
        }
    }

    private void scheduleReconcile(
            Villager villager,
            boolean reconcileExistingState
    ) {
        UUID uuid = villager.getUniqueId();

        Boolean alreadyScheduled =
                scheduledReconciliations.putIfAbsent(
                        uuid,
                        reconcileExistingState
                );

        if (alreadyScheduled != null) {
            if (reconcileExistingState && !alreadyScheduled) {
                scheduledReconciliations.put(
                        uuid,
                        true
                );
            }

            return;
        }

        Bukkit.getScheduler().runTask(
                plugin,
                () -> {
                    boolean reconcile =
                            Boolean.TRUE.equals(
                                    scheduledReconciliations.remove(uuid)
                            );

                    if (!villager.isValid()) {
                        return;
                    }

                    synchronize(
                            villager,
                            false,
                            reconcile
                    );
                }
        );
    }

    private void synchronize(
            Villager villager,
            boolean forceFullCustomRebuild,
            boolean reconcileExistingState
    ) {
        TradeSettings currentSettings = settings;

        int currentLevel =
                villager.getVillagerLevel();

        int generatedLevel =
                getGeneratedLevel(villager);

        if (!forceFullCustomRebuild
                && !reconcileExistingState
                && generatedLevel == currentLevel) {
            return;
        }

        VillagerTradeManager manager =
                managers.get(
                        villager.getProfession()
                );

        if (manager == null) {
            villager.setRecipes(List.of());

            clearGeneratedState(villager);
            clearVanillaState(villager);

            return;
        }

        List<MerchantRecipe> existingRecipes =
                cloneRecipes(
                        villager.getRecipes()
                );

        int storedCustomCount =
                Math.max(
                        0,
                        getInt(
                                villager,
                                customCountKey,
                                0
                        )
                );

        boolean storedCustomFirst =
                getBoolean(
                        villager,
                        customFirstKey,
                        true
                );

        int currentVanillaCount =
                getCurrentVanillaCount(
                        villager,
                        currentLevel
                );

        RecipeGroups groups =
                splitRecipes(
                        existingRecipes,
                        storedCustomCount,
                        storedCustomFirst,
                        currentVanillaCount
                );

        List<MerchantRecipe> customRecipes;

        if (forceFullCustomRebuild
                || generatedLevel < 0) {

            customRecipes =
                    buildCustomRecipesUpToLevel(
                            manager,
                            currentLevel
                    );

        } else {

            customRecipes =
                    new ArrayList<>(
                            groups.custom()
                    );


            if (generatedLevel < currentLevel) {
                customRecipes.addAll(
                        buildCustomRecipesBetweenLevels(
                                manager,
                                generatedLevel + 1,
                                currentLevel
                        )
                );
            }
        }

        List<MerchantRecipe> vanillaRecipes =
                new ArrayList<>(
                        groups.vanilla()
                );

        if (!currentSettings.mixVanillaTrades()) {

            vanillaRecipes.clear();

            setVanillaCounter(
                    villager,
                    currentLevel,
                    0
            );

        } else {

            for (
                    int index = 0;
                    index < vanillaRecipes.size();
                    index++
            ) {
                MerchantRecipe recipe =
                        vanillaRecipes.get(index);

                applyVanillaModifiers(
                        recipe,
                        currentSettings
                );

                vanillaRecipes.set(
                        index,
                        recipe
                );
            }
        }

        List<MerchantRecipe> finalRecipes =
                mergeRecipes(
                        customRecipes,
                        vanillaRecipes,
                        currentSettings.prioritizeCustom()
                );

        villager.setRecipes(finalRecipes);

        setInt(
                villager,
                generatedLevelKey,
                currentLevel
        );

        setInt(
                villager,
                customCountKey,
                customRecipes.size()
        );

        setBoolean(
                villager,
                customFirstKey,
                currentSettings.prioritizeCustom()
        );

        ensureVanillaLevelMarker(
                villager,
                currentLevel
        );
    }

    private List<MerchantRecipe> buildCustomRecipesUpToLevel(
            VillagerTradeManager manager,
            int currentLevel
    ) {
        List<MerchantRecipe> result =
                new ArrayList<>();

        for (
                int level = 1;
                level <= currentLevel;
                level++
        ) {
            result.addAll(
                    buildCustomRecipesForLevel(
                            manager,
                            level
                    )
            );
        }

        return result;
    }

    private List<MerchantRecipe> buildCustomRecipesBetweenLevels(
            VillagerTradeManager manager,
            int firstLevel,
            int lastLevel
    ) {
        List<MerchantRecipe> result =
                new ArrayList<>();

        for (
                int level = firstLevel;
                level <= lastLevel;
                level++
        ) {
            result.addAll(
                    buildCustomRecipesForLevel(
                            manager,
                            level
                    )
            );
        }

        return result;
    }

    private List<MerchantRecipe> buildCustomRecipesForLevel(
            VillagerTradeManager manager,
            int levelNumber
    ) {
        TradeLevel level =
                manager.getLevel(levelNumber);

        if (level == null
                || level.trades().isEmpty()) {
            return List.of();
        }

        List<CustomTrade> selectedTrades =
                selectTrades(level);

        List<MerchantRecipe> recipes =
                new ArrayList<>(
                        selectedTrades.size()
                );

        for (CustomTrade trade : selectedTrades) {
            recipes.add(
                    createCustomRecipe(trade)
            );
        }

        return recipes;
    }

    private List<CustomTrade> selectTrades(
            TradeLevel level
    ) {
        List<CustomTrade> available =
                new ArrayList<>(
                        level.trades()
                );

        if (available.isEmpty()) {
            return List.of();
        }

        int max =
                Math.min(
                        level.randomTradesMax(),
                        available.size()
                );

        int min =
                Math.min(
                        Math.max(
                                level.randomTradesMin(),
                                0
                        ),
                        max
                );

        int amount;

        if (min == max) {
            amount = min;
        } else {
            amount =
                    ThreadLocalRandom.current()
                            .nextInt(
                                    min,
                                    max + 1
                            );
        }

        ThreadLocalRandom random =
                ThreadLocalRandom.current();

        for (
                int index = available.size() - 1;
                index > 0;
                index--
        ) {
            int swapIndex =
                    random.nextInt(index + 1);

            CustomTrade current =
                    available.get(index);

            available.set(
                    index,
                    available.get(swapIndex)
            );

            available.set(
                    swapIndex,
                    current
            );
        }

        return new ArrayList<>(
                available.subList(
                        0,
                        amount
                )
        );
    }

    private MerchantRecipe createCustomRecipe(
            CustomTrade trade
    ) {
        int maxUses =
                trade.maxUses() != null
                        ? trade.maxUses()
                        : DEFAULT_MAX_USES;

        MerchantRecipe recipe =
                new MerchantRecipe(
                        trade.result(),
                        0,
                        maxUses,
                        true
                );

        recipe.setPriceMultiplier(
                0.0F
        );

        recipe.setIgnoreDiscounts(
                true
        );

        List<ItemStack> ingredients =
                new ArrayList<>(2);

        ingredients.add(
                trade.cost1()
        );

        if (trade.cost2() != null) {
            ingredients.add(
                    trade.cost2()
            );
        }

        recipe.setIngredients(
                ingredients
        );

        return recipe;
    }

    private static void applyVanillaModifiers(
            MerchantRecipe recipe,
            TradeSettings settings
    ) {
        recipe.setPriceMultiplier(
                settings.priceMultiplier()
        );

        if (settings.disableExpReward()) {
            recipe.setExperienceReward(false);
        }

        recipe.setIgnoreDiscounts(
                settings.disableDiscount()
        );
    }

    private RecipeGroups splitRecipes(
            List<MerchantRecipe> recipes,
            int customCount,
            boolean customFirst,
            int currentVanillaCount
    ) {
        if (recipes.isEmpty()
                || customCount <= 0) {
            return new RecipeGroups(
                    List.of(),
                    recipes
            );
        }

        int safeCustomCount =
                Math.min(
                        customCount,
                        recipes.size()
                );

        if (customFirst) {
            return new RecipeGroups(
                    new ArrayList<>(
                            recipes.subList(
                                    0,
                                    safeCustomCount
                            )
                    ),
                    new ArrayList<>(
                            recipes.subList(
                                    safeCustomCount,
                                    recipes.size()
                            )
                    )
            );
        }

        int maxVanillaCount =
                Math.max(
                        0,
                        recipes.size()
                                - safeCustomCount
                );

        int safeCurrentVanillaCount =
                Math.clamp(
                        currentVanillaCount,
                        0,
                        maxVanillaCount
                );

        int currentVanillaStart =
                recipes.size()
                        - safeCurrentVanillaCount;

        int customStart =
                Math.max(
                        0,
                        currentVanillaStart
                                - safeCustomCount
                );

        int customEnd =
                Math.min(
                        recipes.size(),
                        customStart
                                + safeCustomCount
                );

        List<MerchantRecipe> custom =
                new ArrayList<>(
                        recipes.subList(
                                customStart,
                                customEnd
                        )
                );

        List<MerchantRecipe> vanilla =
                new ArrayList<>();

        vanilla.addAll(
                recipes.subList(
                        0,
                        customStart
                )
        );

        vanilla.addAll(
                recipes.subList(
                        customEnd,
                        recipes.size()
                )
        );

        return new RecipeGroups(
                custom,
                vanilla
        );
    }

    private static List<MerchantRecipe> mergeRecipes(
            List<MerchantRecipe> custom,
            List<MerchantRecipe> vanilla,
            boolean prioritizeCustom
    ) {
        List<MerchantRecipe> result =
                new ArrayList<>(
                        custom.size()
                                + vanilla.size()
                );

        if (prioritizeCustom) {
            result.addAll(custom);
            result.addAll(vanilla);
        } else {
            result.addAll(vanilla);
            result.addAll(custom);
        }

        return result;
    }

    private static List<MerchantRecipe> cloneRecipes(
            List<MerchantRecipe> recipes
    ) {
        List<MerchantRecipe> result =
                new ArrayList<>(
                        recipes.size()
                );

        for (MerchantRecipe recipe : recipes) {
            result.add(
                    new MerchantRecipe(recipe)
            );
        }

        return result;
    }

    private static boolean rollChance(
            double chance
    ) {
        if (chance <= 0.0) {
            return false;
        }

        if (chance >= 1.0) {
            return true;
        }

        return ThreadLocalRandom.current()
                .nextDouble()
                < chance;
    }

    private void ensureVanillaLevelMarker(
            Villager villager,
            int currentLevel
    ) {
        Integer storedLevel =
                villager.getPersistentDataContainer().get(
                        vanillaLevelKey,
                        PersistentDataType.INTEGER
                );

        if (storedLevel == null
                || storedLevel != currentLevel) {
            setVanillaCounter(
                    villager,
                    currentLevel,
                    0
            );
        }
    }

    private int getCurrentVanillaCount(
            Villager villager,
            int currentLevel
    ) {
        PersistentDataContainer container =
                villager.getPersistentDataContainer();

        Integer storedLevel =
                container.get(
                        vanillaLevelKey,
                        PersistentDataType.INTEGER
                );

        if (storedLevel == null
                || storedLevel != currentLevel) {
            return 0;
        }

        Integer count =
                container.get(
                        vanillaCountKey,
                        PersistentDataType.INTEGER
                );

        return count != null
                ? Math.max(0, count)
                : 0;
    }

    private void prepareVanillaCounter(
            Villager villager,
            int currentLevel
    ) {
        Integer storedLevel =
                villager.getPersistentDataContainer().get(
                        vanillaLevelKey,
                        PersistentDataType.INTEGER
                );

        if (storedLevel == null
                || storedLevel != currentLevel) {
            setVanillaCounter(
                    villager,
                    currentLevel,
                    0
            );
        }
    }

    private void setVanillaCounter(
            Villager villager,
            int level,
            int count
    ) {
        PersistentDataContainer container =
                villager.getPersistentDataContainer();

        container.set(
                vanillaLevelKey,
                PersistentDataType.INTEGER,
                level
        );

        container.set(
                vanillaCountKey,
                PersistentDataType.INTEGER,
                count
        );
    }

    private int getGeneratedLevel(
            Villager villager
    ) {
        return getInt(
                villager,
                generatedLevelKey,
                -1
        );
    }

    private static int getInt(
            Villager villager,
            NamespacedKey key,
            int fallback
    ) {
        Integer value =
                villager.getPersistentDataContainer().get(
                        key,
                        PersistentDataType.INTEGER
                );

        return value != null
                ? value
                : fallback;
    }

    private static boolean getBoolean(
            Villager villager,
            NamespacedKey key,
            boolean fallback
    ) {
        Byte value =
                villager.getPersistentDataContainer().get(
                        key,
                        PersistentDataType.BYTE
                );

        return value != null
                ? value != 0
                : fallback;
    }

    private static void setInt(
            Villager villager,
            NamespacedKey key,
            int value
    ) {
        villager.getPersistentDataContainer().set(
                key,
                PersistentDataType.INTEGER,
                value
        );
    }

    private static void setBoolean(
            Villager villager,
            NamespacedKey key,
            boolean value
    ) {
        villager.getPersistentDataContainer().set(
                key,
                PersistentDataType.BYTE,
                (byte) (value ? 1 : 0)
        );
    }

    private void clearGeneratedState(
            Villager villager
    ) {
        PersistentDataContainer container =
                villager.getPersistentDataContainer();

        container.remove(
                generatedLevelKey
        );

        container.remove(
                customCountKey
        );

        container.remove(
                customFirstKey
        );
    }

    private void clearVanillaState(
            Villager villager
    ) {
        PersistentDataContainer container =
                villager.getPersistentDataContainer();

        container.remove(
                vanillaLevelKey
        );

        container.remove(
                vanillaCountKey
        );
    }

    private record RecipeGroups(
            List<MerchantRecipe> custom,
            List<MerchantRecipe> vanilla
    ) {
    }
}