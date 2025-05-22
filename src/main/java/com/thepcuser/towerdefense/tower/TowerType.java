package com.thepcuser.towerdefense.tower;

import com.thepcuser.towerdefense.TowerDefense;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Represents a type of tower, loaded from towers.yml.
 * Contains base properties and stats for different levels.
 */
public class TowerType {

    private static final Map<String, TowerType> towerTypes = new HashMap<>();
    private final TowerDefense plugin;

    private final String id;
    private final String displayName;
    private final String description;
    private final Material itemMaterial;
    private final String defaultTargetingPriority;
    private final Map<Integer, TowerLevelStats> levelStats;
    private final double areaOfEffectRadius; // For AoE towers, 0 if not AoE
    private final double splashRadius; // For splash towers, 0 if not splash
    private final double slowEffectDuration; // For slow towers
    private final int slowEffectAmplifier; // For slow towers

    public static class TowerLevelStats {
        private final int cost;
        private final double range;
        private final double damage;
        private final double attackSpeed; // Attacks per second
        private final String particleEffect;
        // Optional fields from new towers.yml structure
        private final double aoeRadius; // Level specific AoE
        private final double splashRadius; // Level specific Splash
        private final double slowDuration; // Level specific slow duration
        private final int slowAmplifier; // Level specific slow amplifier

        public TowerLevelStats(int cost, double range, double damage, double attackSpeed, String particleEffect,
                               double aoeRadius, double splashRadius, double slowDuration, int slowAmplifier) {
            this.cost = cost;
            this.range = range;
            this.damage = damage;
            this.attackSpeed = attackSpeed;
            this.particleEffect = particleEffect;
            this.aoeRadius = aoeRadius;
            this.splashRadius = splashRadius;
            this.slowDuration = slowDuration;
            this.slowAmplifier = slowAmplifier;
        }

        public int getCost() { return cost; }
        public double getRange() { return range; }
        public double getDamage() { return damage; }
        public double getAttackSpeed() { return attackSpeed; }
        public String getParticleEffect() { return particleEffect; }
        public double getAoeRadius() { return aoeRadius; }
        public double getSplashRadius() { return splashRadius; }
        public double getSlowDuration() { return slowDuration; }
        public int getSlowAmplifier() { return slowAmplifier; }

        public String getAttackSound() {
            return "entity." + particleEffect.toLowerCase() + ".ambient"; // Default sound for particle effect
        }
    }

    private TowerType(TowerDefense plugin, String id, String displayName, String description, Material itemMaterial,
                      String defaultTargetingPriority, Map<Integer, TowerLevelStats> levelStats,
                      double areaOfEffectRadius, double splashRadius, double slowEffectDuration, int slowEffectAmplifier) {
        this.plugin = plugin;
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.itemMaterial = itemMaterial;
        this.defaultTargetingPriority = defaultTargetingPriority;
        this.levelStats = levelStats;
        this.areaOfEffectRadius = areaOfEffectRadius;
        this.splashRadius = splashRadius;
        this.slowEffectDuration = slowEffectDuration;
        this.slowEffectAmplifier = slowEffectAmplifier;
    }

    public static void loadTypes(TowerDefense plugin, FileConfiguration config) {
        towerTypes.clear();
        ConfigurationSection towersSection = config.getConfigurationSection("towers");
        if (towersSection == null) {
            plugin.getLogger().warning("No 'towers' section found in towers.yml.");
            return;
        }

        for (String towerId : towersSection.getKeys(false)) {
            ConfigurationSection currentTowerSection = towersSection.getConfigurationSection(towerId);
            if (currentTowerSection == null) continue;

            try {
                String name = Color.translateAlternateColorCodes('&', currentTowerSection.getString("name", "Unnamed Tower"));
                String desc = ChatColor.translateAlternateColorCodes('&', currentTowerSection.getString("description", "No description."));
                Material material = Material.matchMaterial(currentTowerSection.getString("item-material", "BARRIER"));
                if (material == null) {
                    plugin.getLogger().warning("Invalid item-material for tower " + towerId + ": " + currentTowerSection.getString("item-material") + ". Defaulting to BARRIER.");
                    material = Material.BARRIER;
                }
                String targeting = currentTowerSection.getString("default-targeting-priority", "FIRST");

                double aoeRadius = currentTowerSection.getDouble("area-of-effect-radius", 0.0);
                double splashRad = currentTowerSection.getDouble("splash-radius", 0.0);
                double slowDuration = currentTowerSection.getDouble("slow-effect-duration-seconds", 0.0);
                int slowAmplifier = currentTowerSection.getInt("slow-effect-amplifier", 0);

                Map<Integer, TowerLevelStats> levels = new HashMap<>();
                ConfigurationSection levelsSection = currentTowerSection.getConfigurationSection("levels");
                if (levelsSection == null) {
                    plugin.getLogger().warning("Tower type '" + towerId + "' has no 'levels' defined. Skipping.");
                    continue;
                }

                for (String levelKey : levelsSection.getKeys(false)) {
                    try {
                        int levelNum = Integer.parseInt(levelKey);
                        ConfigurationSection levelData = levelsSection.getConfigurationSection(levelKey);
                        if (levelData == null) continue;

                        levels.put(levelNum, new TowerLevelStats(
                                levelData.getInt("cost"),
                                levelData.getDouble("range"),
                                levelData.getDouble("damage"),
                                levelData.getDouble("attack-speed"),
                                levelData.getString("particle-effect", "CRIT"),
                                levelData.getDouble("area-of-effect-radius", aoeRadius), // Inherit from base if not specified
                                levelData.getDouble("splash-radius", splashRad),
                                levelData.getDouble("slow-effect-duration-seconds", slowDuration),
                                levelData.getInt("slow-effect-amplifier", slowAmplifier)
                        ));
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid level key '" + levelKey + "' for tower '" + towerId + "'. Must be a number.");
                    }
                }

                if (levels.isEmpty()) {
                    plugin.getLogger().warning("Tower type '" + towerId + "' has no valid levels defined. Skipping.");
                    continue;
                }

                TowerType type = new TowerType(plugin, towerId, name, desc, material, targeting, levels, aoeRadius, splashRad, slowDuration, slowAmplifier);
                towerTypes.put(towerId.toUpperCase(), type);
                plugin.getLogger().info("Loaded tower type: " + name + " (ID: " + towerId + ") with " + levels.size() + " levels.");

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load tower type: " + towerId, e);
            }
        }
        plugin.getLogger().info("Successfully loaded " + towerTypes.size() + " tower type(s).");
    }

    public static TowerType getById(String id) {
        return towerTypes.get(id.toUpperCase());
    }

    public static Set<String> getLoadedTowerIds() {
        return towerTypes.keySet().stream().map(String::toLowerCase).collect(Collectors.toSet());
    }
    
    public static Map<String, TowerType> getAllTypes() {
        return new HashMap<>(towerTypes);
    }

    // Getters for TowerType properties
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public Material getItemMaterial() { return itemMaterial; }
    public String getDefaultTargetingPriority() { return defaultTargetingPriority; }
    public Map<Integer, TowerLevelStats> getAllLevelStats() { return levelStats; }

    public TowerLevelStats getStatsForLevel(int level) {
        return levelStats.getOrDefault(level, levelStats.get(1)); // Default to level 1 stats if level not found
    }

    public int getMaxLevel() {
        return levelStats.keySet().stream().max(Integer::compareTo).orElse(1);
    }

    // Convenience getters for level 1 (base) stats, or specific level if needed
    public int getBaseCost() { return getStatsForLevel(1) != null ? getStatsForLevel(1).getCost() : 0; }
    public double getBaseDamage() { return getStatsForLevel(1) != null ? getStatsForLevel(1).getDamage() : 0; }
    public double getBaseRange() { return getStatsForLevel(1) != null ? getStatsForLevel(1).getRange() : 0; }
    public double getBaseAttackSpeed() { return getStatsForLevel(1) != null ? getStatsForLevel(1).getAttackSpeed() : 0; }
    
    // Getters for general tower type characteristics (not level-specific)
    public double getAreaOfEffectRadius() { return areaOfEffectRadius; } // Base AoE if defined at tower level
    public double getSplashRadius() { return splashRadius; } // Base Splash if defined at tower level
    public double getSlowEffectDuration() { return slowEffectDuration; }
    public int getSlowEffectAmplifier() { return slowEffectAmplifier; }

    public String getTargetingStrategy() {
        return defaultTargetingPriority;
    }

    public int getSlowAmount() {
        return slowEffectAmplifier;
    }

    public int getTargetingStrategyClass() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getTargetingStrategyClass'");
    }

    public int getAoeRadius() {
        return this.getAoeRadius();
    }
}