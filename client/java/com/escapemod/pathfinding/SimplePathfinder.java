package com.escapemod.pathfinding;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

/**
 * Ultra-lightweight pathfinding for performance
 * Just basic obstacle avoidance and direction following
 */
public class SimplePathfinder {
    private final MinecraftClient client;
    
    public SimplePathfinder(MinecraftClient client) {
        this.client = client;
    }
    
    /**
     * Get next movement direction (no complex pathfinding)
     */
    public Vec3d getMovementDirection(BlockPos currentPos, Vec3d targetDirection) {
        if (client.world == null || targetDirection == null) {
            return null;
        }
        
        // Check if we can move in the target direction
        if (canMoveInDirection(currentPos, targetDirection)) {
            return targetDirection;
        }
        
        // Try slight variations if blocked
        Vec3d[] variations = {
            rotateDirection(targetDirection, 15),  // 15 degrees right
            rotateDirection(targetDirection, -15), // 15 degrees left
            rotateDirection(targetDirection, 30),  // 30 degrees right
            rotateDirection(targetDirection, -30), // 30 degrees left
        };
        
        for (Vec3d variation : variations) {
            if (canMoveInDirection(currentPos, variation)) {
                return variation;
            }
        }
        
        // If all else fails, return original direction
        return targetDirection;
    }
    
    /**
     * Check if we can move in a direction
     */
    private boolean canMoveInDirection(BlockPos pos, Vec3d direction) {
        Vec3d checkPos = Vec3d.ofCenter(pos).add(direction.multiply(2.0));
        BlockPos blockPos = BlockPos.ofFloored(checkPos);
        
        // Check feet and head level
        Block feetBlock = client.world.getBlockState(blockPos).getBlock();
        Block headBlock = client.world.getBlockState(blockPos.up()).getBlock();
        
        // Must have air or passable blocks
        return isPassable(feetBlock) && isPassable(headBlock);
    }
    
    /**
     * Check if we should jump
     */
    public boolean shouldJump(BlockPos pos, Vec3d direction) {
        if (client.world == null) return false;
        
        Vec3d checkPos = Vec3d.ofCenter(pos).add(direction.multiply(1.5));
        BlockPos blockPos = BlockPos.ofFloored(checkPos);
        
        // Check for obstacle at feet level
        Block feetBlock = client.world.getBlockState(blockPos).getBlock();
        if (!isPassable(feetBlock)) {
            return true;
        }
        
        // Check if we need to jump up
        Block groundBlock = client.world.getBlockState(blockPos.down()).getBlock();
        if (groundBlock == Blocks.AIR) {
            // Check if there's a block to jump onto nearby
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    Block nearbyBlock = client.world.getBlockState(blockPos.add(x, 0, z)).getBlock();
                    if (!isPassable(nearbyBlock)) {
                        return true; // Jump to get onto the block
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Rotate direction by degrees
     */
    private Vec3d rotateDirection(Vec3d direction, double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        
        double newX = direction.x * cos - direction.z * sin;
        double newZ = direction.x * sin + direction.z * cos;
        
        return new Vec3d(newX, 0, newZ).normalize();
    }
    
    /**
     * Check if a block is passable
     */
    private boolean isPassable(Block block) {
        return block == Blocks.AIR ||
               block == Blocks.WATER ||
               block == Blocks.SHORT_GRASS ||
               block == Blocks.TALL_GRASS ||
               block == Blocks.FERN ||
               block == Blocks.LARGE_FERN ||
               block == Blocks.DEAD_BUSH ||
               block == Blocks.VINE ||
               block == Blocks.SNOW;
    }
}