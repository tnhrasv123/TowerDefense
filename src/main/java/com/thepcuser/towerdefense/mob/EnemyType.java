package com.thepcuser.towerdefense.mob;

public enum EnemyType {
    BASIC("Basic Grunt", "ZOMBIE", 50.0, 0.23, 0.0, 5),
    ARMORED("Armored Knight", "ZOMBIE", 100.0, 0.20, 0.30, 10),
    SPEED("Swift Runner", "HUSK", 30.0, 0.35, 0.0, 8),
    FLYING("Phantom Scout", "PHANTOM", 40.0, 0.28, 0.0, 12, true),
    HEALER("Mystic Healer", "VINDICATOR", 60.0, 0.22, 0.10, 15),
    BOSS_GOLEM("Iron Tyrant", "IRON_GOLEM", 1000.0, 0.18, 0.20, 100);

    private final String displayName;
    private final String entityTypeName;
    private final double baseHealth;
    private final double baseSpeed;
    private final double damageReduction;
    private final int killReward;
    private final boolean ignoresGroundPath;

    EnemyType(String displayName, String entityTypeName, double baseHealth, double baseSpeed, double damageReduction, int killReward) {
        this(displayName, entityTypeName, baseHealth, baseSpeed, damageReduction, killReward, false);
    }

    EnemyType(String displayName, String entityTypeName, double baseHealth, double baseSpeed, double damageReduction, int killReward, boolean ignoresGroundPath) {
        this.displayName = displayName;
        this.entityTypeName = entityTypeName;
        this.baseHealth = baseHealth;
        this.baseSpeed = baseSpeed;
        this.damageReduction = damageReduction;
        this.killReward = killReward;
        this.ignoresGroundPath = ignoresGroundPath;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEntityTypeName() {
        return entityTypeName;
    }

    public double getBaseHealth() {
        return baseHealth;
    }

    public double getBaseSpeed() {
        return baseSpeed;
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

    public static EnemyType fromKey(String key) {
        for (EnemyType type : values()) {
            if (type.name().equalsIgnoreCase(key.replace('-', '_'))) {
                return type;
            }
        }
        // Fallback for keys that might not perfectly match enum constant names (e.g. boss_golem vs BOSS_GOLEM)
        try {
            return EnemyType.valueOf(key.toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return null; // Or throw a custom exception / log a warning
        }
    }
}