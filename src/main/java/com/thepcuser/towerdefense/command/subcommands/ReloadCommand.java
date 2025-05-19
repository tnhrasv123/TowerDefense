package com.thepcuser.towerdefense.command.subcommands;

import com.thepcuser.towerdefense.TowerDefense;
import com.thepcuser.towerdefense.command.CommandBase;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * Subcommand to reload the plugin's configuration files.
 */
public class ReloadCommand extends CommandBase {

    public ReloadCommand(TowerDefense plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        try {
            plugin.getConfigManager().loadConfigs(); // Reloads all configurations
            sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("reload-success"));
        } catch (Exception e) {
            plugin.getLogger().severe("Error reloading configurations: " + e.getMessage());
            e.printStackTrace();
            sender.sendMessage(plugin.getConfigManager().getPrefixedMessage("reload-fail"));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<>(); // No arguments for reload command
    }

    @Override
    public String getPermission() {
        return "towerdefense.admin.reload"; // Specific permission for reloading
    }

    @Override
    public int getMinArgs() {
        return 0;
    }

    @Override
    public int getMaxArgs() {
        return 0;
    }

    @Override
    public String getUsage() {
        return "/td reload";
    }
}