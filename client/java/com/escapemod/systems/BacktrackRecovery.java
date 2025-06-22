package com.escapemod.systems;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.text.Text;

import java.util.*;

/**
 * Backtrack Recovery System - Intelligently escapes from traps and dead ends
 * Maintains path history and can retrace steps when stuck
 */
public class BacktrackRecovery {
    private final MinecraftClient client;
    private final Deque<PathPoint> pathHistory = new ArrayDeque<>();
    private final Set<BlockPos> stuckPositions = new HashSet<>();
    
    private boolean isRecovering = false;
    private BlockPos lastSafePosition = null;
    private long lastMovementTime = 0;
    private int stuckCounter = 0;
    private int suffocationCounter = 0;
    
    private static final int MAX_HISTORY_SIZE = 1000;
    private static final int STUCK_THRESHOLD = 100; // 5 seconds at 20 TPS
    private static final int SUFFOCATION_THRESHOLD = 60; // 3 seconds
    
    public BacktrackRecovery(MinecraftClient client) {
        this.client = client;
    }
    
    /**
     * Record current position in path history
     */
    public void recordPosition(BlockPos pos, long timestamp) {
        pathHistory.addLast(new PathPoint(pos, timestamp, isSafePosition(pos)));
        
        // Limit history size
        while (pathHistory.size() > MAX_HISTORY_SIZE) {
            pathHistory.removeFirst();
        }
        
        // Update last safe position
        if (isSafePosition(pos)) {
            lastSafePosition = pos;
        }
    }
    
    /**
     * Check if player is stuck and needs recovery
     */
    public boolean needsRecovery() {
        if (client.player == null) return false;
        
        ClientPlayerEntity player = client.player;
        BlockPos currentPos = player.getBlockPos();
        
        // Check for suffocation
        if (isSuffocating(currentPos)) {
            suffocationCounter++;
            if (suffocationCounter > SUFFOCATION_THRESHOLD) {
                return true;
            }
        } else {
            suffocationCounter = 0;
        }
        
        // Check if stuck in same position
        if (hasMovedRecently(currentPos)) {
            lastMovementTime = System.currentTimeMillis();
            stuckCounter = 0;
        } else {
            stuckCounter++;
            if (stuckCounter > STUCK_THRESHOLD) {
                stuckPositions.add(currentPos);
                return true;
            }
        }
        
        // Check if in a known stuck position
        if (stuckPositions.contains(currentPos)) {
            return true;
        }
        
        // Check if in a dead end
        if (isInDeadEnd(currentPos)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Execute recovery procedure
     */
    public RecoveryAction executeRecovery() {
        if (client.player == null) return RecoveryAction.NONE;
        
        ClientPlayerEntity player = client.player;
        BlockPos currentPos = player.getBlockPos();
        
        isRecovering = true;
        
        // Priority 1: Escape suffocation immediately
        if (isSuffocating(currentPos)) {
            return handleSuffocation(currentPos);
        }
        
        // Priority 2: Find recent safe position to backtrack to
        BlockPos safePos = findRecentSafePosition();
        if (safePos != null && !safePos.equals(currentPos)) {
            return RecoveryAction.BACKTRACK_TO_SAFE;
        }
        
        // Priority 3: Try to dig out of current situation
        if (canDigOut(currentPos)) {
            return RecoveryAction.DIG_OUT;
        }
        
        // Priority 4: Emergency portal escape (if in nether)
        if (isInNether() && hasObsidian()) {
            return RecoveryAction.EMERGENCY_PORTAL;
        }
        
        // Priority 5: Random walk to escape local area
        return RecoveryAction.RANDOM_WALK;
    }
    
    /**
     * Handle suffocation emergency
     */
    private RecoveryAction handleSuffocation(BlockPos pos) {
        if (client.player == null) return RecoveryAction.NONE;
        
        // Try to dig up first
        Block aboveBlock = client.world.getBlockState(pos.up()).getBlock();
        if (canBreakBlock(aboveBlock)) {
            return RecoveryAction.DIG_UP;
        }
        
        // Try to dig horizontally
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                
                BlockPos digPos = pos.add(dx, 0, dz);
                Block block = client.world.getBlockState(digPos).getBlock();
                if (canBreakBlock(block)) {
                    return RecoveryAction.DIG_HORIZONTAL;
                }
            }
        }
        
        // Emergency teleport if available (ender pearl)
        if (hasEnderPearl()) {
            return RecoveryAction.ENDER_PEARL_ESCAPE;
        }
        
        return RecoveryAction.PANIC_DIG;
    }
    
    /**
     * Check if player is suffocating
     */
    private boolean isSuffocating(BlockPos pos) {
        if (client.world == null || client.player == null) return false;
        
        // Check if head is in solid block
        Block headBlock = client.world.getBlockState(pos.up()).getBlock();
        Block feetBlock = client.world.getBlockState(pos).getBlock();
        
        return !isPassableBlock(headBlock) || !isPassableBlock(feetBlock);
    }
    
    /**
     * Check if player has moved recently
     */
    private boolean hasMovedRecently(BlockPos currentPos) {
        if (pathHistory.isEmpty()) return true;
        
        PathPoint lastPoint = pathHistory.peekLast();
        return !lastPoint.position.equals(currentPos);
    }
    
    /**
     * Check if position is in a dead end
     */
    private boolean isInDeadEnd(BlockPos pos) {
        if (client.world == null) return false;
        
        int passableDirections = 0;
        
        // Check 4 cardinal directions
        int[][] directions = {{1,0}, {-1,0}, {0,1}, {0,-1}};
        
        for (int[] dir : directions) {
            BlockPos checkPos = pos.add(dir[0], 0, dir[1]);
            if (isPassableArea(checkPos)) {
                passableDirections++;
            }
        }
        
        // Dead end if only 1 or 0 passable directions
        return passableDirections <= 1;
    }
    
    /**
     * Find most recent safe position to backtrack to
     */
    private BlockPos findRecentSafePosition() {
        // Look through recent history for safe positions
        Iterator<PathPoint> it = pathHistory.descendingIterator();
        
        while (it.hasNext()) {
            PathPoint point = it.next();
            if (point.isSafe && !stuckPositions.contains(point.position)) {
                // Verify position is still safe
                if (isSafePosition(point.position)) {
                    return point.position;
                }
            }
        }
        
        return lastSafePosition;
    }
    
    /**
     * Check if we can dig out of current situation
     */
    private boolean canDigOut(BlockPos pos) {
        if (client.world == null) return false;
        
        // Check if we have tools and can break surrounding blocks
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    
                    BlockPos digPos = pos.add(dx, dy, dz);
                    Block block = client.world.getBlockState(digPos).getBlock();
                    
                    if (canBreakBlock(block) && wouldCreateEscape(digPos)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check if breaking this block would create an escape route
     */
    private boolean wouldCreateEscape(BlockPos pos) {
        if (client.world == null) return false;
        
        // Check if breaking this block connects to open area
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos checkPos = pos.add(dx, 0, dz);
                if (isPassableArea(checkPos) && !stuckPositions.contains(checkPos)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check if position is safe (not trapped, has escape routes)
     */
    private boolean isSafePosition(BlockPos pos) {
        if (client.world == null) return false;
        
        // Must have air to breathe
        if (!isPassableBlock(client.world.getBlockState(pos).getBlock()) ||
            !isPassableBlock(client.world.getBlockState(pos.up()).getBlock())) {
            return false;
        }
        
        // Must have solid ground
        Block ground = client.world.getBlockState(pos.down()).getBlock();
        if (ground == Blocks.AIR || ground == Blocks.LAVA) {
            return false;
        }
        
        // Must have at least 2 escape directions
        int escapeRoutes = 0;
        int[][] directions = {{1,0}, {-1,0}, {0,1}, {0,-1}};
        
        for (int[] dir : directions) {
            if (isPassableArea(pos.add(dir[0], 0, dir[1]))) {
                escapeRoutes++;
            }
        }
        
        return escapeRoutes >= 2;
    }
    
    /**
     * Check if area around position is passable
     */
    private boolean isPassableArea(BlockPos pos) {
        if (client.world == null) return false;
        
        return isPassableBlock(client.world.getBlockState(pos).getBlock()) &&
               isPassableBlock(client.world.getBlockState(pos.up()).getBlock());
    }
    
    /**
     * Check if block is passable
     */
    private boolean isPassableBlock(Block block) {
        return block == Blocks.AIR ||
               block == Blocks.WATER ||
               block == Blocks.SHORT_GRASS ||
               block == Blocks.TALL_GRASS ||
               block == Blocks.FERN ||
               block == Blocks.VINE ||
               block == Blocks.SNOW;
    }
    
    /**
     * Check if block can be broken
     */
    private boolean canBreakBlock(Block block) {
        // Can't break bedrock, obsidian without diamond pick, etc.
        return block != Blocks.BEDROCK &&
               block != Blocks.BARRIER &&
               block != Blocks.COMMAND_BLOCK &&
               block != Blocks.STRUCTURE_BLOCK;
    }
    
    /**
     * Utility checks
     */
    private boolean isInNether() {
        return client.world != null && client.world.getRegistryKey().getValue().getPath().equals("the_nether");
    }
    
    private boolean hasObsidian() {
        // Check inventory for obsidian (simplified)
        return client.player != null && client.player.getInventory().count(Blocks.OBSIDIAN.asItem()) >= 10;
    }
    
    private boolean hasEnderPearl() {
        // Check inventory for ender pearls (simplified)
        return client.player != null && client.player.getInventory().count(net.minecraft.item.Items.ENDER_PEARL) > 0;
    }
    
    /**
     * Get recovery path to safe position
     */
    public List<BlockPos> getRecoveryPath(BlockPos target) {
        List<BlockPos> path = new ArrayList<>();
        
        if (client.player == null) return path;
        
        BlockPos current = client.player.getBlockPos();
        
        // Simple pathfinding back to target
        while (!current.equals(target) && path.size() < 100) {
            Vec3d direction = Vec3d.ofCenter(target).subtract(Vec3d.ofCenter(current)).normalize();
            
            BlockPos next = current.add(
                (int) Math.signum(direction.x),
                0,
                (int) Math.signum(direction.z)
            );
            
            if (isPassableArea(next)) {
                path.add(next);
                current = next;
            } else {
                break; // Can't reach target directly
            }
        }
        
        return path;
    }
    
    /**
     * Reset recovery state
     */
    public void resetRecovery() {
        isRecovering = false;
        stuckCounter = 0;
        suffocationCounter = 0;
    }
    
    /**
     * Clear stuck positions (when successfully escaped)
     */
    public void clearStuckPositions() {
        stuckPositions.clear();
    }
    
    // Getters
    public boolean isRecovering() { return isRecovering; }
    public BlockPos getLastSafePosition() { return lastSafePosition; }
    public int getPathHistorySize() { return pathHistory.size(); }
    
    /**
     * Path point data class
     */
    private static class PathPoint {
        final BlockPos position;
        final long timestamp;
        final boolean isSafe;
        
        PathPoint(BlockPos position, long timestamp, boolean isSafe) {
            this.position = position;
            this.timestamp = timestamp;
            this.isSafe = isSafe;
        }
    }
    
    /**
     * Recovery action types
     */
    public enum RecoveryAction {
        NONE,
        BACKTRACK_TO_SAFE,
        DIG_OUT,
        DIG_UP,
        DIG_HORIZONTAL,
        EMERGENCY_PORTAL,
        ENDER_PEARL_ESCAPE,
        RANDOM_WALK,
        PANIC_DIG
    }
}