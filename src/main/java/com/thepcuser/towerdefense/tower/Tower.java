package com.thepcuser.towerdefense.tower;

import com.thepcuser.towerdefense.TowerDefense;
import com.thepcuser.towerdefense.game.Game;
import com.thepcuser.towerdefense.mob.Enemy;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.annotation.Target;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Represents an individual tower instance placed in the game.
 * Each tower has a type, location, level, and potentially an owner.
 */
public class Tower {

    private final TowerDefense plugin;
    private final TowerType type;
    private final Location location;
    private final UUID ownerId; // UUID of the player who placed the tower
    private int level;
    private long lastAttackTime;

    // TODO: Add fields for current target, upgrade progress, specific stats (damage, range, speed) that might differ from base type due to upgrades.

    /**
     * Constructor for a new Tower.
     *
     * @param plugin   The main plugin instance.
     * @param type     The type of the tower.
     * @param location The location where the tower is placed.
     * @param owner    The player who placed the tower.
     */
    public Tower(TowerDefense plugin, TowerType type, Location location, Player owner) {
        this.plugin = plugin;
        this.type = type;
        this.location = location;
        this.ownerId = (owner != null) ? owner.getUniqueId() : null;
        this.level = 1; // Towers start at level 1
        this.lastAttackTime = 0;
    }

    // --- Getters ---
    public TowerType getType() {
        return type;
    }

    public Location getLocation() {
        return location;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public Player getOwner() {
        return (ownerId != null) ? plugin.getServer().getPlayer(ownerId) : null;
    }

    public int getLevel() {
        return level;
    }

    public double getCurrentDamage() {
        TowerType.TowerLevelStats stats = type.getStatsForLevel(level);
        return stats != null ? stats.getDamage() : type.getBaseDamage(); // Fallback to base if level stats not found
    }

    public double getCurrentRange() {
        TowerType.TowerLevelStats stats = type.getStatsForLevel(level);
        return stats != null ? stats.getRange() : type.getBaseRange(); // Fallback to base if level stats not found
    }

    public double getCurrentAttackSpeed() {
        TowerType.TowerLevelStats stats = type.getStatsForLevel(level);
        return stats != null ? stats.getAttackSpeed() : type.getBaseAttackSpeed(); // Fallback to base if level stats not found
    }

    // --- Setters ---
    public void setLevel(int level) {
        if (level >= 1 && level <= type.getMaxLevel()) {
            this.level = level;
            // Stats are now dynamically fetched via getters, no need to update fields here.
        }
    }

    // --- Tower Actions ---

    /**
     * Attempts to upgrade the tower to the next level.
     * @param player The player attempting the upgrade.
     * @return True if upgrade was successful, false otherwise (e.g., max level, not enough money).
     */
    public boolean upgrade(Player player) {
        int nextLevel = this.level + 1;
        if (nextLevel > type.getMaxLevel()) {
            player.sendMessage(plugin.getConfigManager().getPrefixedMessage("tower.upgrade.max-level"));
            return false;
        }

        TowerType.TowerLevelStats nextLevelStats = type.getStatsForLevel(nextLevel);
        if (nextLevelStats == null) {
             plugin.getLogger().warning("Attempted to upgrade tower " + type.getId() + " to non-existent level " + nextLevel);
             player.sendMessage(plugin.getConfigManager().getPrefixedMessage("tower.upgrade.error"));
             return false;
        }

        int upgradeCost = nextLevelStats.getCost();
        Game game = plugin.getGameManager().getPlayerGame(player); // Assuming GameManager exists and can get game by player
        if (game == null) {
             player.sendMessage(plugin.getConfigManager().getPrefixedMessage("game.not-in-game"));
             return false;
        }

        if (game.getMoney(player) < upgradeCost) {
            player.sendMessage(plugin.getConfigManager().getPrefixedMessage("tower.upgrade.not-enough-money").replace("%cost%", String.valueOf(upgradeCost)));
            return false;
        }

        // Deduct money and increment level
        game.removeMoney(player, upgradeCost);
        setLevel(nextLevel);

        player.sendMessage(plugin.getConfigManager().getPrefixedMessage("tower.upgrade.success").replace("%level%", String.valueOf(this.level)));
        plugin.getLogger().info("Tower at " + location.toString() + " upgraded to level " + this.level + " by " + player.getName());

        // TODO: Potentially change tower appearance based on level

        return true;
    }

    /**
     * Makes the tower attempt to find and attack a target.
     * This method would be called periodically by a game task.
     */
    public void attack(Game game) {
        long currentTime = System.currentTimeMillis();
        double attacksPerSecond = getCurrentAttackSpeed();
        if (attacksPerSecond <= 0) return; // Avoid division by zero or negative speed

        // Calculate time needed between attacks in milliseconds
        long attackCooldownMillis = (long) (1000.0 / attacksPerSecond);

        if (currentTime - lastAttackTime >= attackCooldownMillis) {
            List<Enemy> potentialTargets = findTargetsInRange(game);
            Enemy target = selectTarget(potentialTargets);

            if (target != null && target.getEntity() != null && target.isAlive()) {
                // Deal primary damage
                target.damage(getCurrentDamage());
                
                // Apply special effects based on tower type
                applyAoeDamage(target, game);
                applySplashDamage(target, game);
                applySlowEffect(target);

                // Play particle effect (using level-specific effect if available, fallback to base)
                String particleName = type.getStatsForLevel(level).getParticleEffect();
                try {
                    org.bukkit.Particle particle = org.bukkit.Particle.valueOf(particleName.toUpperCase());
                    location.getWorld().spawnParticle(particle, location.clone().add(0.5, 1.5, 0.5), 10, 0.2, 0.2, 0.2, 0.02);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid particle effect configured for tower " + type.getId() + " level " + level + ": " + particleName);
                    // Fallback to a default particle if configured one is invalid
                    location.getWorld().spawnParticle(org.bukkit.Particle.CRIT, location.clone().add(0.5, 1.5, 0.5), 10, 0.2, 0.2, 0.2, 0.02);
                }
                
                // Play attack sound effect
                String soundName = type.getStatsForLevel(level).getAttackSound();
                try {
                    org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName.toUpperCase());
                    location.getWorld().playSound(location, sound, 1.0f, 1.0f);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid sound effect configured for tower " + type.getId() + " level " + level + ": " + soundName);
                    // Fallback to a default sound if configured one is invalid
                    location.getWorld().playSound(location, org.bukkit.Sound.ENTITY_ARROW_HIT, 1.0f, 1.0f);
                }

                // Update last attack time
                lastAttackTime = currentTime;
            }
        }
    }

    /**
     * Selects an enemy from a list based on this targeting priority.
     * @param enemies List of potential targets
     * @return Selected enemy or null if list is empty
     */
    public Enemy selectTarget(List<Enemy> enemies) {
        if (enemies == null || enemies.isEmpty()) return null;
        
        switch(type.getDefaultTargetingPriority()) {
            case TargetingPriority.FIRST:
                return enemies.get(0);
            case LAST:
                return enemies.get(enemies.size() - 1);
            case STRONGEST:
                return enemies.stream()
                    .max(Comparator.comparingDouble(Enemy::getCurrentHealth))
                    .orElse(null);
            case WEAKEST:
                return enemies.stream()
                    .min(Comparator.comparingDouble(Enemy::getCurrentHealth))
                    .orElse(null);
            default:
                return enemies.get(0);
        }
    }

    /**
     * Finds potential enemy targets within the tower's range.
     * @return A list of enemies in range.
     */
    private List<Enemy> findTargetsInRange(Game game) {
        if (game == null) return new java.util.ArrayList<>();

        List<Enemy> enemiesInRange = new java.util.ArrayList<>();
        double currentRangeSquared = getCurrentRange() * getCurrentRange();

        for (Enemy enemy : game.getActiveEnemies()) { // Assumes game.getActiveEnemies() returns a safe copy or is safe to iterate
            if (enemy.getEntity() != null && !enemy.getEntity().isDead()) {
                if (enemy.getEntity().getWorld().equals(this.location.getWorld())) { // Ensure same world
                    if (enemy.getEntity().getLocation().distanceSquared(this.location) <= currentRangeSquared) {
                        enemiesInRange.add(enemy);
                    }
                }
            }
        }
        return enemiesInRange;
    }
    
    /**
     * Applies area-of-effect damage around the primary target.
     * @param primaryTarget The main target that was hit.
     * @param game The current game instance.
     */
    private void applyAoeDamage(Enemy primaryTarget, Game game) {
        if (type.getAoeRadius() <= 0) return;
        
        // Get all enemies in AOE range
        List<Enemy> aoeTargets = game.getActiveEnemies().stream()
            .filter(e -> e.getEntity() != null && !e.getEntity().isDead())
            .filter(e -> e.getEntity().getLocation().distance(primaryTarget.getEntity().getLocation()) <= type.getAoeRadius())
            .collect(Collectors.toList());
        
        // Apply reduced damage to each target
        double aoeDamage = getCurrentDamage() * 0.5; // Default 50% damage for AoE
        aoeTargets.forEach(target -> target.damage(aoeDamage));
    }
    
    /**
     * Applies splash damage around the primary target.
     * @param primaryTarget The main target that was hit.
     * @param game The current game instance.
     */
    private void applySplashDamage(Enemy primaryTarget, Game game) {
        if (type.getSplashRadius() <= 0) return;
        
        // Get all enemies in splash range
        List<Enemy> splashTargets = game.getActiveEnemies().stream()
            .filter(e -> e.getEntity() != null && !e.getEntity().isDead())
            .filter(e -> e.getEntity().getLocation().distance(primaryTarget.getEntity().getLocation()) <= type.getSplashRadius())
            .collect(Collectors.toList());
        
        // Apply reduced damage to each target
        double splashDamage = getCurrentDamage() * 0.3; // Default 30% damage for splash
        splashTargets.forEach(target -> target.damage(splashDamage));
    }
    
    /**
     * Applies slow effect to the target.
     * @param target The enemy to slow.
     */
    private void applySlowEffect(Enemy target) {
        if (type.getSlowEffectDuration() <= 0 || type.getSlowAmount() <= 0) return;
        
        // Apply slow effect to target
        target.getEntity().addPotionEffect(new PotionEffect(
            PotionEffectType.SLOWNESS, 
            (int)(type.getSlowEffectDuration() * 20), // Convert seconds to ticks
            type.getSlowEffectAmplifier()
        ));
    }

    // TODO: Method to display tower info (to player clicking it)

    /**
     * Calculates the total amount of currency invested into this tower,
     * including its base cost and all upgrade costs up to its current level.
     *
     * @return The total invested cost.
     */
    public int getTotalInvestedCost() {
        int totalCost = type.getBaseCost(); // Assumes TowerType has getBaseCost()
        for (int i = 2; i <= this.level; i++) {
            TowerType.TowerLevelStats levelStats = type.getStatsForLevel(i);
            if (levelStats != null) {
                totalCost += levelStats.getCost(); // Cost to upgrade TO level i
            }
        }
        return totalCost;
    }

    /**
     * Calculates the sell value of the tower.
     * This is typically a percentage of the total invested cost.
     *
     * @return The amount of currency the player receives when selling this tower.
     */
    public int getSellValue() {
        double sellPercentage = plugin.getConfigManager().getConfig().getDouble("tower.sell-percentage", 0.75); // Default 75%
        return (int) Math.floor(getTotalInvestedCost() * sellPercentage);
    }

    // TODO: Method to handle tower selling/removal (This will likely be in Game.java, calling getSellValue() here)
}