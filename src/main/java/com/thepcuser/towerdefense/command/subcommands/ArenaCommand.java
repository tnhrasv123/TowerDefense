package com.thepcuser.towerdefense.command.subcommands;

import com.thepcuser.towerdefense.TowerDefense;
import com.thepcuser.towerdefense.command.CommandBase;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handler for arena-specific subcommands.
 * Delegates execution to further sub-subcommands like 'create', 'delete', 'list'.
 */
public class ArenaCommand extends CommandBase {

    private final Map<String, CommandBase> subArenaCommands = new HashMap<>();

    public ArenaCommand(TowerDefense plugin) {
        super(plugin);
        registerSubArenaCommands();
    }

    private void registerSubArenaCommands() {
        // Register arena sub-subcommands here
        // Example: subArenaCommands.put("create", new ArenaCreateCommand(plugin));
        // Example: subArenaCommands.put("delete", new ArenaDeleteCommand(plugin));
        // Example: subArenaCommands.put("list", new ArenaListCommand(plugin));
        // For now, we'll leave this empty. Implementations will be added later.
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendIncorrectUsage(sender); // Or a specific help message for /td arena
            return true;
        }

        String subArenaCommandName = args[0].toLowerCase();
        CommandBase subArenaCommand = subArenaCommands.get(subArenaCommandName);

        if (subArenaCommand == null) {
            sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("unknown-arena-command").replace("%subcommand%", args[0]));
            // Ensure "unknown-arena-command" key exists in messages.yml, e.g., "&cUnknown arena command: %subcommand%"
            return true;
        }

        // Permission check for the specific sub-arena-command
        if (subArenaCommand.getPermission() != null && !sender.hasPermission(subArenaCommand.getPermission())) {
            subArenaCommand.sendNoPermission(sender);
            return true;
        }

        String[] subArenaArgs = Arrays.copyOfRange(args, 1, args.length);

        // Argument count check for the specific sub-arena-command
        if (subArenaArgs.length < subArenaCommand.getMinArgs() || (subArenaCommand.getMaxArgs() != -1 && subArenaArgs.length > subArenaCommand.getMaxArgs())) {
            sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("incorrect-usage").replace("%usage%", subArenaCommand.getUsage()));
            return true;
        }

        return subArenaCommand.execute(sender, subArenaArgs);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return subArenaCommands.keySet().stream()
                    .filter(name -> name.startsWith(args[0].toLowerCase()))
                    .filter(name -> {
                        CommandBase subCmd = subArenaCommands.get(name);
                        // Check permission for tab completion suggestion
                        return subCmd.getPermission() == null || sender.hasPermission(subCmd.getPermission());
                    })
                    .collect(Collectors.toList());
        }

        if (args.length > 1) {
            CommandBase subArenaCommand = subArenaCommands.get(args[0].toLowerCase());
            if (subArenaCommand != null) {
                // Ensure sender has permission for the parent arena command before suggesting sub-subcommands
                if (subArenaCommand.getPermission() != null && !sender.hasPermission(subArenaCommand.getPermission())) {
                    return new ArrayList<>();
                }
                String[] subArenaArgs = Arrays.copyOfRange(args, 1, args.length);
                List<String> completions = subArenaCommand.tabComplete(sender, subArenaArgs);
                if (completions != null) {
                    return completions.stream()
                            .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
        }
        return new ArrayList<>(); // Default to no suggestions
    }

    @Override
    public String getPermission() {
        return "towerdefense.arena"; // General permission for /td arena command group
    }

    @Override
    public int getMinArgs() {
        return 1; // Requires at least one argument (the sub-arena-command)
    }

    @Override
    public int getMaxArgs() {
        return -1; // Allows for multiple arguments for sub-arena-commands
    }

    @Override
    public String getUsage() {
        return "/td arena <subcommand> [args...]"; // General usage for arena commands
    }
}