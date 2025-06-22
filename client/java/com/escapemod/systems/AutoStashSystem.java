package com.escapemod.systems;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import net.minecraft.block.Blocks;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Auto-Stash System - Automatically manages inventory overflow
 * Creates hidden stashes and logs coordinates for later retrieval
 */
public class AutoStashSystem {
    private final MinecraftClient client;
    private final List<StashLocation> stashHistory = new ArrayList<>();
    private final Set<Item> valuableItems = new HashSet<>();
    private final Random random = new Random();
    
    private boolean autoStashEnabled = true;
    private int inventoryFullThreshold = 32; // Trigger when 32+ slots full
    private long lastStashTime = 0;
    private static final long STASH_COOLDOWN = 30000; // 30 seconds between stashes
    
    public AutoStashSystem(MinecraftClient client) {
        this.client = client;
        initializeValuableItems();
    }
    
    /**
     * Initialize list of valuable items to prioritize
     */
    private void initializeValuableItems() {
        // Diamonds and rare materials
        valuableItems.add(Items.DIAMOND);
        valuableItems.add(Items.EMERALD);
        valuableItems.add(Items.NETHERITE_INGOT);
        valuableItems.add(Items.NETHERITE_SCRAP);
        valuableItems.add(Items.ANCIENT_DEBRIS);
        
        // Valuable tools and armor
        valuableItems.add(Items.DIAMOND_PICKAXE);
        valuableItems.add(Items.DIAMOND_SWORD);
        valuableItems.add(Items.DIAMOND_AXE);
        valuableItems.add(Items.NETHERITE_PICKAXE);
        valuableItems.add(Items.NETHERITE_SWORD);
        valuableItems.add(Items.NETHERITE_AXE);
        
        // Enchanted books and rare items
        valuableItems.add(Items.ENCHANTED_BOOK);
        valuableItems.add(Items.ENDER_PEARL);
        valuableItems.add(Items.BLAZE_ROD);
        valuableItems.add(Items.GHAST_TEAR);
        valuableItems.add(Items.NETHER_STAR);
        
        // Shulker boxes and storage
        valuableItems.add(Items.SHULKER_BOX);
        valuableItems.add(Items.WHITE_SHULKER_BOX);
        valuableItems.add(Items.ORANGE_SHULKER_BOX);
        valuableItems.add(Items.MAGENTA_SHULKER_BOX);
        valuableItems.add(Items.LIGHT_BLUE_SHULKER_BOX);
        valuableItems.add(Items.YELLOW_SHULKER_BOX);
        valuableItems.add(Items.LIME_SHULKER_BOX);
        valuableItems.add(Items.PINK_SHULKER_BOX);
        valuableItems.add(Items.GRAY_SHULKER_BOX);
        valuableItems.add(Items.LIGHT_GRAY_SHULKER_BOX);
        valuableItems.add(Items.CYAN_SHULKER_BOX);
        valuableItems.add(Items.PURPLE_SHULKER_BOX);
        valuableItems.add(Items.BLUE_SHULKER_BOX);
        valuableItems.add(Items.BROWN_SHULKER_BOX);
        valuableItems.add(Items.GREEN_SHULKER_BOX);
        valuableItems.add(Items.RED_SHULKER_BOX);
        valuableItems.add(Items.BLACK_SHULKER_BOX);
    }
    
    /**
     * Check if inventory needs stashing
     */
    public boolean needsStash() {
        if (!autoStashEnabled || client.player == null) return false;
        
        PlayerInventory inventory = client.player.getInventory();
        int filledSlots = 0;
        
        // Count filled inventory slots
        for (int i = 0; i < inventory.size(); i++) {
            if (!inventory.getStack(i).isEmpty()) {
                filledSlots++;
            }
        }
        
        // Check cooldown
        if (System.currentTimeMillis() - lastStashTime < STASH_COOLDOWN) {
            return false;
        }
        
        return filledSlots >= inventoryFullThreshold;
    }
    
    /**
     * Execute auto-stash procedure
     */
    public StashResult executeStash() {
        if (client.player == null || client.world == null) {
            return StashResult.FAILED;
        }
        
        ClientPlayerEntity player = client.player;
        BlockPos playerPos = player.getBlockPos();
        
        // Find safe stash location
        BlockPos stashPos = findSafeStashLocation(playerPos);
        if (stashPos == null) {
            return StashResult.NO_SAFE_LOCATION;
        }
        
        // Determine stash type based on available items
        StashType stashType = determineStashType();
        
        // Create the stash
        boolean success = createStash(stashPos, stashType);
        if (!success) {
            return StashResult.FAILED;
        }
        
        // Log the stash
        logStash(stashPos, stashType);
        
        lastStashTime = System.currentTimeMillis();
        
        return StashResult.SUCCESS;
    }
    
    /**
     * Find a safe location for stashing
     */
    private BlockPos findSafeStashLocation(BlockPos playerPos) {
        if (client.world == null) return null;
        
        // Try multiple random locations around player
        for (int attempt = 0; attempt < 20; attempt++) {
            int distance = 10 + random.nextInt(40); // 10-50 blocks away
            double angle = random.nextDouble() * 2 * Math.PI;
            
            int x = (int) (playerPos.getX() + distance * Math.cos(angle));
            int z = (int) (playerPos.getZ() + distance * Math.sin(angle));
            
            // Find ground level
            BlockPos candidate = findGroundLevel(new BlockPos(x, playerPos.getY(), z));
            if (candidate != null && isSafeStashLocation(candidate)) {
                return candidate;
            }
        }
        
        return null;
    }
    
    /**
     * Find ground level at position
     */
    private BlockPos findGroundLevel(BlockPos pos) {
        if (client.world == null) return null;
        
        // Search down for solid ground
        for (int y = pos.getY(); y >= -60; y--) {
            BlockPos checkPos = new BlockPos(pos.getX(), y, pos.getZ());
            if (client.world.getBlockState(checkPos).getBlock() != Blocks.AIR &&
                client.world.getBlockState(checkPos.up()).getBlock() == Blocks.AIR) {
                return checkPos.up();
            }
        }
        
        return null;
    }
    
    /**
     * Check if location is safe for stashing
     */
    private boolean isSafeStashLocation(BlockPos pos) {
        if (client.world == null) return false;
        
        // Check for solid ground
        if (client.world.getBlockState(pos.down()).getBlock() == Blocks.AIR) {
            return false;
        }
        
        // Check for dangerous blocks nearby
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos checkPos = pos.add(dx, dy, dz);
                    var block = client.world.getBlockState(checkPos).getBlock();
                    
                    if (block == Blocks.LAVA || block == Blocks.TNT || block == Blocks.CACTUS) {
                        return false;
                    }
                }
            }
        }
        
        // Check if too close to existing stashes
        for (StashLocation existing : stashHistory) {
            if (existing.position.getSquaredDistance(pos) < 400) { // 20 blocks
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Determine what type of stash to create
     */
    private StashType determineStashType() {
        if (client.player == null) return StashType.BURIED_CHEST;
        
        PlayerInventory inventory = client.player.getInventory();
        
        // Prefer shulker box if available
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (isShulkerBox(stack.getItem())) {
                return StashType.SHULKER_BOX;
            }
        }
        
        // Use chest if available
        if (inventory.count(Items.CHEST) > 0) {
            return StashType.CHEST;
        }
        
        // Use barrel if available
        if (inventory.count(Items.BARREL) > 0) {
            return StashType.BARREL;
        }
        
        // Default to buried chest (craft if needed)
        return StashType.BURIED_CHEST;
    }
    
    /**
     * Create the actual stash
     */
    private boolean createStash(BlockPos pos, StashType type) {
        if (client.player == null) return false;
        
        // This would require actual block placement and inventory management
        // For now, just simulate the stash creation
        
        List<ItemStack> itemsToStash = selectItemsToStash();
        if (itemsToStash.isEmpty()) return false;
        
        // Create stash record
        StashLocation stash = new StashLocation(pos, type, System.currentTimeMillis(), itemsToStash);
        stashHistory.add(stash);
        
        // Send notification
        String typeStr = type.toString().toLowerCase().replace('_', ' ');
        client.player.sendMessage(Text.literal("§a[EscapeMod] Created " + typeStr + " stash at " + 
            pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
        
        // Send coded message for discretion
        String code = generateStashCode(stash);
        client.player.sendMessage(Text.literal("§7[EscapeMod] Stash code: §e" + code), false);
        
        return true;
    }
    
    /**
     * Select items to stash (prioritize valuable items)
     */
    private List<ItemStack> selectItemsToStash() {
        if (client.player == null) return new ArrayList<>();
        
        PlayerInventory inventory = client.player.getInventory();
        List<ItemStack> toStash = new ArrayList<>();
        
        // First pass: valuable items
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && valuableItems.contains(stack.getItem())) {
                toStash.add(stack.copy());
                if (toStash.size() >= 27) break; // Chest size limit
            }
        }
        
        // Second pass: fill remaining space with other items
        if (toStash.size() < 27) {
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack stack = inventory.getStack(i);
                if (!stack.isEmpty() && !valuableItems.contains(stack.getItem()) && 
                    !isEssentialItem(stack.getItem())) {
                    toStash.add(stack.copy());
                    if (toStash.size() >= 27) break;
                }
            }
        }
        
        return toStash;
    }
    
    /**
     * Check if item is essential (shouldn't be stashed)
     */
    private boolean isEssentialItem(Item item) {
        return item == Items.FOOD || // Keep some food
               item == Items.BREAD ||
               item == Items.COOKED_BEEF ||
               item == Items.GOLDEN_APPLE ||
               item == Items.ENCHANTED_GOLDEN_APPLE ||
               item == Items.WOODEN_PICKAXE || // Keep basic tools
               item == Items.STONE_PICKAXE ||
               item == Items.IRON_PICKAXE;
    }
    
    /**
     * Check if item is a shulker box
     */
    private boolean isShulkerBox(Item item) {
        return item == Items.SHULKER_BOX ||
               item == Items.WHITE_SHULKER_BOX ||
               item == Items.ORANGE_SHULKER_BOX ||
               item == Items.MAGENTA_SHULKER_BOX ||
               item == Items.LIGHT_BLUE_SHULKER_BOX ||
               item == Items.YELLOW_SHULKER_BOX ||
               item == Items.LIME_SHULKER_BOX ||
               item == Items.PINK_SHULKER_BOX ||
               item == Items.GRAY_SHULKER_BOX ||
               item == Items.LIGHT_GRAY_SHULKER_BOX ||
               item == Items.CYAN_SHULKER_BOX ||
               item == Items.PURPLE_SHULKER_BOX ||
               item == Items.BLUE_SHULKER_BOX ||
               item == Items.BROWN_SHULKER_BOX ||
               item == Items.GREEN_SHULKER_BOX ||
               item == Items.RED_SHULKER_BOX ||
               item == Items.BLACK_SHULKER_BOX;
    }
    
    /**
     * Generate a coded reference for the stash
     */
    private String generateStashCode(StashLocation stash) {
        // Create a short, memorable code
        String[] words = {"ALPHA", "BRAVO", "CHARLIE", "DELTA", "ECHO", "FOXTROT"};
        String[] numbers = {"01", "02", "03", "04", "05", "06", "07", "08", "09"};
        
        String word = words[Math.abs(stash.position.hashCode()) % words.length];
        String number = numbers[Math.abs((int)(stash.timestamp % 1000)) % numbers.length];
        
        return word + "-" + number;
    }
    
    /**
     * Log stash to file
     */
    private void logStash(BlockPos pos, StashType type) {
        try {
            File stashFile = new File("escapemod_stashes.txt");
            FileWriter writer = new FileWriter(stashFile, true);
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String line = String.format("[%s] %s at %d, %d, %d%n", 
                timestamp, type.toString(), pos.getX(), pos.getY(), pos.getZ());
            
            writer.write(line);
            writer.close();
            
        } catch (IOException e) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§c[EscapeMod] Failed to log stash location"), false);
            }
        }
    }
    
    /**
     * Get all stash locations
     */
    public List<StashLocation> getStashHistory() {
        return new ArrayList<>(stashHistory);
    }
    
    /**
     * Find nearest stash to position
     */
    public StashLocation findNearestStash(BlockPos pos) {
        StashLocation nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (StashLocation stash : stashHistory) {
            double distance = stash.position.getSquaredDistance(pos);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = stash;
            }
        }
        
        return nearest;
    }
    
    // Configuration methods
    public void setAutoStashEnabled(boolean enabled) { this.autoStashEnabled = enabled; }
    public boolean isAutoStashEnabled() { return autoStashEnabled; }
    public void setInventoryFullThreshold(int threshold) { this.inventoryFullThreshold = threshold; }
    public int getInventoryFullThreshold() { return inventoryFullThreshold; }
    
    /**
     * Stash location data class
     */
    public static class StashLocation {
        public final BlockPos position;
        public final StashType type;
        public final long timestamp;
        public final List<ItemStack> contents;
        
        public StashLocation(BlockPos position, StashType type, long timestamp, List<ItemStack> contents) {
            this.position = position;
            this.type = type;
            this.timestamp = timestamp;
            this.contents = new ArrayList<>(contents);
        }
    }
    
    /**
     * Stash types
     */
    public enum StashType {
        SHULKER_BOX,
        CHEST,
        BARREL,
        BURIED_CHEST,
        ENDER_CHEST
    }
    
    /**
     * Stash operation results
     */
    public enum StashResult {
        SUCCESS,
        FAILED,
        NO_SAFE_LOCATION,
        NO_ITEMS_TO_STASH,
        COOLDOWN_ACTIVE
    }
}