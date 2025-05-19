package com.thepcuser.towerdefense.game;

import com.thepcuser.towerdefense.TowerDefense;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a single game arena in the Tower Defense minigame.
 * Contains all properties and data related to a specific arena.
 */
public class Arena {

    // Import ConfigManager if it's not already imported for the class
    // import com.thepcuser.towerdefense.manager.ConfigManager; // Already imported via TowerDefense plugin field usually

    private final TowerDefense plugin;
    private final String id;
    private String name;
    private World world;
    private boolean enabled;
    private ConfigurationSection sourceConfigSection; // Stores the arena's specific configuration section

    // Arena boundaries (defined by WorldEdit selection)
    private Location minBoundary;
    private Location maxBoundary;

    private Location spawnPoint;
    private Location endPoint;
    private List<Location> waypoints;
    private List<TowerZone> towerZones;

    // Game state related to this arena (could be moved to a GameSession class later)
    private GameState gameState;
    private int currentWave;
    private int lives;
    private int maxPlayers;
    private int minPlayers;

    /**
     * Constructor for an Arena.
     *
     * @param plugin The main plugin instance.
     * @param id     The unique ID of the arena.
     * @param name   The display name of the arena.
     * @param world  The world this arena is in.
     */
    public Arena(TowerDefense plugin, String id, String name, World world) {
        this.plugin = plugin;
        this.id = id;
        this.name = name;
        this.world = world;
        this.enabled = false;
        this.waypoints = new ArrayList<>();
        this.towerZones = new ArrayList<>();
        this.gameState = GameState.WAITING;
        this.maxPlayers = 10;
        this.minPlayers = 2;
        // Default values, can be overridden by config
        this.currentWave = 0;
        this.lives = plugin.getConfigManager().getConfig().getInt("game.default-lives", 20);
    }

    /**
     * Loads arena data from a ConfigurationSection.
     *
     * @param section The ConfigurationSection to load from.
     */
    public void loadFromConfig(ConfigurationSection section) {
        if (section == null) return;
        this.sourceConfigSection = section; // Store the source for arena-specific settings

        this.name = section.getString("name", this.id);
        this.enabled = section.getBoolean("enabled", false);
        String worldName = section.getString("world");
        if (worldName != null) {
            World loadedWorld = plugin.getServer().getWorld(worldName);
            if (loadedWorld != null) {
                this.world = loadedWorld;
            } else {
                plugin.getLogger().warning("Arena '" + id + "' - world '" + worldName + "' not found. Using default or previously set world.");
            }
        }

        if (section.contains("region")) {
            this.minBoundary = loadLocation(section.getConfigurationSection("region"), "min");
            this.maxBoundary = loadLocation(section.getConfigurationSection("region"), "max");
        }
        this.spawnPoint = loadLocation(section.getConfigurationSection("spawn-point"));
        this.endPoint = loadLocation(section.getConfigurationSection("end-point"));

        this.waypoints.clear();
        ConfigurationSection waypointsSection = section.getConfigurationSection("waypoints");
        if (waypointsSection != null) {
            for (String key : waypointsSection.getKeys(false)) {
                Location waypoint = loadLocation(waypointsSection.getConfigurationSection(key));
                if (waypoint != null) {
                    this.waypoints.add(waypoint);
                }
            }
        }

        this.towerZones.clear();
        ConfigurationSection towerZonesSection = section.getConfigurationSection("tower-zones");
        if (towerZonesSection != null) {
            for (String key : towerZonesSection.getKeys(false)) {
                ConfigurationSection zoneSec = towerZonesSection.getConfigurationSection(key);
                if (zoneSec != null) {
                    Location min = loadLocation(zoneSec, "min");
                    Location max = loadLocation(zoneSec, "max");
                    if (min != null && max != null) {
                        this.towerZones.add(new TowerZone(min, max));
                    }
                }
            }
        }

        // Load arena-specific overrides for player counts and lives
        // Defaults are taken from global config if not present in arena section,
        // or from constructor values if global keys are also missing.
        // Note: this.lives is already initialized in constructor from global config or its default.
        // The arena-specific config can override it here.
        com.thepcuser.towerdefense.manager.ConfigManager configManager = plugin.getConfigManager(); // Ensure ConfigManager is accessible
        this.maxPlayers = section.getInt("max-players", configManager.getConfig().getInt("game.max-players-per-arena", this.maxPlayers));
        this.minPlayers = section.getInt("min-players", configManager.getConfig().getInt("game.min-players-to-start", this.minPlayers));
        this.lives = section.getInt("lives", this.lives);
    }

    /**
     * Helper method to load a Location from a ConfigurationSection.
     * Assumes x, y, z, and optionally yaw, pitch are direct children.
     * @param section The ConfigurationSection containing location data.
     * @return Location object or null if essential data is missing.
     */
    private Location loadLocation(ConfigurationSection section) {
        if (section == null || !section.contains("x") || !section.contains("y") || !section.contains("z")) {
            return null;
        }
        return new Location(
                this.world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw", 0.0),
                (float) section.getDouble("pitch", 0.0)
        );
    }

    /**
     * Helper method to load a Location from a nested ConfigurationSection (e.g., region.min.x).
     * @param parentSection The parent ConfigurationSection.
     * @param key The key of the nested section (e.g., "min" or "max").
     * @return Location object or null.
     */
    private Location loadLocation(ConfigurationSection parentSection, String key) {
        if (parentSection == null || !parentSection.isConfigurationSection(key)) {
            return null;
        }
        return loadLocation(parentSection.getConfigurationSection(key));
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public World getWorld() { return world; }
    public boolean isEnabled() { return enabled; }
    public Location getMinBoundary() { return minBoundary; }
    public Location getMaxBoundary() { return maxBoundary; }
    public Location getSpawnPoint() { return spawnPoint; }
    public Location getEndPoint() { return endPoint; }
    public List<Location> getWaypoints() { return new ArrayList<>(waypoints); } // Return a copy
    public List<TowerZone> getTowerZones() { return new ArrayList<>(towerZones); } // Return a copy
    public GameState getGameState() { return gameState; }
    public int getCurrentWave() { return currentWave; }
    public int getLives() { return lives; }

    // Setters
    public void setName(String name) { this.name = name; }
    public void setWorld(World world) { this.world = world; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setMinBoundary(Location minBoundary) { this.minBoundary = minBoundary; }
    public void setMaxBoundary(Location maxBoundary) { this.maxBoundary = maxBoundary; }
    public void setSpawnPoint(Location spawnPoint) { this.spawnPoint = spawnPoint; }
    public void setEndPoint(Location endPoint) { this.endPoint = endPoint; }
    public void setWaypoints(List<Location> waypoints) { this.waypoints = new ArrayList<>(waypoints); }
    public void addWaypoint(Location waypoint) { this.waypoints.add(waypoint); }
    public void clearWaypoints() { this.waypoints.clear(); }
    public void setTowerZones(List<TowerZone> towerZones) { this.towerZones = new ArrayList<>(towerZones); }
    public void addTowerZone(TowerZone towerZone) { this.towerZones.add(towerZone); }
    public void clearTowerZones() { this.towerZones.clear(); }
    public void setGameState(GameState gameState) { this.gameState = gameState; }
    public void setCurrentWave(int currentWave) { this.currentWave = currentWave; }
    public void setLives(int lives) { this.lives = lives; }

    /**
     * Checks if a given location is within any of the tower placement zones.
     *
     * @param location The location to check.
     * @return True if the location is within a tower zone, false otherwise.
     */
    public boolean canPlaceTower(Location location) {
        if (location == null || !Objects.equals(location.getWorld(), this.world)) return false;
        for (TowerZone zone : towerZones) {
            if (zone.contains(location)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a given location is within the arena's main boundaries.
     * @param location The location to check.
     * @return True if within boundaries, false otherwise.
     */
    public boolean isInArena(Location location) {
        if (location == null || !Objects.equals(location.getWorld(), this.world) || minBoundary == null || maxBoundary == null) {
            return false;
        }
        return location.getX() >= minBoundary.getX() && location.getX() <= maxBoundary.getX() &&
               location.getY() >= minBoundary.getY() && location.getY() <= maxBoundary.getY() &&
               location.getZ() >= minBoundary.getZ() && location.getZ() <= maxBoundary.getZ();
    }

    /**
     * Represents a zone where towers can be placed.
     */
    public static class TowerZone {
        private final Location min;
        private final Location max;

        public TowerZone(Location loc1, Location loc2) {
            this.min = new Location(loc1.getWorld(),
                    Math.min(loc1.getX(), loc2.getX()),
                    Math.min(loc1.getY(), loc2.getY()),
                    Math.min(loc1.getZ(), loc2.getZ()));
            this.max = new Location(loc1.getWorld(),
                    Math.max(loc1.getX(), loc2.getX()),
                    Math.max(loc1.getY(), loc2.getY()),
                    Math.max(loc1.getZ(), loc2.getZ()));
        }

        public Location getMin() { return min; }
        public Location getMax() { return max; }

        public boolean contains(Location loc) {
            if (!Objects.equals(loc.getWorld(), min.getWorld())) return false;
            return loc.getX() >= min.getX() && loc.getX() <= max.getX() &&
                   loc.getY() >= min.getY() && loc.getY() <= max.getY() &&
                   loc.getZ() >= min.getZ() && loc.getZ() <= max.getZ();
        }

        public ConfigurationSection serialize() {
            ConfigurationSection section = new YamlConfiguration().createSection("temp");
            ConfigurationSection minSec = section.createSection("min");
            minSec.set("x", min.getX());
            minSec.set("y", min.getY());
            minSec.set("z", min.getZ());
            ConfigurationSection maxSec = section.createSection("max");
            maxSec.set("x", max.getX());
            maxSec.set("y", max.getY());
            maxSec.set("z", max.getZ());
            return section;
        }
    }

    /**
     * Represents the current state of a game in an arena.
     */
    public enum GameState {
        WAITING,    // Waiting for players, or in lobby
        STARTING,   // Countdown before game starts
        ACTIVE,     // Game in progress
        PAUSED,     // Game paused (optional feature)
        ENDED       // Game finished (win/loss)
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getMinPlayersToStart() {
        return minPlayers;
    }

    public ConfigurationSection getWaveConfig() {
        // Try to get arena-specific wave configuration first
        if (this.sourceConfigSection != null) {
            ConfigurationSection arenaSpecificWaves = this.sourceConfigSection.getConfigurationSection("wave-config");
            if (arenaSpecificWaves == null) { // Try alternative common key "waves"
                arenaSpecificWaves = this.sourceConfigSection.getConfigurationSection("waves");
            }
            if (arenaSpecificWaves != null) {
                return arenaSpecificWaves;
            }
        }

        // Fallback to global wave configuration
        ConfigurationSection globalWaveConfig = plugin.getConfigManager().getConfig().getConfigurationSection("waves");
        if (globalWaveConfig == null) {
            plugin.getLogger().warning("Global wave configuration ('waves') not found in config.yml. Arena " + id + " will use an empty wave config.");
            // Return an empty but valid ConfigurationSection to prevent NullPointerExceptions downstream
            return new YamlConfiguration(); // YamlConfiguration is a ConfigurationSection
        }
        return globalWaveConfig;
    }
}