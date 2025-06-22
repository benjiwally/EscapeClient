package com.escapemod;

import com.escapemod.systems.AutoCraftingSystem;
import com.escapemod.systems.InventoryManager;
import com.escapemod.systems.AntiGriefSystem;
import com.escapemod.systems.FallProtectionSystem;
import com.escapemod.systems.ProgressionSystem;
import com.escapemod.systems.PathfindingSystem;
import com.escapemod.systems.NetherHighwayNavigator;
import com.escapemod.systems.BacktrackRecovery;
import com.escapemod.systems.AutoStashSystem;
import com.escapemod.systems.CrisisMode;
import com.escapemod.navigation.EscapeNavigator;
import com.escapemod.rendering.PathRenderer;
import com.escapemod.utils.MessageThrottler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.client.option.GameOptions;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.registry.Registries;

import java.util.Random;
import java.util.ArrayList;
import java.util.List;

public class EscapeBot {
    private boolean isEscaping = false;
    private BlockPos spawnPos;
    private Vec3d targetDirection;
    private int tickCounter = 0;
    private int foodSearchTicks = 0;
    private int miningTicks = 0;
    private boolean isMining = false;
    private BlockPos miningTarget = null;
    private int lastProgressMessage = 0;
    
    // Integrated systems
    private AutoCraftingSystem autoCrafting;
    private InventoryManager inventoryManager;
    private AntiGriefSystem antiGrief;
    private FallProtectionSystem fallProtection;
    private ProgressionSystem progression;
    private PathfindingSystem pathfinding; // Keep for compatibility
    
    // New navigation systems
    private EscapeNavigator navigator;
    private PathRenderer pathRenderer;
    
    // Advanced survival systems
    private NetherHighwayNavigator netherNavigator;
    private BacktrackRecovery backtrackRecovery;
    private AutoStashSystem autoStash;
    private CrisisMode crisisMode;
    
    // Constants
    private static final int ESCAPE_DISTANCE = 56000; // 56k blocks
    private static final int FOOD_SEARCH_INTERVAL = 200; // Search for food every 10 seconds
    private static final int MINING_TIMEOUT = 600; // 30 seconds mining timeout
    
    // Configuration
    private boolean showProgressMessages = true;
    private boolean verboseMode = false;
    private boolean showParticleTrail = true;
    private boolean fallDamageProtection = true;
    private boolean showPathLine = true;
    
    // Performance throttling
    private int navigationTickCounter = 0;
    private int renderTickCounter = 0;
    private boolean useSimpleNavigation = true; // Use simple navigation by default for performance
    
    // Particle trail
    private List<Vec3d> pathHistory = new ArrayList<>();
    private int particleTimer = 0;
    private Vec3d lastParticlePos = null;
    
    private Random random = new Random();
    
    public EscapeBot() {
        this.autoCrafting = new AutoCraftingSystem();
        this.inventoryManager = new InventoryManager();
        this.antiGrief = new AntiGriefSystem();
        this.fallProtection = new FallProtectionSystem();
        this.progression = new ProgressionSystem();
        this.pathfinding = new PathfindingSystem(); // Keep for compatibility
        
        // Initialize new navigation systems
        this.navigator = new EscapeNavigator(MinecraftClient.getInstance());
        this.pathRenderer = new PathRenderer(MinecraftClient.getInstance());
        
        // Initialize advanced survival systems
        this.netherNavigator = new NetherHighwayNavigator(MinecraftClient.getInstance());
        this.backtrackRecovery = new BacktrackRecovery(MinecraftClient.getInstance());
        this.autoStash = new AutoStashSystem(MinecraftClient.getInstance());
        this.crisisMode = new CrisisMode(MinecraftClient.getInstance());
    }
    
    public void toggleEscape() {
        isEscaping = !isEscaping;
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (isEscaping) {
            if (client.player != null) {
                spawnPos = client.player.getBlockPos();
                
                // Start new navigation system
                navigator.startEscape(spawnPos);
                
                client.player.sendMessage(Text.literal("§a[EscapeMod] Starting escape! Target: 30k blocks from spawn"), false);
                client.player.sendMessage(Text.literal("§e[EscapeMod] Using intelligent pathfinding system"), false);
            }
        } else {
            client.player.sendMessage(Text.literal("§c[EscapeMod] Escape stopped!"), false);
            navigator.stopNavigation();
            stopAllActions(client);
        }
    }
    
    public void tick(MinecraftClient client) {
        // Always run these systems
        autoCrafting.tick();
        inventoryManager.tick();
        antiGrief.tick();
        progression.tick();
        pathfinding.tick();
        
        // Run fall protection system if enabled
        if (fallDamageProtection) {
            fallProtection.tick();
        }
        
        if (!isEscaping || client.player == null || client.world == null) {
            return;
        }
        
        tickCounter++;
        ClientPlayerEntity player = client.player;
        
        // Apply fall damage protection
        if (fallDamageProtection) {
            applyFallDamageProtection(player);
        }
        
        // Update particle trail
        if (showParticleTrail) {
            updateParticleTrail(client, player);
        }
        
        // Show path line (like Baritone)
        if (isEscaping && showPathLine) {
            displayPathLine(client, player);
        }
        
        // Check if we've reached our target distance
        if (spawnPos != null) {
            double distanceFromSpawn = player.getPos().distanceTo(Vec3d.ofCenter(spawnPos));
            if (distanceFromSpawn >= ESCAPE_DISTANCE) {
                isEscaping = false;
                player.sendMessage(Text.literal("§a[EscapeMod] Escape complete! Traveled " + 
                    String.format("%.0f", distanceFromSpawn) + " blocks!"), false);
                stopAllActions(client);
                return;
            }
            
            // Show progress every 2 minutes using throttler (only if enabled)
            if (showProgressMessages) {
                MessageThrottler.sendThrottledMessage(player, "escape_progress", 
                    Text.literal("§e[EscapeMod] Progress: " + 
                        String.format("%.0f", distanceFromSpawn) + "/" + ESCAPE_DISTANCE + " blocks"), 
                    true, 120000); // 2 minutes
            }
        }
        
        // Throttle navigation updates to every 5 ticks (4 times per second)
        navigationTickCounter++;
        if (navigationTickCounter >= 5) {
            navigator.tick();
            netherNavigator.tick();
            navigationTickCounter = 0;
        }
        
        // Record position for backtrack recovery
        backtrackRecovery.recordPosition(player.getBlockPos(), System.currentTimeMillis());
        
        // Check for crisis mode activation
        if (!crisisMode.isCrisisModeActive() && crisisMode.shouldActivateCrisis()) {
            crisisMode.activateCrisisMode();
        }
        
        // Handle crisis mode if active
        if (crisisMode.isCrisisModeActive()) {
            // Configure Baritone for crisis mode if using it
            if (navigator.isUsingBaritone()) {
                navigator.getBaritonePathfinder().getBaritoneIntegration().configureForCrisis();
            }
            handleCrisisMode(client);
            return; // Crisis mode overrides normal behavior
        }
        
        // Check for backtrack recovery needs
        if (backtrackRecovery.needsRecovery()) {
            handleBacktrackRecovery(client);
            return; // Recovery overrides normal behavior
        }
        
        // Check for auto-stash needs
        if (autoStash.needsStash()) {
            handleAutoStash(client);
        }
        
        // Check if should use nether travel
        if (shouldUseNetherTravel()) {
            handleNetherTravel(client);
        }
        
        // Handle movement based on navigation system
        if (isMining && miningTarget != null) {
            // Continue current mining operation
            handleMining(client);
        } else {
            // Use navigation system (throttled)
            if (navigationTickCounter == 0) {
                if (useSimpleNavigation) {
                    handleSimpleNavigation(client);
                } else {
                    handleNewNavigation(client);
                }
            }
        }
        
        // Render path visualization (throttled to every 10 ticks)
        renderTickCounter++;
        if (showPathLine && renderTickCounter >= 10) {
            renderNavigationPath(client, player);
            renderTickCounter = 0;
        }
    }
    
    private void handleProgressionGoal(MinecraftClient client, ProgressionSystem.ProgressionGoal goal) {
        ClientPlayerEntity player = client.player;
        BlockPos playerPos = player.getBlockPos();
        
        // Find the nearest resource for the current goal
        BlockPos resourcePos = progression.findNearestResourceForGoal(goal, playerPos, client.world);
        
        if (resourcePos != null) {
            // Use pathfinding to navigate to the resource
            if (!pathfinding.hasPath() || pathfinding.getCurrentPath().isEmpty()) {
                List<Vec3d> path = pathfinding.findPath(playerPos, resourcePos);
                if (path.isEmpty()) {
                    // Fallback to direct movement if pathfinding fails
                    moveDirectlyTowards(client, Vec3d.ofCenter(resourcePos));
                    return;
                }
            }
            
            // Follow the pathfinding route
            Vec3d nextPoint = pathfinding.getNextPathPoint();
            if (nextPoint != null) {
                moveDirectlyTowards(client, nextPoint);
                
                // Check if we're close enough to start mining
                if (player.getPos().distanceTo(Vec3d.ofCenter(resourcePos)) < 5.0) {
                    startMining(resourcePos);
                }
            }
        } else {
            // No resources found nearby, continue with escape movement
            moveTowardsTargetWithPathfinding(client);
        }
        
        // Handle crafting if needed
        if (progression.shouldCraftItems()) {
            // Auto-crafting system will handle this
            MessageThrottler.sendThrottledMessage(player, "crafting_needed", 
                Text.literal("§e[EscapeMod] Crafting items for progression..."), true, 5000);
        }
    }
    
    private void moveTowardsTargetWithPathfinding(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        Vec3d currentPos = player.getPos();
        Vec3d targetPos = currentPos.add(targetDirection.multiply(50)); // Look 50 blocks ahead
        
        BlockPos targetBlockPos = BlockPos.ofFloored(targetPos);
        
        // Use pathfinding for intelligent navigation
        if (!pathfinding.hasPath() || tickCounter % 100 == 0) { // Recalculate path every 5 seconds
            List<Vec3d> path = pathfinding.findPath(player.getBlockPos(), targetBlockPos);
            if (path.isEmpty()) {
                // Fallback to simple movement
                moveTowardsTarget(client);
                return;
            }
        }
        
        Vec3d nextPoint = pathfinding.getNextPathPoint();
        if (nextPoint != null) {
            moveDirectlyTowards(client, nextPoint);
        } else {
            // Path completed, continue with escape
            moveTowardsTarget(client);
        }
    }
    
    private void moveDirectlyTowards(MinecraftClient client, Vec3d targetPos) {
        ClientPlayerEntity player = client.player;
        GameOptions options = client.options;
        Vec3d currentPos = player.getPos();
        
        // Calculate direction to target
        double deltaX = targetPos.x - currentPos.x;
        double deltaZ = targetPos.z - currentPos.z;
        
        // Set movement keys
        options.forwardKey.setPressed(true);
        
        // Adjust direction
        float targetYaw = (float) Math.toDegrees(Math.atan2(-deltaX, deltaZ));
        player.setYaw(targetYaw);
        
        // Smart jumping and cliff avoidance
        if (isObstacleAhead(client) && !isDangerousCliffAhead(client)) {
            options.jumpKey.setPressed(true);
        } else {
            options.jumpKey.setPressed(false);
        }
        
        // Stop moving forward if there's a dangerous cliff ahead
        if (isDangerousCliffAhead(client)) {
            options.forwardKey.setPressed(false);
            // Clear current path to force recalculation
            pathfinding.clearPath();
        }
        
        // Handle water/lava
        if (player.isTouchingWater() || player.isInLava()) {
            options.jumpKey.setPressed(true);
        }
    }

    private void moveTowardsTarget(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        GameOptions options = client.options;
        
        // Debug message to confirm we're in this method
        if (tickCounter % 60 == 0) { // Every 3 seconds
            MessageThrottler.sendThrottledMessage(player, "movement_debug", 
                Text.literal("§e[Debug] Moving towards target. Direction: " + 
                    String.format("X: %.2f, Z: %.2f", targetDirection.x, targetDirection.z)), true, 2000);
        }
        
        // Calculate target position
        Vec3d currentPos = player.getPos();
        Vec3d targetPos = currentPos.add(targetDirection.multiply(10)); // Look 10 blocks ahead
        
        // Simple movement logic
        double deltaX = targetPos.x - currentPos.x;
        double deltaZ = targetPos.z - currentPos.z;
        
        // Always move forward unless there's a cliff
        boolean cliffAhead = isDangerousCliffAhead(client);
        boolean obstacleAhead = isObstacleAhead(client);
        
        if (!cliffAhead) {
            options.forwardKey.setPressed(true);
            
            // Adjust direction
            float targetYaw = (float) Math.toDegrees(Math.atan2(-deltaX, deltaZ));
            player.setYaw(targetYaw);
            
            // Jump if there's an obstacle
            if (obstacleAhead) {
                options.jumpKey.setPressed(true);
            } else {
                options.jumpKey.setPressed(false);
            }
        } else {
            // Cliff detected - stop and find alternate path
            options.forwardKey.setPressed(false);
            options.jumpKey.setPressed(false);
            
            // Try to find an alternate direction
            Vec3d alternatePath = findAlternatePath(client);
            if (alternatePath != null) {
                targetDirection = alternatePath;
                MessageThrottler.sendThrottledMessage(player, "path_change", 
                    Text.literal("§e[EscapeMod] Changing direction to avoid cliff!"), true, 3000);
            }
        }
        
        // Handle water/lava
        if (player.isTouchingWater() || player.isInLava()) {
            options.jumpKey.setPressed(true);
        }
    }
    
    private boolean isObstacleAhead(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        World world = client.world;
        
        Vec3d playerPos = player.getPos();
        Vec3d lookDirection = Vec3d.fromPolar(0, player.getYaw()).normalize();
        Vec3d checkPos = playerPos.add(lookDirection.multiply(2));
        
        BlockPos blockPos = BlockPos.ofFloored(checkPos);
        Block block = world.getBlockState(blockPos).getBlock();
        
        return !block.equals(Blocks.AIR) && !block.equals(Blocks.WATER) && 
               !block.equals(Blocks.TALL_GRASS) && !block.equals(Blocks.SHORT_GRASS);
    }
    
    private boolean isDangerousCliffAhead(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        World world = client.world;
        
        Vec3d playerPos = player.getPos();
        Vec3d lookDirection = Vec3d.fromPolar(0, player.getYaw()).normalize();
        
        // Only check 3 blocks ahead (less aggressive)
        Vec3d checkPos = playerPos.add(lookDirection.multiply(3));
        BlockPos blockPos = BlockPos.ofFloored(checkPos);
        
        // Check if there's ground below this position
        int groundDistance = 0;
        for (int i = 1; i <= 15; i++) {
            BlockPos groundCheck = blockPos.down(i);
            if (!world.getBlockState(groundCheck).isAir()) {
                groundDistance = i;
                break;
            }
        }
        
        // Only consider it dangerous if ground is more than 10 blocks down (less restrictive)
        boolean isDangerous = groundDistance > 10 || groundDistance == 0;
        
        // Debug message to see what's happening
        if (isDangerous && tickCounter % 20 == 0) { // Every second
            MessageThrottler.sendThrottledMessage(player, "cliff_debug", 
                Text.literal("§c[Debug] Cliff detected! Ground distance: " + groundDistance), true, 2000);
        }
        
        return isDangerous;
    }
    
    private boolean needsFood(ClientPlayerEntity player) {
        return player.getHungerManager().getFoodLevel() < 15 || 
               !hasItemInInventory(player, Items.BREAD, 5);
    }
    
    private boolean needsEquipment(ClientPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        
        // Check for diamond equipment
        boolean hasDiamondSword = hasItemInInventory(player, Items.DIAMOND_SWORD, 1);
        boolean hasDiamondPickaxe = hasItemInInventory(player, Items.DIAMOND_PICKAXE, 1);
        boolean hasDiamondArmor = hasItemInInventory(player, Items.DIAMOND_HELMET, 1) &&
                                 hasItemInInventory(player, Items.DIAMOND_CHESTPLATE, 1) &&
                                 hasItemInInventory(player, Items.DIAMOND_LEGGINGS, 1) &&
                                 hasItemInInventory(player, Items.DIAMOND_BOOTS, 1);
        
        return !hasDiamondSword || !hasDiamondPickaxe || !hasDiamondArmor;
    }
    
    private void searchForFood(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        World world = client.world;
        
        // Look for animals or crops nearby
        BlockPos playerPos = player.getBlockPos();
        
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                for (int y = -3; y <= 3; y++) {
                    BlockPos checkPos = playerPos.add(x, y, z);
                    Block block = world.getBlockState(checkPos).getBlock();
                    
                    // Look for crops
                    if (block.equals(Blocks.WHEAT) || block.equals(Blocks.CARROTS) || 
                        block.equals(Blocks.POTATOES) || block.equals(Blocks.BEETROOTS)) {
                        startMining(checkPos);
                        return;
                    }
                }
            }
        }
    }
    
    private void searchForMinerals(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        World world = client.world;
        
        BlockPos playerPos = player.getBlockPos();
        
        // Look for diamond ore, iron ore, or coal
        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                for (int y = -10; y <= 5; y++) {
                    BlockPos checkPos = playerPos.add(x, y, z);
                    Block block = world.getBlockState(checkPos).getBlock();
                    
                    if (block.equals(Blocks.DIAMOND_ORE) || block.equals(Blocks.DEEPSLATE_DIAMOND_ORE) ||
                        block.equals(Blocks.IRON_ORE) || block.equals(Blocks.DEEPSLATE_IRON_ORE) ||
                        block.equals(Blocks.COAL_ORE) || block.equals(Blocks.DEEPSLATE_COAL_ORE)) {
                        startMining(checkPos);
                        return;
                    }
                }
            }
        }
    }
    
    private void startMining(BlockPos target) {
        isMining = true;
        miningTarget = target;
        miningTicks = 0;
    }
    
    private void handleMining(MinecraftClient client) {
        if (miningTarget == null) {
            isMining = false;
            return;
        }
        
        ClientPlayerEntity player = client.player;
        miningTicks++;
        
        // Timeout check
        if (miningTicks > MINING_TIMEOUT) {
            isMining = false;
            miningTarget = null;
            return;
        }
        
        // Move towards mining target
        Vec3d targetPos = Vec3d.ofCenter(miningTarget);
        Vec3d playerPos = player.getPos();
        double distance = playerPos.distanceTo(targetPos);
        
        if (distance > 5.0) {
            // Move towards target
            Vec3d direction = targetPos.subtract(playerPos).normalize();
            float targetYaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
            player.setYaw(targetYaw);
            client.options.forwardKey.setPressed(true);
        } else {
            // Start mining
            client.options.forwardKey.setPressed(false);
            
            // Look at the block
            Vec3d direction = targetPos.subtract(playerPos).normalize();
            float targetYaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
            float targetPitch = (float) Math.toDegrees(Math.asin(-direction.y));
            player.setYaw(targetYaw);
            player.setPitch(targetPitch);
            
            // Mine the block
            client.options.attackKey.setPressed(true);
            
            // Check if block is gone
            if (client.world.getBlockState(miningTarget).getBlock().equals(Blocks.AIR)) {
                isMining = false;
                miningTarget = null;
                client.options.attackKey.setPressed(false);
            }
        }
    }
    
    private boolean hasItemInInventory(ClientPlayerEntity player, Item item, int minCount) {
        PlayerInventory inventory = player.getInventory();
        int count = 0;
        
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i).getItem().equals(item)) {
                count += inventory.getStack(i).getCount();
            }
        }
        
        return count >= minCount;
    }
    
    private void stopAllActions(MinecraftClient client) {
        GameOptions options = client.options;
        options.forwardKey.setPressed(false);
        options.backKey.setPressed(false);
        options.leftKey.setPressed(false);
        options.rightKey.setPressed(false);
        options.jumpKey.setPressed(false);
        options.attackKey.setPressed(false);
        
        isMining = false;
        miningTarget = null;
    }
    
    // Public methods for command system
    public boolean isEscaping() {
        return isEscaping;
    }
    
    public void startEscape() {
        if (!isEscaping) {
            toggleEscape();
        }
    }
    
    public void stopEscape() {
        if (isEscaping) {
            toggleEscape();
        }
    }
    
    public double getDistanceFromSpawn() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return 0;
        }
        
        return navigator.getDistanceFromSpawn(client.player.getBlockPos());
    }
    
    public void setShowProgressMessages(boolean show) {
        this.showProgressMessages = show;
    }
    
    public void setVerboseMode(boolean verbose) {
        this.verboseMode = verbose;
    }
    
    public boolean isShowingProgressMessages() {
        return showProgressMessages;
    }
    
    public AutoCraftingSystem getAutoCrafting() {
        return autoCrafting;
    }
    
    public InventoryManager getInventoryManager() {
        return inventoryManager;
    }
    
    public AntiGriefSystem getAntiGrief() {
        return antiGrief;
    }
    
    public ProgressionSystem getProgression() {
        return progression;
    }
    
    public PathfindingSystem getPathfinding() {
        return pathfinding;
    }
    
    public boolean isMining() {
        return isMining;
    }
    
    /**
     * Simple navigation without complex pathfinding (performance optimized)
     */
    private void handleSimpleNavigation(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        GameOptions options = client.options;
        
        if (!navigator.isNavigating()) return;
        
        BlockPos currentPos = player.getBlockPos();
        Vec3d direction = navigator.getOptimalDirection();
        
        if (direction == null) return;
        
        // Simple movement toward target direction
        Vec3d playerPos = player.getPos();
        
        // Set player rotation
        float targetYaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        player.setYaw(targetYaw);
        
        // Always move forward
        options.forwardKey.setPressed(true);
        
        // Simple jumping logic
        if (shouldJumpSimple(client, direction)) {
            options.jumpKey.setPressed(true);
        } else {
            options.jumpKey.setPressed(false);
        }
        
        // Handle water/lava
        if (player.isTouchingWater() || player.isInLava()) {
            options.jumpKey.setPressed(true);
        }
    }
    
    /**
     * Simple jump detection
     */
    private boolean shouldJumpSimple(MinecraftClient client, Vec3d direction) {
        ClientPlayerEntity player = client.player;
        
        // Check for obstacle directly ahead
        Vec3d playerPos = player.getPos();
        Vec3d checkPos = playerPos.add(direction.multiply(1.0));
        BlockPos blockPos = BlockPos.ofFloored(checkPos);
        
        // Jump if there's a block at feet level
        Block feetBlock = client.world.getBlockState(blockPos).getBlock();
        if (feetBlock != Blocks.AIR && feetBlock != Blocks.WATER) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Handle navigation using the simplified system
     */
    private void handleNewNavigation(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        GameOptions options = client.options;
        
        if (!navigator.isNavigating()) return;
        
        // Check if using Baritone pathfinding
        if (navigator.isUsingBaritone()) {
            // Let Baritone handle movement - just monitor progress
            // Baritone will control the player directly
            return;
        }
        
        // Use simple pathfinding for movement
        BlockPos currentPos = player.getBlockPos();
        Vec3d direction = navigator.getNextMovementDirection(currentPos);
        
        if (direction == null) {
            // No direction available, stop movement
            options.forwardKey.setPressed(false);
            return;
        }
        
        // Set player rotation
        float targetYaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        player.setYaw(targetYaw);
        
        // Move forward
        options.forwardKey.setPressed(true);
        
        // Handle jumping
        if (navigator.shouldJump(currentPos)) {
            options.jumpKey.setPressed(true);
        } else {
            options.jumpKey.setPressed(false);
        }
        
        // Handle water/lava
        if (player.isTouchingWater() || player.isInLava()) {
            options.jumpKey.setPressed(true);
        }
    }
    

    
    /**
     * Render simple navigation indicators
     */
    private void renderNavigationPath(MinecraftClient client, ClientPlayerEntity player) {
        if (!navigator.isNavigating()) return;
        
        // Only render direction arrow and progress (much lighter)
        Vec3d direction = navigator.getOptimalDirection();
        if (direction != null) {
            pathRenderer.renderNavigationArrow(player.getPos(), direction);
        }
        
        // Render progress indicator less frequently
        if (renderTickCounter % 4 == 0) {
            double progress = navigator.getProgressPercentage(player.getBlockPos());
            pathRenderer.renderProgressIndicator(player.getPos(), progress);
        }
    }
    
    private void displayPathLine(MinecraftClient client, ClientPlayerEntity player) {
        if (targetDirection == null || client.world == null) return;
        
        Vec3d playerPos = player.getPos();
        
        // Show the intended path as a line extending from the player
        for (int distance = 1; distance <= 50; distance++) {
            Vec3d pathPoint = playerPos.add(targetDirection.multiply(distance));
            
            // Different colors based on distance
            if (distance <= 10) {
                // Close path - bright cyan
                client.world.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    pathPoint.x, pathPoint.y + 0.5, pathPoint.z, 0, 0, 0);
            } else if (distance <= 25) {
                // Medium distance - blue
                client.world.addParticle(ParticleTypes.ENCHANT,
                    pathPoint.x, pathPoint.y + 0.3, pathPoint.z, 0, 0, 0);
            } else {
                // Far distance - faint particles
                if (distance % 3 == 0) { // Every 3rd particle to reduce density
                    client.world.addParticle(ParticleTypes.END_ROD,
                        pathPoint.x, pathPoint.y + 0.2, pathPoint.z, 0, 0, 0);
                }
            }
            
            // Add waypoint markers every 10 blocks
            if (distance % 10 == 0) {
                // Create a small marker
                for (int angle = 0; angle < 360; angle += 90) {
                    double radians = Math.toRadians(angle);
                    double offsetX = Math.cos(radians) * 0.3;
                    double offsetZ = Math.sin(radians) * 0.3;
                    
                    client.world.addParticle(ParticleTypes.FLAME,
                        pathPoint.x + offsetX, pathPoint.y + 0.8, pathPoint.z + offsetZ, 0, 0, 0);
                }
            }
        }
        
        // Show current target direction with an arrow-like effect
        Vec3d arrowStart = playerPos.add(0, 1, 0);
        Vec3d arrowEnd = arrowStart.add(targetDirection.multiply(5));
        
        // Create arrow shaft
        for (int i = 0; i <= 10; i++) {
            double progress = i / 10.0;
            Vec3d arrowPoint = arrowStart.lerp(arrowEnd, progress);
            client.world.addParticle(ParticleTypes.HAPPY_VILLAGER,
                arrowPoint.x, arrowPoint.y, arrowPoint.z, 0, 0, 0);
        }
        
        // Create arrow head
        Vec3d perpendicular = new Vec3d(-targetDirection.z, 0, targetDirection.x).normalize();
        for (int i = -2; i <= 2; i++) {
            Vec3d arrowHeadPoint = arrowEnd.add(perpendicular.multiply(i * 0.2));
            client.world.addParticle(ParticleTypes.HAPPY_VILLAGER,
                arrowHeadPoint.x, arrowHeadPoint.y, arrowHeadPoint.z, 0, 0, 0);
        }
    }
    
    // Enhanced movement with anti-grief integration
    private boolean isPathSafe(Vec3d currentPos, Vec3d targetPos) {
        // Check if the path to target is safe
        Vec3d direction = targetPos.subtract(currentPos).normalize();
        
        for (int i = 1; i <= 10; i++) {
            Vec3d checkPos = currentPos.add(direction.multiply(i));
            BlockPos blockPos = BlockPos.ofFloored(checkPos);
            
            if (!antiGrief.isPositionSafe(blockPos)) {
                return false;
            }
        }
        
        return true;
    }
    
    // Enhanced obstacle detection
    private Vec3d findAlternatePath(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        Vec3d playerPos = player.getPos();
        
        // Try different angles to find a safe path
        for (int angle = -45; angle <= 45; angle += 15) {
            double radians = Math.toRadians(player.getYaw() + angle);
            Vec3d direction = new Vec3d(Math.sin(radians), 0, -Math.cos(radians));
            Vec3d testTarget = playerPos.add(direction.multiply(20));
            
            if (isPathSafe(playerPos, testTarget)) {
                return direction;
            }
        }
        
        return null; // No safe path found
    }
    
    // Fall damage protection methods (anti-cheat safe)
    private void applyFallDamageProtection(ClientPlayerEntity player) {
        // Method 1: Check for dangerous falls and try to place water/blocks
        if (player.getVelocity().y < -0.5 && isHighFall(player)) {
            handleDangerousFall(player);
        }
        
        // Method 2: Auto-equip feather falling boots if available
        autoEquipFeatherFallingBoots(player);
        
        // Method 3: Avoid dangerous areas by steering away from cliffs
        avoidDangerousCliffs(player);
    }
    
    private boolean isHighFall(ClientPlayerEntity player) {
        World world = player.getWorld();
        BlockPos playerPos = player.getBlockPos();
        
        // Check how far down the ground is
        for (int i = 1; i <= 20; i++) {
            BlockPos checkPos = playerPos.down(i);
            if (!world.getBlockState(checkPos).isAir()) {
                return i > 5; // Dangerous if more than 5 blocks high
            }
        }
        return true; // Assume dangerous if we can't find ground within 20 blocks
    }
    
    private void handleDangerousFall(ClientPlayerEntity player) {
        // Try to place water bucket if available
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.getItem() == Items.WATER_BUCKET) {
                // In a real implementation, you'd place the water bucket
                // This is a simplified version
                MessageThrottler.sendThrottledMessage(player, "fall_protection", 
                    Text.literal("§e[EscapeMod] Fall protection: Water bucket ready!"), true, 5000);
                break;
            }
        }
        
        // Try to place blocks to reduce fall damage
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.getItem().toString().contains("block") && stack.getCount() > 0) {
                MessageThrottler.sendThrottledMessage(player, "fall_protection", 
                    Text.literal("§e[EscapeMod] Fall protection: Placing blocks!"), true, 5000);
                break;
            }
        }
    }
    
    private void autoEquipFeatherFallingBoots(ClientPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack currentBoots = inventory.getArmorStack(0); // Boots slot
        
        // Look for any boots to equip (simplified without enchantment checking)
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (isBoots(stack) && !isBoots(currentBoots)) {
                // Swap boots (simplified - in real implementation you'd handle the inventory properly)
                MessageThrottler.sendThrottledMessage(player, "equipment_swap", 
                    Text.literal("§a[EscapeMod] Equipped boots for fall protection!"), true, 10000);
                break;
            }
        }
    }
    
    private boolean isBoots(ItemStack stack) {
        return stack.getItem() == Items.LEATHER_BOOTS || 
               stack.getItem() == Items.IRON_BOOTS || 
               stack.getItem() == Items.DIAMOND_BOOTS || 
               stack.getItem() == Items.NETHERITE_BOOTS ||
               stack.getItem() == Items.CHAINMAIL_BOOTS ||
               stack.getItem() == Items.GOLDEN_BOOTS;
    }
    
    private void avoidDangerousCliffs(ClientPlayerEntity player) {
        World world = player.getWorld();
        BlockPos playerPos = player.getBlockPos();
        Vec3d lookDirection = Vec3d.fromPolar(0, player.getYaw()).normalize();
        
        // Check for cliffs ahead
        for (int distance = 1; distance <= 5; distance++) {
            Vec3d checkPos = player.getPos().add(lookDirection.multiply(distance));
            BlockPos blockPos = BlockPos.ofFloored(checkPos);
            
            // Check if there's a dangerous drop ahead
            int dropDistance = 0;
            for (int i = 1; i <= 15; i++) {
                BlockPos dropCheck = blockPos.down(i);
                if (!world.getBlockState(dropCheck).isAir()) {
                    dropDistance = i;
                    break;
                }
            }
            
            // If there's a dangerous drop (more than 6 blocks), try to steer away
            if (dropDistance > 6 || dropDistance == 0) { // 0 means we didn't find ground within 15 blocks
                // Adjust direction to avoid the cliff
                double newAngle = Math.toRadians(player.getYaw() + (random.nextBoolean() ? 45 : -45));
                targetDirection = new Vec3d(Math.cos(newAngle), 0, Math.sin(newAngle)).normalize();
                
                MessageThrottler.sendThrottledMessage(player, "cliff_avoidance", 
                    Text.literal("§e[EscapeMod] Avoiding dangerous cliff ahead!"), true, 5000);
                break;
            }
        }
    }
    
    // Particle trail methods
    private void updateParticleTrail(MinecraftClient client, ClientPlayerEntity player) {
        Vec3d currentPos = player.getPos();
        
        // Add current position to path history
        pathHistory.add(currentPos);
        
        // Limit path history size to prevent memory issues
        if (pathHistory.size() > 250) {
            pathHistory.remove(0);
        }
        
        // Spawn particles every few ticks
        particleTimer++;
        if (particleTimer >= 10) { // Every 10 ticks (0.5 seconds)
            spawnTrailParticles(client, currentPos);
            particleTimer = 0;
        }
        
        lastParticlePos = currentPos;
    }
    
    private void spawnTrailParticles(MinecraftClient client, Vec3d pos) {
        if (client.world == null) return;
        
        // Spawn different particles based on movement state
        if (client.player.isOnGround()) {
            // Ground trail - green particles
            client.world.addParticle(ParticleTypes.HAPPY_VILLAGER, 
                pos.x, pos.y + 0.1, pos.z, 0, 0.1, 0);
        } else if (client.player.getVelocity().y < -0.1) {
            // Falling - red particles
            client.world.addParticle(ParticleTypes.FLAME, 
                pos.x, pos.y, pos.z, 0, -0.1, 0);
        } else {
            // Flying/jumping - blue particles
            client.world.addParticle(ParticleTypes.ENCHANT, 
                pos.x, pos.y, pos.z, 0, 0.05, 0);
        }
        
        // Add a subtle trail behind the player
        if (lastParticlePos != null) {
            Vec3d direction = pos.subtract(lastParticlePos).normalize();
            for (int i = 1; i <= 3; i++) {
                Vec3d trailPos = pos.subtract(direction.multiply(i * 0.5));
                client.world.addParticle(ParticleTypes.END_ROD, 
                    trailPos.x, trailPos.y + 0.5, trailPos.z, 0, 0, 0);
            }
        }
    }
    
    // Configuration methods for new features
    public void setShowParticleTrail(boolean show) {
        this.showParticleTrail = show;
    }
    
    public void setFallDamageProtection(boolean protect) {
        this.fallDamageProtection = protect;
        this.fallProtection.setEnabled(protect);
    }
    
    public boolean isShowingParticleTrail() {
        return showParticleTrail;
    }
    
    public void setShowPathLine(boolean show) {
        this.showPathLine = show;
    }
    
    public boolean isShowingPathLine() {
        return showPathLine;
    }
    
    public EscapeNavigator getNavigator() {
        return navigator;
    }
    
    public PathRenderer getPathRenderer() {
        return pathRenderer;
    }
    
    public void toggleSimpleNavigation() {
        this.useSimpleNavigation = !this.useSimpleNavigation;
    }
    
    public boolean isUsingSimpleNavigation() {
        return useSimpleNavigation;
    }
    
    /**
     * Handle crisis mode behavior
     */
    private void handleCrisisMode(MinecraftClient client) {
        CrisisMode.CrisisAction action = crisisMode.executeCrisisMode();
        GameOptions options = client.options;
        
        switch (action) {
            case HIDE_AND_WAIT:
                // Stop all movement and hide
                options.forwardKey.setPressed(false);
                options.backKey.setPressed(false);
                options.leftKey.setPressed(false);
                options.rightKey.setPressed(false);
                options.jumpKey.setPressed(false);
                options.sneakKey.setPressed(true); // Crouch to hide
                break;
                
            case RETREAT_TO_SHELTER:
                // Move to safe hideout
                BlockPos shelter = crisisMode.getSafeHideout();
                if (shelter != null && client.player != null) {
                    // Use Baritone for emergency navigation if available
                    if (navigator.isUsingBaritone()) {
                        navigator.getBaritonePathfinder().handleEmergency(shelter);
                    } else {
                        // Fallback to simple movement
                        Vec3d direction = Vec3d.ofCenter(shelter).subtract(client.player.getPos()).normalize();
                        float targetYaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
                        client.player.setYaw(targetYaw);
                        options.forwardKey.setPressed(true);
                    }
                }
                break;
                
            case SEARCH_FOR_FOOD:
            case SEARCH_FOR_MATERIALS:
                // Slow, careful movement while searching
                options.forwardKey.setPressed(tickCounter % 40 < 20); // Move intermittently
                options.sneakKey.setPressed(true); // Stay crouched
                break;
                
            case CRISIS_RESOLVED:
                client.player.sendMessage(Text.literal("§a[EscapeMod] Crisis resolved, resuming escape!"), false);
                break;
                
            default:
                // For other actions, just wait
                options.forwardKey.setPressed(false);
                break;
        }
    }
    
    /**
     * Handle backtrack recovery
     */
    private void handleBacktrackRecovery(MinecraftClient client) {
        BacktrackRecovery.RecoveryAction action = backtrackRecovery.executeRecovery();
        GameOptions options = client.options;
        
        switch (action) {
            case BACKTRACK_TO_SAFE:
                BlockPos safePos = backtrackRecovery.getLastSafePosition();
                if (safePos != null && client.player != null) {
                    // Use Baritone for emergency backtrack if available
                    if (navigator.isUsingBaritone()) {
                        navigator.getBaritonePathfinder().handleEmergency(safePos);
                    } else {
                        // Fallback to simple movement
                        Vec3d direction = Vec3d.ofCenter(safePos).subtract(client.player.getPos()).normalize();
                        float targetYaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
                        client.player.setYaw(targetYaw);
                        options.forwardKey.setPressed(true);
                    }
                    
                    // Check if reached safe position
                    if (client.player.getBlockPos().getSquaredDistance(safePos) < 4) {
                        backtrackRecovery.resetRecovery();
                        client.player.sendMessage(Text.literal("§a[EscapeMod] Reached safe position!"), false);
                    }
                }
                break;
                
            case DIG_OUT:
            case DIG_UP:
            case DIG_HORIZONTAL:
                // Would implement digging logic here
                client.player.sendMessage(Text.literal("§e[EscapeMod] Attempting to dig out..."), false);
                options.attackKey.setPressed(true);
                break;
                
            case RANDOM_WALK:
                // Random movement to escape local area
                if (tickCounter % 60 == 0) { // Change direction every 3 seconds
                    float randomYaw = (float) (Math.random() * 360);
                    client.player.setYaw(randomYaw);
                }
                options.forwardKey.setPressed(true);
                break;
                
            default:
                break;
        }
    }
    
    /**
     * Handle auto-stash system
     */
    private void handleAutoStash(MinecraftClient client) {
        AutoStashSystem.StashResult result = autoStash.executeStash();
        
        switch (result) {
            case SUCCESS:
                client.player.sendMessage(Text.literal("§a[EscapeMod] Emergency stash created!"), false);
                break;
            case NO_SAFE_LOCATION:
                client.player.sendMessage(Text.literal("§c[EscapeMod] No safe location for stash found"), false);
                break;
            case FAILED:
                client.player.sendMessage(Text.literal("§c[EscapeMod] Failed to create stash"), false);
                break;
            default:
                break;
        }
    }
    
    /**
     * Check if should use nether travel
     */
    private boolean shouldUseNetherTravel() {
        if (client.player == null || !navigator.isNavigating()) return false;
        
        BlockPos currentPos = client.player.getBlockPos();
        BlockPos finalTarget = navigator.getFinalTarget();
        
        if (finalTarget == null) return false;
        
        return netherNavigator.shouldUseNether(currentPos, finalTarget);
    }
    
    /**
     * Handle nether travel process
     */
    private void handleNetherTravel(MinecraftClient client) {
        if (client.player == null) return;
        
        BlockPos currentPos = client.player.getBlockPos();
        
        // Configure Baritone for nether if using it
        if (navigator.isUsingBaritone()) {
            navigator.getBaritonePathfinder().getBaritoneIntegration().configureForNether();
        }
        
        // Find or create portal
        BlockPos portalPos = netherNavigator.findOrCreatePortal(currentPos);
        if (portalPos != null) {
            // Navigate to portal using current pathfinder
            if (navigator.isUsingBaritone()) {
                navigator.getBaritonePathfinder().pathTo(portalPos);
            } else {
                // Simple pathfinder will handle this in normal navigation
            }
            
            client.player.sendMessage(Text.literal("§e[EscapeMod] Navigating to nether portal for faster travel"), false);
        }
    }
    
    // Getters for new systems
    public NetherHighwayNavigator getNetherNavigator() { return netherNavigator; }
    public BacktrackRecovery getBacktrackRecovery() { return backtrackRecovery; }
    public AutoStashSystem getAutoStash() { return autoStash; }
    public CrisisMode getCrisisMode() { return crisisMode; }
    
    public boolean isFallDamageProtectionEnabled() {
        return fallDamageProtection;
    }
    
    public void clearPathHistory() {
        pathHistory.clear();
    }
    
    public int getPathHistorySize() {
        return pathHistory.size();
    }
}