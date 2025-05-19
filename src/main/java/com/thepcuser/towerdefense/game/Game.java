package com.thepcuser.towerdefense.game;

import com.thepcuser.towerdefense.TowerDefense;
import com.thepcuser.towerdefense.manager.ConfigManager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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
    private final List<LivingEntity> activeEnemies; // Added in a previous step, ensure it's here
    private BukkitTask waveStartTask; // Added in a previous step, ensure it's here
    // TODO: Add fields for active towers (Map<Location, Tower> or similar).
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
        this.activeEnemies = new ArrayList<>(); // Ensure this is initialized
        
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
     */
    public void startGame() {
        if (this.gameState != Arena.GameState.WAITING && this.gameState != Arena.GameState.STARTING) {
            plugin.getLogger().warning("Attempted to start a game that is not in WAITING or STARTING state: " + arena.getId());
            return;
        }
        this.gameState = Arena.GameState.ACTIVE;
        this.arena.setGameState(Arena.GameState.ACTIVE);
        // currentWaveNumber will be set by nextWave/startWave
        // Player scores/money are initialized in addPlayer
        broadcastMessage(plugin.getConfigManager().getPrefixedMessage("game.started").replace("%arena_name%", arena.getName()));
        // TODO: SCOREBOARD: Initialize/display scoreboard for all players (showing wave, lives, players, etc.)
        nextWave(); // Start the first wave (this was part of a previous merge, ensure it's correct)
    }

    /**
     * Ends the game.
     *
     * @param won True if the players won, false if they lost.
     */
    public void endGame(boolean won) {
        this.gameState = Arena.GameState.ENDED;
        this.arena.setGameState(Arena.GameState.ENDED);
        if (won) {
            broadcastMessage(plugin.getConfigManager().getPrefixedMessage("game.win"));
            // TODO: Distribute rewards, update leaderboards
        } else {
            broadcastMessage(plugin.getConfigManager().getPrefixedMessage("game.lose"));
            // TODO: Handle loss, update leaderboards
        }

        if (waveStartTask != null && !waveStartTask.isCancelled()) {
            waveStartTask.cancel();
        }
        clearActiveEnemies();

        // Teleport players out
        Location lobbyLocation = null; // Placeholder, assuming LobbyManager might not exist yet
        // if (plugin.getLobbyManager() != null) lobbyLocation = plugin.getLobbyManager().getLobbyLocation();
        for (Player p : new ArrayList<>(players)) { // Iterate copy as removePlayer modifies 'players'
            removePlayer(p, false); // Remove player without broadcasting leave message again or ending game due to no players
            if (lobbyLocation != null) {
                p.teleport(lobbyLocation);
            } else {
                // Maybe teleport to world spawn or a configured fallback if lobby is not set
                if (p.getBedSpawnLocation() != null) p.teleport(p.getBedSpawnLocation());
                else p.teleport(p.getWorld().getSpawnLocation());
            }
            // TODO: Restore player inventory if changed for the game (should be part of removePlayer logic)
        }

        // TODO: Reset arena state if needed (e.g., remove placed towers, reset blocks if map is modified)

        // TODO: SCOREBOARD: Clear or update scoreboard to show game over status.

        // Notify GameManager to remove this game instance
        // if (plugin.getGameManager() != null) {
        //     plugin.getGameManager().removeGame(this);
        // } else {
        //     plugin.getLogger().severe("GameManager is null, cannot remove game instance: " + arena.getId());
        // }
        plugin.getLogger().info("Game ended for arena: " + arena.getId() + ". GameManager notification TODO.");
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
                endGame(false); // End game if no players left
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
            endGame(false); // Players lose if lives reach 0
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
            endGame(true); // All waves completed
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
            endGame(true);
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
            endGame(false);
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
                    spawnEnemy(entityType, spawnPoint, waveSpecificConfig.getConfigurationSection(enemyKey));
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
    }

     /* @param player The player who killed the enemy (can be null if killed by environment/tower).
     */
    /**
     * Spawns a single enemy and adds it to the active list.
     * @param type The EntityType to spawn.
     * @param location The Location to spawn at.
     * @param enemyConfig The ConfigurationSection for this specific enemy type in the wave, for custom properties.
     * @return The spawned LivingEntity, or null if spawning failed.
     */
    public LivingEntity spawnEnemy(EntityType type, Location location, ConfigurationSection enemyConfig) {
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("Attempted to spawn enemy with null location or world.");
            return null;
        }
        Entity entity = location.getWorld().spawnEntity(location, type);
        if (entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) entity;
            // TODO: ENEMY_CUSTOMIZATION: Apply custom health, name, equipment, AI goals etc. here from enemyConfig
            // if (enemyConfig != null) { ... livingEntity.setHealth(enemyConfig.getDouble("health", livingEntity.getHealth())); ... }
            activeEnemies.add(livingEntity);
            return livingEntity;
        }
        plugin.getLogger().warning("Failed to spawn " + type.name() + " as LivingEntity.");
        if(entity != null && !entity.isDead()) entity.remove(); // Clean up non-living entity if spawned
        return null;
    }

    public void enemyKilled(LivingEntity enemy, Player player) {
        activeEnemies.remove(enemy);

        if (player != null) {
            double moneyEarned = plugin.getConfigManager().getConfig().getDouble("economy.money-per-kill", 5.0); // Default money per kill
            // TODO: Implement more complex money calculation based on enemy type/difficulty from wave config
            // String enemyConfigPath = "waves." + currentWaveNumber + ".enemies." + enemy.getType().name(); (Example path)
            // moneyEarned = arena.getWaveConfig().getDouble(enemyConfigPath + ".money", moneyEarned);

            double currentMoney = playerMoney.getOrDefault(player.getUniqueId(), 0.0);
            playerMoney.put(player.getUniqueId(), currentMoney + moneyEarned);
            player.sendMessage(plugin.getConfigManager().getPrefixedMessage("economy.kill-reward")
                .replace("%amount%", String.format("%.2f", moneyEarned))
                .replace("%enemy_type%", enemy.getType().name().toLowerCase().replace('_', ' ')));
            // TODO: SCOREBOARD: Update player's money/score on their scoreboard and potentially a global game score.
        }

        if (gameState == Arena.GameState.ACTIVE && activeEnemies.isEmpty()) {
            broadcastMessage(plugin.getConfigManager().getPrefixedMessage("wave.cleared").replace("%wave_number%", String.valueOf(currentWaveNumber)));
            // Potentially add a small delay here before calling nextWave if desired
            Bukkit.getScheduler().runTaskLater(plugin, this::nextWave, 20L * 3); // 3 second delay
        }
        // TODO: SCOREBOARD: Update wave number display and potentially enemies remaining for all players.
    }

    private void clearActiveEnemies() {
        for (LivingEntity enemy : new ArrayList<>(activeEnemies)) { // Iterate over a copy to avoid ConcurrentModificationException
            if (enemy != null && !enemy.isDead()) {
                enemy.remove();
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

    // --- Economy ---
    public void addMoney(Player player, double amount) {
        playerMoney.put(player.getUniqueId(), getMoney(player) + amount);
        // TODO: Update scoreboard
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
}