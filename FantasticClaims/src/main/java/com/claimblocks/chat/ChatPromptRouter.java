package com.claimblocks.chat;

import com.claimblocks.ClaimBlocksMod;
import com.claimblocks.gui.AdminClaimSubMenuHandler;
import com.claimblocks.gui.ClaimMenuHandler;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ChatPromptRouter {
    private static final long SUPPRESS_WINDOW_MS = 2000L;
    private static final Map<UUID, ChatPromptRouter.Suppression> suppressions = new ConcurrentHashMap<>();
    private static volatile boolean packetCaptureActive;

    private ChatPromptRouter() {
    }

    public static boolean isPacketCaptureActive() {
        return packetCaptureActive;
    }

    public static boolean hasPending(UUID uuid) {
        return uuid != null && (ClaimMenuHandler.hasPrompt(uuid) || AdminClaimSubMenuHandler.hasPendingTransfer(uuid));
    }

    public static boolean consume(ServerPlayer serverplayer, String s) {
        if (serverplayer != null && s != null) {
            MinecraftServer minecraftserver = serverplayer.getServer();
            if (minecraftserver == null) {
                return false;
            } else {
                UUID uuid = serverplayer.getUUID();
                UUID uuid1 = AdminClaimSubMenuHandler.popPendingTransfer(uuid);
                if (uuid1 != null) {
                    String s2 = stripControlChars(s);
                    markSuppressed(uuid, s);
                    minecraftserver.execute(() -> ClaimMenuHandler.dispatchAdminTransfer(serverplayer, uuid1, s2));
                    return true;
                } else {
                    ClaimMenuHandler.PendingChat claimmenuhandler$pendingchat = ClaimMenuHandler.popPrompt(uuid);
                    if (claimmenuhandler$pendingchat != null) {
                        String s1 = stripControlChars(s);
                        markSuppressed(uuid, s);
                        minecraftserver.execute(() -> ClaimMenuHandler.dispatchPrompt(serverplayer, claimmenuhandler$pendingchat, s1));
                        return true;
                    } else {
                        return false;
                    }
                }
            }
        } else {
            return false;
        }
    }

    private static void markSuppressed(UUID uuid, String s) {
        suppressions.put(uuid, new ChatPromptRouter.Suppression(s, System.currentTimeMillis() + 2000L));
    }

    public static boolean shouldSuppress(UUID uuid, String s) {
        if (uuid != null && s != null) {
            ChatPromptRouter.Suppression chatpromptrouter$suppression = suppressions.get(uuid);
            if (chatpromptrouter$suppression == null) {
                return false;
            } else if (System.currentTimeMillis() > chatpromptrouter$suppression.expiresAt()) {
                suppressions.remove(uuid, chatpromptrouter$suppression);
                return false;
            } else if (!chatpromptrouter$suppression.rawText().equals(s)) {
                return false;
            } else {
                suppressions.remove(uuid, chatpromptrouter$suppression);
                return true;
            }
        } else {
            return false;
        }
    }

    public static void onPlayerDisconnect(UUID uuid) {
        if (uuid != null) {
            suppressions.remove(uuid);
        }
    }

    public static void markPacketCaptureActive() {
        if (!packetCaptureActive) {
            packetCaptureActive = true;
            ClaimBlocksMod.LOGGER.info("[FantasticClaims] Captura de respuestas a nivel de paquete ACTIVA (compatible con Mohist y plugins de chat).");
        }
    }

    public static String stripControlChars(String s) {
        if (s == null) {
            return "";
        } else {
            StringBuilder stringbuilder = new StringBuilder(s.length());

            for (int i = 0; i < s.length(); i++) {
                char c0 = s.charAt(i);
                if (c0 == '\n' || c0 == '\r' || c0 == '\t') {
                    stringbuilder.append(' ');
                } else if (c0 >= ' ') {
                    stringbuilder.append(c0);
                }
            }

            return stringbuilder.toString().trim();
        }
    }

    public static String sanitize(String s) {
        if (s == null) {
            return "";
        } else {
            StringBuilder stringbuilder = new StringBuilder(s.length());

            for (int i = 0; i < s.length(); i++) {
                char c0 = s.charAt(i);
                if ((c0 == 167 || c0 == '&') && i + 1 < s.length() && isColorCode(s.charAt(i + 1))) {
                    i++;
                } else if (c0 == '\n' || c0 == '\r' || c0 == '\t') {
                    stringbuilder.append(' ');
                } else if (c0 >= ' ') {
                    stringbuilder.append(c0);
                }
            }

            return stringbuilder.toString().trim().replaceAll("\\s{2,}", " ");
        }
    }

    private static boolean isColorCode(char c0) {
        return "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(c0) >= 0;
    }

    public static boolean isCancel(String s) {
        if (s != null && !s.isBlank()) {
            String s1 = s.trim();
            return s1.equalsIgnoreCase("cancelar") || s1.equalsIgnoreCase("cancel") || s1.startsWith("/");
        } else {
            return true;
        }
    }

    public static String extractPlayerName(String s) {
        String s1 = sanitize(s);
        if (!s1.isEmpty() && !isValidName(s1)) {
            String[] astring = s1.split(" ");

            for (int i = astring.length - 1; i >= 0; i--) {
                if (isValidName(astring[i])) {
                    return astring[i];
                }
            }

            return s1;
        } else {
            return s1;
        }
    }

    private static boolean isValidName(String s) {
        if (s != null && s.length() >= 3 && s.length() <= 16) {
            for (int i = 0; i < s.length(); i++) {
                char c0 = s.charAt(i);
                boolean flag = c0 >= 'a' && c0 <= 'z' || c0 >= 'A' && c0 <= 'Z' || c0 >= '0' && c0 <= '9' || c0 == '_';
                if (!flag) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private static record Suppression(String rawText, long expiresAt) {
    }
}
