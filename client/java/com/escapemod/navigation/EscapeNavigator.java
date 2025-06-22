package com.escapemod.navigation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;

import com.escapemod.pathfinding.SimplePathfinder;
import com.escapemod.navigation.BaritonePathfinder;
import com.escapemod.utils.MessageThrottler;

import java.util.Random;

/**
 * Intelligent navigation system for escaping to 30k blocks from spawn
 * Calculates optimal direction and manages long-distance pathfinding
 */
public class EscapeNavigator {
    private static final int TARGET_DISTANCE = 30000; // 30k blocks from 0,0
    private static final int WAYPOINT_DISTANCE = 2000; // Create waypoints every 2k blocks (reduced frequency)
    private static final int RECALCULATION_DISTANCE = 500; // Recalculate path every 500 blocks (reduced frequency)
    
    private final MinecraftClient client;
    private final SimplePathfinder simplePathfinder;
    private final BaritonePathfinder baritonePathfinder;
    private boolean useBaritone = true; // Prefer Baritone when available
    private final Random random;
    
    private BlockPos spawnPos;
    private BlockPos finalTarget;
    private BlockPos currentWaypoint;
    private Vec3d optimalDirection;
    private boolean isNavigating;
    private int lastRecalculationDistance;
    
    public EscapeNavigator(MinecraftClient client) {
        this.client = client;
        this.simplePathfinder = new SimplePathfinder(client);
        this.baritonePathfinder = new BaritonePathfinder(client);
        
        // Check if Baritone is available
        if (!baritonePathfinder.isBaritoneAvailable()) {
            useBaritone = false;
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§e[EscapeMod] Baritone not detected, using simple pathfinding"), false);
            }
        } else {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§a[EscapeMod] Baritone integration active!"), false);
            }
        }
        this.random = new Random();
        this.isNavigating = false;
    }
    
    /**
     * Start navigation to 30k blocks from spawn
     */
    public void startEscape(BlockPos playerSpawn) {
        this.spawnPos = playerSpawn;
        this.isNavigating = true;
        
        // Calculate optimal escape direction
        this.optimalDirection = calculateOptimalDirection(playerSpawn);
        
        // Calculate final target position
        this.finalTarget = new BlockPos(
            (int) (spawnPos.getX() + optimalDirection.x * TARGET_DISTANCE),
            spawnPos.getY(),
            (int) (spawnPos.getZ() + optimalDirection.z * TARGET_DISTANCE)
        );
        
        // Set initial waypoint
        updateWaypoint(playerSpawn);
        
        if (client.player != null) {
            MessageThrottler.sendThrottledMessage(client.player, "escape_start",
                Text.literal("§a[EscapeMod] Starting escape to 30k blocks!"), false, 5000);
            MessageThrottler.sendThrottledMessage(client.player, "escape_direction",
                Text.literal("§e[EscapeMod] Target: " + finalTarget.getX() + ", " + finalTarget.getZ()), false, 5000);
            MessageThrottler.sendThrottledMessage(client.player, "escape_direction2",
                Text.literal("§e[EscapeMod] Direction: " + String.format("%.2f, %.2f", optimalDirection.x, optimalDirection.z)), false, 5000);
        }
    }
    
    /**
     * Calculate optimal direction using terrain analysis and strategic considerations
     */
    private Vec3d calculateOptimalDirection(BlockPos spawn) {
        // Generate candidate directions (8 main directions + variations)
        Vec3d[] candidateDirections = new Vec3d[12];
        
        // Primary diagonal directions (preferred for anarchy servers)
        candidateDirections[0] = new Vec3d(1, 0, 1).normalize();   // Northeast
        candidateDirections[1] = new Vec3d(-1, 0, 1).normalize();  // Northwest
        candidateDirections[2] = new Vec3d(-1, 0, -1).normalize(); // Southwest
        candidateDirections[3] = new Vec3d(1, 0, -1).normalize();  // Southeast
        
        // Secondary cardinal directions
        candidateDirections[4] = new Vec3d(1, 0, 0).normalize();   // East
        candidateDirections[5] = new Vec3d(-1, 0, 0).normalize();  // West
        candidateDirections[6] = new Vec3d(0, 0, 1).normalize();   // North
        candidateDirections[7] = new Vec3d(0, 0, -1).normalize();  // South
        
        // Randomized variations of best directions
        for (int i = 8; i < 12; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            candidateDirections[i] = new Vec3d(Math.cos(angle), 0, Math.sin(angle)).normalize();
        }
        
        // Use simple direction selection for performance (terrain analysis disabled by default)
        Vec3d bestDirection = candidateDirections[random.nextInt(4)]; // Pick from first 4 (diagonal directions)
        
        // Add some strategic considerations
        bestDirection = applyStrategicAdjustments(bestDirection, spawn);
        
        return bestDirection;
    }
    
    /**
     * Apply strategic adjustments to the chosen direction
     */
    private Vec3d applyStrategicAdjustments(Vec3d direction, BlockPos spawn) {
        // Avoid going directly towards common coordinates like 0,0 or world border
        double x = direction.x;
        double z = direction.z;
        
        // If too close to cardinal directions, add some angle
        if (Math.abs(x) < 0.1) { // Too close to North/South
            x += (random.nextDouble() - 0.5) * 0.3;
        }
        if (Math.abs(z) < 0.1) { // Too close to East/West
            z += (random.nextDouble() - 0.5) * 0.3;
        }
        
        // Prefer directions that lead away from 0,0 if spawn is not at 0,0
        if (spawn.getX() != 0 || spawn.getZ() != 0) {
            Vec3d awayFromOrigin = new Vec3d(-spawn.getX(), 0, -spawn.getZ()).normalize();
            // Blend 70% chosen direction with 30% away-from-origin
            x = x * 0.7 + awayFromOrigin.x * 0.3;
            z = z * 0.7 + awayFromOrigin.z * 0.3;
        }
        
        return new Vec3d(x, 0, z).normalize();
    }
    
    /**
     * Update navigation waypoint based on current position
     */
    private void updateWaypoint(BlockPos currentPos) {
        double currentDistance = getDistanceFromSpawn(currentPos);
        double targetDistance = Math.min(currentDistance + WAYPOINT_DISTANCE, TARGET_DISTANCE);
        
        // Calculate waypoint position
        BlockPos waypoint = new BlockPos(
            (int) (spawnPos.getX() + optimalDirection.x * targetDistance),
            currentPos.getY(), // Keep similar Y level
            (int) (spawnPos.getZ() + optimalDirection.z * targetDistance)
        );
        
        this.currentWaypoint = waypoint;
        
        // Start pathfinding to the new waypoint
        if (useBaritone && baritonePathfinder.isBaritoneAvailable()) {
            baritonePathfinder.pathTo(waypoint);
        }
        
        if (client.player != null) {
            String pathfinder = (useBaritone && baritonePathfinder.isBaritoneAvailable()) ? "Baritone" : "Simple";
            MessageThrottler.sendThrottledMessage(client.player, "waypoint_update",
                Text.literal("§e[EscapeMod] New waypoint: " + waypoint.getX() + ", " + waypoint.getZ() + 
                    " (Distance: " + (int)targetDistance + ") [" + pathfinder + "]"), false, 3000);
        }
    }
    
    /**
     * Tick the navigation system
     */
    public void tick() {
        if (!isNavigating || client.player == null) return;
        
        BlockPos playerPos = client.player.getBlockPos();
        double distanceFromSpawn = getDistanceFromSpawn(playerPos);
        
        // Tick the active pathfinder
        if (useBaritone && baritonePathfinder.isBaritoneAvailable()) {
            baritonePathfinder.tick();
        }
        
        // Check if we've reached the target
        if (distanceFromSpawn >= TARGET_DISTANCE) {
            completeEscape();
            return;
        }
        
        // Check if we need to recalculate path
        if (shouldRecalculatePath(playerPos)) {
            recalculatePath(playerPos);
        }
        
        // Update waypoint if we're close to current one
        if (currentWaypoint != null && playerPos.getSquaredDistance(currentWaypoint) < WAYPOINT_DISTANCE * WAYPOINT_DISTANCE / 4) {
            updateWaypoint(playerPos);
            recalculatePath(playerPos);
        }
    }
    
    /**
     * Check if we need to update navigation
     */
    private boolean shouldRecalculatePath(BlockPos playerPos) {
        double currentDistance = getDistanceFromSpawn(playerPos);
        return Math.abs(currentDistance - lastRecalculationDistance) > RECALCULATION_DISTANCE;
    }
    
    /**
     * Update navigation direction
     */
    private void recalculatePath(BlockPos playerPos) {
        lastRecalculationDistance = (int) getDistanceFromSpawn(playerPos);
        
        if (client.player != null) {
            MessageThrottler.sendThrottledMessage(client.player, "navigation_update",
                Text.literal("§a[EscapeMod] Navigation updated"), true, 10000);
        }
    }
    
    /**
     * Complete the escape sequence
     */
    private void completeEscape() {
        isNavigating = false;
        
        if (client.player != null) {
            MessageThrottler.sendThrottledMessage(client.player, "escape_complete",
                Text.literal("§a§l[EscapeMod] ESCAPE COMPLETE! You are now 30k+ blocks from spawn!"), false, 10000);
            MessageThrottler.sendThrottledMessage(client.player, "escape_stats",
                Text.literal("§e[EscapeMod] Final distance: " + (int)getDistanceFromSpawn(client.player.getBlockPos()) + " blocks"), false, 10000);
        }
    }
    
    /**
     * Get next movement direction
     */
    public Vec3d getNextMovementDirection(BlockPos currentPos) {
        if (!isNavigating || optimalDirection == null) return null;
        
        // Use Baritone if available, otherwise fall back to simple pathfinder
        if (useBaritone && baritonePathfinder.isBaritoneAvailable()) {
            return baritonePathfinder.getMovementDirection(currentPos, optimalDirection);
        } else {
            return simplePathfinder.getMovementDirection(currentPos, optimalDirection);
        }
    }
    
    /**
     * Check if should jump
     */
    public boolean shouldJump(BlockPos currentPos) {
        if (!isNavigating || optimalDirection == null) return false;
        
        // Use Baritone if available, otherwise fall back to simple pathfinder
        if (useBaritone && baritonePathfinder.isBaritoneAvailable()) {
            return baritonePathfinder.shouldJump(currentPos, optimalDirection);
        } else {
            return simplePathfinder.shouldJump(currentPos, optimalDirection);
        }
    }
    
    /**
     * Get distance from spawn (0,0)
     */
    public double getDistanceFromSpawn(BlockPos pos) {
        if (spawnPos == null) {
            // Fallback to 0,0 if spawn not set
            return Math.sqrt(pos.getX() * pos.getX() + pos.getZ() * pos.getZ());
        }
        
        double dx = pos.getX() - spawnPos.getX();
        double dz = pos.getZ() - spawnPos.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
    
    /**
     * Get progress percentage
     */
    public double getProgressPercentage(BlockPos pos) {
        double currentDistance = getDistanceFromSpawn(pos);
        return Math.min((currentDistance / TARGET_DISTANCE) * 100.0, 100.0);
    }
    
    /**
     * Check if currently navigating
     */
    public boolean isNavigating() {
        return isNavigating;
    }
    
    /**
     * Stop navigation
     */
    public void stopNavigation() {
        isNavigating = false;
        currentWaypoint = null;
        
        // Stop both pathfinders
        if (useBaritone && baritonePathfinder.isBaritoneAvailable()) {
            baritonePathfinder.stop();
        }
        simplePathfinder.stop();
    }
    
    /**
     * Get current pathfinder (returns active one)
     */
    public Object getPathfinder() {
        if (useBaritone && baritonePathfinder.isBaritoneAvailable()) {
            return baritonePathfinder;
        } else {
            return simplePathfinder;
        }
    }
    
    /**
     * Get Baritone pathfinder
     */
    public BaritonePathfinder getBaritonePathfinder() {
        return baritonePathfinder;
    }
    
    /**
     * Get simple pathfinder
     */
    public SimplePathfinder getSimplePathfinder() {
        return simplePathfinder;
    }
    
    /**
     * Toggle between Baritone and simple pathfinding
     */
    public void toggleBaritone() {
        if (baritonePathfinder.isBaritoneAvailable()) {
            useBaritone = !useBaritone;
            
            if (client.player != null) {
                String mode = useBaritone ? "Baritone" : "Simple";
                client.player.sendMessage(Text.literal("§e[EscapeMod] Switched to " + mode + " pathfinding"), false);
            }
            
            // If switching while navigating, restart with new pathfinder
            if (isNavigating && currentWaypoint != null) {
                startNavigation(currentWaypoint);
            }
        } else {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§c[EscapeMod] Baritone not available"), false);
            }
        }
    }
    
    /**
     * Check if using Baritone
     */
    public boolean isUsingBaritone() {
        return useBaritone && baritonePathfinder.isBaritoneAvailable();
    }
    
    /**
     * Get current waypoint
     */
    public BlockPos getCurrentWaypoint() {
        return currentWaypoint;
    }
    
    /**
     * Get final target
     */
    public BlockPos getFinalTarget() {
        return finalTarget;
    }
    
    /**
     * Get optimal direction
     */
    public Vec3d getOptimalDirection() {
        return optimalDirection;
    }
    
    /**
     * Get target distance
     */
    public int getTargetDistance() {
        return TARGET_DISTANCE;
    }
}