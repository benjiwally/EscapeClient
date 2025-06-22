package com.escapemod.systems;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.Entity;

import java.util.*;

/**
 * Crisis Mode - Emergency survival system when critically low on resources
 * Switches to minimal movement, hide-and-recover mode when in danger
 */
public class CrisisMode {
    private final MinecraftClient client;
    private boolean crisisModeActive = false;
    private CrisisType currentCrisis = CrisisType.NONE;
    private long crisisStartTime = 0;
    private BlockPos safeHideout = null;
    private int recoveryAttempts = 0;
    
    // Crisis thresholds
    private static final float CRITICAL_HEALTH = 5.0f;
    private static final int CRITICAL_HUNGER = 6; // 3 hunger bars
    private static final int CRITICAL_FOOD_ITEMS = 0;
    private static final int MAX_RECOVERY_ATTEMPTS = 10;
    
    public CrisisMode(MinecraftClient client) {
        this.client = client;
    }
    
    /**
     * Check if player is in crisis and needs emergency mode
     */
    public boolean shouldActivateCrisis() {
        if (client.player == null) return false;
        
        ClientPlayerEntity player = client.player;
        
        // Check health crisis
        if (player.getHealth() <= CRITICAL_HEALTH) {
            currentCrisis = CrisisType.LOW_HEALTH;
            return true;
        }
        
        // Check hunger crisis
        if (player.getHungerManager().getFoodLevel() <= CRITICAL_HUNGER) {
            currentCrisis = CrisisType.LOW_HUNGER;
            return true;
        }
        
        // Check food crisis (no food items)
        if (countFoodItems() <= CRITICAL_FOOD_ITEMS && player.getHungerManager().getFoodLevel() < 15) {
            currentCrisis = CrisisType.NO_FOOD;
            return true;
        }
        
        // Check tool crisis (no tools and low durability)
        if (hasNoUsableTools()) {
            currentCrisis = CrisisType.NO_TOOLS;
            return true;
        }
        
        // Check combined crisis (multiple problems)
        if (player.getHealth() <= 10.0f && player.getHungerManager().getFoodLevel() <= 10 && countFoodItems() <= 2) {
            currentCrisis = CrisisType.MULTIPLE_CRISIS;
            return true;
        }
        
        return false;
    }
    
    /**
     * Activate crisis mode
     */
    public void activateCrisisMode() {
        if (crisisModeActive) return;
        
        crisisModeActive = true;
        crisisStartTime = System.currentTimeMillis();
        recoveryAttempts = 0;
        
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§c§l[EscapeMod] CRISIS MODE ACTIVATED!"), false);
            client.player.sendMessage(Text.literal("§e[EscapeMod] Crisis Type: " + currentCrisis.getDescription()), false);
            client.player.sendMessage(Text.literal("§e[EscapeMod] Switching to survival mode..."), false);
        }
        
        // Find immediate shelter
        findEmergencyShelter();
    }
    
    /**
     * Execute crisis mode behavior
     */
    public CrisisAction executeCrisisMode() {
        if (!crisisModeActive || client.player == null) {
            return CrisisAction.NONE;
        }
        
        ClientPlayerEntity player = client.player;
        recoveryAttempts++;
        
        // Check if crisis is resolved
        if (isCrisisResolved()) {
            deactivateCrisisMode();
            return CrisisAction.CRISIS_RESOLVED;
        }
        
        // If too many attempts, give up and resume normal operation
        if (recoveryAttempts > MAX_RECOVERY_ATTEMPTS) {
            deactivateCrisisMode();
            return CrisisAction.GIVE_UP;
        }
        
        // Execute crisis-specific actions
        switch (currentCrisis) {
            case LOW_HEALTH:
                return handleHealthCrisis();
            case LOW_HUNGER:
                return handleHungerCrisis();
            case NO_FOOD:
                return handleFoodCrisis();
            case NO_TOOLS:
                return handleToolCrisis();
            case MULTIPLE_CRISIS:
                return handleMultipleCrisis();
            default:
                return CrisisAction.HIDE_AND_WAIT;
        }
    }
    
    /**
     * Handle low health crisis
     */
    private CrisisAction handleHealthCrisis() {
        if (client.player == null) return CrisisAction.HIDE_AND_WAIT;
        
        // Priority 1: Use healing items
        if (useHealingItem()) {
            return CrisisAction.USE_HEALING_ITEM;
        }
        
        // Priority 2: Eat food to regenerate
        if (eatFood()) {
            return CrisisAction.EAT_FOOD;
        }
        
        // Priority 3: Find safe place to hide
        if (safeHideout == null) {
            findEmergencyShelter();
        }
        
        if (safeHideout != null) {
            return CrisisAction.RETREAT_TO_SHELTER;
        }
        
        // Priority 4: Craft shield for protection
        if (canCraftShield()) {
            return CrisisAction.CRAFT_SHIELD;
        }
        
        return CrisisAction.HIDE_AND_WAIT;
    }
    
    /**
     * Handle hunger crisis
     */
    private CrisisAction handleHungerCrisis() {
        // Priority 1: Eat any available food
        if (eatFood()) {
            return CrisisAction.EAT_FOOD;
        }
        
        // Priority 2: Hunt animals
        Entity nearbyAnimal = findNearbyAnimal();
        if (nearbyAnimal != null) {
            return CrisisAction.HUNT_ANIMAL;
        }
        
        // Priority 3: Look for food sources
        BlockPos foodSource = findFoodSource();
        if (foodSource != null) {
            return CrisisAction.GATHER_FOOD;
        }
        
        // Priority 4: Craft basic food if possible
        if (canCraftBread()) {
            return CrisisAction.CRAFT_FOOD;
        }
        
        return CrisisAction.SEARCH_FOR_FOOD;
    }
    
    /**
     * Handle no food crisis
     */
    private CrisisAction handleFoodCrisis() {
        // Similar to hunger crisis but more aggressive
        Entity nearbyAnimal = findNearbyAnimal();
        if (nearbyAnimal != null) {
            return CrisisAction.HUNT_ANIMAL;
        }
        
        BlockPos foodSource = findFoodSource();
        if (foodSource != null) {
            return CrisisAction.GATHER_FOOD;
        }
        
        return CrisisAction.SEARCH_FOR_FOOD;
    }
    
    /**
     * Handle no tools crisis
     */
    private CrisisAction handleToolCrisis() {
        // Priority 1: Craft basic tools
        if (canCraftWoodenTools()) {
            return CrisisAction.CRAFT_TOOLS;
        }
        
        // Priority 2: Find materials for tools
        BlockPos woodSource = findNearbyWood();
        if (woodSource != null) {
            return CrisisAction.GATHER_WOOD;
        }
        
        BlockPos stoneSource = findNearbyStone();
        if (stoneSource != null) {
            return CrisisAction.GATHER_STONE;
        }
        
        return CrisisAction.SEARCH_FOR_MATERIALS;
    }
    
    /**
     * Handle multiple crisis situation
     */
    private CrisisAction handleMultipleCrisis() {
        // Prioritize immediate survival
        if (client.player.getHealth() <= 2.0f) {
            return handleHealthCrisis();
        }
        
        if (client.player.getHungerManager().getFoodLevel() <= 2) {
            return handleHungerCrisis();
        }
        
        // Find shelter and wait
        if (safeHideout == null) {
            findEmergencyShelter();
        }
        
        return CrisisAction.HIDE_AND_WAIT;
    }
    
    /**
     * Find emergency shelter
     */
    private void findEmergencyShelter() {
        if (client.world == null || client.player == null) return;
        
        BlockPos playerPos = client.player.getBlockPos();
        
        // Look for natural shelter nearby
        for (int radius = 5; radius <= 20; radius += 5) {
            for (int angle = 0; angle < 360; angle += 45) {
                double radians = Math.toRadians(angle);
                int x = (int) (playerPos.getX() + radius * Math.cos(radians));
                int z = (int) (playerPos.getZ() + radius * Math.sin(radians));
                
                BlockPos candidate = new BlockPos(x, playerPos.getY(), z);
                if (isSafeShelter(candidate)) {
                    safeHideout = candidate;
                    return;
                }
            }
        }
        
        // If no natural shelter, find place to dig in
        safeHideout = findDiggableShelter(playerPos);
    }
    
    /**
     * Check if location provides safe shelter
     */
    private boolean isSafeShelter(BlockPos pos) {
        if (client.world == null) return false;
        
        // Check for overhead protection
        boolean hasRoof = false;
        for (int y = 1; y <= 5; y++) {
            Block block = client.world.getBlockState(pos.up(y)).getBlock();
            if (block != Blocks.AIR) {
                hasRoof = true;
                break;
            }
        }
        
        if (!hasRoof) return false;
        
        // Check for walls (at least 2 sides protected)
        int protectedSides = 0;
        int[][] directions = {{1,0}, {-1,0}, {0,1}, {0,-1}};
        
        for (int[] dir : directions) {
            BlockPos wallPos = pos.add(dir[0], 0, dir[1]);
            Block block = client.world.getBlockState(wallPos).getBlock();
            if (block != Blocks.AIR) {
                protectedSides++;
            }
        }
        
        return protectedSides >= 2;
    }
    
    /**
     * Find location suitable for digging shelter
     */
    private BlockPos findDiggableShelter(BlockPos playerPos) {
        if (client.world == null) return null;
        
        // Look for hillside or underground location
        for (int dx = -10; dx <= 10; dx += 2) {
            for (int dz = -10; dz <= 10; dz += 2) {
                BlockPos candidate = playerPos.add(dx, 0, dz);
                
                // Check if we can dig into a hill
                Block block = client.world.getBlockState(candidate).getBlock();
                if (block == Blocks.STONE || block == Blocks.DIRT || block == Blocks.COBBLESTONE) {
                    return candidate;
                }
            }
        }
        
        return playerPos; // Fallback to current position
    }
    
    /**
     * Utility methods for crisis handling
     */
    private boolean useHealingItem() {
        if (client.player == null) return false;
        
        PlayerInventory inventory = client.player.getInventory();
        
        // Look for healing items
        Item[] healingItems = {
            Items.GOLDEN_APPLE,
            Items.ENCHANTED_GOLDEN_APPLE,
            Items.POTION // Health potions
        };
        
        for (Item item : healingItems) {
            if (inventory.count(item) > 0) {
                // Would use the item here
                return true;
            }
        }
        
        return false;
    }
    
    private boolean eatFood() {
        if (client.player == null) return false;
        
        PlayerInventory inventory = client.player.getInventory();
        
        // Look for any food items
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.getItem().isFood()) {
                // Would eat the food here
                return true;
            }
        }
        
        return false;
    }
    
    private int countFoodItems() {
        if (client.player == null) return 0;
        
        PlayerInventory inventory = client.player.getInventory();
        int foodCount = 0;
        
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.getItem().isFood()) {
                foodCount += stack.getCount();
            }
        }
        
        return foodCount;
    }
    
    private boolean hasNoUsableTools() {
        if (client.player == null) return true;
        
        PlayerInventory inventory = client.player.getInventory();
        
        // Check for any pickaxe, axe, or sword
        Item[] tools = {
            Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE,
            Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE,
            Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD
        };
        
        for (Item tool : tools) {
            if (inventory.count(tool) > 0) {
                return false;
            }
        }
        
        return true;
    }
    
    private Entity findNearbyAnimal() {
        if (client.world == null || client.player == null) return null;
        
        // Look for nearby animals to hunt
        return client.world.getEntitiesByClass(AnimalEntity.class, 
            client.player.getBoundingBox().expand(20), 
            entity -> entity.isAlive()).stream().findFirst().orElse(null);
    }
    
    private BlockPos findFoodSource() {
        if (client.world == null || client.player == null) return null;
        
        BlockPos playerPos = client.player.getBlockPos();
        
        // Look for crops, berries, etc.
        Block[] foodBlocks = {
            Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES,
            Blocks.SWEET_BERRY_BUSH, Blocks.MELON, Blocks.PUMPKIN
        };
        
        for (int radius = 5; radius <= 30; radius += 5) {
            for (Block foodBlock : foodBlocks) {
                BlockPos found = findNearbyBlock(playerPos, foodBlock, radius);
                if (found != null) {
                    return found;
                }
            }
        }
        
        return null;
    }
    
    private BlockPos findNearbyWood() {
        if (client.player == null) return null;
        
        Block[] woodBlocks = {
            Blocks.OAK_LOG, Blocks.BIRCH_LOG, Blocks.SPRUCE_LOG,
            Blocks.JUNGLE_LOG, Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG
        };
        
        BlockPos playerPos = client.player.getBlockPos();
        
        for (Block woodBlock : woodBlocks) {
            BlockPos found = findNearbyBlock(playerPos, woodBlock, 20);
            if (found != null) {
                return found;
            }
        }
        
        return null;
    }
    
    private BlockPos findNearbyStone() {
        if (client.player == null) return null;
        
        BlockPos playerPos = client.player.getBlockPos();
        return findNearbyBlock(playerPos, Blocks.STONE, 15);
    }
    
    private BlockPos findNearbyBlock(BlockPos center, Block targetBlock, int radius) {
        if (client.world == null) return null;
        
        for (int x = -radius; x <= radius; x += 2) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -radius; z <= radius; z += 2) {
                    BlockPos checkPos = center.add(x, y, z);
                    if (client.world.getBlockState(checkPos).getBlock() == targetBlock) {
                        return checkPos;
                    }
                }
            }
        }
        
        return null;
    }
    
    private boolean canCraftShield() {
        if (client.player == null) return false;
        
        PlayerInventory inventory = client.player.getInventory();
        return inventory.count(Items.IRON_INGOT) >= 1 && inventory.count(Items.OAK_PLANKS) >= 6;
    }
    
    private boolean canCraftBread() {
        if (client.player == null) return false;
        
        PlayerInventory inventory = client.player.getInventory();
        return inventory.count(Items.WHEAT) >= 3;
    }
    
    private boolean canCraftWoodenTools() {
        if (client.player == null) return false;
        
        PlayerInventory inventory = client.player.getInventory();
        return inventory.count(Items.OAK_PLANKS) >= 3 && inventory.count(Items.STICK) >= 2;
    }
    
    /**
     * Check if crisis is resolved
     */
    private boolean isCrisisResolved() {
        if (client.player == null) return true;
        
        ClientPlayerEntity player = client.player;
        
        switch (currentCrisis) {
            case LOW_HEALTH:
                return player.getHealth() > CRITICAL_HEALTH + 5;
            case LOW_HUNGER:
                return player.getHungerManager().getFoodLevel() > CRITICAL_HUNGER + 4;
            case NO_FOOD:
                return countFoodItems() > 5;
            case NO_TOOLS:
                return !hasNoUsableTools();
            case MULTIPLE_CRISIS:
                return player.getHealth() > 10 && player.getHungerManager().getFoodLevel() > 10 && countFoodItems() > 3;
            default:
                return true;
        }
    }
    
    /**
     * Deactivate crisis mode
     */
    public void deactivateCrisisMode() {
        if (!crisisModeActive) return;
        
        crisisModeActive = false;
        safeHideout = null;
        recoveryAttempts = 0;
        
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§a[EscapeMod] Crisis resolved! Resuming normal operation."), false);
        }
        
        currentCrisis = CrisisType.NONE;
    }
    
    // Getters
    public boolean isCrisisModeActive() { return crisisModeActive; }
    public CrisisType getCurrentCrisis() { return currentCrisis; }
    public BlockPos getSafeHideout() { return safeHideout; }
    public long getCrisisDuration() { return crisisModeActive ? System.currentTimeMillis() - crisisStartTime : 0; }
    
    /**
     * Crisis types
     */
    public enum CrisisType {
        NONE("No Crisis"),
        LOW_HEALTH("Critical Health"),
        LOW_HUNGER("Critical Hunger"),
        NO_FOOD("No Food Available"),
        NO_TOOLS("No Usable Tools"),
        MULTIPLE_CRISIS("Multiple Critical Issues");
        
        private final String description;
        
        CrisisType(String description) {
            this.description = description;
        }
        
        public String getDescription() { return description; }
    }
    
    /**
     * Crisis actions
     */
    public enum CrisisAction {
        NONE,
        CRISIS_RESOLVED,
        GIVE_UP,
        HIDE_AND_WAIT,
        USE_HEALING_ITEM,
        EAT_FOOD,
        RETREAT_TO_SHELTER,
        CRAFT_SHIELD,
        HUNT_ANIMAL,
        GATHER_FOOD,
        CRAFT_FOOD,
        SEARCH_FOR_FOOD,
        CRAFT_TOOLS,
        GATHER_WOOD,
        GATHER_STONE,
        SEARCH_FOR_MATERIALS
    }
}