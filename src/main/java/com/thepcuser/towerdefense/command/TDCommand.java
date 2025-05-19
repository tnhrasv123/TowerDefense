package com.thepcuser.towerdefense.command;

import com.thepcuser.towerdefense.TowerDefense;
import com.thepcuser.towerdefense.command.subcommands.ReloadCommand; // Example subcommand
import com.thepcuser.towerdefense.command.subcommands.ArenaCommand;
// Import other subcommands here as they are created

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Main command handler for the TowerDefense plugin.
 * Delegates execution and tab completion to registered subcommands.
 */
public class TDCommand implements CommandExecutor, TabCompleter {

    private final TowerDefense plugin;
    private final Map<String, CommandBase> subCommands = new HashMap<>();

    /**
     * Constructor for TDCommand.
     *
     * @param plugin The main plugin instance.
     */
    public TDCommand(TowerDefense plugin) {
        this.plugin = plugin;
        registerSubCommands();
    }

    /**
     * Registers all available subcommands.
     */
    private void registerSubCommands() {
        // Register subcommands here
        // Format: subCommands.put("subcommand_name", new SubCommandClass(plugin));
        subCommands.put("reload", new ReloadCommand(plugin));
        subCommands.put("arena", new ArenaCommand(plugin));
        // Add more subcommands as they are implemented
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            // Send help message or plugin info
            sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("plugin-info")
                .replace("%version%", plugin.getDescription().getVersion()));
            // Ensure "plugin-info" key exists in messages.yml, e.g., "&bTowerDefense &7version &e%version%&7. Type &e/td help&7."
            return true;
        }

        String subCommandName = args[0].toLowerCase();
        CommandBase subCommand = subCommands.get(subCommandName);

        if (subCommand == null) {
            sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("unknown-command"));
            return true;
        }

        if (subCommand.getPermission() != null && !sender.hasPermission(subCommand.getPermission())) {
            subCommand.sendNoPermission(sender);
            return true;
        }

        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        if (subArgs.length < subCommand.getMinArgs() || (subCommand.getMaxArgs() != -1 && subArgs.length > subCommand.getMaxArgs())) {
            subCommand.sendIncorrectUsage(sender);
            return true;
        }

        return subCommand.execute(sender, subArgs);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return subCommands.keySet().stream()
                    .filter(name -> name.startsWith(args[0].toLowerCase()))
                    .filter(name -> {
                        CommandBase subCmd = subCommands.get(name);
                        return subCmd.getPermission() == null || sender.hasPermission(subCmd.getPermission());
                    })
                    .collect(Collectors.toList());
        }

        if (args.length > 1) {
            CommandBase subCommand = subCommands.get(args[0].toLowerCase());
            if (subCommand != null) {
                if (subCommand.getPermission() != null && !sender.hasPermission(subCommand.getPermission())) {
                    return new ArrayList<>(); // No suggestions if no permission for subcommand
                }
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                List<String> completions = subCommand.tabComplete(sender, subArgs);
                if (completions != null) {
                    return completions.stream()
                            .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
        }
        return new ArrayList<>();
    }
}