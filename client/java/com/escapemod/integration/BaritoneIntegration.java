package com.escapemod.integration;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.*;
import baritone.api.pathing.calc.IPath;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BetterBlockPos;
import baritone.api.Settings;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;

/**
 * Baritone Integration Layer for EscapeMod
 * Provides high-level pathfinding and navigation using Baritone API
 */
public class BaritoneIntegration {
    private final MinecraftClient client;
    private final IBaritone baritone;
    private final Settings settings;
    private final BaritoneConfig config;
    
    private boolean isActive = false;
    private Goal currentGoal = null;
    private EscapeProcess escapeProcess = null;
    
    public BaritoneIntegration(MinecraftClient client) {
        this.client = client;
        this.baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        this.settings = BaritoneAPI.getSettings();
        this.config = new BaritoneConfig();
        
        // Configure Baritone settings for escape scenarios
        config.configureForEscape();
    }
    
    /**
     * Configure for crisis mode
     */
    public void configureForCrisis() {
        config.configureForCrisis();
    }
    
    /**
     * Configure for nether travel
     */
    public void configureForNether() {
        config.configureForNether();
    }
    
    /**
     * Reset to default configuration
     */
    public void resetConfiguration() {
        config.resetToDefaults();
    }
    
    /**
     * Start pathfinding to a specific location
     */
    public boolean pathTo(BlockPos target) {
        if (!isBaritoneAvailable()) return false;
        
        try {
            currentGoal = new GoalBlock(target);
            baritone.getCustomGoalProcess().setGoalAndPath(currentGoal);
            isActive = true;
            
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§a[EscapeMod] Baritone pathfinding to " + 
                    target.getX() + ", " + target.getY() + ", " + target.getZ()), false);
            }
            
            return true;
        } catch (Exception e) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§c[EscapeMod] Baritone pathfinding failed: " + e.getMessage()), false);
            }
            return false;
        }
    }
    
    /**
     * Start pathfinding in a specific direction for a distance
     */
    public boolean pathInDirection(Vec3d direction, int distance) {
        if (!isBaritoneAvailable() || client.player == null) return false;
        
        BlockPos playerPos = client.player.getBlockPos();
        BlockPos target = playerPos.add(
            (int)(direction.x * distance),
            0,
            (int)(direction.z * distance)
        );
        
        // Use GoalXZ for direction-based movement (allows Y flexibility)
        currentGoal = new GoalXZ(target.getX(), target.getZ());
        baritone.getCustomGoalProcess().setGoalAndPath(currentGoal);
        isActive = true;
        
        return true;
    }
    
    /**
     * Start escape process with custom behavior
     */
    public boolean startEscapeProcess(BlockPos finalTarget) {
        if (!isBaritoneAvailable()) return false;
        
        try {
            // Create custom escape process
            escapeProcess = new EscapeProcess(baritone, finalTarget);
            baritone.getPathingControlManager().registerProcess(escapeProcess);
            isActive = true;
            
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§a[EscapeMod] Baritone escape process started"), false);
            }
            
            return true;
        } catch (Exception e) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§c[EscapeMod] Failed to start escape process: " + e.getMessage()), false);
            }
            return false;
        }
    }
    
    /**
     * Stop all Baritone pathfinding
     */
    public void stop() {
        if (!isBaritoneAvailable()) return;
        
        try {
            baritone.getPathingBehavior().cancelEverything();
            baritone.getCustomGoalProcess().setGoal(null);
            
            if (escapeProcess != null) {
                baritone.getPathingControlManager().unregisterProcess(escapeProcess);
                escapeProcess = null;
            }
            
            isActive = false;
            currentGoal = null;
            
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§e[EscapeMod] Baritone pathfinding stopped"), false);
            }
        } catch (Exception e) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§c[EscapeMod] Error stopping Baritone: " + e.getMessage()), false);
            }
        }
    }
    
    /**
     * Pause pathfinding temporarily
     */
    public void pause() {
        if (isBaritoneAvailable() && isActive) {
            baritone.getPathingBehavior().requestPause();
        }
    }
    
    /**
     * Resume pathfinding
     */
    public void resume() {
        if (isBaritoneAvailable() && isActive) {
            baritone.getPathingBehavior().requestUnpause();
        }
    }
    
    /**
     * Check if Baritone is currently pathfinding
     */
    public boolean isPathfinding() {
        if (!isBaritoneAvailable()) return false;
        return baritone.getPathingBehavior().isPathing();
    }
    
    /**
     * Get current path from Baritone
     */
    public Optional<IPath> getCurrentPath() {
        if (!isBaritoneAvailable()) return Optional.empty();
        return Optional.ofNullable(baritone.getPathingBehavior().getPath());
    }
    
    /**
     * Get next position in path
     */
    public Optional<BlockPos> getNextPosition() {
        Optional<IPath> path = getCurrentPath();
        if (path.isEmpty()) return Optional.empty();
        
        IPath currentPath = path.get();
        if (currentPath.positions().isEmpty()) return Optional.empty();
        
        // Get next position in path
        List<BetterBlockPos> positions = currentPath.positions();
        if (positions.size() > 1) {
            BetterBlockPos next = positions.get(1); // Next position after current
            return Optional.of(new BlockPos(next.x, next.y, next.z));
        }
        
        return Optional.empty();
    }
    
    /**
     * Get estimated time to complete current path
     */
    public Optional<Double> getEstimatedTimeToGoal() {
        Optional<IPath> path = getCurrentPath();
        if (path.isEmpty()) return Optional.empty();
        
        return Optional.of(path.get().ticksRemainingInSegment() / 20.0); // Convert ticks to seconds
    }
    
    /**
     * Force Baritone to recalculate path
     */
    public void forceRecalculate() {
        if (isBaritoneAvailable() && currentGoal != null) {
            baritone.getPathingBehavior().forceCancel();
            baritone.getCustomGoalProcess().setGoalAndPath(currentGoal);
        }
    }
    
    /**
     * Set temporary goal (like avoiding danger)
     */
    public boolean setTemporaryGoal(Goal goal, int priority) {
        if (!isBaritoneAvailable()) return false;
        
        try {
            baritone.getCustomGoalProcess().setGoalAndPath(goal);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Avoid specific area
     */
    public void avoidArea(BlockPos center, int radius) {
        if (!isBaritoneAvailable()) return;
        
        // Add avoidance area to Baritone's avoidance system
        settings.avoidanceRadius.value = Math.max(settings.avoidanceRadius.value, radius);
    }
    
    /**
     * Check if Baritone is available and working
     */
    public boolean isBaritoneAvailable() {
        try {
            return baritone != null && BaritoneAPI.getProvider() != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get Baritone status information
     */
    public BaritoneStatus getStatus() {
        if (!isBaritoneAvailable()) {
            return new BaritoneStatus(false, false, null, null, 0);
        }
        
        boolean pathing = baritone.getPathingBehavior().isPathing();
        String currentGoalStr = currentGoal != null ? currentGoal.toString() : null;
        Optional<IPath> path = getCurrentPath();
        String pathInfo = path.map(p -> p.getNumNodesConsidered() + " nodes").orElse(null);
        double eta = getEstimatedTimeToGoal().orElse(0.0);
        
        return new BaritoneStatus(true, pathing, currentGoalStr, pathInfo, eta);
    }
    
    /**
     * Custom Baritone process for escape scenarios
     */
    private static class EscapeProcess implements IBaritoneProcess {
        private final IBaritone baritone;
        private final BlockPos finalTarget;
        private boolean isActive = true;
        
        public EscapeProcess(IBaritone baritone, BlockPos finalTarget) {
            this.baritone = baritone;
            this.finalTarget = finalTarget;
        }
        
        @Override
        public boolean isActive() {
            return isActive;
        }
        
        @Override
        public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
            if (!isActive) return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
            
            // Custom escape logic here
            Goal goal = new GoalXZ(finalTarget.getX(), finalTarget.getZ());
            return new PathingCommand(goal, PathingCommandType.SET_GOAL_AND_PATH);
        }
        
        @Override
        public void onLostControl() {
            isActive = false;
        }
        
        @Override
        public String displayName() {
            return "EscapeMod Escape Process";
        }
        
        @Override
        public boolean isTemporary() {
            return false;
        }
        
        @Override
        public double priority() {
            return 1000; // High priority
        }
        
        public void stop() {
            isActive = false;
        }
    }
    
    /**
     * Baritone status data class
     */
    public static class BaritoneStatus {
        public final boolean available;
        public final boolean pathing;
        public final String currentGoal;
        public final String pathInfo;
        public final double estimatedTimeToGoal;
        
        public BaritoneStatus(boolean available, boolean pathing, String currentGoal, 
                            String pathInfo, double estimatedTimeToGoal) {
            this.available = available;
            this.pathing = pathing;
            this.currentGoal = currentGoal;
            this.pathInfo = pathInfo;
            this.estimatedTimeToGoal = estimatedTimeToGoal;
        }
    }
    
    // Getters
    public boolean isActive() { return isActive; }
    public Goal getCurrentGoal() { return currentGoal; }
    public IBaritone getBaritone() { return baritone; }
    public BaritoneConfig getConfig() { return config; }
}