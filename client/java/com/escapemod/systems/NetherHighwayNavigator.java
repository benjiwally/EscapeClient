package com.escapemod.systems;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.text.Text;
import net.minecraft.world.dimension.DimensionTypes;

import java.util.*;

/**
 * Nether Highway Navigator - Detects and uses existing nether highways
 * Builds portals when needed and avoids trapped portals
 */
public class NetherHighwayNavigator {
    private final MinecraftClient client;
    private final Map<String, BlockPos> knownPortals = new HashMap<>();
    private final Set<BlockPos> trappedPortals = new HashSet<>();
    private final List<NetherHighway> detectedHighways = new ArrayList<>();
    
    private BlockPos currentPortalPos = null;
    private boolean isInNether = false;
    private long lastPortalCheck = 0;
    
    public NetherHighwayNavigator(MinecraftClient client) {
        this.client = client;
    }
    
    /**
     * Check if we should use nether travel for current escape
     */
    public boolean shouldUseNether(BlockPos currentPos, BlockPos targetPos) {
        double overworldDistance = currentPos.getSquaredDistance(targetPos);
        double netherEquivalent = overworldDistance / 64; // 8x faster in nether
        
        // Use nether if distance > 5000 blocks
        return overworldDistance > 25_000_000; // 5000^2
    }
    
    /**
     * Find or create a portal for nether travel
     */
    public BlockPos findOrCreatePortal(BlockPos nearPos) {
        // Check for existing safe portals nearby
        BlockPos existingPortal = findNearestSafePortal(nearPos, 500);
        if (existingPortal != null) {
            return existingPortal;
        }
        
        // Find a safe location to build a portal
        BlockPos portalLocation = findSafePortalLocation(nearPos);
        if (portalLocation != null) {
            return buildPortal(portalLocation);
        }
        
        return null;
    }
    
    /**
     * Find nearest safe portal (not trapped)
     */
    private BlockPos findNearestSafePortal(BlockPos pos, int radius) {
        if (client.world == null) return null;
        
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        
        // Search for nether portals in area
        for (int x = -radius; x <= radius; x += 16) {
            for (int z = -radius; z <= radius; z += 16) {
                for (int y = -10; y <= 100; y += 5) {
                    BlockPos checkPos = pos.add(x, y, z);
                    
                    if (isNetherPortal(checkPos) && !isTrappedPortal(checkPos)) {
                        double distance = pos.getSquaredDistance(checkPos);
                        if (distance < nearestDistance) {
                            nearestDistance = distance;
                            nearest = checkPos;
                        }
                    }
                }
            }
        }
        
        return nearest;
    }
    
    /**
     * Check if position has a nether portal
     */
    private boolean isNetherPortal(BlockPos pos) {
        if (client.world == null) return false;
        
        // Check for obsidian frame pattern
        Block block = client.world.getBlockState(pos).getBlock();
        if (block == Blocks.NETHER_PORTAL) {
            return true;
        }
        
        // Check for obsidian frame
        if (block == Blocks.OBSIDIAN) {
            // Simple check for portal frame
            int obsidianCount = 0;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    Block frameBlock = client.world.getBlockState(pos.add(dx, dy, 0)).getBlock();
                    if (frameBlock == Blocks.OBSIDIAN) {
                        obsidianCount++;
                    }
                }
            }
            return obsidianCount >= 10; // Minimum for portal frame
        }
        
        return false;
    }
    
    /**
     * Check if portal is trapped (has suspicious blocks nearby)
     */
    private boolean isTrappedPortal(BlockPos pos) {
        if (trappedPortals.contains(pos)) return true;
        if (client.world == null) return false;
        
        // Check for common trap blocks around portal
        int trapBlockCount = 0;
        for (int x = -5; x <= 5; x++) {
            for (int y = -3; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {
                    BlockPos checkPos = pos.add(x, y, z);
                    Block block = client.world.getBlockState(checkPos).getBlock();
                    
                    // Common trap blocks
                    if (block == Blocks.TNT || 
                        block == Blocks.LAVA ||
                        block == Blocks.MAGMA_BLOCK ||
                        block == Blocks.CACTUS ||
                        block == Blocks.WITHER_SKELETON_SKULL) {
                        trapBlockCount++;
                    }
                }
            }
        }
        
        // If too many trap blocks, mark as trapped
        if (trapBlockCount > 3) {
            trappedPortals.add(pos);
            return true;
        }
        
        return false;
    }
    
    /**
     * Find safe location to build a portal
     */
    private BlockPos findSafePortalLocation(BlockPos nearPos) {
        if (client.world == null) return null;
        
        // Look for flat, safe area
        for (int radius = 10; radius <= 100; radius += 10) {
            for (int angle = 0; angle < 360; angle += 45) {
                double radians = Math.toRadians(angle);
                int x = (int) (nearPos.getX() + radius * Math.cos(radians));
                int z = (int) (nearPos.getZ() + radius * Math.sin(radians));
                
                BlockPos candidate = new BlockPos(x, nearPos.getY(), z);
                if (isSafePortalLocation(candidate)) {
                    return candidate;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Check if location is safe for portal building
     */
    private boolean isSafePortalLocation(BlockPos pos) {
        if (client.world == null) return false;
        
        // Check for flat area
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos checkPos = pos.add(x, 0, z);
                Block ground = client.world.getBlockState(checkPos.down()).getBlock();
                Block air1 = client.world.getBlockState(checkPos).getBlock();
                Block air2 = client.world.getBlockState(checkPos.up()).getBlock();
                Block air3 = client.world.getBlockState(checkPos.up(2)).getBlock();
                Block air4 = client.world.getBlockState(checkPos.up(3)).getBlock();
                
                // Need solid ground and 4 blocks of air
                if (ground == Blocks.AIR || ground == Blocks.LAVA || ground == Blocks.WATER) {
                    return false;
                }
                
                if (air1 != Blocks.AIR || air2 != Blocks.AIR || air3 != Blocks.AIR || air4 != Blocks.AIR) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Build a nether portal at specified location
     */
    private BlockPos buildPortal(BlockPos pos) {
        // This would require inventory management and block placement
        // For now, just mark the location and return it
        knownPortals.put("escape_portal_" + System.currentTimeMillis(), pos);
        
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§e[EscapeMod] Portal location marked: " + 
                pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
            client.player.sendMessage(Text.literal("§c[EscapeMod] Manual portal building required (need obsidian)"), false);
        }
        
        return pos;
    }
    
    /**
     * Detect nether highways in the area
     */
    public void scanForHighways() {
        if (client.world == null || client.player == null) return;
        if (!client.world.getDimensionEntry().matchesKey(DimensionTypes.THE_NETHER)) return;
        
        BlockPos playerPos = client.player.getBlockPos();
        
        // Look for long stretches of cleared paths
        for (int direction = 0; direction < 4; direction++) {
            Vec3d dir = getCardinalDirection(direction);
            NetherHighway highway = scanDirection(playerPos, dir, 500);
            if (highway != null && !detectedHighways.contains(highway)) {
                detectedHighways.add(highway);
                
                client.player.sendMessage(Text.literal("§a[EscapeMod] Nether highway detected: " + 
                    highway.direction + " - Length: " + highway.length + " blocks"), false);
            }
        }
    }
    
    /**
     * Scan in a direction for highway patterns
     */
    private NetherHighway scanDirection(BlockPos start, Vec3d direction, int maxDistance) {
        if (client.world == null) return null;
        
        int clearBlocks = 0;
        int totalBlocks = 0;
        
        for (int distance = 1; distance <= maxDistance; distance++) {
            BlockPos checkPos = start.add(
                (int)(direction.x * distance),
                0,
                (int)(direction.z * distance)
            );
            
            // Check if path is clear (typical highway pattern)
            boolean isClear = true;
            for (int y = 0; y <= 3; y++) {
                Block block = client.world.getBlockState(checkPos.add(0, y, 0)).getBlock();
                if (block != Blocks.AIR) {
                    isClear = false;
                    break;
                }
            }
            
            if (isClear) clearBlocks++;
            totalBlocks++;
        }
        
        // If more than 70% clear, consider it a highway
        double clearPercentage = (double) clearBlocks / totalBlocks;
        if (clearPercentage > 0.7 && clearBlocks > 100) {
            return new NetherHighway(start, direction, clearBlocks, getDirectionName(direction));
        }
        
        return null;
    }
    
    /**
     * Get cardinal direction vector
     */
    private Vec3d getCardinalDirection(int index) {
        switch (index) {
            case 0: return new Vec3d(1, 0, 0);  // East
            case 1: return new Vec3d(-1, 0, 0); // West
            case 2: return new Vec3d(0, 0, 1);  // North
            case 3: return new Vec3d(0, 0, -1); // South
            default: return new Vec3d(1, 0, 0);
        }
    }
    
    /**
     * Get direction name
     */
    private String getDirectionName(Vec3d direction) {
        if (direction.x > 0) return "East";
        if (direction.x < 0) return "West";
        if (direction.z > 0) return "North";
        if (direction.z < 0) return "South";
        return "Unknown";
    }
    
    /**
     * Get best highway for target direction
     */
    public NetherHighway getBestHighway(Vec3d targetDirection) {
        NetherHighway best = null;
        double bestAlignment = -1;
        
        for (NetherHighway highway : detectedHighways) {
            double alignment = highway.direction.dotProduct(targetDirection);
            if (alignment > bestAlignment) {
                bestAlignment = alignment;
                best = highway;
            }
        }
        
        return bestAlignment > 0.7 ? best : null; // Must be reasonably aligned
    }
    
    /**
     * Nether Highway data class
     */
    public static class NetherHighway {
        public final BlockPos start;
        public final Vec3d direction;
        public final int length;
        public final String directionName;
        
        public NetherHighway(BlockPos start, Vec3d direction, int length, String directionName) {
            this.start = start;
            this.direction = direction;
            this.length = length;
            this.directionName = directionName;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof NetherHighway)) return false;
            NetherHighway other = (NetherHighway) obj;
            return start.equals(other.start) && directionName.equals(other.directionName);
        }
        
        @Override
        public int hashCode() {
            return start.hashCode() + directionName.hashCode();
        }
    }
    
    // Getters
    public List<NetherHighway> getDetectedHighways() { return new ArrayList<>(detectedHighways); }
    public Map<String, BlockPos> getKnownPortals() { return new HashMap<>(knownPortals); }
    public boolean isInNether() { return isInNether; }
    
    public void tick() {
        if (client.world != null) {
            isInNether = client.world.getDimensionEntry().matchesKey(DimensionTypes.THE_NETHER);
            
            // Scan for highways periodically when in nether
            if (isInNether && System.currentTimeMillis() - lastPortalCheck > 10000) {
                scanForHighways();
                lastPortalCheck = System.currentTimeMillis();
            }
        }
    }
}