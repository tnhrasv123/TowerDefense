package com.thepcuser.towerdefense.command;

import com.thepcuser.towerdefense.TowerDefense;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Abstract base class for all TowerDefense subcommands.
 * Provides common functionality and structure for command handling.
 */
public abstract class CommandBase {

    protected final TowerDefense plugin;

    /**
     * Constructor for CommandBase.
     *
     * @param plugin The main plugin instance.
     */
    public CommandBase(TowerDefense plugin) {
        this.plugin = plugin;
    }

    /**
     * Executes the subcommand.
     *
     * @param sender The CommandSender who issued the command.
     * @param args   The arguments provided with the command.
     * @return True if the command was handled successfully, false otherwise.
     */
    public abstract boolean execute(CommandSender sender, String[] args);

    /**
     * Provides tab completion suggestions for the subcommand.
     *
     * @param sender The CommandSender requesting tab completion.
     * @param args   The current arguments typed by the sender.
     * @return A list of suggested completions, or null for default behavior.
     */
    public abstract List<String> tabComplete(CommandSender sender, String[] args);

    /**
     * Gets the required permission node for this command.
     *
     * @return The permission string, or null if no permission is required.
     */
    public abstract String getPermission();

    /**
     * Gets the minimum number of arguments required for this command.
     *
     * @return The minimum argument count.
     */
    public abstract int getMinArgs();

    /**
     * Gets the maximum number of arguments allowed for this command.
     * Use -1 for an unlimited number of arguments.
     *
     * @return The maximum argument count.
     */
    public abstract int getMaxArgs();

    /**
     * Gets the usage string for this command.
     * Example: "/td arena create <name>"
     *
     * @return The command usage string.
     */
    public abstract String getUsage();

    /**
     * Checks if the command sender is a player.
     *
     * @param sender The CommandSender to check.
     * @return True if the sender is a Player, false otherwise.
     */
    protected boolean isPlayer(CommandSender sender) {
        return sender instanceof Player;
    }

    /**
     * Sends a "no permission" message to the sender.
     *
     * @param sender The CommandSender.
     */
    public void sendNoPermission(CommandSender sender) {
        sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("no-permission"));
    }

    /**
     * Sends a "player only" message to the sender.
     *
     * @param sender The CommandSender.
     */
    protected void sendPlayerOnly(CommandSender sender) {
        sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("player-only-command"));
    }

    /**
     * Sends an "incorrect usage" message to the sender, including the command's usage string.
     *
     * @param sender The CommandSender.
     */
    protected void sendIncorrectUsage(CommandSender sender) {
        sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("incorrect-usage").replace("%usage%", getUsage()));
        // Ensure "incorrect-usage" key exists in messages.yml or add it.
        // Example: incorrect-usage: "&cIncorrect usage. Correct format: &e%usage%"
    }
}