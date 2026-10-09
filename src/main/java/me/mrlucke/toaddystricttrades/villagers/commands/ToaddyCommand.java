package me.mrlucke.toaddystricttrades.villagers.commands;

import me.mrlucke.toaddystricttrades.ToaddyStrictTrades;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class ToaddyCommand
        implements CommandExecutor, TabCompleter {

    private static final String PERMISSION =
            "toaddystricttrades.admin";

    private static final String USAGE =
            "§eUsage: /toaddy StrictTrades reload";

    private final ToaddyStrictTrades plugin;

    public ToaddyCommand(ToaddyStrictTrades plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length != 2
                || !args[0].equalsIgnoreCase("StrictTrades")
                || !args[1].equalsIgnoreCase("reload")) {
            sender.sendMessage(USAGE);
            return true;
        }

        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(
                    "§cYou don't have permission to do this."
            );
            return true;
        }

        sender.sendMessage(
                "§7Reloading ToaddyStrictTrades configuration..."
        );

        boolean success =
                plugin.reloadConfiguration();

        if (success) {
            sender.sendMessage(
                    "§aToaddyStrictTrades configuration reloaded successfully."
            );
        } else {
            sender.sendMessage(
                    "§cFailed to reload ToaddyStrictTrades configuration."
            );

            sender.sendMessage(
                    "§7Check the server console for details."
            );
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            return List.of("StrictTrades");
        }

        if (args.length == 2
                && args[0].equalsIgnoreCase("StrictTrades")) {
            return List.of("reload");
        }

        return List.of();
    }
}