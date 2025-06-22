package com.escapemod.systems;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.*;

public class InventoryManager {
    private MinecraftClient client;
    private int managementCooldown = 0;
    private int lastMessageTime = 0;
    
    // Item priority levels (higher = more important)
    private static final Map<Item, Integer> ITEM_PRIORITIES = new HashMap<>();
    static {
        // Essential tools (highest priority)
        ITEM_PRIORITIES.put(Items.DIAMOND_PICKAXE, 100);
        ITEM_PRIORITIES.put(Items.DIAMOND_SWORD, 100);
        ITEM_PRIORITIES.put(Items.IRON_PICKAXE, 90);
        ITEM_PRIORITIES.put(Items.IRON_SWORD, 90);
        ITEM_PRIORITIES.put(Items.STONE_PICKAXE, 80);
        ITEM_PRIORITIES.put(Items.STONE_SWORD, 80);
        ITEM_PRIORITIES.put(Items.WOODEN_PICKAXE, 70);
        ITEM_PRIORITIES.put(Items.WOODEN_SWORD, 70);
        
        // Armor
        ITEM_PRIORITIES.put(Items.DIAMOND_HELMET, 95);
        ITEM_PRIORITIES.put(Items.DIAMOND_CHESTPLATE, 95);
        ITEM_PRIORITIES.put(Items.DIAMOND_LEGGINGS, 95);
        ITEM_PRIORITIES.put(Items.DIAMOND_BOOTS, 95);
        ITEM_PRIORITIES.put(Items.IRON_HELMET, 85);
        ITEM_PRIORITIES.put(Items.IRON_CHESTPLATE, 85);
        ITEM_PRIORITIES.put(Items.IRON_LEGGINGS, 85);
        ITEM_PRIORITIES.put(Items.IRON_BOOTS, 85);
        
        // Food
        ITEM_PRIORITIES.put(Items.GOLDEN_APPLE, 95);
        ITEM_PRIORITIES.put(Items.COOKED_BEEF, 75);
        ITEM_PRIORITIES.put(Items.COOKED_PORKCHOP, 75);
        ITEM_PRIORITIES.put(Items.BREAD, 70);
        ITEM_PRIORITIES.put(Items.APPLE, 65);
        
        // Resources
        ITEM_PRIORITIES.put(Items.DIAMOND, 90);
        ITEM_PRIORITIES.put(Items.IRON_INGOT, 80);
        ITEM_PRIORITIES.put(Items.GOLD_INGOT, 75);
        ITEM_PRIORITIES.put(Items.COAL, 60);
        ITEM_PRIORITIES.put(Items.STICK, 50);
        ITEM_PRIORITIES.put(Items.OAK_PLANKS, 45);
        ITEM_PRIORITIES.put(Items.COBBLESTONE, 40);
        
        // Utility
        ITEM_PRIORITIES.put(Items.CRAFTING_TABLE, 85);
        ITEM_PRIORITIES.put(Items.FURNACE, 80);
        ITEM_PRIORITIES.put(Items.CHEST, 75);
        ITEM_PRIORITIES.put(Items.WHITE_BED, 70);
        ITEM_PRIORITIES.put(Items.TORCH, 65);
        
        // Building blocks
        ITEM_PRIORITIES.put(Items.STONE, 35);
        ITEM_PRIORITIES.put(Items.DIRT, 20);
        ITEM_PRIORITIES.put(Items.SAND, 25);
        
        // Junk items (low priority)
        ITEM_PRIORITIES.put(Items.ROTTEN_FLESH, 10);
        ITEM_PRIORITIES.put(Items.SPIDER_EYE, 15);
        ITEM_PRIORITIES.put(Items.BONE, 20);
    }
    
    public InventoryManager() {
        this.client = MinecraftClient.getInstance();
    }
    
    public void tick() {
        if (client.player == null || managementCooldown > 0) {
            managementCooldown--;
            return;
        }
        
        // Run inventory management every 5 seconds
        manageInventory();
        managementCooldown = 100;
    }
    
    public void manageInventory() {
        ClientPlayerEntity player = client.player;
        if (player == null) return;
        
        PlayerInventory inventory = player.getInventory();
        
        // Sort inventory by priority
        sortInventory(inventory);
        
        // Drop low-priority items if inventory is full
        if (isInventoryFull(inventory)) {
            dropLowPriorityItems(inventory);
        }
        
        // Stack similar items
        stackItems(inventory);
        
        // Organize hotbar
        organizeHotbar(inventory);
    }
    
    private void sortInventory(PlayerInventory inventory) {
        List<ItemStack> items = new ArrayList<>();
        
        // Collect all items (excluding hotbar and armor)
        for (int i = 9; i < 36; i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                items.add(stack.copy());
                inventory.setStack(i, ItemStack.EMPTY);
            }
        }
        
        // Sort by priority
        items.sort((a, b) -> {
            int priorityA = ITEM_PRIORITIES.getOrDefault(a.getItem(), 30);
            int priorityB = ITEM_PRIORITIES.getOrDefault(b.getItem(), 30);
            return Integer.compare(priorityB, priorityA); // Higher priority first
        });
        
        // Place back in inventory
        int slot = 9;
        for (ItemStack stack : items) {
            if (slot < 36) {
                inventory.setStack(slot, stack);
                slot++;
            }
        }
    }
    
    private void dropLowPriorityItems(PlayerInventory inventory) {
        ClientPlayerEntity player = client.player;
        List<Integer> itemsToRemove = new ArrayList<>();
        
        // Find lowest priority items
        for (int i = 9; i < 36; i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                int priority = ITEM_PRIORITIES.getOrDefault(stack.getItem(), 30);
                if (priority < 25) { // Drop very low priority items
                    itemsToRemove.add(i);
                }
            }
        }
        
        // Drop items (with message throttling)
        for (int slot : itemsToRemove) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty()) {
                // Only show drop messages every 10 seconds
                if (System.currentTimeMillis() - lastMessageTime > 10000) {
                    player.sendMessage(Text.literal("§7[InvManager] Dropping " + 
                        stack.getCount() + "x " + stack.getName().getString()), true);
                    lastMessageTime = (int) System.currentTimeMillis();
                }
                
                // In a real implementation, you'd call player.dropItem()
                inventory.setStack(slot, ItemStack.EMPTY);
            }
        }
    }
    
    private void stackItems(PlayerInventory inventory) {
        Map<Item, List<Integer>> itemSlots = new HashMap<>();
        
        // Find all items and their slots
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.isStackable()) {
                itemSlots.computeIfAbsent(stack.getItem(), k -> new ArrayList<>()).add(i);
            }
        }
        
        // Stack items together
        for (Map.Entry<Item, List<Integer>> entry : itemSlots.entrySet()) {
            List<Integer> slots = entry.getValue();
            if (slots.size() > 1) {
                stackItemsInSlots(inventory, slots);
            }
        }
    }
    
    private void stackItemsInSlots(PlayerInventory inventory, List<Integer> slots) {
        int totalCount = 0;
        Item item = null;
        
        // Count total items
        for (int slot : slots) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty()) {
                totalCount += stack.getCount();
                item = stack.getItem();
                inventory.setStack(slot, ItemStack.EMPTY);
            }
        }
        
        if (item == null) return;
        
        // Create new stacks
        int maxStackSize = item.getMaxCount();
        int slotsUsed = 0;
        
        for (int slot : slots) {
            if (totalCount <= 0) break;
            
            int stackSize = Math.min(totalCount, maxStackSize);
            inventory.setStack(slot, new ItemStack(item, stackSize));
            totalCount -= stackSize;
            slotsUsed++;
        }
    }
    
    private void organizeHotbar(PlayerInventory inventory) {
        // Preferred hotbar layout:
        // 0: Sword, 1: Pickaxe, 2: Food, 3: Blocks, 4: Torch
        // 5-8: Other useful items
        
        Item[] preferredLayout = {
            getBestSword(inventory),
            getBestPickaxe(inventory),
            getBestFood(inventory),
            getBestBlocks(inventory),
            Items.TORCH,
            Items.CRAFTING_TABLE,
            Items.FURNACE,
            Items.CHEST,
            null // Last slot flexible
        };
        
        for (int i = 0; i < Math.min(preferredLayout.length, 9); i++) {
            Item preferredItem = preferredLayout[i];
            if (preferredItem != null) {
                moveItemToHotbarSlot(inventory, preferredItem, i);
            }
        }
    }
    
    private Item getBestSword(PlayerInventory inventory) {
        Item[] swords = {Items.DIAMOND_SWORD, Items.IRON_SWORD, Items.STONE_SWORD, Items.WOODEN_SWORD};
        for (Item sword : swords) {
            if (hasItem(inventory, sword)) return sword;
        }
        return null;
    }
    
    private Item getBestPickaxe(PlayerInventory inventory) {
        Item[] pickaxes = {Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, Items.STONE_PICKAXE, Items.WOODEN_PICKAXE};
        for (Item pickaxe : pickaxes) {
            if (hasItem(inventory, pickaxe)) return pickaxe;
        }
        return null;
    }
    
    private Item getBestFood(PlayerInventory inventory) {
        Item[] foods = {Items.GOLDEN_APPLE, Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.BREAD, Items.APPLE};
        for (Item food : foods) {
            if (hasItem(inventory, food)) return food;
        }
        return null;
    }
    
    private Item getBestBlocks(PlayerInventory inventory) {
        Item[] blocks = {Items.COBBLESTONE, Items.STONE, Items.DIRT, Items.OAK_PLANKS};
        for (Item block : blocks) {
            if (countItem(inventory, block) >= 32) return block; // Only if we have enough
        }
        return null;
    }
    
    private void moveItemToHotbarSlot(PlayerInventory inventory, Item item, int targetSlot) {
        // Find the item in inventory
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.getItem().equals(item)) {
                // Swap with hotbar slot
                ItemStack hotbarStack = inventory.getStack(targetSlot);
                inventory.setStack(targetSlot, stack);
                inventory.setStack(i, hotbarStack);
                break;
            }
        }
    }
    
    private boolean isInventoryFull(PlayerInventory inventory) {
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }
    
    private boolean hasItem(PlayerInventory inventory, Item item) {
        return countItem(inventory, item) > 0;
    }
    
    private int countItem(PlayerInventory inventory, Item item) {
        int count = 0;
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i).getItem().equals(item)) {
                count += inventory.getStack(i).getCount();
            }
        }
        return count;
    }
    
    public void emergencyCleanup() {
        ClientPlayerEntity player = client.player;
        if (player == null) return;
        
        player.sendMessage(Text.literal("§c[InvManager] Emergency cleanup - dropping junk items!"), false);
        
        PlayerInventory inventory = player.getInventory();
        
        // Drop all very low priority items
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                int priority = ITEM_PRIORITIES.getOrDefault(stack.getItem(), 30);
                if (priority < 20) {
                    inventory.setStack(i, ItemStack.EMPTY);
                }
            }
        }
    }
    
    public void showInventoryStatus() {
        ClientPlayerEntity player = client.player;
        if (player == null) return;
        
        PlayerInventory inventory = player.getInventory();
        int usedSlots = 0;
        int totalValue = 0;
        
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                usedSlots++;
                totalValue += ITEM_PRIORITIES.getOrDefault(stack.getItem(), 30) * stack.getCount();
            }
        }
        
        player.sendMessage(Text.literal("§e[InvManager] Slots used: " + usedSlots + "/36 | Total value: " + totalValue), false);
    }
}