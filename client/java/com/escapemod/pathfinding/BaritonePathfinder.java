package com.escapemod.pathfinding;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.world.World;

import java.util.*;

/**
 * Baritone-inspired pathfinding system adapted for EscapeMod
 * Uses A* algorithm with intelligent heuristics for long-distance travel
 */
public class BaritonePathfinder {
    private static final int MAX_SEARCH_NODES = 1000; // Reduced from 10000
    private static final int CHUNK_SIZE = 16;
    private static final double COST_HEURISTIC_WEIGHT = 1.5;
    private static final int MAX_CALCULATION_TIME_MS = 50; // Max 50ms per calculation
    
    private final MinecraftClient client;
    private List<BlockPos> currentPath;
    private BlockPos targetPos;
    private boolean isCalculating;
    
    public BaritonePathfinder(MinecraftClient client) {
        this.client = client;
        this.currentPath = new ArrayList<>();
        this.isCalculating = false;
    }
    
    /**
     * Calculate path to target coordinates using A* algorithm
     */
    public boolean calculatePath(BlockPos start, BlockPos target) {
        if (isCalculating) return false;
        
        isCalculating = true;
        targetPos = target;
        
        try {
            List<BlockPos> path = findPath(start, target);
            if (path != null && !path.isEmpty()) {
                currentPath = path;
                return true;
            }
        } catch (Exception e) {
            System.err.println("Pathfinding error: " + e.getMessage());
        } finally {
            isCalculating = false;
        }
        
        return false;
    }
    
    /**
     * A* pathfinding implementation adapted from Baritone (optimized for performance)
     */
    private List<BlockPos> findPath(BlockPos start, BlockPos goal) {
        PriorityQueue<PathNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fCost));
        Set<BlockPos> closedSet = new HashSet<>();
        Map<BlockPos, PathNode> allNodes = new HashMap<>();
        
        PathNode startNode = new PathNode(start, 0, heuristic(start, goal), null);
        openSet.add(startNode);
        allNodes.put(start, startNode);
        
        int searchedNodes = 0;
        long startTime = System.currentTimeMillis();
        
        while (!openSet.isEmpty() && searchedNodes < MAX_SEARCH_NODES) {
            // Check if we've exceeded time limit
            if (System.currentTimeMillis() - startTime > MAX_CALCULATION_TIME_MS) {
                break; // Time limit exceeded, return partial path
            }
            PathNode current = openSet.poll();
            searchedNodes++;
            
            if (current.pos.equals(goal) || current.pos.getSquaredDistance(goal) < 100) {
                return reconstructPath(current);
            }
            
            closedSet.add(current.pos);
            
            // Get neighbors with intelligent movement options
            for (BlockPos neighbor : getNeighbors(current.pos)) {
                if (closedSet.contains(neighbor)) continue;
                
                double moveCost = calculateMoveCost(current.pos, neighbor);
                if (moveCost == Double.MAX_VALUE) continue; // Impassable
                
                double tentativeGCost = current.gCost + moveCost;
                
                PathNode neighborNode = allNodes.get(neighbor);
                if (neighborNode == null) {
                    neighborNode = new PathNode(neighbor, tentativeGCost, 
                        heuristic(neighbor, goal), current);
                    allNodes.put(neighbor, neighborNode);
                    openSet.add(neighborNode);
                } else if (tentativeGCost < neighborNode.gCost) {
                    neighborNode.gCost = tentativeGCost;
                    neighborNode.fCost = tentativeGCost + neighborNode.hCost;
                    neighborNode.parent = current;
                }
            }
        }
        
        return null; // No path found
    }
    
    /**
     * Get valid neighboring positions for pathfinding
     */
    private List<BlockPos> getNeighbors(BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();
        
        // Simplified 4-directional movement for performance
        int[][] directions = {
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}
        };
        
        for (int[] dir : directions) {
            BlockPos neighbor = pos.add(dir[0], dir[1], dir[2]);
            
            // Limited vertical movement options
            for (int y = -1; y <= 2; y++) {
                BlockPos verticalNeighbor = neighbor.add(0, y, 0);
                if (isValidPosition(verticalNeighbor)) {
                    neighbors.add(verticalNeighbor);
                }
            }
        }
        
        return neighbors;
    }
    
    /**
     * Calculate movement cost between two positions
     */
    private double calculateMoveCost(BlockPos from, BlockPos to) {
        if (client.world == null) return Double.MAX_VALUE;
        
        Vec3i diff = to.subtract(from);
        double baseCost = Math.sqrt(diff.getX() * diff.getX() + diff.getZ() * diff.getZ());
        
        // Vertical movement costs
        if (diff.getY() > 0) {
            baseCost += diff.getY() * 2; // Jumping/climbing cost
        } else if (diff.getY() < 0) {
            baseCost += Math.abs(diff.getY()) * 0.5; // Falling cost
        }
        
        // Check if movement is possible
        if (!canMoveTo(from, to)) {
            return Double.MAX_VALUE;
        }
        
        // Terrain-based cost modifiers
        Block blockAt = client.world.getBlockState(to).getBlock();
        Block blockBelow = client.world.getBlockState(to.down()).getBlock();
        
        // Prefer solid ground
        if (blockBelow == Blocks.AIR) {
            baseCost += 5; // Avoid floating
        }
        
        // Avoid dangerous blocks
        if (blockAt == Blocks.LAVA || blockAt == Blocks.FIRE) {
            return Double.MAX_VALUE;
        }
        
        // Prefer open areas
        if (blockAt != Blocks.AIR) {
            baseCost += 2;
        }
        
        return baseCost;
    }
    
    /**
     * Check if movement between two positions is possible
     */
    private boolean canMoveTo(BlockPos from, BlockPos to) {
        if (client.world == null) return false;
        
        // Check if destination is passable
        BlockPos feetPos = to;
        BlockPos headPos = to.up();
        
        Block feetBlock = client.world.getBlockState(feetPos).getBlock();
        Block headBlock = client.world.getBlockState(headPos).getBlock();
        
        // Must have air for feet and head
        if (feetBlock != Blocks.AIR && !isPassable(feetBlock)) return false;
        if (headBlock != Blocks.AIR && !isPassable(headBlock)) return false;
        
        // Check for ground support (unless we're falling)
        Vec3i diff = to.subtract(from);
        if (diff.getY() <= 0) {
            BlockPos groundPos = to.down();
            Block groundBlock = client.world.getBlockState(groundPos).getBlock();
            if (groundBlock == Blocks.AIR) {
                // Check if fall is safe (not too far)
                int fallDistance = 0;
                for (int i = 1; i <= 10; i++) {
                    Block checkBlock = client.world.getBlockState(to.down(i)).getBlock();
                    if (checkBlock != Blocks.AIR) {
                        fallDistance = i - 1;
                        break;
                    }
                }
                if (fallDistance > 3) return false; // Too far to fall safely
            }
        }
        
        return true;
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
               block == Blocks.LARGE_FERN;
    }
    
    /**
     * Check if a position is valid for pathfinding
     */
    private boolean isValidPosition(BlockPos pos) {
        if (client.world == null) return false;
        
        // Basic bounds checking
        if (pos.getY() < -64 || pos.getY() > 320) return false;
        
        // Check if chunk is loaded
        if (!client.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Heuristic function for A* (Manhattan distance with optimizations)
     */
    private double heuristic(BlockPos from, BlockPos to) {
        Vec3i diff = to.subtract(from);
        double dx = Math.abs(diff.getX());
        double dy = Math.abs(diff.getY());
        double dz = Math.abs(diff.getZ());
        
        // Use Euclidean distance for better pathfinding
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        
        // Add vertical cost
        return horizontalDistance + dy * 1.5;
    }
    
    /**
     * Reconstruct path from goal to start
     */
    private List<BlockPos> reconstructPath(PathNode goalNode) {
        List<BlockPos> path = new ArrayList<>();
        PathNode current = goalNode;
        
        while (current != null) {
            path.add(current.pos);
            current = current.parent;
        }
        
        Collections.reverse(path);
        return path;
    }
    
    /**
     * Get current path
     */
    public List<BlockPos> getCurrentPath() {
        return new ArrayList<>(currentPath);
    }
    
    /**
     * Check if pathfinder has a valid path
     */
    public boolean hasPath() {
        return !currentPath.isEmpty();
    }
    
    /**
     * Get next position in path
     */
    public BlockPos getNextPosition(BlockPos currentPos) {
        if (currentPath.isEmpty()) return null;
        
        // Find closest point in path to current position
        int closestIndex = 0;
        double closestDistance = Double.MAX_VALUE;
        
        for (int i = 0; i < currentPath.size(); i++) {
            double distance = currentPos.getSquaredDistance(currentPath.get(i));
            if (distance < closestDistance) {
                closestDistance = distance;
                closestIndex = i;
            }
        }
        
        // Return next position in path
        if (closestIndex + 1 < currentPath.size()) {
            return currentPath.get(closestIndex + 1);
        }
        
        return null;
    }
    
    /**
     * Clear current path
     */
    public void clearPath() {
        currentPath.clear();
    }
    
    /**
     * Check if currently calculating path
     */
    public boolean isCalculating() {
        return isCalculating;
    }
    
    /**
     * Path node for A* algorithm
     */
    private static class PathNode {
        BlockPos pos;
        double gCost; // Distance from start
        double hCost; // Heuristic distance to goal
        double fCost; // Total cost
        PathNode parent;
        
        PathNode(BlockPos pos, double gCost, double hCost, PathNode parent) {
            this.pos = pos;
            this.gCost = gCost;
            this.hCost = hCost;
            this.fCost = gCost + hCost;
            this.parent = parent;
        }
    }
}