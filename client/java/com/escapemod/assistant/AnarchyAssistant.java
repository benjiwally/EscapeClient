package com.escapemod.assistant;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.List;

public class AnarchyAssistant {
    private MinecraftClient client;
    
    public AnarchyAssistant() {
        this.client = MinecraftClient.getInstance();
    }
    
    public void analyzeAndAdvise() {
        if (client.player == null || client.world == null) return;
        
        client.player.sendMessage(Text.literal("§6[Anarchy Assistant] Analyzing area..."), false);
        
        // Perform comprehensive analysis
        AreaAnalysis analysis = analyzeArea();
        InventoryAnalysis inventory = analyzeInventory();
        ThreatAnalysis threats = analyzeThreat();
        
        // Generate advice based on analysis
        generateAdvice(analysis, inventory, threats);
    }
    
    private AreaAnalysis analyzeArea() {
        AreaAnalysis analysis = new AreaAnalysis();
        ClientPlayerEntity player = client.player;
        World world = client.world;
        BlockPos playerPos = player.getBlockPos();
        
        // Check spawn distance
        BlockPos spawnPos = new BlockPos(0, 0, 0); // Approximate spawn
        analysis.distanceFromSpawn = Math.sqrt(playerPos.getSquaredDistance(spawnPos));
        
        // Scan surrounding area
        int waterBlocks = 0, lavaBlocks = 0, oreBlocks = 0, structureBlocks = 0;
        int foodSources = 0, shelterMaterials = 0;
        
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                for (int y = -10; y <= 10; y++) {
                    BlockPos checkPos = playerPos.add(x, y, z);
                    Block block = world.getBlockState(checkPos).getBlock();
                    
                    // Count resources
                    if (block.equals(Blocks.WATER)) waterBlocks++;
                    else if (block.equals(Blocks.LAVA)) lavaBlocks++;
                    else if (isOreBlock(block)) oreBlocks++;
                    else if (isStructureBlock(block)) structureBlocks++;
                    else if (isFoodSource(block)) foodSources++;
                    else if (isShelterMaterial(block)) shelterMaterials++;
                }
            }
        }
        
        analysis.hasWaterNearby = waterBlocks > 5;
        analysis.hasLavaNearby = lavaBlocks > 0;
        analysis.oreCount = oreBlocks;
        analysis.hasStructures = structureBlocks > 10;
        analysis.foodSources = foodSources;
        analysis.shelterMaterials = shelterMaterials;
        
        // Check biome and terrain
        analysis.biome = world.getBiome(playerPos).getIdAsString();
        analysis.elevation = playerPos.getY();
        
        return analysis;
    }
    
    private InventoryAnalysis analyzeInventory() {
        InventoryAnalysis analysis = new InventoryAnalysis();
        PlayerInventory inventory = client.player.getInventory();
        
        // Count essential items
        analysis.foodCount = countItem(inventory, Items.BREAD) + countItem(inventory, Items.COOKED_BEEF) + 
                           countItem(inventory, Items.COOKED_PORKCHOP) + countItem(inventory, Items.APPLE);
        
        analysis.hasPickaxe = hasAnyPickaxe(inventory);
        analysis.hasSword = hasAnySword(inventory);
        analysis.hasArmor = hasAnyArmor(inventory);
        analysis.hasBlocks = countItem(inventory, Items.COBBLESTONE) + countItem(inventory, Items.DIRT) > 32;
        
        analysis.woodCount = countItem(inventory, Items.OAK_LOG) + countItem(inventory, Items.OAK_PLANKS);
        analysis.coalCount = countItem(inventory, Items.COAL);
        analysis.ironCount = countItem(inventory, Items.IRON_INGOT);
        analysis.diamondCount = countItem(inventory, Items.DIAMOND);
        
        return analysis;
    }
    
    private ThreatAnalysis analyzeThreat() {
        ThreatAnalysis analysis = new ThreatAnalysis();
        
        // Check for nearby players
        List<PlayerEntity> nearbyPlayers = client.world.getEntitiesByClass(
            PlayerEntity.class, 
            client.player.getBoundingBox().expand(50), 
            p -> p != client.player
        );
        
        analysis.nearbyPlayers = nearbyPlayers.size();
        analysis.isNearSpawn = client.player.getBlockPos().getSquaredDistance(0, 0, 0) < 10000; // Within 100 blocks of spawn
        
        // Check time of day
        long timeOfDay = client.world.getTimeOfDay() % 24000;
        analysis.isNight = timeOfDay > 13000 && timeOfDay < 23000;
        
        // Check health and hunger
        analysis.lowHealth = client.player.getHealth() < 10;
        analysis.lowHunger = client.player.getHungerManager().getFoodLevel() < 10;
        
        return analysis;
    }
    
    private void generateAdvice(AreaAnalysis area, InventoryAnalysis inventory, ThreatAnalysis threats) {
        List<String> advice = new ArrayList<>();
        
        // Priority 1: Immediate survival threats
        if (threats.lowHealth) {
            advice.add("§c[URGENT] Your health is low! Find food or shelter immediately!");
        }
        if (threats.lowHunger) {
            advice.add("§c[URGENT] You're starving! Find food sources or hunt animals!");
        }
        
        // Priority 2: Location-based advice
        if (threats.isNearSpawn) {
            advice.add("§e[WARNING] You're near spawn - high PvP risk! Consider escaping to safer areas.");
            if (threats.nearbyPlayers > 0) {
                advice.add("§c[DANGER] " + threats.nearbyPlayers + " players nearby! Be ready to run or fight!");
            }
        }
        
        if (area.distanceFromSpawn < 1000) {
            advice.add("§6[STRATEGY] You're in the spawn region. Priorities: Get basic tools, food, then escape!");
        } else if (area.distanceFromSpawn < 10000) {
            advice.add("§6[STRATEGY] You're in the outer spawn area. Focus on gathering resources and building strength.");
        } else {
            advice.add("§a[STRATEGY] You're in the wilderness! Good for base building and resource gathering.");
        }
        
        // Priority 3: Resource and equipment advice
        if (!inventory.hasPickaxe) {
            advice.add("§e[EQUIPMENT] No pickaxe detected! Craft one ASAP - you need it for mining.");
        }
        if (!inventory.hasSword) {
            advice.add("§e[EQUIPMENT] No sword detected! Craft one for protection against mobs and players.");
        }
        if (!inventory.hasArmor) {
            advice.add("§e[EQUIPMENT] No armor detected! Even leather armor provides protection.");
        }
        
        if (inventory.foodCount < 5) {
            advice.add("§e[SURVIVAL] Low food supplies! " + getFoodAdvice(area));
        }
        
        // Priority 4: Area-specific advice
        if (area.hasLavaNearby) {
            advice.add("§c[HAZARD] Lava detected nearby! Be careful and consider using it for smelting.");
        }
        if (!area.hasWaterNearby) {
            advice.add("§e[RESOURCE] No water source nearby. Find one for farming and emergencies.");
        }
        
        if (area.oreCount > 5) {
            advice.add("§a[OPPORTUNITY] Rich ore deposits detected! Good area for mining operations.");
        }
        if (area.hasStructures) {
            advice.add("§a[OPPORTUNITY] Structures detected! Explore for loot but watch for traps.");
        }
        
        // Priority 5: Strategic advice
        if (threats.isNight && !inventory.hasBlocks) {
            advice.add("§e[STRATEGY] It's night and you have no blocks! Find shelter or dig underground.");
        }
        
        if (area.elevation < 20) {
            advice.add("§6[STRATEGY] You're at low elevation - good for mining but watch for caves.");
        } else if (area.elevation > 100) {
            advice.add("§6[STRATEGY] You're at high elevation - good visibility but exposed to players.");
        }
        
        // Priority 6: Long-term planning
        if (inventory.diamondCount > 0) {
            advice.add("§a[PLANNING] You have diamonds! Prioritize diamond pickaxe, then sword, then armor.");
        } else if (inventory.ironCount > 10) {
            advice.add("§a[PLANNING] Good iron supplies! Make full iron gear before venturing further.");
        }
        
        // Send all advice
        if (advice.isEmpty()) {
            client.player.sendMessage(Text.literal("§a[Anarchy Assistant] You're doing well! Keep up the good work!"), false);
        } else {
            for (String tip : advice) {
                client.player.sendMessage(Text.literal(tip), false);
            }
        }
        
        // Final summary
        String summary = String.format("§7[Summary] Distance from spawn: %.0f blocks | Players nearby: %d | Threat level: %s", 
            area.distanceFromSpawn, threats.nearbyPlayers, getThreatLevel(threats));
        client.player.sendMessage(Text.literal(summary), false);
    }
    
    private String getFoodAdvice(AreaAnalysis area) {
        if (area.foodSources > 0) {
            return "Crops detected nearby - harvest them!";
        } else {
            return "Hunt animals or fish if near water.";
        }
    }
    
    private String getThreatLevel(ThreatAnalysis threats) {
        if (threats.lowHealth || threats.lowHunger) return "§cCRITICAL";
        if (threats.nearbyPlayers > 2 && threats.isNearSpawn) return "§cHIGH";
        if (threats.nearbyPlayers > 0 || threats.isNearSpawn) return "§eMODERATE";
        return "§aLOW";
    }
    
    // Helper methods
    private boolean isOreBlock(Block block) {
        return block.equals(Blocks.COAL_ORE) || block.equals(Blocks.IRON_ORE) || 
               block.equals(Blocks.GOLD_ORE) || block.equals(Blocks.DIAMOND_ORE) ||
               block.equals(Blocks.DEEPSLATE_COAL_ORE) || block.equals(Blocks.DEEPSLATE_IRON_ORE) ||
               block.equals(Blocks.DEEPSLATE_GOLD_ORE) || block.equals(Blocks.DEEPSLATE_DIAMOND_ORE);
    }
    
    private boolean isStructureBlock(Block block) {
        return block.equals(Blocks.COBBLESTONE) || block.equals(Blocks.STONE_BRICKS) ||
               block.equals(Blocks.MOSSY_COBBLESTONE) || block.equals(Blocks.CHISELED_STONE_BRICKS);
    }
    
    private boolean isFoodSource(Block block) {
        return block.equals(Blocks.WHEAT) || block.equals(Blocks.CARROTS) || 
               block.equals(Blocks.POTATOES) || block.equals(Blocks.BEETROOTS);
    }
    
    private boolean isShelterMaterial(Block block) {
        return block.equals(Blocks.OAK_LOG) || block.equals(Blocks.STONE) || 
               block.equals(Blocks.COBBLESTONE) || block.equals(Blocks.DIRT);
    }
    
    private int countItem(PlayerInventory inventory, net.minecraft.item.Item item) {
        int count = 0;
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i).getItem().equals(item)) {
                count += inventory.getStack(i).getCount();
            }
        }
        return count;
    }
    
    private boolean hasAnyPickaxe(PlayerInventory inventory) {
        return countItem(inventory, Items.WOODEN_PICKAXE) > 0 ||
               countItem(inventory, Items.STONE_PICKAXE) > 0 ||
               countItem(inventory, Items.IRON_PICKAXE) > 0 ||
               countItem(inventory, Items.DIAMOND_PICKAXE) > 0;
    }
    
    private boolean hasAnySword(PlayerInventory inventory) {
        return countItem(inventory, Items.WOODEN_SWORD) > 0 ||
               countItem(inventory, Items.STONE_SWORD) > 0 ||
               countItem(inventory, Items.IRON_SWORD) > 0 ||
               countItem(inventory, Items.DIAMOND_SWORD) > 0;
    }
    
    private boolean hasAnyArmor(PlayerInventory inventory) {
        return countItem(inventory, Items.LEATHER_HELMET) > 0 ||
               countItem(inventory, Items.LEATHER_CHESTPLATE) > 0 ||
               countItem(inventory, Items.IRON_HELMET) > 0 ||
               countItem(inventory, Items.IRON_CHESTPLATE) > 0 ||
               countItem(inventory, Items.DIAMOND_HELMET) > 0 ||
               countItem(inventory, Items.DIAMOND_CHESTPLATE) > 0;
    }
    
    // Analysis data classes
    private static class AreaAnalysis {
        double distanceFromSpawn;
        boolean hasWaterNearby;
        boolean hasLavaNearby;
        int oreCount;
        boolean hasStructures;
        int foodSources;
        int shelterMaterials;
        String biome;
        int elevation;
    }
    
    private static class InventoryAnalysis {
        int foodCount;
        boolean hasPickaxe;
        boolean hasSword;
        boolean hasArmor;
        boolean hasBlocks;
        int woodCount;
        int coalCount;
        int ironCount;
        int diamondCount;
    }
    
    private static class ThreatAnalysis {
        int nearbyPlayers;
        boolean isNearSpawn;
        boolean isNight;
        boolean lowHealth;
        boolean lowHunger;
    }
}