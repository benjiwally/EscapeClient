package com.escapemod.systems;

import com.escapemod.utils.MessageThrottler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.util.ArrayList;
import java.util.List;

public class ProgressionSystem {
    private MinecraftClient client;
    private List<ProgressionGoal> goals;
    private int currentGoalIndex = 0;
    private boolean enabled = true;
    
    public enum GoalType {
        GATHER_WOOD,
        CRAFT_WOODEN_TOOLS,
        GATHER_STONE,
        CRAFT_STONE_TOOLS,
        GATHER_IRON,
        CRAFT_IRON_ARMOR,
        CRAFT_IRON_TOOLS,
        CRAFT_SHIELD,
        GATHER_FOOD,
        ESTABLISH_BASE,
        CONTINUE_ESCAPE
    }
    
    public static class ProgressionGoal {
        public GoalType type;
        public String description;
        public boolean completed;
        public int priority; // Lower number = higher priority
        
        public ProgressionGoal(GoalType type, String description, int priority) {
            this.type = type;
            this.description = description;
            this.priority = priority;
            this.completed = false;
        }
    }
    
    public ProgressionSystem() {
        this.client = MinecraftClient.getInstance();
        initializeGoals();
    }
    
    private void initializeGoals() {
        goals = new ArrayList<>();
        
        // Essential survival goals in order of priority
        goals.add(new ProgressionGoal(GoalType.GATHER_WOOD, "Gather 32+ wood logs", 1));
        goals.add(new ProgressionGoal(GoalType.CRAFT_WOODEN_TOOLS, "Craft wooden pickaxe and axe", 2));
        goals.add(new ProgressionGoal(GoalType.GATHER_STONE, "Gather 64+ cobblestone", 3));
        goals.add(new ProgressionGoal(GoalType.CRAFT_STONE_TOOLS, "Craft stone pickaxe, axe, sword", 4));
        goals.add(new ProgressionGoal(GoalType.GATHER_FOOD, "Maintain 20+ food items", 0)); // Always high priority
        goals.add(new ProgressionGoal(GoalType.GATHER_IRON, "Gather 24+ iron ingots", 5));
        goals.add(new ProgressionGoal(GoalType.CRAFT_IRON_ARMOR, "Craft full iron armor set", 6));
        goals.add(new ProgressionGoal(GoalType.CRAFT_IRON_TOOLS, "Craft iron pickaxe, axe, sword", 7));
        goals.add(new ProgressionGoal(GoalType.CRAFT_SHIELD, "Craft iron shield", 8));
        goals.add(new ProgressionGoal(GoalType.CONTINUE_ESCAPE, "Continue escape journey", 9));
    }
    
    public void tick() {
        if (!enabled || client.player == null || client.world == null) {
            return;
        }
        
        updateGoalStatus();
        displayCurrentGoals();
    }
    
    private void updateGoalStatus() {
        ClientPlayerEntity player = client.player;
        PlayerInventory inventory = player.getInventory();
        
        for (ProgressionGoal goal : goals) {
            if (goal.completed) continue;
            
            switch (goal.type) {
                case GATHER_WOOD:
                    goal.completed = countItemInInventory(inventory, Items.OAK_LOG) + 
                                   countItemInInventory(inventory, Items.BIRCH_LOG) + 
                                   countItemInInventory(inventory, Items.SPRUCE_LOG) + 
                                   countItemInInventory(inventory, Items.JUNGLE_LOG) + 
                                   countItemInInventory(inventory, Items.ACACIA_LOG) + 
                                   countItemInInventory(inventory, Items.DARK_OAK_LOG) >= 32;
                    break;
                    
                case CRAFT_WOODEN_TOOLS:
                    goal.completed = hasItemInInventory(inventory, Items.WOODEN_PICKAXE) &&
                                   hasItemInInventory(inventory, Items.WOODEN_AXE);
                    break;
                    
                case GATHER_STONE:
                    goal.completed = countItemInInventory(inventory, Items.COBBLESTONE) >= 64;
                    break;
                    
                case CRAFT_STONE_TOOLS:
                    goal.completed = hasItemInInventory(inventory, Items.STONE_PICKAXE) &&
                                   hasItemInInventory(inventory, Items.STONE_AXE) &&
                                   hasItemInInventory(inventory, Items.STONE_SWORD);
                    break;
                    
                case GATHER_FOOD:
                    int foodCount = countItemInInventory(inventory, Items.BREAD) +
                                  countItemInInventory(inventory, Items.COOKED_BEEF) +
                                  countItemInInventory(inventory, Items.COOKED_PORKCHOP) +
                                  countItemInInventory(inventory, Items.COOKED_CHICKEN) +
                                  countItemInInventory(inventory, Items.APPLE) +
                                  countItemInInventory(inventory, Items.CARROT) +
                                  countItemInInventory(inventory, Items.POTATO) +
                                  countItemInInventory(inventory, Items.BEETROOT);
                    goal.completed = foodCount >= 20;
                    break;
                    
                case GATHER_IRON:
                    goal.completed = countItemInInventory(inventory, Items.IRON_INGOT) >= 24;
                    break;
                    
                case CRAFT_IRON_ARMOR:
                    goal.completed = hasItemInInventory(inventory, Items.IRON_HELMET) &&
                                   hasItemInInventory(inventory, Items.IRON_CHESTPLATE) &&
                                   hasItemInInventory(inventory, Items.IRON_LEGGINGS) &&
                                   hasItemInInventory(inventory, Items.IRON_BOOTS);
                    break;
                    
                case CRAFT_IRON_TOOLS:
                    goal.completed = hasItemInInventory(inventory, Items.IRON_PICKAXE) &&
                                   hasItemInInventory(inventory, Items.IRON_AXE) &&
                                   hasItemInInventory(inventory, Items.IRON_SWORD);
                    break;
                    
                case CRAFT_SHIELD:
                    goal.completed = hasItemInInventory(inventory, Items.SHIELD);
                    break;
                    
                case CONTINUE_ESCAPE:
                    goal.completed = false; // This goal is never "completed"
                    break;
            }
        }
    }
    
    private void displayCurrentGoals() {
        ClientPlayerEntity player = client.player;
        
        // Find the highest priority incomplete goal
        ProgressionGoal currentGoal = getCurrentGoal();
        if (currentGoal != null) {
            MessageThrottler.sendThrottledMessage(player, "current_goal", 
                Text.literal("§e[Progression] Current Goal: " + currentGoal.description), 
                true, 10000); // Show every 10 seconds
        }
        
        // Show progress summary every 30 seconds
        int completedGoals = (int) goals.stream().filter(g -> g.completed).count();
        MessageThrottler.sendThrottledMessage(player, "progression_summary", 
            Text.literal("§a[Progression] Progress: " + completedGoals + "/" + (goals.size() - 1) + " goals completed"), 
            true, 30000);
    }
    
    public ProgressionGoal getCurrentGoal() {
        // Always prioritize food if it's not completed
        for (ProgressionGoal goal : goals) {
            if (!goal.completed && goal.type == GoalType.GATHER_FOOD) {
                return goal;
            }
        }
        
        // Find the highest priority incomplete goal
        ProgressionGoal highestPriority = null;
        for (ProgressionGoal goal : goals) {
            if (!goal.completed) {
                if (highestPriority == null || goal.priority < highestPriority.priority) {
                    highestPriority = goal;
                }
            }
        }
        
        return highestPriority;
    }
    
    public BlockPos findNearestResourceForGoal(ProgressionGoal goal, BlockPos playerPos, World world) {
        if (goal == null) return null;
        
        int searchRadius = 32;
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int z = -searchRadius; z <= searchRadius; z++) {
                for (int y = -16; y <= 16; y++) {
                    BlockPos checkPos = playerPos.add(x, y, z);
                    Block block = world.getBlockState(checkPos).getBlock();
                    
                    if (isResourceForGoal(block, goal)) {
                        double distance = playerPos.getSquaredDistance(checkPos);
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
    
    private boolean isResourceForGoal(Block block, ProgressionGoal goal) {
        switch (goal.type) {
            case GATHER_WOOD:
                return block == Blocks.OAK_LOG || block == Blocks.BIRCH_LOG || 
                       block == Blocks.SPRUCE_LOG || block == Blocks.JUNGLE_LOG ||
                       block == Blocks.ACACIA_LOG || block == Blocks.DARK_OAK_LOG;
                       
            case GATHER_STONE:
                return block == Blocks.STONE || block == Blocks.COBBLESTONE ||
                       block == Blocks.ANDESITE || block == Blocks.GRANITE || block == Blocks.DIORITE;
                       
            case GATHER_IRON:
                return block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE;
                
            case GATHER_FOOD:
                return block == Blocks.WHEAT || block == Blocks.CARROTS || 
                       block == Blocks.POTATOES || block == Blocks.BEETROOTS ||
                       block == Blocks.SWEET_BERRY_BUSH;
                       
            default:
                return false;
        }
    }
    
    private boolean hasItemInInventory(PlayerInventory inventory, Item item) {
        return countItemInInventory(inventory, item) > 0;
    }
    
    private int countItemInInventory(PlayerInventory inventory, Item item) {
        int count = 0;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }
    
    public boolean shouldCraftItems() {
        ProgressionGoal currentGoal = getCurrentGoal();
        return currentGoal != null && (
            currentGoal.type == GoalType.CRAFT_WOODEN_TOOLS ||
            currentGoal.type == GoalType.CRAFT_STONE_TOOLS ||
            currentGoal.type == GoalType.CRAFT_IRON_ARMOR ||
            currentGoal.type == GoalType.CRAFT_IRON_TOOLS ||
            currentGoal.type == GoalType.CRAFT_SHIELD
        );
    }
    
    public boolean isReadyForEscape() {
        // Check if we have basic survival gear
        return goals.stream()
            .filter(g -> g.type != GoalType.CONTINUE_ESCAPE && g.type != GoalType.GATHER_FOOD)
            .allMatch(g -> g.completed);
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public List<ProgressionGoal> getGoals() {
        return new ArrayList<>(goals);
    }
}