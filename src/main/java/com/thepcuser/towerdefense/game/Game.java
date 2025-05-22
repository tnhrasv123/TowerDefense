package com.thepcuser.towerdefense.game;

import com.thepcuser.towerdefense.TowerDefense;
import com.thepcuser.towerdefense.manager.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import com.thepcuser.towerdefense.mob.Enemy;
import com.thepcuser.towerdefense.mob.EnemyType;
import com.thepcuser.towerdefense.tower.Tower;
import com.thepcuser.towerdefense.tower.TowerType;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a single active Tower Defense game session.
 * Manages players, game state, waves, economy, and scoring for one arena instance.
 */
public class Game {

    private final TowerDefense plugin;
    private final Arena arena;
    private Arena.GameState gameState;
    private final List<Player> players;
    private final Map<UUID, Integer> playerScores;
    private final Map<UUID, Double> playerMoney;
    private int currentWaveNumber;
    private int lives;
    private final List<Enemy> activeEnemies; // Stores active custom Enemy objects
    private final Map<Location, Tower> activeTowers; // Stores active towers
    private BukkitTask waveStartTask; // Added in a previous step, ensure it's here
    private BukkitTask towerAttackTask; // For towers to attack periodically
    // TODO: Consider a dedicated WaveManager class if wave logic becomes very complex.
    // TODO: Add field for ScoreboardManager/Handler if using a custom scoreboard system.

    /**
     * Constructor for a new Game session.
     *
     * @param plugin The main plugin instance.
     * @param arena  The arena this game is being played in.
     */
    public Game(TowerDefense plugin, Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
        this.gameState = Arena.GameState.WAITING; // Initial state
        this.players = new ArrayList<>();
        this.playerScores = new HashMap<>();
        this.playerMoney = new HashMap<>();
        this.currentWaveNumber = 0;
        this.activeEnemies = new ArrayList<>(); // Initializes list for custom Enemy objects
        this.activeTowers = new HashMap<>(); // Initialize active towers map
        
        ConfigManager cfgMgr = plugin.getConfigManager();
        this.lives = cfgMgr.getConfig().getInt("game.default-lives", 20);
        // Potentially override with arena-specific lives if configured
        // if (arena.getArenaSpecificConfig().contains("lives")) { 
        //     this.lives = arena.getArenaSpecificConfig().getInt("lives");
        // }
    }

    /**
     * Starts the game.
     * Transitions state, initializes players, and starts the first wave.
     * @return 
     */
    public void startGame() {
        if (this.gameState != Arena.GameState.WAITING && this.gameState != Arena.GameState.STARTING) {
            plugin.getLogger().warning("Attempted to start a game that is not in WAITING or STARTING state: " + arena.getId());
            return;
        }

        this.gameState = Arena.GameState.ACTIVE;
        this.arena.setGameState(Arena.GameState.ACTIVE);

        // Initialize player money and scores
        double startingMoney = plugin.getConfigManager().getConfig().getDouble("game.starting-money", 100.0);
        for (Player player : players) {
            playerMoney.put(player.getUniqueId(), startingMoney);
            playerScores.put(player.getUniqueId(), 0);
        }

        // Start tower attack task
        towerAttackTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Tower tower : activeTowers.values()) {
                tower.attack(this);
            }
        }, 0L, 5L); // Start immediately (0L delay), repeat every 5 ticks (0.25 seconds)

        nextWave(); // Start the first wave
    }

    /**
     * Deducts money from a player's balance in this game.
     * @param player The player.
     * @param amount The amount to deduct.
     * @return True if deduction was successful (player had enough money), false otherwise.
     */
    public boolean removePlayerMoney(Player player, double amount) {
        UUID playerId = player.getUniqueId();
        if (!playerMoney.containsKey(playerId)) {
            return false; // Player not in this game
        }
        double currentMoney = playerMoney.get(playerId);
        if (currentMoney < amount) {
            return false; // Not enough money
        }
        playerMoney.put(playerId, currentMoney - amount);
        updateScoreboard(); // Update player's scoreboard
        return true;
    }
    /**
     * Allows a player to sell a tower at a specific location.
     * @param player The player attempting to sell the tower.
     * @param towerLocation The location of the tower to sell.
     * @return True if the tower was sold successfully, false otherwise.
     */
    public boolean sellTower(Player player, Location towerLocation) {
        Tower tower = activeTowers.get(towerLocation);

        if (tower == null) {
            player.sendMessage(plugin.getConfigManager().getPrefixedMessage("tower.sell.not-found"));
            return false;
        }

        // Check ownership (or allow selling any tower if ownerId is null, though this might be a design choice)
        if (tower.getOwnerId() != null && !tower.getOwnerId().equals(player.getUniqueId())) {
            // Potentially add a config option to allow admins to sell any tower
            player.sendMessage(plugin.getConfigManager().getPrefixedMessage("tower.sell.not-owner"));
            return false;
        }

        int sellValue = tower.getSellValue();
        addMoney(player, sellValue); // Assumes addMoney method exists

        // Remove tower from active towers and world
        activeTowers.remove(towerLocation);
        towerLocation.getBlock().setType(Material.AIR); // Remove visual representation

        player.sendMessage(plugin.getConfigManager().getPrefixedMessage("tower.sell.success")
                .replace("%tower%", tower.getType().getDisplayName())
                .replace("%value%", String.valueOf(sellValue)));
        plugin.getLogger().info("Tower " + tower.getType().getId() + " at " + towerLocation.toString() + " sold by " + player.getName() + " for " + sellValue);

        // TODO: Play a sound effect for selling
        // TODO: Potentially add particle effects for selling

        return true;
    }

    /**
     * Ends the game.
     *
     * @param won True if the players won, false if they lost.
     */
    public void endGame(boolean won) {
        // First set game states to prevent new operations
        this.gameState = Arena.GameState.ENDED;
        this.arena.setGameState(Arena.GameState.ENDED);

        // Cancel all tasks safely
        try {
            if (waveStartTask != null && !waveStartTask.isCancelled()) {
                waveStartTask.cancel();
            }
            if (towerAttackTask != null && !towerAttackTask.isCancelled()) {
                towerAttackTask.cancel();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error cancelling game tasks: " + e.getMessage());
        }

        // Clear all enemies safely
        try {
            for (Enemy enemy : activeEnemies) {
                try {
                    if (enemy.getEntity() != null && !enemy.getEntity().isDead()) {
                        enemy.getEntity().remove();
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error removing enemy: " + e.getMessage());
                }
            }
            activeEnemies.clear();
        } catch (Exception e) {
            plugin.getLogger().warning("Error clearing enemies: " + e.getMessage());
        }

        // Clear all towers safely
        try {
            for (Tower tower : activeTowers.values()) {
                try {
                    if (tower.getLocation() != null && tower.getLocation().getBlock() != null) {
                        tower.getLocation().getBlock().setType(Material.AIR);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error removing tower block: " + e.getMessage());
                }
            }
            activeTowers.clear();
        } catch (Exception e) {
            plugin.getLogger().warning("Error clearing towers: " + e.getMessage());
        }

        if (won) {
            broadcastMessage(plugin.getConfigManager().getPrefixedMessage("game.win"));
            // Distribute rewards
            for (Player player : players) {
                int score = playerScores.getOrDefault(player.getUniqueId(), 0);
                // Add to player stats/leaderboard
                // TODO: Implement stats system
                player.sendMessage(ChatColor.GREEN + "Final Score: " + score);
            }
        } else {
            broadcastMessage(plugin.getConfigManager().getPrefixedMessage("game.lose"));
            // TODO: Handle loss, update leaderboards
        }
    }

    private void clearActiveTowers() {
        for (Tower tower : activeTowers.values()) {
            // Remove visual representation of the tower (e.g., set block to AIR)
            if (tower.getLocation() != null && tower.getLocation().getBlock() != null) {
                tower.getLocation().getBlock().setType(Material.AIR);
            }
        }
        activeTowers.clear();
    }

















    /**
     * Adds a player to this game session.
     *
     * @param player The player to add.
     * @return True if the player was added successfully, false otherwise (e.g., game full, already in game).
     */
    public boolean addPlayer(Player player) {
        if (players.size() >= arena.getMaxPlayers() && arena.getMaxPlayers() > 0) { // arena.getMaxPlayers can be from arena.yml or global config
            player.sendMessage(plugin.getConfigManager().getPrefixedMessage("game.full").replace("%arena_name%", arena.getName()));
            return false;
        }


















        if (players.contains(player)) {
            player.sendMessage(plugin.getConfigManager().getPrefixedMessage("game.already-in-game"));
            return false;
        }


















        players.add(player);
        playerScores.put(player.getUniqueId(), 0);
        playerMoney.put(player.getUniqueId(), plugin.getConfigManager().getConfig().getDouble("economy.starting-balance", 100.0));

        if (arena.getSpawnPoint() != null) {
            player.teleport(arena.getSpawnPoint());
        } else {
            plugin.getLogger().warning("No spawn point set for arena " + arena.getId() + " - cannot teleport player " + player.getName());
        }


















        // TODO: Give starting items/kit to player (configurable through config.yml, e.g., economy.starting-kit).
        // TODO: Clear player's inventory before giving kit, or save and restore later.

        // TODO: SCOREBOARD: Update scoreboard for all players (player count changed).
        // TODO: SCOREBOARD: Show individual scoreboard to the joining player (money, score, etc.).

        player.sendMessage(plugin.getConfigManager().getPrefixedMessage("game.join").replace("%arena_name%", arena.getName()));
        broadcastMessage(plugin.getConfigManager().getPrefixedMessage("game.player-joined-arena")
            .replace("%player_name%", player.getName())
            .replace("%current_players%", String.valueOf(players.size()))
            .replace("%max_players%", String.valueOf(arena.getMaxPlayers())));
        return true;
    }



    /**
     * Removes a player from this game session.
     *
     * @param player The player to remove.
     */
    public void removePlayer(Player player) {
        removePlayer(player, true); // Default to broadcasting and checking game end
    }
    /**
     * Internal method to remove a player, with option to suppress certain actions.
     * @param player The player to remove.
     * @param performFullRemovalActions If true, broadcasts messages and checks for game end.
     */
    public void removePlayer(Player player, boolean performFullRemovalActions) {
        if (!players.contains(player)) return;

        players.remove(player);
        playerScores.remove(player.getUniqueId());
        playerMoney.remove(player.getUniqueId());

        // TODO: Teleport player to a safe lobby (e.g., plugin.getLobbyManager().getLobbyLocation()) or their previous location (requires storing it on join).
        // TODO: Restore player's inventory if it was cleared/modified for the game (e.g., if they had a specific game kit).

        // TODO: SCOREBOARD: Update scoreboard for all players (player count changed).

        if (performFullRemovalActions) {
            player.sendMessage(plugin.getConfigManager().getPrefixedMessage("game.leave"));
            broadcastMessage(plugin.getConfigManager().getPrefixedMessage("game.player-left-arena")
                .replace("%player_name%", player.getName())
                .replace("%current_players%", String.valueOf(players.size()))
                .replace("%max_players%", String.valueOf(arena.getMaxPlayers())));

            if (players.isEmpty() && gameState == Arena.GameState.ACTIVE) {
                if (plugin.getGameManager() != null) {
                    plugin.getGameManager().endGame(this.arena.getId(), false);
                } else {
                    plugin.getLogger().severe("GameManager is null, cannot end game for arena: " + arena.getId() + " after last player left.");
                    this.endGame(false); // Fallback to internal endGame if GameManager is somehow null
                }
            }
        } else {
            // If not performing full removal (e.g. during endGame), ensure player still gets a leave message if appropriate
            // but avoid recursive endGame calls or double broadcasts.
        }
    }
    /**
     * Broadcasts a message to all players in this game.
     *
     * @param message The message to send.
     */
    public void broadcastMessage(String message) {
        for (Player p : players) {
            p.sendMessage(message);
        }


















    }



















    // --- Getters ---
    public Arena getArena() { return arena; }
    public Arena.GameState getGameState() { return gameState; }
    public List<Player> getPlayers() { return new ArrayList<>(players); } // Return a copy
    public int getCurrentWaveNumber() { return currentWaveNumber; }
    public int getLives() { return lives; }

    // --- Setters ---
    public void setGameState(Arena.GameState gameState) { 
        this.gameState = gameState; 
        this.arena.setGameState(gameState); // Keep arena's state in sync
    }


















    public void setLives(int lives) { this.lives = lives; }
    public void decrementLives(int amount) {
        this.lives -= amount;
        if (this.lives <= 0) {
            this.lives = 0;
            if (plugin.getGameManager() != null) {
                plugin.getGameManager().endGame(this.arena.getId(), false);
            } else {
                plugin.getLogger().severe("GameManager is null. Cannot properly end game for arena: " + arena.getId() + " due to lives depletion. Attempting local cleanup.");
                this.endGame(false); // Fallback
            }


















        }


















        // TODO: SCOREBOARD: Update lives display for all players.
    }



















    // --- Wave Management ---
    /**
     * Advances to the next wave, spawns enemies, and handles boss/special logic.
     */
    public void nextWave() {
        int nextWaveNum = currentWaveNumber + 1;
        int totalWaves = arena.getWaveConfig().getInt("total-waves", plugin.getConfigManager().getConfig().getInt("game.default-total-waves", 20));

        if (currentWaveNumber >= totalWaves && currentWaveNumber > 0) { // currentWaveNumber > 0 ensures this isn't triggered before first wave
            if (plugin.getGameManager() != null) {
                plugin.getGameManager().endGame(this.arena.getId(), true);
            } else {
                plugin.getLogger().severe("GameManager is null. Cannot properly end game for arena: " + arena.getId() + " after all waves completed. Attempting local cleanup.");
                this.endGame(true); // Fallback
            }


















            return;
        }



















        if (gameState != Arena.GameState.ACTIVE) {
            plugin.getLogger().info("Game " + arena.getId() + " is not active, cannot start next wave.");
            return;
        }



















        broadcastMessage(plugin.getConfigManager().getPrefixedMessage("wave.starting")
                .replace("%wave_number%", String.valueOf(nextWaveNum))
                .replace("%total_waves%", String.valueOf(totalWaves)));

        long delayTicks = plugin.getConfigManager().getConfig().getLong("wave.delay-seconds", 5) * 20L;

        if (waveStartTask != null && !waveStartTask.isCancelled()) {
            waveStartTask.cancel();
        }



















        waveStartTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (gameState == Arena.GameState.ACTIVE) { // Double check game is still active
                startWave(nextWaveNum);
            }


















        }, delayTicks);
    }



















    /**
     * Spawns all enemies for the given wave.
     * @param waveNumber The wave to start.
     */
    public void startWave(int waveNumber) {
        if (gameState != Arena.GameState.ACTIVE) return;

        this.currentWaveNumber = waveNumber;
        this.arena.setCurrentWave(waveNumber); // Keep arena in sync if it tracks this too
        clearActiveEnemies(); // Clear any stragglers from previous waves

        broadcastMessage(plugin.getConfigManager().getPrefixedMessage("wave.started").replace("%wave_number%", String.valueOf(waveNumber)));

        ConfigurationSection waveSpecificConfig = arena.getWaveConfig().getConfigurationSection(String.valueOf(waveNumber));

        if (waveSpecificConfig == null) {
            plugin.getLogger().warning("No configuration found for wave " + waveNumber + " in arena " + arena.getId() + ". Ending game as win (or handle differently).");
            if (plugin.getGameManager() != null) {
                plugin.getGameManager().endGame(this.arena.getId(), true);
            } else {
                plugin.getLogger().severe("GameManager is null. Cannot properly end game for arena: " + arena.getId() + " due to missing wave config. Attempting local cleanup.");
                this.endGame(true); // Fallback
            }


















            return;
        }



















        ConfigurationSection enemiesSection = waveSpecificConfig.getConfigurationSection("enemies");
        if (enemiesSection == null || enemiesSection.getKeys(false).isEmpty()) {
            plugin.getLogger().info("Wave " + waveNumber + " for arena " + arena.getId() + " has no enemies defined. Proceeding to next wave.");
            Bukkit.getScheduler().runTaskLater(plugin, this::nextWave, 20L * 3); // 3 second delay
            return;
        }



















        Location spawnPoint = arena.getSpawnPoint();
        if (spawnPoint == null) {
            plugin.getLogger().severe("Arena " + arena.getId() + " has no spawn point defined! Cannot spawn enemies.");
            if (plugin.getGameManager() != null) {
                plugin.getGameManager().endGame(this.arena.getId(), false);
            } else {
                plugin.getLogger().severe("GameManager is null. Cannot properly end game for arena: " + arena.getId() + " due to missing spawn point. Attempting local cleanup.");
                this.endGame(false); // Fallback
            }


















            return;
        }



















        for (String enemyKey : enemiesSection.getKeys(false)) {
            try {
                EntityType entityType = EntityType.valueOf(enemyKey.toUpperCase());
                int count = enemiesSection.getInt(enemyKey + ".count", 1);
                // TODO: ENEMY_CUSTOMIZATION: Read more properties like health, speed, equipment, custom name from config.
                // Example: double health = enemiesSection.getDouble(enemyKey + ".health", -1); // -1 for default
                // Example: double speed = enemiesSection.getDouble(enemyKey + ".speed", -1);
                // Example: String customName = enemiesSection.getString(enemyKey + ".name");
                // Example: ConfigurationSection equipment = enemiesSection.getConfigurationSection(enemyKey + ".equipment");

                for (int i = 0; i < count; i++) {
                    spawnEnemy(spawnPoint, entityType.name(), waveSpecificConfig.getConfigurationSection(enemyKey));
                }


















            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid entity type in wave config for arena " + arena.getId() + ", wave " + waveNumber + ": " + enemyKey);
            }


















        }



















        if (activeEnemies.isEmpty()) {
            plugin.getLogger().info("Wave " + waveNumber + " for arena " + arena.getId() + " resulted in no active enemies. Proceeding to next wave.");
            Bukkit.getScheduler().runTaskLater(plugin, this::nextWave, 20L * 3); // 3 second delay
        }


















        // TODO: SCOREBOARD: Update wave number display for all players.

        // Start tower attack loop if not already running and there are towers
        if ((towerAttackTask == null || towerAttackTask.isCancelled()) && !activeTowers.isEmpty()) {
            startTowerAttackLoop();
        }


















    }



















    // --- Tower Management ---

    /**
     * Allows a player to place a tower at a specified location.
     *
     * @param player The player placing the tower.
     * @param towerType The type of tower to place.
     * @param location The location to place the tower.
     * @return True if the tower was placed successfully, false otherwise.
     */
    public boolean placeTower(Player player, TowerType towerType, Location location) {
        if (!arena.isValidTowerLocation(location)) {
            player.sendMessage(ChatColor.RED + "You can only place towers in designated tower zones.");
            return false;
        }



















        if (activeTowers.containsKey(location)) {
            player.sendMessage(ChatColor.RED + "There is already a tower at this location.");
            return false;
        }



















        double cost = towerType.getBaseCost();
        UUID playerId = player.getUniqueId();
        if (playerMoney.getOrDefault(playerId, 0.0) < cost) {
            player.sendMessage(ChatColor.RED + "You don't have enough money to build this tower. Cost: " + cost);
            return false;
        }



















        // Deduct money
        playerMoney.put(playerId, playerMoney.get(playerId) - cost);
        // TODO: SCOREBOARD: Update player's money display

        Tower tower = new Tower(plugin, towerType, location, player);
        activeTowers.put(location, tower);

        // Visually place the tower
        location.getBlock().setType(towerType.getItemMaterial()); // Use material from TowerType
        // TODO: Potentially set block data or use custom models/armor stands for tower appearance

        player.sendMessage(ChatColor.GREEN + towerType.getDisplayName() + " placed successfully!");
        // plugin.getLogger().info("Player " + player.getName() + " placed " + towerType.name() + " at " + locationToString(location));

        // Start tower attack loop if it's the first tower and the loop isn't running
        if (towerAttackTask == null || towerAttackTask.isCancelled()) {
            startTowerAttackLoop();
        }



















        return true;
    }





















    /**
     * Starts the task that makes towers attack periodically.
     */
    private void startTowerAttackLoop() {
        if (towerAttackTask != null && !towerAttackTask.isCancelled()) {
            return; // Already running
        }


















        // Attack tick rate - e.g., 4 times per second (every 5 ticks)
        long attackInterval = plugin.getConfigManager().getConfig().getLong("towers.attack-interval-ticks", 5L);
        if (attackInterval <= 0) attackInterval = 5L;

        towerAttackTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (gameState != Arena.GameState.ACTIVE || activeTowers.isEmpty()) {
                if (towerAttackTask != null) {
                    towerAttackTask.cancel();
                    towerAttackTask = null;
                }


















                return;
            }


















            for (Tower tower : activeTowers.values()) {
                tower.attack(this); // Pass Game instance to tower for accessing enemies
            }


















        }, 0L, attackInterval);
        plugin.getLogger().info("Tower attack loop started for arena: " + arena.getId());
    }



















    public List<Enemy> getActiveEnemies() {
        return new ArrayList<>(activeEnemies); // Return a copy for safe iteration
    }



















    // Utility to convert location to string, useful for logging
    // private String locationToString(Location loc) {
    //    if (loc == null) return "null";
    //    return String.format("%s,%.1f,%.1f,%.1f", loc.getWorld() != null ? loc.getWorld().getName() : "null_world", loc.getX(), loc.getY(), loc.getZ());
    // }

    /*
     * @param player The player who killed the enemy (can be null if killed by environment/tower).
     */

     /* @param player The player who killed the enemy (can be null if killed by environment/tower).
     */
    /**
     * Spawns a single enemy and adds it to the active list.
     * @param type The EntityType to spawn.
     * @param location The Location to spawn at.
     * @param enemyConfig The ConfigurationSection for this specific enemy type in the wave, for custom properties.
     * @return The spawned LivingEntity, or null if spawning failed.
     */
    public Enemy spawnEnemy(Location location, String enemyKey, ConfigurationSection enemyConfig) {
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("Attempted to spawn enemy with null location or world for key: " + enemyKey);
            return null;
        }


















        if (enemyConfig == null) {
            plugin.getLogger().warning("Attempted to spawn enemy with null enemyConfig for key: " + enemyKey);
            return null;
        }



















        EnemyType enemyType = EnemyType.fromKey(enemyKey);
        if (enemyType == null) {
            plugin.getLogger().warning("Unknown EnemyType for key: " + enemyKey + ". Cannot spawn enemy.");
            return null;
        }



















        String mcEntityTypeName = enemyConfig.getString("entity-type", enemyType.getEntityTypeName());
        EntityType mcEntityType;
        try {
            mcEntityType = EntityType.valueOf(mcEntityTypeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().severe("Invalid entity-type '" + mcEntityTypeName + "' in enemies.yml for key: " + enemyKey);
            return null;
        }



















        Entity entity = location.getWorld().spawnEntity(location, mcEntityType);
        if (!(entity instanceof LivingEntity)) {
            plugin.getLogger().warning("Failed to spawn " + mcEntityType.name() + " as LivingEntity for key: " + enemyKey);
            if (entity != null && !entity.isDead()) entity.remove(); // Clean up
            return null;
        }


















        LivingEntity livingEntity = (LivingEntity) entity;

        String customName = ChatColor.translateAlternateColorCodes('&', enemyConfig.getString("name", enemyType.getDisplayName()));
        double health = enemyConfig.getDouble("health", enemyType.getBaseHealth());
        double speed = enemyConfig.getDouble("speed", enemyType.getBaseSpeed());
        double damageReduction = enemyConfig.getDouble("damage-reduction", enemyType.getDamageReduction());
        int killReward = enemyConfig.getInt("kill-reward", enemyType.getKillReward());
        boolean ignoresGroundPath = enemyConfig.getBoolean("ignores-ground-path", enemyType.isIgnoresGroundPath());

        Map<String, String> equipmentItems = new HashMap<>();
        ConfigurationSection equipSection = enemyConfig.getConfigurationSection("equipment");
        if (equipSection != null) {
            for (String key : equipSection.getKeys(false)) {
                equipmentItems.put(key, equipSection.getString(key));
            }


















        }



















        List<String> potionEffects = enemyConfig.getStringList("potion-effects");
        Map<String, Object> specificConfigForEnemyObject = enemyConfig.getValues(false);

        Enemy newEnemy = new Enemy(livingEntity, enemyType, customName, health, speed, damageReduction, killReward, equipmentItems, potionEffects, ignoresGroundPath, specificConfigForEnemyObject);

        activeEnemies.add(newEnemy);
        // TODO: Pathfinding logic for the newEnemy.getEntity() if needed.

        return newEnemy;
    }



















    public void enemyKilled(LivingEntity killedEntity, Player player) {
        Enemy killedEnemyObject = null;
        int foundAtIndex = -1;
        UUID killedEntityId = killedEntity.getUniqueId();

        for (int i = 0; i < activeEnemies.size(); i++) {
            Enemy currentEnemy = activeEnemies.get(i);
            if (currentEnemy.getEntity() != null && currentEnemy.getEntity().getUniqueId().equals(killedEntityId)) {
                killedEnemyObject = currentEnemy;
                foundAtIndex = i;
                break;
            }


















        }



















        if (killedEnemyObject != null && foundAtIndex != -1) {
            activeEnemies.remove(foundAtIndex);
        } else {
            plugin.getLogger().warning("EnemyKilled: Could not find an active Enemy wrapper for LivingEntity UUID: " + killedEntityId + ". It might have been already removed or was not a tracked game enemy.");
            return; // Exit if no tracked enemy was found and removed.
        }



















        if (player != null) {
            double moneyEarned = killedEnemyObject.getKillReward();
            double currentMoney = playerMoney.getOrDefault(player.getUniqueId(), 0.0);
            playerMoney.put(player.getUniqueId(), currentMoney + moneyEarned);

            String enemyDisplayName = killedEnemyObject.getCustomName();
            if (enemyDisplayName == null || enemyDisplayName.isEmpty()) {
                enemyDisplayName = killedEnemyObject.getEnemyType().getDisplayName();
            }
            player.sendMessage(plugin.getConfigManager().getPrefixedMessage("economy.kill-reward")
                .replace("%amount%", String.format("%.2f", moneyEarned))
                .replace("%enemy_type%", ChatColor.stripColor(enemyDisplayName))); // Use the actual enemy's name, stripped of color for consistency
            // TODO: SCOREBOARD: Update player's money/score on their scoreboard and potentially a global game score.
            }
        }


    private void clearActiveEnemies() {
        for (Enemy enemy : new ArrayList<>(activeEnemies)) { // Iterate over a copy to avoid ConcurrentModificationException
            if (enemy.getEntity() != null && !enemy.getEntity().isDead()) {
                enemy.getEntity().remove(); // Remove from world
            }


















        }


















        activeEnemies.clear();
    }

    /**
     * Called when an enemy leaks (reaches the end).
     */
    public void enemyLeaked() {
        decrementLives(1);
        broadcastMessage(plugin.getConfigManager().getPrefixedMessage("enemy-leaked").replace("%lives%", String.valueOf(lives)));
        // TODO: Check for game over
    }
    // --- Tower Placement & Upgrades ---
    /**
     * Attempts to place a tower for a player at a location.
     */
    public boolean placeTower(Player player, String towerType, Object location) {
        // TODO: Validate location (use arena.canPlaceTower), check money, deduct cost, place tower
        // TODO: Reference towers.yml for cost and properties
        return false;
    }

    /**
     * Attempts to upgrade a tower for a player.
     */
    public boolean upgradeTower(Player player, Object tower) {
        // TODO: Check if upgrade possible, deduct money, apply upgrade
        return false;
    }
    public boolean removeMoney(Player player, double amount) {
        double current = getMoney(player);
        if (current < amount) return false;
        playerMoney.put(player.getUniqueId(), current - amount);
        // TODO: Update scoreboard
        return true;
    }


    public double getMoney(Player player) {
        return playerMoney.getOrDefault(player.getUniqueId(), 0.0);
    }

    // --- Scoring ---
    public void addScore(Player player, int score) {
        playerScores.put(player.getUniqueId(), getScore(player) + score);
        // TODO: Update scoreboard
    }

    /**
     * Gets a player's current money balance in this game.
     * @param player The player.
     * @return The player's money, or 0 if not in game.
     */
    public double getPlayerMoney(Player player) {
        return playerMoney.getOrDefault(player.getUniqueId(), 0.0);
    }

    /**
     * Adds money to a player's balance in this game.
     * @param player The player.
     * @param amount The amount to add.
     */
    public void addMoney(Player player, double amount) {
        UUID playerId = player.getUniqueId();
        double currentMoney = playerMoney.getOrDefault(playerId, 0.0);
        playerMoney.put(playerId, currentMoney + amount);
        updateScoreboard(); // Update player's scoreboard
    }

    /**
     * Adds a tower to the game.
     * @param tower The tower instance to add.
     */
    public void addTower(Tower tower) {
        activeTowers.put(tower.getLocation(), tower);
        // TODO: Potentially add visual representation (block) here if not done elsewhere
        plugin.getLogger().info("Tower " + tower.getType().getId() + " placed at " + tower.getLocation().toString());
    }
    public int getScore(Player player) {
        return playerScores.getOrDefault(player.getUniqueId(), 0);
    }



















    // --- Scoreboard & UI ---
    /**
     * Updates the scoreboard for all players.
     */
    public void updateScoreboard() {
        // TODO: Implement scoreboard update logic (display wave, lives, money, score)
    }

    public Tower getTowerAtLocation(Location clickedLocation) {
        return activeTowers.get(clickedLocation);
    }
}