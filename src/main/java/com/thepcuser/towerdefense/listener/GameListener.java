package com.thepcuser.towerdefense.listener;

import com.thepcuser.towerdefense.TowerDefense;
import com.thepcuser.towerdefense.game.Game;
import com.thepcuser.towerdefense.manager.GameManager;
import com.thepcuser.towerdefense.tower.Tower;
import com.thepcuser.towerdefense.tower.TowerType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class GameListener implements Listener {

    private final TowerDefense plugin;
    private final GameManager gameManager;

    public GameListener(TowerDefense plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Game game = gameManager.getPlayerGame(player); // Assuming GameManager has this method

        // Only process if the player is in a game
        if (game == null) {
            return;
        }

        // Check for right-click block action
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            if (itemInHand == null || itemInHand.getType() == Material.AIR) {
                return; // Player is not holding anything
            }

            // TODO: Identify if the item in hand is a tower placement item
            // This could involve checking item metadata (display name, lore, NBT tags)
            // For now, let's assume a simple check based on material and name (requires item helper)

            // Placeholder: Check if item is a specific material (e.g., BRICK) and has a custom name
            // This requires a way to map items to TowerTypes.
            // Let's assume we have a method like TowerType.getTowerTypeByPlacementItem(ItemStack item)

            // Example placeholder logic:
            // TowerType towerType = TowerType.getTowerTypeByPlacementItem(itemInHand);
            // if (towerType == null) {
            //     return; // Not a tower placement item
            // }

            // For now, let's use a simple check for a specific material as a placeholder
            if (itemInHand.getType() != Material.BRICK) { // Example material
                 return;
            }

            // Prevent placing the actual block
            event.setCancelled(true);

            Location placementLocation = event.getClickedBlock().getLocation().add(event.getBlockFace().getDirection());

            // TODO: Check if the placementLocation is a valid spot for a tower in the arena
            // This requires checking against the arena's path or buildable areas.
            // Example: game.getArena().isValidTowerLocation(placementLocation)
            // For now, let's assume any non-air block location is potentially valid (needs refinement)
            if (placementLocation.getBlock().getType() != Material.AIR) {
                 player.sendMessage("§cYou can only place towers on air blocks!"); // Basic check
                 return;
            }

            // Placeholder for getting the actual tower type based on the item
            // This needs to be properly implemented based on how tower items are defined.
            // For demonstration, let's assume a default tower type exists or is hardcoded.
            // TowerType towerType = TowerType.getById("BASIC_ARCHER"); // Example ID
            // if (towerType == null) {
            //     player.sendMessage("§cUnknown tower type!");
            //     return;
            // }

            // Placeholder cost check (using a hardcoded value for now)
            // int towerCost = towerType.getBaseCost();
            int towerCost = 100; // Example cost

            if (game.getPlayerMoney(player) < towerCost) {
                player.sendMessage(plugin.getConfigManager().getPrefixedMessage("tower.place.not-enough-money").replace("%cost%", String.valueOf(towerCost)));
                return;
            }

            // TODO: Deduct money from player
            // game.removePlayerMoney(player, towerCost);

            // TODO: Create and add the tower instance to the game
            // Tower newTower = new Tower(plugin, towerType, placementLocation, player);
            // game.addTower(newTower); // Assuming Game class has an addTower method

            // TODO: Place the visual representation of the tower (e.g., set block type)
            // placementLocation.getBlock().setType(towerType.getItemMaterial()); // Use the item material as block

            player.sendMessage(plugin.getConfigManager().getPrefixedMessage("tower.place.success").replace("%tower_name%", "Basic Archer")); // Placeholder name

            // Decrease item stack amount if not in creative mode
            if (!player.getGameMode().equals(org.bukkit.GameMode.CREATIVE)) {
                itemInHand.setAmount(itemInHand.getAmount() - 1);
            }
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK && player.isSneaking() && event.getClickedBlock() != null) {
            // Attempt to sell tower (Shift-Right-Click)
            Location clickedLocation = event.getClickedBlock().getLocation();
            Tower towerAtLocation = game.getTowerAtLocation(clickedLocation); // Assumes Game class has getTowerAtLocation

            if (towerAtLocation != null) {
                event.setCancelled(true); // Prevent any default action
                game.sellTower(player, clickedLocation);
            } else {
                // Optional: Send a message if they shift-right-clicked a non-tower block
                // player.sendMessage(plugin.getConfigManager().getPrefixedMessage("tower.sell.not-a-tower"));
            }
        }
    }

    // TODO: Add other event handlers as needed (e.g., for tower upgrades, etc.)
}