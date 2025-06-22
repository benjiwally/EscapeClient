package com.escapemod.integration;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Baritone Configuration Manager for EscapeMod
 * Optimizes Baritone settings for anarchy server escape scenarios
 */
public class BaritoneConfig {
    private final Settings settings;
    private boolean isConfigured = false;
    
    public BaritoneConfig() {
        this.settings = BaritoneAPI.getSettings();
    }
    
    /**
     * Apply optimal settings for escape scenarios
     */
    public void configureForEscape() {
        if (isConfigured) return;
        
        try {
            // === PERFORMANCE SETTINGS ===
            settings.primaryTimeoutMS.value = 2000L; // Faster timeout for responsiveness
            settings.failureTimeoutMS.value = 5000L; // Quick failure recovery
            settings.planAheadPrimaryTimeoutMS.value = 4000L; // Plan ahead timeout
            settings.backtrackCostFavoringCoefficient.value = 0.5; // Less backtracking
            settings.costHeuristic.value = 3.563; // Optimized heuristic
            
            // === MOVEMENT SETTINGS ===
            settings.allowBreak.value = true; // Allow breaking blocks
            settings.allowPlace.value = true; // Allow placing blocks
            settings.allowParkour.value = true; // Enable parkour movements
            settings.allowParkourPlace.value = true; // Place blocks for parkour
            settings.allowSprint.value = true; // Always sprint
            settings.sprintInWater.value = true; // Sprint in water
            settings.allowWaterBucketFall.value = true; // Use water bucket for falls
            
            // === FALL DAMAGE SETTINGS ===
            settings.maxFallHeightNoWater.value = 10; // Allow higher falls
            settings.maxFallHeightBucket.value = 20; // With water bucket
            settings.maxFallHeightEnderpearl.value = 100; // With ender pearl
            
            // === SAFETY SETTINGS ===
            settings.avoidance.value = true; // Avoid dangerous areas
            settings.mobAvoidanceRadius.value = 8; // Avoid mobs
            settings.mobSpawnerAvoidanceRadius.value = 16; // Avoid spawners
            settings.avoidUpdatedFallingBlocks.value = true; // Avoid falling blocks
            
            // === BLOCK PREFERENCES ===
            settings.legitMine.value = false; // Don't worry about mining speed
            settings.buildInLayers.value = false; // Don't build in layers
            settings.okayToBreakFromAbove.value = true; // Break from above
            settings.freeLook.value = false; // Don't use freelook
            
            // === WATER AND LAVA HANDLING ===
            settings.assumeWalkOnWater.value = false; // Don't assume water walking
            settings.assumeWalkOnLava.value = false; // Don't assume lava walking
            settings.assumeSafeWalk.value = false; // Don't assume safe walk
            
            // === INVENTORY MANAGEMENT ===
            settings.allowInventory.value = true; // Allow inventory management
            settings.allowParkourAscend.value = true; // Parkour ascend
            settings.allowParkourPrecision.value = true; // Precision parkour
            
            // === PATHFINDING OPTIMIZATION ===
            settings.pathingMaxChunkBorderFetch.value = 160; // Chunk border fetch
            settings.simplifyUnloadedYCoord.value = true; // Simplify unloaded chunks
            settings.renderPath.value = true; // Render path for debugging
            settings.renderGoal.value = true; // Render goal
            
            // === ANARCHY SERVER SPECIFIC ===
            settings.antiCheatCompatibility.value = true; // Anti-cheat compatibility
            settings.assumeStep.value = false; // Don't assume step assist
            settings.assumeSpeed.value = false; // Don't assume speed hacks
            settings.chatControl.value = false; // Don't control chat
            settings.chatControlAnyway.value = false; // Really don't control chat
            
            // === ESCAPE SPECIFIC OPTIMIZATIONS ===
            settings.goalRenderLineWidthPixels.value = 2.0f; // Thinner goal lines
            settings.pathRenderLineWidthPixels.value = 3.0f; // Visible path lines
            settings.maxPathHistoryLength.value = 100; // Reasonable history
            settings.desktopNotifications.value = false; // No desktop notifications
            
            // === RESOURCE MANAGEMENT ===
            settings.buildIgnoreBlocks.value.clear(); // Clear ignore list
            settings.buildIgnoreBlocks.value.add("minecraft:dirt"); // Can use dirt
            settings.buildIgnoreBlocks.value.add("minecraft:cobblestone"); // Can use cobble
            settings.buildIgnoreBlocks.value.add("minecraft:netherrack"); // Can use netherrack
            
            // === ADVANCED PATHFINDING ===
            settings.slowPath.value = false; // Don't use slow path
            settings.fastPath.value = true; // Use fast path
            settings.favorWalkOnWater.value = false; // Don't favor water walking
            settings.allowDiagonalDescend.value = true; // Allow diagonal descend
            settings.allowDiagonalAscend.value = true; // Allow diagonal ascend
            
            isConfigured = true;
            
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§a[EscapeMod] Baritone configured for optimal escape performance"), false);
            }
            
        } catch (Exception e) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§c[EscapeMod] Failed to configure Baritone: " + e.getMessage()), false);
            }
        }
    }
    
    /**
     * Configure for crisis mode (more conservative settings)
     */
    public void configureForCrisis() {
        try {
            // More conservative settings for crisis situations
            settings.maxFallHeightNoWater.value = 3; // Lower fall height
            settings.maxFallHeightBucket.value = 10; // Conservative with water
            settings.allowParkour.value = false; // Disable risky parkour
            settings.allowParkourPlace.value = false; // No parkour placing
            settings.mobAvoidanceRadius.value = 12; // Larger mob avoidance
            settings.avoidance.value = true; // Definitely avoid danger
            
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§e[EscapeMod] Baritone configured for crisis mode"), false);
            }
            
        } catch (Exception e) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§c[EscapeMod] Failed to configure crisis mode: " + e.getMessage()), false);
            }
        }
    }
    
    /**
     * Configure for nether travel
     */
    public void configureForNether() {
        try {
            // Nether-specific settings
            settings.maxFallHeightNoWater.value = 5; // Lower in nether (lava)
            settings.maxFallHeightBucket.value = 15; // Still conservative
            settings.assumeWalkOnLava.value = false; // Never assume lava walking
            settings.mobAvoidanceRadius.value = 16; // Avoid ghasts/piglins
            settings.mobSpawnerAvoidanceRadius.value = 24; // Avoid nether spawners
            
            // Nether-specific blocks
            settings.buildIgnoreBlocks.value.add("minecraft:netherrack");
            settings.buildIgnoreBlocks.value.add("minecraft:blackstone");
            
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§c[EscapeMod] Baritone configured for nether travel"), false);
            }
            
        } catch (Exception e) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§c[EscapeMod] Failed to configure nether mode: " + e.getMessage()), false);
            }
        }
    }
    
    /**
     * Reset to default settings
     */
    public void resetToDefaults() {
        try {
            // Reset key settings to Baritone defaults
            settings.primaryTimeoutMS.reset();
            settings.allowBreak.reset();
            settings.allowPlace.reset();
            settings.allowParkour.reset();
            settings.maxFallHeightNoWater.reset();
            settings.maxFallHeightBucket.reset();
            settings.mobAvoidanceRadius.reset();
            settings.avoidance.reset();
            
            isConfigured = false;
            
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§e[EscapeMod] Baritone settings reset to defaults"), false);
            }
            
        } catch (Exception e) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§c[EscapeMod] Failed to reset settings: " + e.getMessage()), false);
            }
        }
    }
    
    /**
     * Get current configuration status
     */
    public boolean isConfigured() {
        return isConfigured;
    }
    
    /**
     * Get settings instance
     */
    public Settings getSettings() {
        return settings;
    }
}