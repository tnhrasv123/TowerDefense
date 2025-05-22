package com.thepcuser.towerdefense.mob;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Enemy {
    private final UUID uuid;
    private LivingEntity entity;
    private final EnemyType enemyType;
    private double currentHealth;
    private final String customName;
    private final double speed;
    private final double damageReduction;
    private final int killReward;
    private final Map<String, String> equipmentItems; // e.g., helmet: IRON_HELMET
    private final List<String> potionEffects; // e.g., SPEED:1:100000
    private final boolean ignoresGroundPath;

    // Special properties for specific enemy types
    private double healRadius = 0;
    private double healAmount = 0;
    private int healIntervalTicks = 0;
    private List<String> abilities = null;

    public Enemy(LivingEntity entity, EnemyType enemyType, String customName, double health, double speed, double damageReduction, int killReward, Map<String, String> equipmentItems, List<String> potionEffects, boolean ignoresGroundPath, Map<String, Object> specificConfig) {
        this.uuid = entity.getUniqueId();
        this.entity = entity;
        this.enemyType = enemyType;
        this.customName = customName;
        this.currentHealth = health;
        this.speed = speed;
        this.damageReduction = damageReduction;
        this.killReward = killReward;
        this.equipmentItems = equipmentItems;
        this.potionEffects = potionEffects;
        this.ignoresGroundPath = ignoresGroundPath;

        if (entity != null) {
            if (customName != null && !customName.isEmpty()) {
                entity.setCustomName(customName);
                entity.setCustomNameVisible(true);
            }
            entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
            entity.setHealth(health);
            entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(speed);

            if (equipmentItems != null && !equipmentItems.isEmpty() && entity.getEquipment() != null) {
                EntityEquipment bukkitEquipment = entity.getEquipment();
                try {
                    String helmetStr = equipmentItems.get("helmet");
                    if (helmetStr != null && !helmetStr.equalsIgnoreCase("AIR")) bukkitEquipment.setHelmet(new ItemStack(Material.valueOf(helmetStr.toUpperCase())));

                    String chestplateStr = equipmentItems.get("chestplate");
                    if (chestplateStr != null && !chestplateStr.equalsIgnoreCase("AIR")) bukkitEquipment.setChestplate(new ItemStack(Material.valueOf(chestplateStr.toUpperCase())));

                    String leggingsStr = equipmentItems.get("leggings");
                    if (leggingsStr != null && !leggingsStr.equalsIgnoreCase("AIR")) bukkitEquipment.setLeggings(new ItemStack(Material.valueOf(leggingsStr.toUpperCase())));

                    String bootsStr = equipmentItems.get("boots");
                    if (bootsStr != null && !bootsStr.equalsIgnoreCase("AIR")) bukkitEquipment.setBoots(new ItemStack(Material.valueOf(bootsStr.toUpperCase())));

                    String mainHandStr = equipmentItems.get("mainhand");
                    if (mainHandStr != null && !mainHandStr.equalsIgnoreCase("AIR")) bukkitEquipment.setItemInMainHand(new ItemStack(Material.valueOf(mainHandStr.toUpperCase())));

                    String offHandStr = equipmentItems.get("offhand");
                    if (offHandStr != null && !offHandStr.equalsIgnoreCase("AIR")) bukkitEquipment.setItemInOffHand(new ItemStack(Material.valueOf(offHandStr.toUpperCase())));
                } catch (IllegalArgumentException e) {
                    // Consider logging this to the plugin's logger instead of System.err
                    System.err.println("[TowerDefense] Invalid material type in enemy equipment config for " + enemyType.name() + ": " + e.getMessage());
                }
            }

            if (potionEffects != null && !potionEffects.isEmpty()) {
                for (String effectStr : potionEffects) {
                    String[] parts = effectStr.split(":");
                    if (parts.length >= 2) {
                        PotionEffectType type = PotionEffectType.getByName(parts[0].toUpperCase());
                        int amplifier = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                        int duration = parts.length > 2 ? Integer.parseInt(parts[2]) : Integer.MAX_VALUE; // Default to very long duration
                        if (type != null) {
                            entity.addPotionEffect(new PotionEffect(type, duration, amplifier));
                        }
                    }
                }
            }
        }

        // Load specific configurations
        if (specificConfig != null) {
            if (enemyType == EnemyType.HEALER) {
                this.healRadius = ((Number) specificConfig.getOrDefault("heal-radius", 0.0)).doubleValue();
                this.healAmount = ((Number) specificConfig.getOrDefault("heal-amount", 0.0)).doubleValue();
                this.healIntervalTicks = ((Number) specificConfig.getOrDefault("heal-interval-ticks", 40)).intValue();
            }
            if (specificConfig.containsKey("abilities")) {
                this.abilities = (List<String>) specificConfig.get("abilities");
            }
        }
    }

    public UUID getUuid() {
        return uuid;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public void setEntity(LivingEntity entity) {
        this.entity = entity;
    }

    public EnemyType getEnemyType() {
        return enemyType;
    }

    public double getCurrentHealth() {
        return entity != null ? entity.getHealth() : currentHealth;
    }

    public void setCurrentHealth(double currentHealth) {
        this.currentHealth = currentHealth;
        if (entity != null) {
            entity.setHealth(Math.max(0, Math.min(entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue(), currentHealth)));
        }
    }

    public String getCustomName() {
        return customName;
    }

    public double getSpeed() {
        return speed;
    }

    public double getDamageReduction() {
        return damageReduction;
    }

    public int getKillReward() {
        return killReward;
    }

    public boolean isIgnoresGroundPath() {
        return ignoresGroundPath;
    }

    public Map<String, String> getEquipmentItems() {
        return equipmentItems;
    }

    public List<String> getPotionEffects() {
        return potionEffects;
    }

    public double getHealRadius() {
        return healRadius;
    }

    public double getHealAmount() {
        return healAmount;
    }

    public int getHealIntervalTicks() {
        return healIntervalTicks;
    }

    public List<String> getAbilities() {
        return abilities;
    }

    public Location getLocation() {
        return entity != null ? entity.getLocation() : null;
    }

    public boolean isAlive() {
        return entity != null && !entity.isDead() && entity.getHealth() > 0;
    }

    public void damage(double amount) {
        double actualDamage = amount * (1 - damageReduction);
        if (entity != null && isAlive()) {
            entity.damage(actualDamage);
            this.currentHealth = entity.getHealth();
        }
    }

    public void heal(double amount) {
        if (entity != null && isAlive()) {
            double maxHealth = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
            entity.setHealth(Math.min(maxHealth, entity.getHealth() + amount));
            this.currentHealth = entity.getHealth();
        }
    }

    public double getProgress() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getProgress'");
    }
}