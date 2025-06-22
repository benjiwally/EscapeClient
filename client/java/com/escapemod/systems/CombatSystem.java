package com.escapemod.systems;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class CombatSystem {
    private MinecraftClient client;
    private LivingEntity currentTarget = null;
    private int combatCooldown = 0;
    private int retreatTimer = 0;
    private boolean isRetreating = false;
    private long lastCombatMessage = 0;
    
    // Combat settings
    private boolean autoFightMobs = true;
    private boolean autoFightPlayers = false; // Disabled by default for safety
    private double engagementRange = 8.0;
    private double retreatRange = 15.0;
    
    public CombatSystem() {
        this.client = MinecraftClient.getInstance();
    }
    
    public void tick() {
        if (client.player == null || client.world == null) return;
        
        combatCooldown--;
        retreatTimer--;
        
        ClientPlayerEntity player = client.player;
        
        // Check if we should retreat
        if (shouldRetreat()) {
            handleRetreat();
            return;
        }
        
        // Find and engage targets
        if (combatCooldown <= 0) {
            LivingEntity target = findTarget();
            if (target != null) {
                engageTarget(target);
            }
        }
        
        // Continue fighting current target
        if (currentTarget != null && isValidTarget(currentTarget)) {
            fightTarget();
        } else {
            currentTarget = null;
            isRetreating = false;
        }
    }
    
    private LivingEntity findTarget() {
        ClientPlayerEntity player = client.player;
        LivingEntity closestTarget = null;
        double closestDistance = engagementRange;
        
        // Find hostile mobs
        if (autoFightMobs) {
            List<HostileEntity> hostileMobs = client.world.getEntitiesByClass(
                HostileEntity.class,
                player.getBoundingBox().expand(engagementRange),
                mob -> mob.isAlive() && mob.canSee(player)
            );
            
            for (HostileEntity mob : hostileMobs) {
                double distance = player.getPos().distanceTo(mob.getPos());
                if (distance < closestDistance) {
                    closestTarget = mob;
                    closestDistance = distance;
                }
            }
        }
        
        // Find hostile players (if enabled)
        if (autoFightPlayers) {
            List<PlayerEntity> nearbyPlayers = client.world.getEntitiesByClass(
                PlayerEntity.class,
                player.getBoundingBox().expand(engagementRange),
                p -> p != player && p.isAlive()
            );
            
            for (PlayerEntity otherPlayer : nearbyPlayers) {
                // Only target players who are attacking us or very close
                if (isPlayerHostile(otherPlayer)) {
                    double distance = player.getPos().distanceTo(otherPlayer.getPos());
                    if (distance < closestDistance) {
                        closestTarget = otherPlayer;
                        closestDistance = distance;
                    }
                }
            }
        }
        
        return closestTarget;
    }
    
    private boolean isPlayerHostile(PlayerEntity otherPlayer) {
        ClientPlayerEntity player = client.player;
        
        // Check if player is very close (potential threat)
        double distance = player.getPos().distanceTo(otherPlayer.getPos());
        if (distance < 3.0) return true;
        
        // Check if player is looking at us and moving towards us
        Vec3d playerLook = Vec3d.fromPolar(otherPlayer.getPitch(), otherPlayer.getYaw());
        Vec3d toUs = player.getPos().subtract(otherPlayer.getPos()).normalize();
        double lookAlignment = playerLook.dotProduct(toUs);
        
        if (lookAlignment > 0.8 && distance < 8.0) return true;
        
        // Check if player has weapon equipped
        if (otherPlayer.getMainHandStack().getItem() instanceof net.minecraft.item.SwordItem ||
            otherPlayer.getMainHandStack().getItem() instanceof net.minecraft.item.AxeItem) {
            return distance < 6.0;
        }
        
        return false;
    }
    
    private void engageTarget(LivingEntity target) {
        currentTarget = target;
        ClientPlayerEntity player = client.player;
        
        // Only show combat messages every 5 seconds
        if (System.currentTimeMillis() - lastCombatMessage > 5000) {
            String targetType = target instanceof PlayerEntity ? "Player" : "Mob";
            player.sendMessage(Text.literal("§c[Combat] Engaging " + targetType + ": " + 
                target.getName().getString()), true);
            lastCombatMessage = System.currentTimeMillis();
        }
        
        combatCooldown = 10; // Brief cooldown before next target search
    }
    
    private void fightTarget() {
        if (currentTarget == null) return;
        
        ClientPlayerEntity player = client.player;
        double distance = player.getPos().distanceTo(currentTarget.getPos());
        
        // Face the target
        Vec3d targetPos = currentTarget.getPos();
        Vec3d playerPos = player.getPos();
        Vec3d direction = targetPos.subtract(playerPos).normalize();
        
        float targetYaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float targetPitch = (float) Math.toDegrees(Math.asin(-direction.y));
        
        player.setYaw(targetYaw);
        player.setPitch(targetPitch);
        
        // Move towards target if too far
        if (distance > 3.0) {
            client.options.forwardKey.setPressed(true);
            client.options.sprintKey.setPressed(true);
        } else {
            client.options.forwardKey.setPressed(false);
            client.options.sprintKey.setPressed(false);
        }
        
        // Attack when in range
        if (distance <= 4.0) {
            // Equip best weapon
            equipBestWeapon();
            
            // Attack
            client.options.attackKey.setPressed(true);
            
            // Strafe to avoid attacks
            if (System.currentTimeMillis() % 1000 < 500) {
                client.options.leftKey.setPressed(true);
                client.options.rightKey.setPressed(false);
            } else {
                client.options.leftKey.setPressed(false);
                client.options.rightKey.setPressed(true);
            }
            
            // Jump occasionally to avoid attacks
            if (System.currentTimeMillis() % 2000 < 100) {
                client.options.jumpKey.setPressed(true);
            } else {
                client.options.jumpKey.setPressed(false);
            }
        } else {
            client.options.attackKey.setPressed(false);
            client.options.leftKey.setPressed(false);
            client.options.rightKey.setPressed(false);
            client.options.jumpKey.setPressed(false);
        }
    }
    
    private boolean shouldRetreat() {
        ClientPlayerEntity player = client.player;
        
        // Retreat if health is low
        if (player.getHealth() < 6.0f) {
            if (!isRetreating) {
                player.sendMessage(Text.literal("§c[Combat] Low health! Retreating!"), false);
                isRetreating = true;
                retreatTimer = 200; // Retreat for 10 seconds
            }
            return true;
        }
        
        // Retreat if outnumbered
        List<LivingEntity> nearbyEnemies = client.world.getEntitiesByClass(
            LivingEntity.class,
            player.getBoundingBox().expand(10),
            entity -> (entity instanceof HostileEntity || 
                      (entity instanceof PlayerEntity && entity != player)) && 
                     entity.isAlive()
        );
        
        if (nearbyEnemies.size() > 3) {
            if (!isRetreating) {
                player.sendMessage(Text.literal("§c[Combat] Outnumbered! Retreating!"), false);
                isRetreating = true;
                retreatTimer = 150; // Retreat for 7.5 seconds
            }
            return true;
        }
        
        return isRetreating && retreatTimer > 0;
    }
    
    private void handleRetreat() {
        ClientPlayerEntity player = client.player;
        
        // Stop all combat actions
        client.options.attackKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        
        // Find escape direction
        Vec3d escapeDirection = findEscapeDirection();
        if (escapeDirection != null) {
            float targetYaw = (float) Math.toDegrees(Math.atan2(-escapeDirection.x, escapeDirection.z));
            player.setYaw(targetYaw);
            
            client.options.forwardKey.setPressed(true);
            client.options.sprintKey.setPressed(true);
            client.options.jumpKey.setPressed(true); // Jump while retreating
        }
        
        // Use food if available
        if (player.getHealth() < 10.0f) {
            useFood();
        }
        
        currentTarget = null;
    }
    
    private Vec3d findEscapeDirection() {
        ClientPlayerEntity player = client.player;
        Vec3d playerPos = player.getPos();
        
        // Find direction away from all enemies
        Vec3d escapeDirection = Vec3d.ZERO;
        
        List<LivingEntity> nearbyEnemies = client.world.getEntitiesByClass(
            LivingEntity.class,
            player.getBoundingBox().expand(15),
            entity -> (entity instanceof HostileEntity || 
                      (entity instanceof PlayerEntity && entity != player)) && 
                     entity.isAlive()
        );
        
        for (LivingEntity enemy : nearbyEnemies) {
            Vec3d awayFromEnemy = playerPos.subtract(enemy.getPos()).normalize();
            escapeDirection = escapeDirection.add(awayFromEnemy);
        }
        
        if (escapeDirection.length() > 0) {
            return escapeDirection.normalize();
        }
        
        // If no specific direction, just run forward
        return Vec3d.fromPolar(0, player.getYaw());
    }
    
    private void equipBestWeapon() {
        ClientPlayerEntity player = client.player;
        
        // Priority order for weapons
        Item[] weapons = {
            Items.DIAMOND_SWORD, Items.IRON_SWORD, Items.STONE_SWORD, Items.WOODEN_SWORD,
            Items.DIAMOND_AXE, Items.IRON_AXE, Items.STONE_AXE, Items.WOODEN_AXE
        };
        
        for (Item weapon : weapons) {
            for (int i = 0; i < 9; i++) { // Check hotbar
                if (player.getInventory().getStack(i).getItem().equals(weapon)) {
                    player.getInventory().selectedSlot = i;
                    return;
                }
            }
        }
    }
    
    private void useFood() {
        ClientPlayerEntity player = client.player;
        
        // Priority order for food
        Item[] foods = {
            Items.GOLDEN_APPLE, Items.COOKED_BEEF, Items.COOKED_PORKCHOP, 
            Items.BREAD, Items.APPLE, Items.COOKED_CHICKEN
        };
        
        for (Item food : foods) {
            for (int i = 0; i < 9; i++) { // Check hotbar
                if (player.getInventory().getStack(i).getItem().equals(food)) {
                    int previousSlot = player.getInventory().selectedSlot;
                    player.getInventory().selectedSlot = i;
                    
                    // Use the food
                    client.options.useKey.setPressed(true);
                    
                    // Schedule to release use key and restore slot
                    // This would need proper timing implementation
                    
                    return;
                }
            }
        }
    }
    
    private boolean isValidTarget(LivingEntity target) {
        if (target == null || !target.isAlive()) return false;
        
        ClientPlayerEntity player = client.player;
        double distance = player.getPos().distanceTo(target.getPos());
        
        return distance <= retreatRange;
    }
    
    // Configuration methods
    public void setAutoFightMobs(boolean enabled) {
        this.autoFightMobs = enabled;
        ClientPlayerEntity player = client.player;
        if (player != null) {
            player.sendMessage(Text.literal("§e[Combat] Auto-fight mobs: " + (enabled ? "§aEnabled" : "§cDisabled")), false);
        }
    }
    
    public void setAutoFightPlayers(boolean enabled) {
        this.autoFightPlayers = enabled;
        ClientPlayerEntity player = client.player;
        if (player != null) {
            player.sendMessage(Text.literal("§e[Combat] Auto-fight players: " + (enabled ? "§aEnabled" : "§cDisabled")), false);
            if (enabled) {
                player.sendMessage(Text.literal("§c[Combat] WARNING: PvP mode enabled! Use with caution!"), false);
            }
        }
    }
    
    public void showCombatStatus() {
        ClientPlayerEntity player = client.player;
        if (player == null) return;
        
        player.sendMessage(Text.literal("§6[Combat] Status:"), false);
        player.sendMessage(Text.literal("§7- Auto-fight mobs: " + (autoFightMobs ? "§aEnabled" : "§cDisabled")), false);
        player.sendMessage(Text.literal("§7- Auto-fight players: " + (autoFightPlayers ? "§aEnabled" : "§cDisabled")), false);
        player.sendMessage(Text.literal("§7- Current target: " + (currentTarget != null ? currentTarget.getName().getString() : "None")), false);
        player.sendMessage(Text.literal("§7- Retreating: " + (isRetreating ? "§cYes" : "§aNo")), false);
    }
    
    public void emergencyStop() {
        currentTarget = null;
        isRetreating = false;
        retreatTimer = 0;
        
        // Stop all combat actions
        client.options.attackKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
        client.options.forwardKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
        
        ClientPlayerEntity player = client.player;
        if (player != null) {
            player.sendMessage(Text.literal("§c[Combat] Emergency stop activated!"), false);
        }
    }
}