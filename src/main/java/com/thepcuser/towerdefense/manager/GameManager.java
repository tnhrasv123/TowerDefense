package com.thepcuser.towerdefense.manager;

import com.thepcuser.towerdefense.TowerDefense;
import com.thepcuser.towerdefense.game.Arena;
import com.thepcuser.towerdefense.game.Game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages active game sessions across multiple arenas.
 * Handles game creation, player joining/leaving, and game lifecycle.
 */
public class GameManager {

    private final TowerDefense plugin;
    private final Map<String, Game> activeGames = new HashMap<>(); // Key: Arena ID
    private final Map<UUID, Game> playerGames = new HashMap<>(); // Key: Player UUID

    /**
     * Constructor for the GameManager.
     *
     * @param plugin The main plugin instance.
     */
    public GameManager(TowerDefense plugin) {
        this.plugin = plugin;
    }

    /**
     * Attempts to start a new game in the specified arena.
     *
     * @param arena The arena to start the game in.
     * @return The created Game instance, or null if a game is already running or arena is invalid.
     */
    public Game startGame(Arena arena) {
        if (arena == null || !arena.isEnabled()) {
            plugin.getLogger().warning("Attempted to start a game in a null or disabled arena.");
            return null;
        }
        if (activeGames.containsKey(arena.getId().toLowerCase())) {
            plugin.getLogger().warning("Attempted to start a game in arena '" + arena.getId() + "' which already has an active game.");
            return null;
        }

        // Check for max concurrent games
        int maxConcurrentGames = plugin.getConfigManager().getConfig().getInt("game.max-concurrent-games", 5);
        if (activeGames.size() >= maxConcurrentGames && maxConcurrentGames > 0) {
            plugin.getLogger().warning("Max concurrent games limit reached. Cannot start new game in arena '" + arena.getId() + "'.");
            // Optionally notify players trying to start/join
            return null;
        }

        Game game = new Game(plugin, arena);
        activeGames.put(arena.getId().toLowerCase(), game);
        arena.setGameState(Arena.GameState.STARTING);
        
        // Initialize player tracking in GameManager
        for (Player player : game.getPlayers()) {
            playerGames.put(player.getUniqueId(), game);
        }
        
        plugin.getLogger().info("New game session created for arena: " + arena.getName());
        return game;
    }

    /**
     * Ends a game in the specified arena.
     *
     * @param arenaId The ID of the arena where the game is ending.
     * @param won     Whether the game was won by players.
     */
    public void endGame(String arenaId, boolean won) {
        Game game = activeGames.get(arenaId.toLowerCase());
        if (game == null) {
            plugin.getLogger().warning("Attempted to end a non-existent game in arena: " + arenaId);
            return;
        }

        // Get players BEFORE game.endGame() is called, as game.endGame() will clear its own player list.
        List<Player> playersToRemoveFromManager = new ArrayList<>(game.getPlayers());

        game.endGame(won); // Game object handles internal state change and its own player cleanup (teleports, etc.)
        
        activeGames.remove(arenaId.toLowerCase()); // Remove game from GameManager's active list

        for (Player player : playersToRemoveFromManager) { // Iterate over the snapshot of players
            playerGames.remove(player.getUniqueId());
        }

        Arena arena = game.getArena();
        if (arena != null) {
            arena.setGameState(Arena.GameState.WAITING); // Reset arena state
            plugin.getLogger().info("Game session ended for arena: " + arena.getName());
        } else {
            plugin.getLogger().warning("Game session ended for arena ID: " + arenaId + ", but arena object was null.");
        }
    }

    /**
     * Allows a player to join a game in the specified arena.
     *
     * @param player The player joining.
     * @param arena  The arena to join.
     * @return True if the player joined successfully, false otherwise.
     */
    public boolean joinGame(Player player, Arena arena) {
        if (player == null || arena == null) return false;

        if (playerGames.containsKey(player.getUniqueId())) {
            player.sendMessage(plugin.getConfigManager().getPrefixedMessage("game.already-in-game"));
            return false;
        }

        Game game = activeGames.get(arena.getId().toLowerCase());
        if (game == null) {
            // If no active game, try to start one if conditions met (e.g. auto-start)
            // For now, assume game must be explicitly started or is in WAITING/STARTING phase
            // We might create a new game instance here if the arena is WAITING
            if (arena.getGameState() == Arena.GameState.WAITING) {
                game.startGame(); // This creates a new game instance
            } else {
                 player.sendMessage(plugin.getConfigManager().getPrefixedMessage("arena.not-available").replace("%arena_name%", arena.getName()));
                return false;
            }
        }

        if (game.addPlayer(player)) {
            playerGames.put(player.getUniqueId(), game);
            // Check if game should start based on player count
            if (game.getGameState() == Arena.GameState.WAITING && game.getPlayers().size() >= arena.getMinPlayersToStart()) {
                 int autoStartDelay = plugin.getConfigManager().getConfig().getInt("game.auto-start-delay", 10);
                if (autoStartDelay > 0) {
                    game.setGameState(Arena.GameState.STARTING);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (game.getGameState() == Arena.GameState.STARTING && game.getPlayers().size() >= arena.getMinPlayersToStart()) {
                            game.startGame();
                        }
                    }, autoStartDelay * 20L); // Convert seconds to ticks
                } else {
                    game.startGame();
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Removes a player from their current game.
     *
     * @param player The player to remove.
     */
    public void leaveGame(Player player) {
        Game game = playerGames.get(player.getUniqueId());
        if (game == null) {
            player.sendMessage(plugin.getConfigManager().getPrefixedMessage("game.not-in-game"));
            return;
        }
        game.removePlayer(player);
        playerGames.remove(player.getUniqueId());

        // If the game becomes empty and was active, end it.
        // The Game.removePlayer() method might already handle this.
        if (game.getPlayers().isEmpty() && game.getGameState() == Arena.GameState.ACTIVE) {
            endGame(game.getArena().getId(), false); // Game lost as no players left
        }
    }

    /**
     * Gets the game a player is currently in.
     *
     * @param player The player.
     * @return The Game instance, or null if the player is not in a game.
     */
    public Game getPlayerGame(Player player) {
        return playerGames.get(player.getUniqueId());
    }

    /**
     * Gets the game currently active in a specific arena.
     *
     * @param arenaId The ID of the arena.
     * @return The Game instance, or null if no game is active in that arena.
     */
    public Game getGameByArena(String arenaId) {
        return activeGames.get(arenaId.toLowerCase());
    }

    /**
     * Cleans up all active games, usually on plugin disable.
     */
    public void shutdown() {
        for (String arenaId : activeGames.keySet()) {
            try {
                Game game = activeGames.get(arenaId);
                if (game != null) {
                    // Clean up all players before ending game
                    for (Player player : game.getPlayers()) {
                        playerGames.remove(player.getUniqueId());
                    }
                    game.endGame(false); // End all games, players lose by default on shutdown
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error shutting down game in arena: " + arenaId, e);
            }
        }
        activeGames.clear();
        playerGames.clear();
        plugin.getLogger().info("All active game sessions have been terminated.");
    }
}