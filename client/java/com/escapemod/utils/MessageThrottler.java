package com.escapemod.utils;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

public class MessageThrottler {
    private static final Map<String, Long> lastMessageTimes = new HashMap<>();
    private static final long DEFAULT_THROTTLE_TIME = 5000; // 5 seconds
    
    public static boolean canSendMessage(String messageKey) {
        return canSendMessage(messageKey, DEFAULT_THROTTLE_TIME);
    }
    
    public static boolean canSendMessage(String messageKey, long throttleTimeMs) {
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastMessageTimes.get(messageKey);
        
        if (lastTime == null || currentTime - lastTime >= throttleTimeMs) {
            lastMessageTimes.put(messageKey, currentTime);
            return true;
        }
        
        return false;
    }
    
    public static void sendThrottledMessage(ClientPlayerEntity player, String messageKey, Text message, boolean overlay) {
        if (canSendMessage(messageKey)) {
            player.sendMessage(message, overlay);
        }
    }
    
    public static void sendThrottledMessage(ClientPlayerEntity player, String messageKey, Text message, boolean overlay, long throttleTimeMs) {
        if (canSendMessage(messageKey, throttleTimeMs)) {
            player.sendMessage(message, overlay);
        }
    }
}