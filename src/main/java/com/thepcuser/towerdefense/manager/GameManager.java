package com.thepcuser.towerdefense.manager;

import com.thepcuser.towerdefense.TowerDefense;
import com.thepcuser.towerdefense.game.Arena;
import com.thepcuser.towerdefense.game.Game;
import org.bukkit.entity.Player;

import java.util.HashMap;
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
        arena.setGameState(Arena.GameState.STARTING); // Or directly to ACTIVE if no countdown
        // game.startGame(); // Game constructor might set to WAITING, then startGame() transitions to ACTIVE
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
        game.endGame(won); // Game object handles internal state change and cleanup
        activeGames.remove(arenaId.toLowerCase());
        for (Player player : game.getPlayers()) {
            playerGames.remove(player.getUniqueId());
        }
        game.getArena().setGameState(Arena.GameState.WAITING); // Reset arena state
        plugin.getLogger().info("Game session ended for arena: " + game.getArena().getName());
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
                game = startGame(arena); // This creates a new game instance
                if (game == null) {
                     player.sendMessage(plugin.getConfigManager().getPrefixedMessage("game.cannot-start-error")); // A generic error
                    return false;
                }
            } else {
                 player.sendMessage(plugin.getConfigManager().getPrefixedMessage("arena.not-available").replace("%arena_name%", arena.getName()));
                return false;
            }
        }

        if (game.addPlayer(player)) {
            playerGames.put(player.getUniqueId(), game);
            // Check if game should start based on player count
            if (game.getGameState() == Arena.GameState.WAITING && game.getPlayers().size() >= arena.getMinPlayersToStart()) {
                 // TODO: Implement auto-start timer from config
                 game.startGame(); // Or transition to STARTING with a countdown
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
                    // Consider if players should be marked as losers or if state should be saved
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