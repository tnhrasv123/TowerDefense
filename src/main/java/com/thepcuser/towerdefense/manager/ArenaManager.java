package com.thepcuser.towerdefense.manager;

import com.thepcuser.towerdefense.TowerDefense;
import com.thepcuser.towerdefense.game.Arena;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Manages all game arenas.
 * Handles loading, creation, deletion, and access to arenas.
 */
public class ArenaManager {

    private final TowerDefense plugin;
    private final Map<String, Arena> arenas = new HashMap<>();

    /**
     * Constructor for the ArenaManager.
     *
     * @param plugin The main plugin instance.
     */
    public ArenaManager(TowerDefense plugin) {
        this.plugin = plugin;
        loadArenas();
    }

    /**
     * Loads all arenas from the arenas.yml configuration file.
     */
    public void loadArenas() {
        arenas.clear();
        ConfigurationSection arenaSection = plugin.getConfigManager().getArenasConfig().getConfigurationSection("arenas");
        if (arenaSection == null) {
            plugin.getLogger().info("No arenas found in arenas.yml.");
            return;
        }

        for (String arenaId : arenaSection.getKeys(false)) {
            ConfigurationSection currentArenaSection = arenaSection.getConfigurationSection(arenaId);
            if (currentArenaSection == null) continue;

            try {
                String name = currentArenaSection.getString("name", arenaId);
                boolean enabled = currentArenaSection.getBoolean("enabled", false);
                String worldName = currentArenaSection.getString("world");

                if (worldName == null || plugin.getServer().getWorld(worldName) == null) {
                    plugin.getLogger().warning("Arena '" + arenaId + "' specifies an invalid or unloaded world: " + worldName + ". Skipping.");
                    continue;
                }
                World world = plugin.getServer().getWorld(worldName);

                Arena arena = new Arena(plugin, arenaId, name, world);
                arena.loadFromConfig(currentArenaSection); // This will load all properties
                // 'enabled' is already handled by loadFromConfig if present, or defaults

                arenas.put(arenaId.toLowerCase(), arena);
                plugin.getLogger().info("Loaded arena: " + name + " (ID: " + arenaId + ")");
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load arena: " + arenaId, e);
            }
        }
        plugin.getLogger().info("Successfully loaded " + arenas.size() + " arena(s).");
    }

    /**
     * Gets an arena by its ID.
     *
     * @param id The ID of the arena (case-insensitive).
     * @return The Arena object, or null if not found.
     */
    public Arena getArena(String id) {
        return arenas.get(id.toLowerCase());
    }

    /**
     * Gets all loaded arenas.
     *
     * @return A set of all arena IDs.
     */
    public Set<String> getArenaIds() {
        return arenas.keySet();
    }

    /**
     * Gets a map of all loaded arenas.
     *
     * @return A map where keys are arena IDs and values are Arena objects.
     */
    public Map<String, Arena> getAllArenas() {
        return arenas;
    }

    /**
     * Creates a new arena and saves it to arenas.yml.
     * This method would typically be called by an admin command.
     *
     * @param id The unique ID for the new arena.
     * @param name The display name for the new arena.
     * @param world The world where the arena is located.
     * @return The created Arena object, or null if an arena with that ID already exists.
     */
    public Arena createArena(String id, String name, World world) {
        if (arenas.containsKey(id.toLowerCase())) {
            plugin.getLogger().warning("Attempted to create an arena with an existing ID: " + id);
            return null;
        }

        Arena arena = new Arena(plugin, id, name, world);
        arenas.put(id.toLowerCase(), arena);

        // Save to arenas.yml
        ConfigurationSection arenaSection = plugin.getConfigManager().getArenasConfig().getConfigurationSection("arenas");
        if (arenaSection == null) {
            arenaSection = plugin.getConfigManager().getArenasConfig().createSection("arenas");
        }
        ConfigurationSection newArenaSec = arenaSection.createSection(id);
        newArenaSec.set("name", name);
        newArenaSec.set("enabled", false); // Disabled by default
        newArenaSec.set("world", world.getName());
        newArenaSec.createSection("region");
        newArenaSec.createSection("spawn-point");
        newArenaSec.createSection("end-point");
        newArenaSec.createSection("waypoints");
        newArenaSec.createSection("tower-zones");
        newArenaSec.set("max-players", plugin.getConfigManager().getConfig().getInt("game.max-players-per-arena", 4));
        newArenaSec.set("min-players", plugin.getConfigManager().getConfig().getInt("game.min-players-to-start", 1));
        // Wave config can be added here if there's a default structure
        // newArenaSec.set("wave-config.total-waves", 20);

        plugin.getConfigManager().saveArenasConfig();

        plugin.getLogger().info("Created new arena: " + name + " (ID: " + id + ")");
        return arena;
    }

    /**
     * Deletes an arena and removes it from arenas.yml.
     *
     * @param id The ID of the arena to delete.
     * @return True if the arena was deleted, false otherwise.
     */
    public boolean deleteArena(String id) {
        if (!arenas.containsKey(id.toLowerCase())) {
            return false;
        }
        arenas.remove(id.toLowerCase());
        plugin.getConfigManager().getArenasConfig().set("arenas." + id, null);
        plugin.getConfigManager().saveArenasConfig();
        plugin.getLogger().info("Deleted arena: " + id);
        return true;
    }

    /**
     * Saves a specific arena's configuration to arenas.yml.
     *
     * @param arenaId The ID of the arena to save.
     */
    public void saveArena(String arenaId) {
        Arena arena = getArena(arenaId);
        if (arena == null) {
            plugin.getLogger().warning("Attempted to save a non-existent arena: " + arenaId);
            return;
        }

        ConfigurationSection allArenasSection = plugin.getConfigManager().getArenasConfig().getConfigurationSection("arenas");
        if (allArenasSection == null) {
            allArenasSection = plugin.getConfigManager().getArenasConfig().createSection("arenas");
        }
        ConfigurationSection arenaSec = allArenasSection.createSection(arena.getId());

        arenaSec.set("name", arena.getName());
        arenaSec.set("enabled", arena.isEnabled());
        arenaSec.set("world", arena.getWorld().getName());

        if (arena.getMinBoundary() != null && arena.getMaxBoundary() != null) {
            ConfigurationSection regionSec = arenaSec.createSection("region");
            saveLocation(regionSec.createSection("min"), arena.getMinBoundary());
            saveLocation(regionSec.createSection("max"), arena.getMaxBoundary());
        }

        if (arena.getSpawnPoint() != null) {
            saveLocation(arenaSec.createSection("spawn-point" ), arena.getSpawnPoint());
        }
        if (arena.getEndPoint() != null) {
            saveLocation(arenaSec.createSection("end-point"), arena.getEndPoint());
        }

        if (!arena.getWaypoints().isEmpty()) {
            ConfigurationSection waypointsSec = arenaSec.createSection("waypoints");
            int i = 0;
            for (Location waypoint : arena.getWaypoints()) {
                saveLocation(waypointsSec.createSection("wp" + (i++)), waypoint);
            }
        }

        if (!arena.getTowerZones().isEmpty()) {
            ConfigurationSection towerZonesSec = arenaSec.createSection("tower-zones");
            int i = 0;
            for (Arena.TowerZone zone : arena.getTowerZones()) {
                ConfigurationSection zoneSec = towerZonesSec.createSection("zone" + (i++));
                saveLocation(zoneSec.createSection("min"), zone.getMin());
                saveLocation(zoneSec.createSection("max"), zone.getMax());
            }
        }
        // TODO: Save arena-specific max-players, min-players, wave-config if they differ from global

        plugin.getConfigManager().saveArenasConfig();
        plugin.getLogger().info("Saved arena data for: " + arena.getName());
    }

    private void saveLocation(ConfigurationSection section, Location loc) {
        if (loc == null || section == null) return;
        section.set("x", loc.getX());
        section.set("y", loc.getY());
        section.set("z", loc.getZ());
        if (loc.getYaw() != 0.0f) section.set("yaw", loc.getYaw());
        if (loc.getPitch() != 0.0f) section.set("pitch", loc.getPitch());
    }

    // TODO: Methods for setting arena properties (spawn, end, waypoints, tower zones) using WorldEdit selections
    // public void setArenaSpawn(String id, Location location) { Arena a = getArena(id); if(a!=null) {a.setSpawnPoint(location); saveArena(id); }}
    // public void setArenaEndPoint(String id, Location location) { ... saveArena(id); }
    // public void addArenaWaypoint(String id, Location location) { ... saveArena(id); }
    // public void defineTowerZone(String id, Location loc1, Location loc2) { ... saveArena(id); }
    // public void setArenaRegion(String id, Location loc1, Location loc2) { ... saveArena(id); }
}