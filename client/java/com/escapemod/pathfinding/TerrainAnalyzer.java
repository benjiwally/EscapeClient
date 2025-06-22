package com.escapemod.pathfinding;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.world.biome.Biome;

/**
 * Terrain analysis system for optimal path planning
 * Helps choose the best direction based on terrain features
 */
public class TerrainAnalyzer {
    private final MinecraftClient client;
    
    public TerrainAnalyzer(MinecraftClient client) {
        this.client = client;
    }
    
    /**
     * Analyze terrain in a direction to determine travel difficulty
     * Returns a score from 0.0 (impossible) to 1.0 (perfect)
     */
    public double analyzeDirection(BlockPos start, Vec3d direction, int distance) {
        if (client.world == null) return 0.5;
        
        double totalScore = 0.0;
        int samples = 0;
        
        // Sample terrain at regular intervals
        for (int d = 100; d <= distance; d += 200) {
            BlockPos samplePos = new BlockPos(
                (int) (start.getX() + direction.x * d),
                start.getY(),
                (int) (start.getZ() + direction.z * d)
            );
            
            double sampleScore = analyzeTerrain(samplePos);
            totalScore += sampleScore;
            samples++;
        }
        
        return samples > 0 ? totalScore / samples : 0.5;
    }
    
    /**
     * Analyze terrain at a specific location
     */
    private double analyzeTerrain(BlockPos pos) {
        if (!client.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return 0.3; // Unknown terrain, slightly negative
        }
        
        double score = 0.5; // Base score
        
        // Check ground level variation (prefer flatter terrain)
        int groundLevel = findGroundLevel(pos);
        if (groundLevel == -1) {
            return 0.1; // No ground found (void/ocean)
        }
        
        // Check for water bodies
        if (isWaterArea(pos, groundLevel)) {
            score -= 0.3; // Avoid large water bodies
        }
        
        // Check for lava
        if (isLavaArea(pos, groundLevel)) {
            score -= 0.5; // Strongly avoid lava
        }
        
        // Check terrain roughness
        double roughness = calculateRoughness(pos, groundLevel);
        score -= roughness * 0.2; // Prefer smoother terrain
        
        // Check for obstacles
        if (hasMajorObstacles(pos, groundLevel)) {
            score -= 0.2;
        }
        
        // Prefer higher ground (better visibility, less mobs)
        if (groundLevel > 80) {
            score += 0.1;
        } else if (groundLevel < 40) {
            score -= 0.1;
        }
        
        return Math.max(0.0, Math.min(1.0, score));
    }
    
    /**
     * Find the ground level at a position
     */
    private int findGroundLevel(BlockPos pos) {
        World world = client.world;
        
        // Start from a reasonable height and go down
        for (int y = Math.min(pos.getY() + 50, 250); y >= -60; y--) {
            BlockPos checkPos = new BlockPos(pos.getX(), y, pos.getZ());
            Block block = world.getBlockState(checkPos).getBlock();
            
            if (block != Blocks.AIR && block != Blocks.WATER && 
                block != Blocks.LAVA && !isPassableBlock(block)) {
                return y;
            }
        }
        
        return -1; // No ground found
    }
    
    /**
     * Check if an area is mostly water
     */
    private boolean isWaterArea(BlockPos center, int groundLevel) {
        World world = client.world;
        int waterBlocks = 0;
        int totalBlocks = 0;
        
        // Check a 5x5 area
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos checkPos = new BlockPos(center.getX() + x, groundLevel + 1, center.getZ() + z);
                Block block = world.getBlockState(checkPos).getBlock();
                
                if (block == Blocks.WATER) {
                    waterBlocks++;
                }
                totalBlocks++;
            }
        }
        
        return (double) waterBlocks / totalBlocks > 0.6; // More than 60% water
    }
    
    /**
     * Check if an area has lava
     */
    private boolean isLavaArea(BlockPos center, int groundLevel) {
        World world = client.world;
        
        // Check a 3x3 area around the position
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = -2; y <= 2; y++) {
                    BlockPos checkPos = new BlockPos(center.getX() + x, groundLevel + y, center.getZ() + z);
                    Block block = world.getBlockState(checkPos).getBlock();
                    
                    if (block == Blocks.LAVA) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Calculate terrain roughness (height variation)
     */
    private double calculateRoughness(BlockPos center, int baseLevel) {
        World world = client.world;
        int totalVariation = 0;
        int samples = 0;
        
        // Check height variation in a 3x3 area
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue; // Skip center
                
                int groundLevel = findGroundLevel(new BlockPos(center.getX() + x, center.getY(), center.getZ() + z));
                if (groundLevel != -1) {
                    totalVariation += Math.abs(groundLevel - baseLevel);
                    samples++;
                }
            }
        }
        
        if (samples == 0) return 0.5;
        
        double avgVariation = (double) totalVariation / samples;
        return Math.min(1.0, avgVariation / 10.0); // Normalize to 0-1
    }
    
    /**
     * Check for major obstacles like mountains or structures
     */
    private boolean hasMajorObstacles(BlockPos center, int groundLevel) {
        World world = client.world;
        
        // Check for very tall structures
        for (int y = groundLevel + 1; y <= groundLevel + 20; y++) {
            BlockPos checkPos = new BlockPos(center.getX(), y, center.getZ());
            Block block = world.getBlockState(checkPos).getBlock();
            
            if (block != Blocks.AIR && !isPassableBlock(block)) {
                // Check if it's a large obstacle
                int solidBlocks = 0;
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        Block surroundingBlock = world.getBlockState(checkPos.add(x, 0, z)).getBlock();
                        if (surroundingBlock != Blocks.AIR && !isPassableBlock(surroundingBlock)) {
                            solidBlocks++;
                        }
                    }
                }
                
                if (solidBlocks > 6) { // Large solid structure
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check if a block is passable
     */
    private boolean isPassableBlock(Block block) {
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
    
    /**
     * Get the best direction from multiple options
     */
    public Vec3d getBestDirection(BlockPos start, Vec3d[] directions, int distance) {
        Vec3d bestDirection = directions[0];
        double bestScore = 0.0;
        
        for (Vec3d direction : directions) {
            double score = analyzeDirection(start, direction, distance);
            if (score > bestScore) {
                bestScore = score;
                bestDirection = direction;
            }
        }
        
        return bestDirection;
    }
}