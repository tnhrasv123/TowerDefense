package com.thepcuser.towerdefense.manager;

import com.thepcuser.towerdefense.TowerDefense;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Manages the plugin's configuration files.
 * This class handles loading, accessing, and saving configuration data.
 */
public class ConfigManager {

    private final TowerDefense plugin;
    private FileConfiguration config;
    private File configFile;
    private FileConfiguration messages;
    private File messagesFile;
    private FileConfiguration towers;
    private File towersFile;
    private FileConfiguration enemies;
    private File enemiesFile;
    private FileConfiguration arenas;
    private File arenasFile;

    /**
     * Constructor for the ConfigManager.
     *
     * @param plugin The main plugin instance.
     */
    public ConfigManager(TowerDefense plugin) {
        this.plugin = plugin;
        loadConfigs();
    }

    /**
     * Loads all configuration files. If a file doesn't exist, it's created from defaults in the JAR.
     */
    public void loadConfigs() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        towersFile = new File(plugin.getDataFolder(), "towers.yml");
        enemiesFile = new File(plugin.getDataFolder(), "enemies.yml");
        arenasFile = new File(plugin.getDataFolder(), "arenas.yml");

        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        if (!towersFile.exists()) {
            plugin.saveResource("towers.yml", false);
        }
        if (!enemiesFile.exists()) {
            plugin.saveResource("enemies.yml", false);
        }
        if (!arenasFile.exists()) {
            plugin.saveResource("arenas.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        towers = YamlConfiguration.loadConfiguration(towersFile);
        enemies = YamlConfiguration.loadConfiguration(enemiesFile);
        arenas = YamlConfiguration.loadConfiguration(arenasFile);
        
        // Ensure UTF-8 encoding for files that might contain special characters
        reloadMessagesConfig(); // Example for messages, apply to others if needed
    }

    /**
     * Gets the main plugin configuration (config.yml).
     *
     * @return The FileConfiguration for config.yml.
     */
    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    /**
     * Gets the messages configuration (messages.yml).
     *
     * @return The FileConfiguration for messages.yml.
     */
    public FileConfiguration getMessagesConfig() {
        if (messages == null) {
            reloadMessagesConfig();
        }
        return messages;
    }

    /**
     * Gets the towers configuration (towers.yml).
     *
     * @return The FileConfiguration for towers.yml.
     */
    public FileConfiguration getTowersConfig() {
        if (towers == null) {
            reloadTowersConfig();
        }
        return towers;
    }

    /**
     * Gets the enemies configuration (enemies.yml).
     *
     * @return The FileConfiguration for enemies.yml.
     */
    public FileConfiguration getEnemiesConfig() {
        if (enemies == null) {
            reloadEnemiesConfig();
        }
        return enemies;
    }

    /**
     * Gets the arenas configuration (arenas.yml).
     *
     * @return The FileConfiguration for arenas.yml.
     */
    public FileConfiguration getArenasConfig() {
        if (arenas == null) {
            reloadArenasConfig();
        }
        return arenas;
    }

    /**
     * Reloads the main config.yml file.
     */
    public void reloadConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "config.yml");
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        // Look for defaults in the jar
        InputStream defConfigStream = plugin.getResource("config.yml");
        if (defConfigStream != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8)));
        }
    }
    
    /**
     * Reloads the messages.yml file, ensuring UTF-8 encoding.
     */
    public void reloadMessagesConfig() {
        if (messagesFile == null) {
            messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        InputStream defMessagesStream = plugin.getResource("messages.yml");
        if (defMessagesStream != null) {
            messages.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defMessagesStream, StandardCharsets.UTF_8)));
        }
    }

    /**
     * Reloads the towers.yml file.
     */
    public void reloadTowersConfig() {
        if (towersFile == null) {
            towersFile = new File(plugin.getDataFolder(), "towers.yml");
        }
        towers = YamlConfiguration.loadConfiguration(towersFile);
        InputStream defTowersStream = plugin.getResource("towers.yml");
        if (defTowersStream != null) {
            towers.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defTowersStream, StandardCharsets.UTF_8)));
        }
    }

    /**
     * Reloads the enemies.yml file.
     */
    public void reloadEnemiesConfig() {
        if (enemiesFile == null) {
            enemiesFile = new File(plugin.getDataFolder(), "enemies.yml");
        }
        enemies = YamlConfiguration.loadConfiguration(enemiesFile);
        InputStream defEnemiesStream = plugin.getResource("enemies.yml");
        if (defEnemiesStream != null) {
            enemies.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defEnemiesStream, StandardCharsets.UTF_8)));
        }
    }

    /**
     * Reloads the arenas.yml file.
     */
    public void reloadArenasConfig() {
        if (arenasFile == null) {
            arenasFile = new File(plugin.getDataFolder(), "arenas.yml");
        }
        arenas = YamlConfiguration.loadConfiguration(arenasFile);
        InputStream defArenasStream = plugin.getResource("arenas.yml");
        if (defArenasStream != null) {
            arenas.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defArenasStream, StandardCharsets.UTF_8)));
        }
    }

    /**
     * Saves the main config.yml file.
     */
    public void saveConfig() {
        if (config == null || configFile == null) {
            return;
        }
        try {
            getConfig().save(configFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config to " + configFile, ex);
        }
    }

    /**
     * Saves the messages.yml file.
     */
    public void saveMessagesConfig() {
        if (messages == null || messagesFile == null) {
            return;
        }
        try {
            getMessagesConfig().save(messagesFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save messages to " + messagesFile, ex);
        }
    }

    /**
     * Saves the towers.yml file.
     */
    public void saveTowersConfig() {
        if (towers == null || towersFile == null) {
            return;
        }
        try {
            getTowersConfig().save(towersFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save towers to " + towersFile, ex);
        }
    }

    /**
     * Saves the enemies.yml file.
     */
    public void saveEnemiesConfig() {
        if (enemies == null || enemiesFile == null) {
            return;
        }
        try {
            getEnemiesConfig().save(enemiesFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save enemies to " + enemiesFile, ex);
        }
    }

    /**
     * Saves the arenas.yml file.
     */
    public void saveArenasConfig() {
        if (arenas == null || arenasFile == null) {
            return;
        }
        try {
            getArenasConfig().save(arenasFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save arenas to " + arenasFile, ex);
        }
    }

    /**
     * Gets a specific message string from messages.yml, formatted with color codes.
     *
     * @param path The path to the message string.
     * @return The formatted message string, or the path if not found.
     */
    public String getMessage(String path) {
        String message = getMessagesConfig().getString(path, "&cMessage not found: " + path);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Gets a prefixed message string from messages.yml.
     *
     * @param path The path to the message string (without prefix).
     * @return The formatted and prefixed message string.
     */
    public String getPrefixedMessage(String path) {
        String prefix = getMessagesConfig().getString("prefix", "&8[&bTowerDefense&8] &r");
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', prefix + getMessage(path));
    }
}