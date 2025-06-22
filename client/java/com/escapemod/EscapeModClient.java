package com.escapemod;

import com.escapemod.assistant.AnarchyAssistant;
import com.escapemod.commands.CommandManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class EscapeModClient implements ClientModInitializer {
    private static KeyBinding escapeKey;
    private static KeyBinding assistantKey;
    private EscapeBot escapeBot;
    private AnarchyAssistant anarchyAssistant;
    private CommandManager commandManager;
    private boolean welcomeMessageSent = false;

    @Override
    public void onInitializeClient() {
        // Register keybindings
        escapeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.escapemod.start_escape",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                "category.escapemod.general"
        ));
        
        assistantKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.escapemod.assistant",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F9,
                "category.escapemod.general"
        ));

        // Initialize systems
        escapeBot = new EscapeBot();
        anarchyAssistant = new AnarchyAssistant();
        commandManager = new CommandManager(escapeBot, anarchyAssistant);

        // Register chat command interceptor
        ClientSendMessageEvents.ALLOW_CHAT.register((message) -> {
            if (commandManager.handleCommand(message)) {
                return false; // Cancel the message from being sent to server
            }
            return true; // Allow normal chat messages
        });

        // Register tick event
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                // Check if escape key was pressed
                if (escapeKey.wasPressed()) {
                    escapeBot.toggleEscape();
                }
                
                // Check if assistant key was pressed
                if (assistantKey.wasPressed()) {
                    anarchyAssistant.analyzeAndAdvise();
                }

                // Update the bot
                escapeBot.tick(client);
            }
        });
        
        // Send welcome message once per world join
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.world != null) {
                if (!welcomeMessageSent) {
                    client.player.sendMessage(Text.literal("§a[EscapeMod] Loaded successfully! Press F8 to escape, F9 for assistant, or type % help"), false);
                    welcomeMessageSent = true;
                }
            } else {
                // Reset flag when player leaves world so message shows again on next join
                welcomeMessageSent = false;
            }
        });
    }
}