package com.escapemod.commands;

import com.escapemod.EscapeBot;
import com.escapemod.assistant.AnarchyAssistant;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class CommandManager {
    private EscapeBot escapeBot;
    private AnarchyAssistant anarchyAssistant;
    
    public CommandManager(EscapeBot escapeBot, AnarchyAssistant anarchyAssistant) {
        this.escapeBot = escapeBot;
        this.anarchyAssistant = anarchyAssistant;
    }
    
    public boolean handleCommand(String message) {
        if (!message.startsWith("%")) {
            return false;
        }
        
        String[] parts = message.split(" ");
        String command = parts[0].toLowerCase();
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return true;
        
        switch (command) {
            case "%":
                if (parts.length > 1) {
                    String subCommand = parts[1].toLowerCase();
                    switch (subCommand) {
                        case "start":
                            escapeBot.startEscape();
                            client.player.sendMessage(Text.literal("§a[EscapeMod] Escape mode started!"), false);
                            break;
                        case "stop":
                            escapeBot.stopEscape();
                            client.player.sendMessage(Text.literal("§c[EscapeMod] Escape mode stopped!"), false);
                            break;
                        case "status":
                            showEscapeStatus(client);
                            break;
                        case "help":
                            showEscapeHelp(client);
                            break;
                        case "assistant":
                            anarchyAssistant.analyzeAndAdvise();
                            break;
                        case "quiet":
                            escapeBot.setShowProgressMessages(false);
                            client.player.sendMessage(Text.literal("§a[EscapeMod] Progress messages disabled"), false);
                            break;
                        case "verbose":
                            escapeBot.setShowProgressMessages(true);
                            client.player.sendMessage(Text.literal("§a[EscapeMod] Progress messages enabled"), false);
                            break;
                        case "particles":
                            escapeBot.setShowParticleTrail(!escapeBot.isShowingParticleTrail());
                            client.player.sendMessage(Text.literal("§a[EscapeMod] Particle trail " + 
                                (escapeBot.isShowingParticleTrail() ? "enabled" : "disabled")), false);
                            break;
                        case "pathline":
                            escapeBot.setShowPathLine(!escapeBot.isShowingPathLine());
                            client.player.sendMessage(Text.literal("§a[EscapeMod] Path line " + 
                                (escapeBot.isShowingPathLine() ? "enabled" : "disabled")), false);
                            break;
                        case "fallprotection":
                            escapeBot.setFallDamageProtection(!escapeBot.isFallDamageProtectionEnabled());
                            client.player.sendMessage(Text.literal("§a[EscapeMod] Fall damage protection " + 
                                (escapeBot.isFallDamageProtectionEnabled() ? "enabled" : "disabled")), false);
                            break;
                        case "clearpath":
                            escapeBot.clearPathHistory();
                            escapeBot.getPathfinding().clearPath();
                            client.player.sendMessage(Text.literal("§a[EscapeMod] Path history cleared"), false);
                            break;
                        case "progression":
                            showProgressionStatus(client);
                            break;
                        case "pathfinding":
                            escapeBot.getPathfinding().setPathfindingEnabled(!escapeBot.getPathfinding().isPathfindingEnabled());
                            client.player.sendMessage(Text.literal("§a[EscapeMod] Pathfinding " + 
                                (escapeBot.getPathfinding().isPathfindingEnabled() ? "enabled" : "disabled")), false);
                            break;
                        case "simple":
                            escapeBot.toggleSimpleNavigation();
                            client.player.sendMessage(Text.literal("§a[EscapeMod] Simple navigation " + 
                                (escapeBot.isUsingSimpleNavigation() ? "enabled" : "disabled")), false);
                            break;
                        case "goals":
                            showProgressionGoals(client);
                            break;
                        case "debug":
                            showDebugInfo(client);
                            break;
                        case "terrain":
                            showTerrainAnalysis(client);
                            break;
                        case "crisis":
                            showCrisisStatus(client);
                            break;
                        case "stash":
                            showStashInfo(client);
                            break;
                        case "nether":
                            showNetherInfo(client);
                            break;
                        case "recovery":
                            showRecoveryInfo(client);
                            break;
                        case "baritone":
                            toggleBaritone(client);
                            break;
                        case "baritone-status":
                            showBaritoneStatus(client);
                            break;
                        default:
                            showEscapeHelp(client);
                    }
                } else {
                    escapeBot.toggleEscape();
                }
                break;
        }
        
        return true;
    }
    
    private void showEscapeStatus(MinecraftClient client) {
        client.player.sendMessage(Text.literal("§e[EscapeMod] Status:"), false);
        client.player.sendMessage(Text.literal("§7- Escape Active: " + (escapeBot.isEscaping() ? "§aYes" : "§cNo")), false);
        if (escapeBot.isEscaping()) {
            double distance = escapeBot.getDistanceFromSpawn();
            double progress = escapeBot.getNavigator().getProgressPercentage(client.player.getBlockPos());
            
            client.player.sendMessage(Text.literal("§7- Distance from spawn: " + String.format("%.0f", distance) + " blocks"), false);
            client.player.sendMessage(Text.literal("§7- Target: 30,000 blocks"), false);
            client.player.sendMessage(Text.literal("§7- Progress: " + String.format("%.1f", progress) + "%"), false);
            
            var waypoint = escapeBot.getNavigator().getCurrentWaypoint();
            if (waypoint != null) {
                client.player.sendMessage(Text.literal("§7- Current waypoint: " + waypoint.getX() + ", " + waypoint.getZ()), false);
            }
            
            client.player.sendMessage(Text.literal("§7- Navigation Mode: §aSimplified (Performance Optimized)"), false);
        }
        client.player.sendMessage(Text.literal("§7- Particle Trail: " + (escapeBot.isShowingParticleTrail() ? "§aEnabled" : "§cDisabled")), false);
        client.player.sendMessage(Text.literal("§7- Path Line: " + (escapeBot.isShowingPathLine() ? "§aEnabled" : "§cDisabled")), false);
        client.player.sendMessage(Text.literal("§7- Fall Protection: " + (escapeBot.isFallDamageProtectionEnabled() ? "§aEnabled" : "§cDisabled")), false);
        client.player.sendMessage(Text.literal("§7- Path History: " + escapeBot.getPathHistorySize() + " points"), false);
        client.player.sendMessage(Text.literal("§7- Progress Messages: " + (escapeBot.isShowingProgressMessages() ? "§aEnabled" : "§cDisabled")), false);
        client.player.sendMessage(Text.literal("§7- Pathfinding: " + (escapeBot.getPathfinding().isPathfindingEnabled() ? "§aEnabled" : "§cDisabled")), false);
        if (escapeBot.getPathfinding().hasPath()) {
            client.player.sendMessage(Text.literal("§7- Current Path Length: " + String.format("%.1f", escapeBot.getPathfinding().getPathLength()) + " blocks"), false);
        }
    }
    
    private void showProgressionStatus(MinecraftClient client) {
        client.player.sendMessage(Text.literal("§e[EscapeMod] Progression Status:"), false);
        
        var currentGoal = escapeBot.getProgression().getCurrentGoal();
        if (currentGoal != null) {
            client.player.sendMessage(Text.literal("§7- Current Goal: §e" + currentGoal.description), false);
        }
        
        int completedGoals = (int) escapeBot.getProgression().getGoals().stream().filter(g -> g.completed).count();
        int totalGoals = escapeBot.getProgression().getGoals().size() - 1; // Exclude "continue escape" goal
        
        client.player.sendMessage(Text.literal("§7- Progress: §a" + completedGoals + "§7/§a" + totalGoals + " §7goals completed"), false);
        client.player.sendMessage(Text.literal("§7- Ready for Escape: " + (escapeBot.getProgression().isReadyForEscape() ? "§aYes" : "§cNo")), false);
    }
    
    private void showProgressionGoals(MinecraftClient client) {
        client.player.sendMessage(Text.literal("§e[EscapeMod] Progression Goals:"), false);
        
        for (var goal : escapeBot.getProgression().getGoals()) {
            if (goal.type == com.escapemod.systems.ProgressionSystem.GoalType.CONTINUE_ESCAPE) continue;
            
            String status = goal.completed ? "§a✓" : "§c✗";
            String priority = goal.type == com.escapemod.systems.ProgressionSystem.GoalType.GATHER_FOOD ? " §c(HIGH PRIORITY)" : "";
            
            client.player.sendMessage(Text.literal("§7- " + status + " §f" + goal.description + priority), false);
        }
    }
    
    private void showDebugInfo(MinecraftClient client) {
        client.player.sendMessage(Text.literal("§e[EscapeMod] Debug Information:"), false);
        
        if (client.player != null) {
            var pos = client.player.getPos();
            client.player.sendMessage(Text.literal("§7- Player Position: " + 
                String.format("X: %.1f, Y: %.1f, Z: %.1f", pos.x, pos.y, pos.z)), false);
        }
        
        client.player.sendMessage(Text.literal("§7- Is Escaping: " + (escapeBot.isEscaping() ? "§aYes" : "§cNo")), false);
        client.player.sendMessage(Text.literal("§7- Mining: " + (escapeBot.isMining() ? "§aYes" : "§cNo")), false);
        client.player.sendMessage(Text.literal("§7- Pathfinding Enabled: " + (escapeBot.getPathfinding().isPathfindingEnabled() ? "§aYes" : "§cNo")), false);
        client.player.sendMessage(Text.literal("§7- Has Path: " + (escapeBot.getPathfinding().hasPath() ? "§aYes" : "§cNo")), false);
        
        var currentGoal = escapeBot.getProgression().getCurrentGoal();
        if (currentGoal != null) {
            client.player.sendMessage(Text.literal("§7- Current Goal: §e" + currentGoal.description), false);
        }
        
        client.player.sendMessage(Text.literal("§7- Ready for Escape: " + (escapeBot.getProgression().isReadyForEscape() ? "§aYes" : "§cNo")), false);
    }
    
    private void showTerrainAnalysis(MinecraftClient client) {
        if (!escapeBot.isEscaping()) {
            client.player.sendMessage(Text.literal("§c[EscapeMod] Start escape mode first to see terrain analysis"), false);
            return;
        }
        
        client.player.sendMessage(Text.literal("§e[EscapeMod] Terrain Analysis:"), false);
        
        var navigator = escapeBot.getNavigator();
        var direction = navigator.getOptimalDirection();
        var currentPos = client.player.getBlockPos();
        
        if (direction != null) {
            client.player.sendMessage(Text.literal("§7- Optimal Direction: " + 
                String.format("X: %.2f, Z: %.2f", direction.x, direction.z)), false);
            
            // Show direction name
            String directionName = getDirectionName(direction);
            client.player.sendMessage(Text.literal("§7- Direction Name: §e" + directionName), false);
            
            var finalTarget = navigator.getFinalTarget();
            if (finalTarget != null) {
                client.player.sendMessage(Text.literal("§7- Final Target: " + 
                    finalTarget.getX() + ", " + finalTarget.getZ()), false);
                
                double distance = Math.sqrt(
                    Math.pow(finalTarget.getX() - currentPos.getX(), 2) + 
                    Math.pow(finalTarget.getZ() - currentPos.getZ(), 2)
                );
                client.player.sendMessage(Text.literal("§7- Distance to Target: " + 
                    String.format("%.0f", distance) + " blocks"), false);
            }
            
            var waypoint = navigator.getCurrentWaypoint();
            if (waypoint != null) {
                double waypointDistance = Math.sqrt(
                    Math.pow(waypoint.getX() - currentPos.getX(), 2) + 
                    Math.pow(waypoint.getZ() - currentPos.getZ(), 2)
                );
                client.player.sendMessage(Text.literal("§7- Next Waypoint Distance: " + 
                    String.format("%.0f", waypointDistance) + " blocks"), false);
            }
        }
    }
    
    private String getDirectionName(Vec3d direction) {
        double angle = Math.atan2(direction.z, direction.x);
        double degrees = Math.toDegrees(angle);
        if (degrees < 0) degrees += 360;
        
        if (degrees >= 337.5 || degrees < 22.5) return "East";
        else if (degrees >= 22.5 && degrees < 67.5) return "Northeast";
        else if (degrees >= 67.5 && degrees < 112.5) return "North";
        else if (degrees >= 112.5 && degrees < 157.5) return "Northwest";
        else if (degrees >= 157.5 && degrees < 202.5) return "West";
        else if (degrees >= 202.5 && degrees < 247.5) return "Southwest";
        else if (degrees >= 247.5 && degrees < 292.5) return "South";
        else return "Southeast";
    }
    
    private void showCrisisStatus(MinecraftClient client) {
        var crisisMode = escapeBot.getCrisisMode();
        
        client.player.sendMessage(Text.literal("§e[EscapeMod] Crisis Mode Status:"), false);
        client.player.sendMessage(Text.literal("§7- Active: " + (crisisMode.isCrisisModeActive() ? "§cYes" : "§aNo")), false);
        
        if (crisisMode.isCrisisModeActive()) {
            client.player.sendMessage(Text.literal("§7- Crisis Type: §c" + crisisMode.getCurrentCrisis().getDescription()), false);
            client.player.sendMessage(Text.literal("§7- Duration: " + (crisisMode.getCrisisDuration() / 1000) + " seconds"), false);
            
            var shelter = crisisMode.getSafeHideout();
            if (shelter != null) {
                client.player.sendMessage(Text.literal("§7- Safe Hideout: " + shelter.getX() + ", " + shelter.getY() + ", " + shelter.getZ()), false);
            }
        }
        
        // Show current health/hunger status
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§7- Health: §" + (client.player.getHealth() > 10 ? "a" : "c") + 
                String.format("%.1f", client.player.getHealth()) + "/20"), false);
            client.player.sendMessage(Text.literal("§7- Hunger: §" + (client.player.getHungerManager().getFoodLevel() > 10 ? "a" : "c") + 
                client.player.getHungerManager().getFoodLevel() + "/20"), false);
        }
    }
    
    private void showStashInfo(MinecraftClient client) {
        var autoStash = escapeBot.getAutoStash();
        var stashes = autoStash.getStashHistory();
        
        client.player.sendMessage(Text.literal("§e[EscapeMod] Auto-Stash System:"), false);
        client.player.sendMessage(Text.literal("§7- Enabled: " + (autoStash.isAutoStashEnabled() ? "§aYes" : "§cNo")), false);
        client.player.sendMessage(Text.literal("§7- Total Stashes: §e" + stashes.size()), false);
        client.player.sendMessage(Text.literal("§7- Inventory Threshold: " + autoStash.getInventoryFullThreshold() + " slots"), false);
        
        if (!stashes.isEmpty()) {
            client.player.sendMessage(Text.literal("§7- Recent Stashes:"), false);
            
            // Show last 3 stashes
            int count = 0;
            for (int i = stashes.size() - 1; i >= 0 && count < 3; i--, count++) {
                var stash = stashes.get(i);
                client.player.sendMessage(Text.literal("§7  " + (count + 1) + ". " + stash.type + " at " + 
                    stash.position.getX() + ", " + stash.position.getY() + ", " + stash.position.getZ()), false);
            }
        }
        
        // Show nearest stash
        if (client.player != null && !stashes.isEmpty()) {
            var nearest = autoStash.findNearestStash(client.player.getBlockPos());
            if (nearest != null) {
                double distance = Math.sqrt(nearest.position.getSquaredDistance(client.player.getBlockPos()));
                client.player.sendMessage(Text.literal("§7- Nearest Stash: " + String.format("%.0f", distance) + " blocks away"), false);
            }
        }
    }
    
    private void showNetherInfo(MinecraftClient client) {
        var netherNav = escapeBot.getNetherNavigator();
        
        client.player.sendMessage(Text.literal("§e[EscapeMod] Nether Navigation:"), false);
        client.player.sendMessage(Text.literal("§7- In Nether: " + (netherNav.isInNether() ? "§aYes" : "§cNo")), false);
        
        var highways = netherNav.getDetectedHighways();
        client.player.sendMessage(Text.literal("§7- Detected Highways: §e" + highways.size()), false);
        
        if (!highways.isEmpty()) {
            client.player.sendMessage(Text.literal("§7- Available Highways:"), false);
            for (var highway : highways) {
                client.player.sendMessage(Text.literal("§7  - " + highway.directionName + " (" + highway.length + " blocks)"), false);
            }
        }
        
        var portals = netherNav.getKnownPortals();
        client.player.sendMessage(Text.literal("§7- Known Portals: §e" + portals.size()), false);
        
        if (client.player != null && escapeBot.isEscaping()) {
            BlockPos currentPos = client.player.getBlockPos();
            BlockPos finalTarget = escapeBot.getNavigator().getFinalTarget();
            
            if (finalTarget != null) {
                boolean shouldUseNether = netherNav.shouldUseNether(currentPos, finalTarget);
                client.player.sendMessage(Text.literal("§7- Recommend Nether: " + (shouldUseNether ? "§aYes" : "§cNo")), false);
            }
        }
    }
    
    private void showRecoveryInfo(MinecraftClient client) {
        var recovery = escapeBot.getBacktrackRecovery();
        
        client.player.sendMessage(Text.literal("§e[EscapeMod] Backtrack Recovery:"), false);
        client.player.sendMessage(Text.literal("§7- Recovery Active: " + (recovery.isRecovering() ? "§cYes" : "§aNo")), false);
        client.player.sendMessage(Text.literal("§7- Path History Size: §e" + recovery.getPathHistorySize()), false);
        
        var lastSafe = recovery.getLastSafePosition();
        if (lastSafe != null) {
            client.player.sendMessage(Text.literal("§7- Last Safe Position: " + 
                lastSafe.getX() + ", " + lastSafe.getY() + ", " + lastSafe.getZ()), false);
            
            if (client.player != null) {
                double distance = Math.sqrt(lastSafe.getSquaredDistance(client.player.getBlockPos()));
                client.player.sendMessage(Text.literal("§7- Distance to Safe: " + String.format("%.0f", distance) + " blocks"), false);
            }
        }
        
        if (recovery.needsRecovery()) {
            client.player.sendMessage(Text.literal("§c[EscapeMod] Recovery needed! Bot is stuck or in danger."), false);
        }
    }
    
    private void toggleBaritone(MinecraftClient client) {
        escapeBot.getNavigator().toggleBaritone();
    }
    
    private void showBaritoneStatus(MinecraftClient client) {
        var navigator = escapeBot.getNavigator();
        var baritonePathfinder = navigator.getBaritonePathfinder();
        
        client.player.sendMessage(Text.literal("§e[EscapeMod] Baritone Integration Status:"), false);
        client.player.sendMessage(Text.literal("§7- Available: " + (baritonePathfinder.isBaritoneAvailable() ? "§aYes" : "§cNo")), false);
        client.player.sendMessage(Text.literal("§7- Currently Using: " + (navigator.isUsingBaritone() ? "§aBaritone" : "§eSimple")), false);
        
        if (baritonePathfinder.isBaritoneAvailable()) {
            var stats = baritonePathfinder.getStats();
            client.player.sendMessage(Text.literal("§7- Pathfinding: " + (stats.isPathing ? "§aActive" : "§cInactive")), false);
            client.player.sendMessage(Text.literal("§7- Path Length: §e" + stats.pathLength + " nodes"), false);
            client.player.sendMessage(Text.literal("§7- Failures: §" + (stats.failures > 0 ? "c" : "a") + stats.failures), false);
            
            if (stats.estimatedTime > 0) {
                client.player.sendMessage(Text.literal("§7- ETA: §e" + String.format("%.1f", stats.estimatedTime) + " seconds"), false);
            }
            
            if (stats.currentGoal != null) {
                client.player.sendMessage(Text.literal("§7- Current Goal: §e" + stats.currentGoal), false);
            }
        } else {
            client.player.sendMessage(Text.literal("§c[EscapeMod] Baritone not detected. Install Baritone for advanced pathfinding."), false);
        }
    }
    
    private void showEscapeHelp(MinecraftClient client) {
        client.player.sendMessage(Text.literal("§6[EscapeMod] Commands:"), false);
        client.player.sendMessage(Text.literal("§e% §7- Toggle escape mode"), false);
        client.player.sendMessage(Text.literal("§e% start §7- Start escape mode"), false);
        client.player.sendMessage(Text.literal("§e% stop §7- Stop escape mode"), false);
        client.player.sendMessage(Text.literal("§e% status §7- Show current status"), false);
        client.player.sendMessage(Text.literal("§e% progression §7- Show progression status"), false);
        client.player.sendMessage(Text.literal("§e% goals §7- Show all progression goals"), false);
        client.player.sendMessage(Text.literal("§e% pathfinding §7- Toggle intelligent pathfinding"), false);
        client.player.sendMessage(Text.literal("§e% simple §7- Toggle simple navigation (better performance)"), false);
        client.player.sendMessage(Text.literal("§e% debug §7- Show debug information"), false);
        client.player.sendMessage(Text.literal("§e% terrain §7- Show terrain analysis"), false);
        client.player.sendMessage(Text.literal("§e% crisis §7- Show crisis mode status"), false);
        client.player.sendMessage(Text.literal("§e% stash §7- Show auto-stash information"), false);
        client.player.sendMessage(Text.literal("§e% nether §7- Show nether navigation info"), false);
        client.player.sendMessage(Text.literal("§e% recovery §7- Show backtrack recovery status"), false);
        client.player.sendMessage(Text.literal("§e% baritone §7- Toggle Baritone/Simple pathfinding"), false);
        client.player.sendMessage(Text.literal("§e% baritone-status §7- Show Baritone integration status"), false);
        client.player.sendMessage(Text.literal("§e% assistant §7- Get anarchy survival tips"), false);
        client.player.sendMessage(Text.literal("§e% quiet §7- Disable progress messages"), false);
        client.player.sendMessage(Text.literal("§e% verbose §7- Enable progress messages"), false);
        client.player.sendMessage(Text.literal("§e% particles §7- Toggle particle trail"), false);
        client.player.sendMessage(Text.literal("§e% pathline §7- Toggle path line visualization"), false);
        client.player.sendMessage(Text.literal("§e% fallprotection §7- Toggle fall damage protection"), false);
        client.player.sendMessage(Text.literal("§e% clearpath §7- Clear path history"), false);
        client.player.sendMessage(Text.literal("§e% help §7- Show this help"), false);
        client.player.sendMessage(Text.literal("§7Or press §eF8 §7to toggle escape mode"), false);
    }
}