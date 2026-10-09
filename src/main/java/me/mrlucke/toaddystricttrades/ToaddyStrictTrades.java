package me.mrlucke.toaddystricttrades;

import me.mrlucke.toaddystricttrades.villagers.commands.ToaddyCommand;
import me.mrlucke.toaddystricttrades.villagers.data.LoadedConfiguration;
import me.mrlucke.toaddystricttrades.villagers.data.TradeSettings;
import me.mrlucke.toaddystricttrades.villagers.listeners.VillagerTradeListener;
import me.mrlucke.toaddystricttrades.villagers.models.VillagerTradeManager;
import me.mrlucke.toaddystricttrades.villagers.tools.MainConfigLoader;
import me.mrlucke.toaddystricttrades.villagers.tools.TradeConfigLoader;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public final class ToaddyStrictTrades extends JavaPlugin {

    private LoadedConfiguration configuration;
    private VillagerTradeListener villagerTradeListener;

    @Override
    public void onEnable() {

        saveDefaultConfig();

        try {
            configuration = loadConfiguration();
        } catch (IOException
                 | InvalidConfigurationException
                 | IllegalArgumentException exception) {

            getLogger().severe(
                    "Failed to load ToaddyStrictTrades configuration."
            );
            getLogger().severe(
                    exception.getMessage()
            );

            getServer()
                    .getPluginManager()
                    .disablePlugin(this);

            return;
        }

        TradeSettings settings =
                configuration.settings();

        Map<Villager.Profession, VillagerTradeManager> managers =
                configuration.tradeManagers();

        villagerTradeListener =
                new VillagerTradeListener(
                        this,
                        settings,
                        managers
                );

        getServer()
                .getPluginManager()
                .registerEvents(
                        villagerTradeListener,
                        this
                );

        registerCommand();

        villagerTradeListener.reloadLoadedVillagers();

        getLogger().info(
                "Loaded "
                        + managers.size()
                        + " villager trade managers."
        );
    }

    private void registerCommand() {
        ToaddyCommand commandHandler =
                new ToaddyCommand(this);

        if (getCommand("toaddy") == null) {
            getLogger().severe(
                    "Command 'toaddy' is not defined in plugin.yml."
            );

            getServer()
                    .getPluginManager()
                    .disablePlugin(this);

            return;
        }

        Objects.requireNonNull(getCommand("toaddy")).setExecutor(commandHandler);
        Objects.requireNonNull(getCommand("toaddy")).setTabCompleter(commandHandler);
    }

    private LoadedConfiguration loadConfiguration()
            throws IOException, InvalidConfigurationException {

        TradeSettings settings =
                new MainConfigLoader(this).load();

        Map<Villager.Profession, VillagerTradeManager> managers =
                new TradeConfigLoader(this).load();

        return new LoadedConfiguration(
                settings,
                managers
        );
    }

    public boolean reloadConfiguration() {
        try {
            LoadedConfiguration newConfiguration =
                    loadConfiguration();

            configuration =
                    newConfiguration;

            villagerTradeListener.updateConfiguration(
                    newConfiguration.settings(),
                    newConfiguration.tradeManagers()
            );

            villagerTradeListener.reloadLoadedVillagers();

            getLogger().info(
                    "ToaddyStrictTrades configuration "
                            + "reloaded successfully."
            );

            return true;

        } catch (IOException
                 | InvalidConfigurationException
                 | IllegalArgumentException exception) {

            getLogger().severe(
                    "Failed to reload ToaddyStrictTrades configuration."
            );
            getLogger().severe(
                    exception.getMessage()
            );

            return false;
        }
    }

    public VillagerTradeManager getTradeManager(
            Villager.Profession profession
    ) {
        return configuration.tradeManagers()
                .get(profession);
    }

    public TradeSettings getTradeSettings() {
        return configuration.settings();
    }

    public VillagerTradeListener getVillagerTradeListener() {
        return villagerTradeListener;
    }
}