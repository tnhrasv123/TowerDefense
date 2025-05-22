package com.thepcuser.towerdefense.tower;

import java.util.Comparator;
import java.util.List;

import com.thepcuser.towerdefense.mob.Enemy;

/**
 * Defines the targeting priority strategies for towers.
 */
public enum TargetingPriority {
    /** Targets the first enemy in range */
    FIRST(),
    
    /** Targets the last enemy in range */
    LAST,
    
    /** Targets the enemy with highest health */
    STRONGEST,
    
    /** Targets the enemy with lowest health */
    WEAKEST;
}
