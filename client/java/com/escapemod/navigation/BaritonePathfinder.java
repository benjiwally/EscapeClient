package com.escapemod.navigation;

import com.escapemod.integration.BaritoneIntegration;
import baritone.api.pathing.calc.IPath;
import baritone.api.utils.BetterBlockPos;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Baritone-powered pathfinder for EscapeMod
 * Provides professional-grade pathfinding using Baritone API
 */
public class BaritonePathfinder {
    private final MinecraftClient client;
    private final BaritoneIntegration baritone;
    
    private BlockPos currentTarget = null;
    private boolean isPathfinding = false;
    private long lastPathUpdate = 0;
    private int pathfindingFailures = 0;
    
    private static final long PATH_UPDATE_INTERVAL = 1000; // 1 second
    private static final int MAX_PATHFINDING_FAILURES = 3;
    
    public BaritonePathfinder(MinecraftClient client) {
        this.client = client;
        this.baritone = new BaritoneIntegration(client);
    }
    
    /**
     * Start pathfinding to target position
     */
    public boolean pathTo(BlockPos target) {
        if (!baritone.isBaritoneAvailable()) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§c[EscapeMod] Baritone not available, using fallback navigation"), false);
            }
            return false;
        }
        
        currentTarget = target;
        isPathfinding = true;
        pathfindingFailures = 0;
        lastPathUpdate = System.currentTimeMillis();
        
        boolean success = baritone.pathTo(target);
        if (!success) {
            pathfindingFailures++;
            if (pathfindingFailures >= MAX_PATHFINDING_FAILURES) {
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§c[EscapeMod] Baritone pathfinding failed, switching to simple navigation"), false);
                }
                return false;
            }
        }
        
        return success;
    }
    
    /**
     * Start pathfinding in a direction for a distance
     */
    public boolean pathInDirection(Vec3d direction, int distance) {
        if (!baritone.isBaritoneAvailable()) return false;
        
        isPathfinding = true;
        pathfindingFailures = 0;
        lastPathUpdate = System.currentTimeMillis();
        
        return baritone.pathInDirection(direction, distance);
    }
    
    /**
     * Start escape process with intelligent waypoint navigation
     */
    public boolean startEscapeProcess(BlockPos finalTarget) {
        if (!baritone.isBaritoneAvailable()) return false;
        
        currentTarget = finalTarget;
        isPathfinding = true;
        pathfindingFailures = 0;
        lastPathUpdate = System.currentTimeMillis();
        
        return baritone.startEscapeProcess(finalTarget);
    }
    
    /**
     * Get next movement direction from Baritone
     */
    public Vec3d getMovementDirection(BlockPos currentPos, Vec3d fallbackDirection) {
        if (!baritone.isBaritoneAvailable() || !isPathfinding) {
            return fallbackDirection;
        }
        
        // Get next position from Baritone path
        Optional<BlockPos> nextPos = baritone.getNextPosition();
        if (nextPos.isPresent()) {
            Vec3d direction = Vec3d.ofCenter(nextPos.get()).subtract(Vec3d.ofCenter(currentPos)).normalize();
            return direction;
        }
        
        // If no path available, use fallback
        return fallbackDirection;
    }
    
    /**
     * Check if should jump based on Baritone's path
     */
    public boolean shouldJump(BlockPos currentPos, Vec3d fallbackDirection) {
        if (!baritone.isBaritoneAvailable() || !isPathfinding) {
            return false; // Let simple pathfinder handle jumping
        }
        
        // Get next position from Baritone
        Optional<BlockPos> nextPos = baritone.getNextPosition();
        if (nextPos.isPresent()) {
            BlockPos next = nextPos.get();
            // Jump if next position is higher
            return next.getY() > currentPos.getY();
        }
        
        return false;
    }
    
    /**
     * Get current path from Baritone
     */
    public List<BlockPos> getCurrentPath() {
        if (!baritone.isBaritoneAvailable()) return new ArrayList<>();
        
        Optional<IPath> path = baritone.getCurrentPath();
        if (path.isEmpty()) return new ArrayList<>();
        
        List<BlockPos> positions = new ArrayList<>();
        for (BetterBlockPos pos : path.get().positions()) {
            positions.add(new BlockPos(pos.x, pos.y, pos.z));
        }
        
        return positions;
    }
    
    /**
     * Check if currently pathfinding
     */
    public boolean isPathfinding() {
        if (!baritone.isBaritoneAvailable()) return false;
        return baritone.isPathfinding() && isPathfinding;
    }
    
    /**
     * Check if has a valid path
     */
    public boolean hasPath() {
        if (!baritone.isBaritoneAvailable()) return false;
        return baritone.getCurrentPath().isPresent();
    }
    
    /**
     * Stop pathfinding
     */
    public void stop() {
        if (baritone.isBaritoneAvailable()) {
            baritone.stop();
        }
        isPathfinding = false;
        currentTarget = null;
        pathfindingFailures = 0;
    }
    
    /**
     * Pause pathfinding temporarily
     */
    public void pause() {
        if (baritone.isBaritoneAvailable()) {
            baritone.pause();
        }
    }
    
    /**
     * Resume pathfinding
     */
    public void resume() {
        if (baritone.isBaritoneAvailable()) {
            baritone.resume();
        }
    }
    
    /**
     * Force recalculation of path
     */
    public void recalculatePath() {
        if (baritone.isBaritoneAvailable() && currentTarget != null) {
            baritone.forceRecalculate();
        }
    }
    
    /**
     * Handle emergency situations (crisis mode, stuck, etc.)
     */
    public boolean handleEmergency(BlockPos safePosition) {
        if (!baritone.isBaritoneAvailable()) return false;
        
        // Stop current pathfinding
        baritone.stop();
        
        // Path to safe position with high priority
        boolean success = baritone.pathTo(safePosition);
        if (success) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§e[EscapeMod] Baritone emergency navigation to safe position"), false);
            }
        }
        
        return success;
    }
    
    /**
     * Avoid dangerous area
     */
    public void avoidArea(BlockPos center, int radius) {
        if (baritone.isBaritoneAvailable()) {
            baritone.avoidArea(center, radius);
        }
    }
    
    /**
     * Update pathfinding state
     */
    public void tick() {
        if (!baritone.isBaritoneAvailable() || !isPathfinding) return;
        
        long currentTime = System.currentTimeMillis();
        
        // Check if pathfinding failed
        if (!baritone.isPathfinding() && currentTarget != null && 
            currentTime - lastPathUpdate > PATH_UPDATE_INTERVAL) {
            
            pathfindingFailures++;
            
            if (pathfindingFailures >= MAX_PATHFINDING_FAILURES) {
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§c[EscapeMod] Baritone pathfinding failed multiple times"), false);
                }
                stop();
                return;
            }
            
            // Try to restart pathfinding
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§e[EscapeMod] Restarting Baritone pathfinding (attempt " + 
                    (pathfindingFailures + 1) + "/" + MAX_PATHFINDING_FAILURES + ")"), false);
            }
            
            baritone.pathTo(currentTarget);
            lastPathUpdate = currentTime;
        }
        
        // Check if reached target
        if (client.player != null && currentTarget != null) {
            double distance = client.player.getBlockPos().getSquaredDistance(currentTarget);
            if (distance < 9) { // Within 3 blocks
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§a[EscapeMod] Baritone reached target position"), false);
                }
                stop();
            }
        }
    }
    
    /**
     * Get pathfinding statistics
     */
    public PathfindingStats getStats() {
        if (!baritone.isBaritoneAvailable()) {
            return new PathfindingStats(false, false, 0, 0, 0.0, "Baritone not available");
        }
        
        BaritoneIntegration.BaritoneStatus status = baritone.getStatus();
        List<BlockPos> path = getCurrentPath();
        
        return new PathfindingStats(
            status.available,
            status.pathing,
            path.size(),
            pathfindingFailures,
            status.estimatedTimeToGoal,
            status.currentGoal
        );
    }
    
    /**
     * Check if Baritone is available
     */
    public boolean isBaritoneAvailable() {
        return baritone.isBaritoneAvailable();
    }
    
    /**
     * Get Baritone integration instance
     */
    public BaritoneIntegration getBaritoneIntegration() {
        return baritone;
    }
    
    /**
     * Pathfinding statistics data class
     */
    public static class PathfindingStats {
        public final boolean baritoneAvailable;
        public final boolean isPathing;
        public final int pathLength;
        public final int failures;
        public final double estimatedTime;
        public final String currentGoal;
        
        public PathfindingStats(boolean baritoneAvailable, boolean isPathing, int pathLength, 
                              int failures, double estimatedTime, String currentGoal) {
            this.baritoneAvailable = baritoneAvailable;
            this.isPathing = isPathing;
            this.pathLength = pathLength;
            this.failures = failures;
            this.estimatedTime = estimatedTime;
            this.currentGoal = currentGoal;
        }
    }
}