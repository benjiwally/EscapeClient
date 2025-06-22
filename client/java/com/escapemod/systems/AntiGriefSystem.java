package com.escapemod.systems;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;

import java.util.*;

public class AntiGriefSystem {
    private MinecraftClient client;
    private Set<BlockPos> suspiciousBlocks = new HashSet<>();
    private Set<BlockPos> knownTraps = new HashSet<>();
    private Map<String, Long> playerLastSeen = new HashMap<>();
    private int scanCooldown = 0;
    private long lastWarningMessage = 0;
    
    // Dangerous block patterns
    private static final Block[] TRAP_BLOCKS = {
        Blocks.LAVA, Blocks.TNT, Blocks.FIRE, Blocks.MAGMA_BLOCK,
        Blocks.CACTUS, Blocks.SWEET_BERRY_BUSH, Blocks.POWDER_SNOW
    };
    
    private static final Block[] SUSPICIOUS_BLOCKS = {
        Blocks.OBSIDIAN, Blocks.BEDROCK, Blocks.BARRIER,
        Blocks.COMMAND_BLOCK, Blocks.CHAIN_COMMAND_BLOCK, Blocks.REPEATING_COMMAND_BLOCK
    };
    
    public AntiGriefSystem() {
        this.client = MinecraftClient.getInstance();
    }
    
    public void tick() {
        if (client.player == null || client.world == null) return;
        
        scanCooldown--;
        if (scanCooldown <= 0) {
            scanForThreats();
            scanCooldown = 40; // Scan every 2 seconds
        }
        
        // Check for immediate dangers
        checkImmediateDangers();
        
        // Monitor nearby players
        monitorPlayers();
    }
    
    private void scanForThreats() {
        ClientPlayerEntity player = client.player;
        World world = client.world;
        BlockPos playerPos = player.getBlockPos();
        
        // Scan area around player
        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                for (int y = -5; y <= 5; y++) {
                    BlockPos checkPos = playerPos.add(x, y, z);
                    Block block = world.getBlockState(checkPos).getBlock();
                    
                    // Check for trap blocks
                    if (isTrapBlock(block)) {
                        if (!knownTraps.contains(checkPos)) {
                            knownTraps.add(checkPos);
                            alertTrap(checkPos, block);
                        }
                    }
                    
                    // Check for suspicious patterns
                    if (isSuspiciousPattern(world, checkPos)) {
                        if (!suspiciousBlocks.contains(checkPos)) {
                            suspiciousBlocks.add(checkPos);
                            alertSuspiciousStructure(checkPos);
                        }
                    }
                }
            }
        }
        
        // Clean up old entries
        cleanupOldEntries(playerPos);
    }
    
    private void checkImmediateDangers() {
        ClientPlayerEntity player = client.player;
        World world = client.world;
        BlockPos playerPos = player.getBlockPos();
        
        // Check blocks directly around player
        BlockPos[] checkPositions = {
            playerPos.add(1, 0, 0), playerPos.add(-1, 0, 0),
            playerPos.add(0, 0, 1), playerPos.add(0, 0, -1),
            playerPos.add(0, 1, 0), playerPos.add(0, -1, 0)
        };
        
        for (BlockPos pos : checkPositions) {
            Block block = world.getBlockState(pos).getBlock();
            
            if (block.equals(Blocks.LAVA)) {
                emergencyAvoidance("LAVA DETECTED! Moving away!");
                return;
            } else if (block.equals(Blocks.TNT)) {
                emergencyAvoidance("TNT DETECTED! Evacuating area!");
                return;
            } else if (block.equals(Blocks.FIRE)) {
                emergencyAvoidance("FIRE DETECTED! Extinguishing or avoiding!");
                return;
            }
        }
        
        // Check for falling into void
        if (playerPos.getY() < 5) {
            emergencyAvoidance("VOID DANGER! Attempting to build up!");
        }
    }
    
    private void monitorPlayers() {
        ClientPlayerEntity player = client.player;
        List<PlayerEntity> nearbyPlayers = client.world.getEntitiesByClass(
            PlayerEntity.class,
            player.getBoundingBox().expand(50),
            p -> p != player
        );
        
        long currentTime = System.currentTimeMillis();
        
        for (PlayerEntity nearbyPlayer : nearbyPlayers) {
            String playerName = nearbyPlayer.getName().getString();
            Long lastSeen = playerLastSeen.get(playerName);
            
            if (lastSeen == null) {
                // New player detected
                alertPlayerDetected(nearbyPlayer);
            }
            
            playerLastSeen.put(playerName, currentTime);
            
            // Check if player is too close
            double distance = player.getPos().distanceTo(nearbyPlayer.getPos());
            if (distance < 10) {
                alertPlayerTooClose(nearbyPlayer, distance);
            }
        }
        
        // Remove players not seen recently
        playerLastSeen.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > 60000); // 1 minute
    }
    
    private boolean isTrapBlock(Block block) {
        for (Block trapBlock : TRAP_BLOCKS) {
            if (block.equals(trapBlock)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean isSuspiciousPattern(World world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        
        // Check for suspicious blocks
        for (Block suspiciousBlock : SUSPICIOUS_BLOCKS) {
            if (block.equals(suspiciousBlock)) {
                return true;
            }
        }
        
        // Check for trap patterns
        if (block.equals(Blocks.STONE_PRESSURE_PLATE) || 
            block.equals(Blocks.OAK_PRESSURE_PLATE)) {
            // Check if there's TNT or lava below
            for (int y = 1; y <= 5; y++) {
                Block below = world.getBlockState(pos.add(0, -y, 0)).getBlock();
                if (below.equals(Blocks.TNT) || below.equals(Blocks.LAVA)) {
                    return true;
                }
            }
        }
        
        // Check for hidden lava (lava covered by blocks)
        if (block.equals(Blocks.LAVA)) {
            Block above = world.getBlockState(pos.add(0, 1, 0)).getBlock();
            if (!above.equals(Blocks.AIR)) {
                return true; // Hidden lava
            }
        }
        
        return false;
    }
    
    private void alertTrap(BlockPos pos, Block block) {
        ClientPlayerEntity player = client.player;
        double distance = Math.sqrt(player.getBlockPos().getSquaredDistance(pos));
        
        // Only show trap alerts every 15 seconds
        if (System.currentTimeMillis() - lastWarningMessage > 15000) {
            player.sendMessage(Text.literal("§c[AntiGrief] TRAP DETECTED: " + 
                block.getName().getString() + " at distance " + String.format("%.1f", distance)), false);
            
            if (distance < 5) {
                player.sendMessage(Text.literal("§c[AntiGrief] WARNING: Trap is very close! Be careful!"), false);
            }
            lastWarningMessage = System.currentTimeMillis();
        }
    }
    
    private void alertSuspiciousStructure(BlockPos pos) {
        ClientPlayerEntity player = client.player;
        double distance = Math.sqrt(player.getBlockPos().getSquaredDistance(pos));
        
        // Only show suspicious structure alerts every 20 seconds
        if (System.currentTimeMillis() - lastWarningMessage > 20000) {
            player.sendMessage(Text.literal("§e[AntiGrief] Suspicious structure detected at distance " + 
                String.format("%.1f", distance)), false);
            lastWarningMessage = System.currentTimeMillis();
        }
    }
    
    private void alertPlayerDetected(PlayerEntity detectedPlayer) {
        ClientPlayerEntity player = client.player;
        double distance = player.getPos().distanceTo(detectedPlayer.getPos());
        
        player.sendMessage(Text.literal("§e[AntiGrief] Player detected: " + 
            detectedPlayer.getName().getString() + " (Distance: " + String.format("%.1f", distance) + ")"), false);
    }
    
    private void alertPlayerTooClose(PlayerEntity nearbyPlayer, double distance) {
        ClientPlayerEntity player = client.player;
        
        if (System.currentTimeMillis() % 5000 < 100) { // Alert every 5 seconds
            player.sendMessage(Text.literal("§c[AntiGrief] WARNING: " + 
                nearbyPlayer.getName().getString() + " is very close! (" + 
                String.format("%.1f", distance) + " blocks)"), true);
        }
    }
    
    private void emergencyAvoidance(String reason) {
        ClientPlayerEntity player = client.player;
        player.sendMessage(Text.literal("§c[AntiGrief] EMERGENCY: " + reason), false);
        
        // Emergency actions
        client.options.jumpKey.setPressed(true);
        client.options.sprintKey.setPressed(true);
        
        // Try to move away from danger
        Vec3d playerPos = player.getPos();
        Vec3d escapeDirection = findSafeDirection(playerPos);
        
        if (escapeDirection != null) {
            float targetYaw = (float) Math.toDegrees(Math.atan2(-escapeDirection.x, escapeDirection.z));
            player.setYaw(targetYaw);
            client.options.forwardKey.setPressed(true);
        }
    }
    
    private Vec3d findSafeDirection(Vec3d playerPos) {
        World world = client.world;
        BlockPos playerBlockPos = BlockPos.ofFloored(playerPos);
        
        // Check 8 directions around player
        Vec3d[] directions = {
            new Vec3d(1, 0, 0), new Vec3d(-1, 0, 0),
            new Vec3d(0, 0, 1), new Vec3d(0, 0, -1),
            new Vec3d(1, 0, 1), new Vec3d(-1, 0, -1),
            new Vec3d(1, 0, -1), new Vec3d(-1, 0, 1)
        };
        
        for (Vec3d direction : directions) {
            boolean safe = true;
            
            // Check if this direction is safe for 10 blocks
            for (int i = 1; i <= 10; i++) {
                BlockPos checkPos = playerBlockPos.add(
                    (int)(direction.x * i), 0, (int)(direction.z * i)
                );
                
                Block block = world.getBlockState(checkPos).getBlock();
                if (isTrapBlock(block) || knownTraps.contains(checkPos)) {
                    safe = false;
                    break;
                }
            }
            
            if (safe) {
                return direction;
            }
        }
        
        return null; // No safe direction found
    }
    
    private void cleanupOldEntries(BlockPos playerPos) {
        // Remove trap markers that are far away
        knownTraps.removeIf(pos -> pos.getSquaredDistance(playerPos) > 2500); // 50 block radius
        suspiciousBlocks.removeIf(pos -> pos.getSquaredDistance(playerPos) > 2500);
    }
    
    public void reportTrap(BlockPos pos, String trapType) {
        knownTraps.add(pos);
        ClientPlayerEntity player = client.player;
        if (player != null) {
            player.sendMessage(Text.literal("§a[AntiGrief] Trap reported and marked: " + trapType), false);
        }
    }
    
    public void showThreatReport() {
        ClientPlayerEntity player = client.player;
        if (player == null) return;
        
        player.sendMessage(Text.literal("§6[AntiGrief] Threat Report:"), false);
        player.sendMessage(Text.literal("§7- Known traps: " + knownTraps.size()), false);
        player.sendMessage(Text.literal("§7- Suspicious structures: " + suspiciousBlocks.size()), false);
        player.sendMessage(Text.literal("§7- Players tracked: " + playerLastSeen.size()), false);
        
        if (!playerLastSeen.isEmpty()) {
            player.sendMessage(Text.literal("§7- Recent players: " + 
                String.join(", ", playerLastSeen.keySet())), false);
        }
    }
    
    public boolean isPositionSafe(BlockPos pos) {
        return !knownTraps.contains(pos) && !suspiciousBlocks.contains(pos);
    }
    
    public void enableParanoidMode() {
        ClientPlayerEntity player = client.player;
        if (player != null) {
            player.sendMessage(Text.literal("§c[AntiGrief] Paranoid mode enabled - maximum threat detection!"), false);
        }
        scanCooldown = 10; // Scan every 0.5 seconds in paranoid mode
    }
}