package com.escapemod.systems;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

public class AutoCraftingSystem {
    private MinecraftClient client;
    private int craftingCooldown = 0;
    private long lastCraftMessage = 0;
    
    // Crafting recipes priority order
    private static final Item[][] PRIORITY_CRAFTS = {
        // Tools (highest priority)
        {Items.WOODEN_PICKAXE, Items.OAK_PLANKS, Items.STICK},
        {Items.STONE_PICKAXE, Items.COBBLESTONE, Items.STICK},
        {Items.IRON_PICKAXE, Items.IRON_INGOT, Items.STICK},
        {Items.DIAMOND_PICKAXE, Items.DIAMOND, Items.STICK},
        
        {Items.WOODEN_SWORD, Items.OAK_PLANKS, Items.STICK},
        {Items.STONE_SWORD, Items.COBBLESTONE, Items.STICK},
        {Items.IRON_SWORD, Items.IRON_INGOT, Items.STICK},
        {Items.DIAMOND_SWORD, Items.DIAMOND, Items.STICK},
        
        // Basic items
        {Items.STICK, Items.OAK_PLANKS},
        {Items.OAK_PLANKS, Items.OAK_LOG},
        {Items.CRAFTING_TABLE, Items.OAK_PLANKS},
        {Items.FURNACE, Items.COBBLESTONE},
        
        // Food
        {Items.BREAD, Items.WHEAT},
        
        // Armor
        {Items.LEATHER_HELMET, Items.LEATHER},
        {Items.LEATHER_CHESTPLATE, Items.LEATHER},
        {Items.LEATHER_LEGGINGS, Items.LEATHER},
        {Items.LEATHER_BOOTS, Items.LEATHER},
        
        {Items.IRON_HELMET, Items.IRON_INGOT},
        {Items.IRON_CHESTPLATE, Items.IRON_INGOT},
        {Items.IRON_LEGGINGS, Items.IRON_INGOT},
        {Items.IRON_BOOTS, Items.IRON_INGOT},
        
        {Items.DIAMOND_HELMET, Items.DIAMOND},
        {Items.DIAMOND_CHESTPLATE, Items.DIAMOND},
        {Items.DIAMOND_LEGGINGS, Items.DIAMOND},
        {Items.DIAMOND_BOOTS, Items.DIAMOND}
    };
    
    public AutoCraftingSystem() {
        this.client = MinecraftClient.getInstance();
    }
    
    public void tick() {
        if (client.player == null || craftingCooldown > 0) {
            craftingCooldown--;
            return;
        }
        
        // Check if we need to craft anything
        Item neededItem = getNextNeededItem();
        if (neededItem != null) {
            attemptCraft(neededItem);
            craftingCooldown = 20; // Wait 1 second between crafting attempts
        }
    }
    
    private Item getNextNeededItem() {
        ClientPlayerEntity player = client.player;
        PlayerInventory inventory = player.getInventory();
        
        // Check priority items in order
        for (Item[] recipe : PRIORITY_CRAFTS) {
            Item result = recipe[0];
            
            // Skip if we already have this item (except for consumables)
            if (hasItem(inventory, result) && !isConsumable(result)) {
                continue;
            }
            
            // Check if we have materials
            if (hasMaterials(inventory, recipe)) {
                return result;
            }
        }
        
        return null;
    }
    
    private boolean hasMaterials(PlayerInventory inventory, Item[] recipe) {
        // Simple material check - this would need to be expanded for complex recipes
        if (recipe.length < 2) return false;
        
        Item material = recipe[1];
        int needed = getRequiredAmount(recipe[0], material);
        
        return countItem(inventory, material) >= needed;
    }
    
    private int getRequiredAmount(Item result, Item material) {
        // Define material requirements for each item
        Map<Item, Map<Item, Integer>> requirements = new HashMap<>();
        
        // Pickaxes
        Map<Item, Integer> pickaxeReq = new HashMap<>();
        pickaxeReq.put(Items.OAK_PLANKS, 3);
        pickaxeReq.put(Items.COBBLESTONE, 3);
        pickaxeReq.put(Items.IRON_INGOT, 3);
        pickaxeReq.put(Items.DIAMOND, 3);
        pickaxeReq.put(Items.STICK, 2);
        requirements.put(Items.WOODEN_PICKAXE, pickaxeReq);
        requirements.put(Items.STONE_PICKAXE, pickaxeReq);
        requirements.put(Items.IRON_PICKAXE, pickaxeReq);
        requirements.put(Items.DIAMOND_PICKAXE, pickaxeReq);
        
        // Swords
        Map<Item, Integer> swordReq = new HashMap<>();
        swordReq.put(Items.OAK_PLANKS, 2);
        swordReq.put(Items.COBBLESTONE, 2);
        swordReq.put(Items.IRON_INGOT, 2);
        swordReq.put(Items.DIAMOND, 2);
        swordReq.put(Items.STICK, 1);
        requirements.put(Items.WOODEN_SWORD, swordReq);
        requirements.put(Items.STONE_SWORD, swordReq);
        requirements.put(Items.IRON_SWORD, swordReq);
        requirements.put(Items.DIAMOND_SWORD, swordReq);
        
        // Basic items
        Map<Item, Integer> basicReq = new HashMap<>();
        basicReq.put(Items.OAK_PLANKS, 2);
        basicReq.put(Items.OAK_LOG, 1);
        basicReq.put(Items.COBBLESTONE, 8);
        basicReq.put(Items.WHEAT, 3);
        requirements.put(Items.STICK, basicReq);
        requirements.put(Items.OAK_PLANKS, basicReq);
        requirements.put(Items.CRAFTING_TABLE, basicReq);
        requirements.put(Items.FURNACE, basicReq);
        requirements.put(Items.BREAD, basicReq);
        
        // Armor requirements
        Map<Item, Integer> armorReq = new HashMap<>();
        armorReq.put(Items.LEATHER, 5); // helmet
        armorReq.put(Items.IRON_INGOT, 5);
        armorReq.put(Items.DIAMOND, 5);
        requirements.put(Items.LEATHER_HELMET, armorReq);
        requirements.put(Items.IRON_HELMET, armorReq);
        requirements.put(Items.DIAMOND_HELMET, armorReq);
        
        armorReq = new HashMap<>();
        armorReq.put(Items.LEATHER, 8); // chestplate
        armorReq.put(Items.IRON_INGOT, 8);
        armorReq.put(Items.DIAMOND, 8);
        requirements.put(Items.LEATHER_CHESTPLATE, armorReq);
        requirements.put(Items.IRON_CHESTPLATE, armorReq);
        requirements.put(Items.DIAMOND_CHESTPLATE, armorReq);
        
        if (requirements.containsKey(result) && requirements.get(result).containsKey(material)) {
            return requirements.get(result).get(material);
        }
        
        return 1; // Default
    }
    
    private void attemptCraft(Item item) {
        ClientPlayerEntity player = client.player;
        
        // This is a simplified crafting attempt
        // In a real implementation, you'd need to:
        // 1. Open crafting table if needed
        // 2. Place items in correct pattern
        // 3. Take result
        
        // Only show crafting messages every 10 seconds
        if (System.currentTimeMillis() - lastCraftMessage > 10000) {
            player.sendMessage(Text.literal("§7[AutoCraft] Attempting to craft " + item.getName().getString()), true);
            lastCraftMessage = System.currentTimeMillis();
        }
        
        // For now, just simulate the crafting by consuming materials and adding result
        // This would need proper implementation with screen handling
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
    
    private boolean isConsumable(Item item) {
        return item.equals(Items.BREAD) || item.equals(Items.COOKED_BEEF) || 
               item.equals(Items.COOKED_PORKCHOP) || item.equals(Items.APPLE);
    }
    
    public void craftEssentials() {
        ClientPlayerEntity player = client.player;
        if (player == null) return;
        
        // Only show crafting essentials message once every 30 seconds
        if (System.currentTimeMillis() - lastCraftMessage > 30000) {
            player.sendMessage(Text.literal("§a[AutoCraft] Crafting essential items..."), false);
            lastCraftMessage = System.currentTimeMillis();
        }
        
        // Priority craft order for survival (no individual messages)
        String[] priorities = {
            "Wooden Pickaxe", "Wooden Sword", "Crafting Table", 
            "Furnace", "Stone Pickaxe", "Stone Sword"
        };
        
        // Remove individual queue messages to reduce spam
        // for (String item : priorities) {
        //     player.sendMessage(Text.literal("§7[AutoCraft] Queued: " + item), true);
        // }
    }
}