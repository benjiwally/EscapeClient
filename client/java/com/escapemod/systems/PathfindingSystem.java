package com.escapemod.systems;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleTypes;

import java.util.*;

public class PathfindingSystem {
    private MinecraftClient client;
    private List<Vec3d> currentPath;
    private BlockPos targetPos;
    private boolean pathfindingEnabled = true;
    private int maxPathLength = 256;
    private int maxSearchNodes = 1000;
    
    // Visual path display
    private List<Vec3d> visualPath;
    private int particleTimer = 0;
    
    public PathfindingSystem() {
        this.client = MinecraftClient.getInstance();
        this.currentPath = new ArrayList<>();
        this.visualPath = new ArrayList<>();
    }
    
    public static class PathNode {
        public BlockPos pos;
        public PathNode parent;
        public double gCost; // Distance from start
        public double hCost; // Distance to target (heuristic)
        public double fCost; // Total cost
        
        public PathNode(BlockPos pos, PathNode parent, double gCost, double hCost) {
            this.pos = pos;
            this.parent = parent;
            this.gCost = gCost;
            this.hCost = hCost;
            this.fCost = gCost + hCost;
        }
    }
    
    public void tick() {
        if (!pathfindingEnabled || client.player == null || client.world == null) {
            return;
        }
        
        // Update visual path display
        updatePathVisualization();
    }
    
    public List<Vec3d> findPath(BlockPos start, BlockPos target) {
        if (start == null || target == null) {
            return new ArrayList<>();
        }
        
        this.targetPos = target;
        World world = client.world;
        
        // A* pathfinding algorithm
        PriorityQueue<PathNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fCost));
        Set<BlockPos> closedSet = new HashSet<>();
        Map<BlockPos, PathNode> allNodes = new HashMap<>();
        
        PathNode startNode = new PathNode(start, null, 0, getHeuristic(start, target));
        openSet.add(startNode);
        allNodes.put(start, startNode);
        
        int searchedNodes = 0;
        
        while (!openSet.isEmpty() && searchedNodes < maxSearchNodes) {
            PathNode current = openSet.poll();
            searchedNodes++;
            
            if (current.pos.equals(target)) {
                // Path found! Reconstruct it
                List<Vec3d> path = reconstructPath(current);
                this.currentPath = path;
                this.visualPath = new ArrayList<>(path);
                return path;
            }
            
            closedSet.add(current.pos);
            
            // Check all neighbors
            for (BlockPos neighbor : getNeighbors(current.pos)) {
                if (closedSet.contains(neighbor) || !isWalkable(world, neighbor)) {
                    continue;
                }
                
                double tentativeGCost = current.gCost + getMovementCost(current.pos, neighbor);
                
                PathNode neighborNode = allNodes.get(neighbor);
                if (neighborNode == null) {
                    neighborNode = new PathNode(neighbor, current, tentativeGCost, getHeuristic(neighbor, target));
                    allNodes.put(neighbor, neighborNode);
                    openSet.add(neighborNode);
                } else if (tentativeGCost < neighborNode.gCost) {
                    neighborNode.parent = current;
                    neighborNode.gCost = tentativeGCost;
                    neighborNode.fCost = neighborNode.gCost + neighborNode.hCost;
                }
            }
        }
        
        // No path found
        this.currentPath = new ArrayList<>();
        this.visualPath = new ArrayList<>();
        return new ArrayList<>();
    }
    
    private List<BlockPos> getNeighbors(BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();
        
        // Basic 8-directional movement (including diagonals)
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                
                // Check multiple Y levels for climbing/descending
                for (int y = -1; y <= 1; y++) {
                    neighbors.add(pos.add(x, y, z));
                }
            }
        }
        
        return neighbors;
    }
    
    private boolean isWalkable(World world, BlockPos pos) {
        // Check if the position is safe to walk on
        Block blockAt = world.getBlockState(pos).getBlock();
        Block blockAbove = world.getBlockState(pos.up()).getBlock();
        Block blockBelow = world.getBlockState(pos.down()).getBlock();
        
        // Can't walk through solid blocks
        if (!blockAt.equals(Blocks.AIR) && !isPassableBlock(blockAt)) {
            return false;
        }
        
        // Need air above head
        if (!blockAbove.equals(Blocks.AIR) && !isPassableBlock(blockAbove)) {
            return false;
        }
        
        // Need solid ground below (or can swim)
        if (blockBelow.equals(Blocks.AIR)) {
            // Check if it's a dangerous fall
            int fallDistance = 0;
            for (int i = 1; i <= 10; i++) {
                Block checkBlock = world.getBlockState(pos.down(i)).getBlock();
                if (!checkBlock.equals(Blocks.AIR)) {
                    fallDistance = i;
                    break;
                }
            }
            
            // Don't allow falls greater than 3 blocks
            if (fallDistance > 3 || fallDistance == 0) {
                return false;
            }
        }
        
        // Avoid dangerous blocks
        if (isDangerousBlock(blockAt) || isDangerousBlock(blockBelow)) {
            return false;
        }
        
        return true;
    }
    
    private boolean isPassableBlock(Block block) {
        return block.equals(Blocks.AIR) || 
               block.equals(Blocks.TALL_GRASS) || 
               block.equals(Blocks.SHORT_GRASS) ||
               block.equals(Blocks.FERN) ||
               block.equals(Blocks.DEAD_BUSH) ||
               block.equals(Blocks.WATER);
    }
    
    private boolean isDangerousBlock(Block block) {
        return block.equals(Blocks.LAVA) ||
               block.equals(Blocks.FIRE) ||
               block.equals(Blocks.MAGMA_BLOCK) ||
               block.equals(Blocks.SWEET_BERRY_BUSH) ||
               block.equals(Blocks.CACTUS);
    }
    
    private double getHeuristic(BlockPos from, BlockPos to) {
        // Manhattan distance with slight preference for straight lines
        double dx = Math.abs(to.getX() - from.getX());
        double dy = Math.abs(to.getY() - from.getY());
        double dz = Math.abs(to.getZ() - from.getZ());
        
        return dx + dy + dz;
    }
    
    private double getMovementCost(BlockPos from, BlockPos to) {
        double baseCost = from.getSquaredDistance(to);
        
        // Add extra cost for vertical movement
        int yDiff = Math.abs(to.getY() - from.getY());
        if (yDiff > 0) {
            baseCost += yDiff * 0.5; // Climbing/descending is more expensive
        }
        
        // Add cost for diagonal movement
        if (Math.abs(to.getX() - from.getX()) > 0 && Math.abs(to.getZ() - from.getZ()) > 0) {
            baseCost += 0.4; // Diagonal movement penalty
        }
        
        return baseCost;
    }
    
    private List<Vec3d> reconstructPath(PathNode endNode) {
        List<Vec3d> path = new ArrayList<>();
        PathNode current = endNode;
        
        while (current != null) {
            path.add(Vec3d.ofCenter(current.pos));
            current = current.parent;
        }
        
        Collections.reverse(path);
        
        // Smooth the path
        return smoothPath(path);
    }
    
    private List<Vec3d> smoothPath(List<Vec3d> rawPath) {
        if (rawPath.size() <= 2) {
            return rawPath;
        }
        
        List<Vec3d> smoothedPath = new ArrayList<>();
        smoothedPath.add(rawPath.get(0));
        
        for (int i = 1; i < rawPath.size() - 1; i++) {
            Vec3d prev = rawPath.get(i - 1);
            Vec3d current = rawPath.get(i);
            Vec3d next = rawPath.get(i + 1);
            
            // Skip points that are roughly in a straight line
            Vec3d dir1 = current.subtract(prev).normalize();
            Vec3d dir2 = next.subtract(current).normalize();
            
            double dot = dir1.dotProduct(dir2);
            if (dot < 0.9) { // Only keep points where direction changes significantly
                smoothedPath.add(current);
            }
        }
        
        smoothedPath.add(rawPath.get(rawPath.size() - 1));
        return smoothedPath;
    }
    
    private void updatePathVisualization() {
        if (visualPath.isEmpty() || client.world == null) {
            return;
        }
        
        particleTimer++;
        if (particleTimer >= 3) { // Update particles every 3 ticks for better visibility
            displayPathParticles();
            particleTimer = 0;
        }
    }
    
    private void displayPathParticles() {
        if (visualPath.size() < 2) return;
        
        // Create a continuous line like Baritone
        for (int i = 0; i < visualPath.size() - 1; i++) {
            Vec3d start = visualPath.get(i);
            Vec3d end = visualPath.get(i + 1);
            
            // Calculate the distance between points
            double distance = start.distanceTo(end);
            int particleCount = Math.max(2, (int) (distance * 8)); // More particles for smoother line
            
            for (int j = 0; j <= particleCount; j++) {
                double progress = (double) j / particleCount;
                Vec3d linePoint = start.lerp(end, progress);
                
                // Different particle types for different sections
                if (i == 0 && j == 0) {
                    // Start point - bright green
                    client.world.addParticle(ParticleTypes.HAPPY_VILLAGER,
                        linePoint.x, linePoint.y + 0.8, linePoint.z, 0, 0.1, 0);
                } else if (i == visualPath.size() - 2 && j == particleCount) {
                    // End point - bright red
                    client.world.addParticle(ParticleTypes.FLAME,
                        linePoint.x, linePoint.y + 0.8, linePoint.z, 0, 0.1, 0);
                } else {
                    // Path line - cyan/blue particles for visibility
                    client.world.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        linePoint.x, linePoint.y + 0.5, linePoint.z, 0, 0, 0);
                    
                    // Add secondary particles for better visibility
                    if (j % 2 == 0) {
                        client.world.addParticle(ParticleTypes.ELECTRIC_SPARK,
                            linePoint.x, linePoint.y + 0.3, linePoint.z, 0, 0, 0);
                    }
                }
            }
        }
        
        // Add waypoint markers at each path node
        for (int i = 1; i < visualPath.size() - 1; i++) {
            Vec3d waypoint = visualPath.get(i);
            
            // Create a small circle of particles around each waypoint
            for (int angle = 0; angle < 360; angle += 45) {
                double radians = Math.toRadians(angle);
                double offsetX = Math.cos(radians) * 0.5;
                double offsetZ = Math.sin(radians) * 0.5;
                
                client.world.addParticle(ParticleTypes.END_ROD,
                    waypoint.x + offsetX, waypoint.y + 0.7, waypoint.z + offsetZ, 0, 0, 0);
            }
        }
    }
    
    public Vec3d getNextPathPoint() {
        if (currentPath.isEmpty()) {
            return null;
        }
        
        if (client.player == null) {
            return null;
        }
        
        Vec3d playerPos = client.player.getPos();
        
        // Find the closest point on the path that's ahead of us
        for (int i = 0; i < currentPath.size(); i++) {
            Vec3d pathPoint = currentPath.get(i);
            if (playerPos.distanceTo(pathPoint) > 1.5) {
                return pathPoint;
            }
        }
        
        // If we're close to all points, return the last one
        return currentPath.get(currentPath.size() - 1);
    }
    
    public boolean hasPath() {
        return !currentPath.isEmpty();
    }
    
    public void clearPath() {
        currentPath.clear();
        visualPath.clear();
    }
    
    public double getPathLength() {
        if (currentPath.size() < 2) {
            return 0;
        }
        
        double length = 0;
        for (int i = 1; i < currentPath.size(); i++) {
            length += currentPath.get(i - 1).distanceTo(currentPath.get(i));
        }
        return length;
    }
    
    public List<Vec3d> getCurrentPath() {
        return new ArrayList<>(currentPath);
    }
    
    public void setMaxPathLength(int maxLength) {
        this.maxPathLength = maxLength;
    }
    
    public void setMaxSearchNodes(int maxNodes) {
        this.maxSearchNodes = maxNodes;
    }
    
    public void setPathfindingEnabled(boolean enabled) {
        this.pathfindingEnabled = enabled;
        if (!enabled) {
            clearPath();
        }
    }
    
    public boolean isPathfindingEnabled() {
        return pathfindingEnabled;
    }
}