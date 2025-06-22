package com.escapemod.rendering;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.particle.ParticleTypes;

import java.util.List;

/**
 * Baritone-style path rendering system
 * Renders clear, visible path lines using particles
 */
public class PathRenderer {
    private static final double PARTICLE_SPACING = 0.5; // Distance between particles
    private static final int RENDER_DISTANCE = 200; // Max render distance
    private static final int PARTICLE_LIFETIME = 60; // Ticks particles stay visible
    
    private final MinecraftClient client;
    private int tickCounter = 0;
    
    public PathRenderer(MinecraftClient client) {
        this.client = client;
    }
    
    /**
     * Render path from current position to target
     */
    public void renderPath(List<BlockPos> path, ClientPlayerEntity player) {
        if (path == null || path.isEmpty() || client.world == null) return;
        
        tickCounter++;
        
        // Only update particles every 20 ticks for performance (once per second)
        if (tickCounter % 20 != 0) return;
        
        Vec3d playerPos = player.getPos();
        
        // Render path segments
        for (int i = 0; i < path.size() - 1; i++) {
            BlockPos start = path.get(i);
            BlockPos end = path.get(i + 1);
            
            // Skip if too far from player
            if (playerPos.squaredDistanceTo(Vec3d.ofCenter(start)) > RENDER_DISTANCE * RENDER_DISTANCE) {
                continue;
            }
            
            renderPathSegment(start, end, i, path.size());
        }
        
        // Render waypoint markers
        renderWaypoints(path, playerPos);
    }
    
    /**
     * Render a single path segment between two points
     */
    private void renderPathSegment(BlockPos start, BlockPos end, int segmentIndex, int totalSegments) {
        Vec3d startVec = Vec3d.ofCenter(start).add(0, 0.5, 0);
        Vec3d endVec = Vec3d.ofCenter(end).add(0, 0.5, 0);
        
        double distance = startVec.distanceTo(endVec);
        int particleCount = Math.max(1, (int) (distance / PARTICLE_SPACING));
        
        for (int i = 0; i <= particleCount; i++) {
            double progress = (double) i / particleCount;
            Vec3d particlePos = startVec.lerp(endVec, progress);
            
            // Different particle types based on position in path
            if (segmentIndex == 0 && i == 0) {
                // Start point - bright green
                spawnParticle(ParticleTypes.HAPPY_VILLAGER, particlePos, 0, 0.1, 0);
            } else if (segmentIndex == totalSegments - 2 && i == particleCount) {
                // End point - bright red
                spawnParticle(ParticleTypes.FLAME, particlePos, 0, 0.1, 0);
            } else {
                // Path line - cyan for visibility
                spawnParticle(ParticleTypes.SOUL_FIRE_FLAME, particlePos, 0, 0, 0);
                
                // Add secondary particles for better visibility
                if (i % 2 == 0) {
                    spawnParticle(ParticleTypes.ELECTRIC_SPARK, particlePos.add(0, -0.2, 0), 0, 0, 0);
                }
            }
        }
    }
    
    /**
     * Render waypoint markers
     */
    private void renderWaypoints(List<BlockPos> path, Vec3d playerPos) {
        for (int i = 1; i < path.size() - 1; i += 5) { // Every 5th waypoint
            BlockPos waypoint = path.get(i);
            Vec3d waypointPos = Vec3d.ofCenter(waypoint).add(0, 1, 0);
            
            // Skip if too far
            if (playerPos.squaredDistanceTo(waypointPos) > RENDER_DISTANCE * RENDER_DISTANCE) {
                continue;
            }
            
            // Create circular marker
            for (int angle = 0; angle < 360; angle += 30) {
                double radians = Math.toRadians(angle);
                double offsetX = Math.cos(radians) * 0.7;
                double offsetZ = Math.sin(radians) * 0.7;
                
                Vec3d markerPos = waypointPos.add(offsetX, 0, offsetZ);
                spawnParticle(ParticleTypes.END_ROD, markerPos, 0, 0, 0);
            }
        }
    }
    
    /**
     * Render direct line to target (when no path available)
     */
    public void renderDirectLine(Vec3d start, Vec3d end, ClientPlayerEntity player) {
        if (client.world == null) return;
        
        tickCounter++;
        if (tickCounter % 40 != 0) return; // Much less frequent updates for direct line
        
        double distance = start.distanceTo(end);
        if (distance > RENDER_DISTANCE) {
            // Only render first part of very long lines
            Vec3d direction = end.subtract(start).normalize();
            end = start.add(direction.multiply(RENDER_DISTANCE));
            distance = RENDER_DISTANCE;
        }
        
        int particleCount = Math.max(10, (int) (distance / 2.0));
        
        for (int i = 0; i <= particleCount; i++) {
            double progress = (double) i / particleCount;
            Vec3d particlePos = start.lerp(end, progress);
            
            // Different colors based on distance
            if (i == 0) {
                // Start - green
                spawnParticle(ParticleTypes.HAPPY_VILLAGER, particlePos, 0, 0.1, 0);
            } else if (progress < 0.3) {
                // Close - bright cyan
                spawnParticle(ParticleTypes.SOUL_FIRE_FLAME, particlePos, 0, 0, 0);
            } else if (progress < 0.7) {
                // Medium - blue
                spawnParticle(ParticleTypes.ENCHANT, particlePos, 0, 0, 0);
            } else {
                // Far - faint
                if (i % 3 == 0) {
                    spawnParticle(ParticleTypes.END_ROD, particlePos, 0, 0, 0);
                }
            }
        }
        
        // Add distance markers
        for (int dist = 50; dist < distance; dist += 50) {
            double progress = dist / distance;
            Vec3d markerPos = start.lerp(end, progress);
            
            // Create small marker
            for (int angle = 0; angle < 360; angle += 90) {
                double radians = Math.toRadians(angle);
                double offsetX = Math.cos(radians) * 0.5;
                double offsetZ = Math.sin(radians) * 0.5;
                
                spawnParticle(ParticleTypes.FLAME, markerPos.add(offsetX, 0.5, offsetZ), 0, 0, 0);
            }
        }
    }
    
    /**
     * Render navigation arrow showing current direction
     */
    public void renderNavigationArrow(Vec3d playerPos, Vec3d direction) {
        if (client.world == null || direction == null) return;
        
        if (tickCounter % 10 != 0) return; // Update every half second
        
        Vec3d arrowStart = playerPos.add(0, 2, 0);
        Vec3d arrowEnd = arrowStart.add(direction.multiply(8));
        
        // Arrow shaft
        for (int i = 0; i <= 16; i++) {
            double progress = i / 16.0;
            Vec3d arrowPoint = arrowStart.lerp(arrowEnd, progress);
            spawnParticle(ParticleTypes.HAPPY_VILLAGER, arrowPoint, 0, 0, 0);
        }
        
        // Arrow head
        Vec3d perpendicular = new Vec3d(-direction.z, 0, direction.x).normalize();
        for (int i = -3; i <= 3; i++) {
            Vec3d arrowHeadPoint = arrowEnd.add(perpendicular.multiply(i * 0.3));
            spawnParticle(ParticleTypes.HAPPY_VILLAGER, arrowHeadPoint, 0, 0, 0);
        }
        
        // Arrow back lines
        Vec3d backDirection = direction.multiply(-2);
        for (int i = -2; i <= 2; i++) {
            Vec3d backPoint = arrowEnd.add(backDirection).add(perpendicular.multiply(i * 0.2));
            spawnParticle(ParticleTypes.HAPPY_VILLAGER, backPoint, 0, 0, 0);
        }
    }
    
    /**
     * Render progress indicator
     */
    public void renderProgressIndicator(Vec3d playerPos, double progressPercent) {
        if (client.world == null) return;
        
        if (tickCounter % 20 != 0) return; // Update every second
        
        Vec3d indicatorPos = playerPos.add(0, 3, 0);
        
        // Create progress circle
        int totalDots = 20;
        int filledDots = (int) (totalDots * progressPercent / 100.0);
        
        for (int i = 0; i < totalDots; i++) {
            double angle = (i / (double) totalDots) * 2 * Math.PI;
            double offsetX = Math.cos(angle) * 1.5;
            double offsetZ = Math.sin(angle) * 1.5;
            
            Vec3d dotPos = indicatorPos.add(offsetX, 0, offsetZ);
            
            if (i < filledDots) {
                // Filled - green
                spawnParticle(ParticleTypes.HAPPY_VILLAGER, dotPos, 0, 0, 0);
            } else {
                // Empty - gray
                spawnParticle(ParticleTypes.SMOKE, dotPos, 0, 0, 0);
            }
        }
        
        // Center indicator
        spawnParticle(ParticleTypes.ENCHANT, indicatorPos, 0, 0, 0);
    }
    
    /**
     * Spawn particle with error handling
     */
    private void spawnParticle(net.minecraft.particle.ParticleEffect particle, Vec3d pos, double velX, double velY, double velZ) {
        try {
            if (client.world != null) {
                client.world.addParticle(particle, pos.x, pos.y, pos.z, velX, velY, velZ);
            }
        } catch (Exception e) {
            // Silently handle particle spawn errors
        }
    }
    
    /**
     * Clear all rendered paths
     */
    public void clearRendering() {
        tickCounter = 0;
    }
}