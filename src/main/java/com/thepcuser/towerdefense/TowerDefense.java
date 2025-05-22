package com.thepcuser.towerdefense;

import com.thepcuser.towerdefense.manager.ArenaManager;
import com.thepcuser.towerdefense.manager.ConfigManager;
import com.thepcuser.towerdefense.manager.GameManager;
import com.thepcuser.towerdefense.tower.TargetingPriority;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main class for the TowerDefense plugin.
 */
public final class TowerDefense extends JavaPlugin {

    private static TowerDefense instance;
    private ConfigManager configManager;
    private ArenaManager arenaManager;
    private GameManager gameManager;

    @Override
    public void onEnable() {
        instance = this;
        // Plugin startup logic
        getLogger().info("TowerDefense plugin has been enabled!");

        // Initialize managers
        this.configManager = new ConfigManager(this);
        this.arenaManager = new ArenaManager(this);
        this.gameManager = new GameManager(this);
        // TODO: Register commands
        // TODO: Register listeners
        // TODO: Load configurations
    }

    /**
     * Gets the instance of the plugin.
     *
     * @return The plugin instance.
     */
    public static TowerDefense getInstance() {
        return instance;
    }

    /**
     * Gets the ConfigManager instance.
     *
     * @return The ConfigManager.
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Gets the ArenaManager instance.
     *
     * @return The ArenaManager.
     */
    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    /**
     * Gets the GameManager instance.
     *
     * @return The GameManager.
     */
    public GameManager getGameManager() {
        return gameManager;
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("TowerDefense plugin has been disabled.");

        if (gameManager != null) {
            gameManager.shutdown();
        }
        // TODO: Save game states if any were running and need persistence beyond server stop
        // TODO: Clean up other resources
    }
}