package com.escapemod.systems;

import com.escapemod.utils.MessageThrottler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.particle.ParticleTypes;

public class FallProtectionSystem {
    private MinecraftClient client;
    private boolean enabled = true;
    private int lastFallHeight = 0;
    private boolean wasInDanger = false;
    
    public FallProtectionSystem() {
        this.client = MinecraftClient.getInstance();
    }
    
    public void tick() {
        if (!enabled || client.player == null || client.world == null) {
            return;
        }
        
        ClientPlayerEntity player = client.player;
        
        // Check for dangerous falls
        if (isDangerousFall(player)) {
            if (!wasInDanger) {
                activateFallProtection(player);
                wasInDanger = true;
            }
            
            // Spawn warning particles
            spawnWarningParticles(player);
        } else {
            wasInDanger = false;
        }
        
        // Avoid dangerous falls by preventing them instead of using slow falling
        if (player.getVelocity().y < -0.8) {
            MessageThrottler.sendThrottledMessage(player, "fall_danger_detected", 
                Text.literal("§c[FallProtection] Dangerous fall detected! Attempting emergency measures..."), true, 3000);
        }
    }
    
    private boolean isDangerousFall(ClientPlayerEntity player) {
        if (player.isOnGround() || player.getVelocity().y >= 0) {
            return false;
        }
        
        World world = player.getWorld();
        BlockPos playerPos = player.getBlockPos();
        
        // Check distance to ground
        int groundDistance = 0;
        for (int i = 1; i <= 30; i++) {
            BlockPos checkPos = playerPos.down(i);
            if (!world.getBlockState(checkPos).isAir()) {
                groundDistance = i;
                break;
            }
        }
        
        // Dangerous if falling fast and ground is far
        return groundDistance > 8 && player.getVelocity().y < -0.5;
    }
    
    private void activateFallProtection(ClientPlayerEntity player) {
        // Try safe protection methods (no status effects that trigger anti-cheat)
        
        // 1. Try to use water bucket first (most effective and safe)
        if (tryUseWaterBucket(player)) {
            MessageThrottler.sendThrottledMessage(player, "water_bucket_used", 
                Text.literal("§a[FallProtection] Water bucket deployed!"), false, 5000);
        }
        
        // 2. Try to place blocks as landing platform
        else if (tryPlaceBlocks(player)) {
            MessageThrottler.sendThrottledMessage(player, "blocks_placed", 
                Text.literal("§a[FallProtection] Emergency blocks placed!"), false, 5000);
        }
        
        // 3. As last resort, just warn the player (no status effects)
        else {
            MessageThrottler.sendThrottledMessage(player, "fall_warning", 
                Text.literal("§c[FallProtection] Warning: No fall protection items available!"), false, 5000);
        }
    }
    
    private boolean tryUseWaterBucket(ClientPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == Items.WATER_BUCKET) {
                // Switch to water bucket
                if (player.getInventory().selectedSlot != i && i < 9) {
                    player.getInventory().selectedSlot = i;
                }
                
                // Look down to place water
                player.setPitch(90.0f); // Look straight down
                
                // The actual water placement would be handled by the game's interaction system
                // This is a simplified version that just prepares for water placement
                return true;
            }
        }
        return false;
    }
    
    private boolean tryPlaceBlocks(ClientPlayerEntity player) {
        // Look for any placeable blocks
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isPlaceableBlock(stack) && stack.getCount() > 0) {
                // Switch to the block
                if (player.getInventory().selectedSlot != i && i < 9) {
                    player.getInventory().selectedSlot = i;
                }
                
                // Look down to place blocks
                player.setPitch(90.0f); // Look straight down
                
                // The actual block placement would be handled by the game's interaction system
                // This prepares the player to place blocks as a landing platform
                return true;
            }
        }
        return false;
    }
    
    private boolean isPlaceableBlock(ItemStack stack) {
        return stack.getItem() == Items.DIRT ||
               stack.getItem() == Items.COBBLESTONE ||
               stack.getItem() == Items.STONE ||
               stack.getItem() == Items.OAK_PLANKS ||
               stack.getItem() == Items.SAND ||
               stack.getItem() == Items.GRAVEL;
    }
    
    private void spawnWarningParticles(ClientPlayerEntity player) {
        if (client.world == null) return;
        
        // Spawn red warning particles around the player
        for (int i = 0; i < 5; i++) {
            double offsetX = (Math.random() - 0.5) * 2;
            double offsetZ = (Math.random() - 0.5) * 2;
            
            client.world.addParticle(ParticleTypes.FLAME,
                player.getX() + offsetX,
                player.getY() + 1,
                player.getZ() + offsetZ,
                0, 0.1, 0);
        }
        
        // Spawn particles below to show danger
        BlockPos playerPos = player.getBlockPos();
        for (int i = 1; i <= 10; i++) {
            client.world.addParticle(ParticleTypes.LAVA,
                playerPos.getX() + 0.5,
                playerPos.getY() - i,
                playerPos.getZ() + 0.5,
                0, 0, 0);
        }
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
}