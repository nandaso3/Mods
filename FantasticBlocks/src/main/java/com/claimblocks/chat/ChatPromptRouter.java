package com.claimblocks.chat;

import com.claimblocks.ClaimBlocksMod;
import com.claimblocks.gui.AdminClaimSubMenuHandler;
import com.claimblocks.gui.ClaimMenuHandler;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Punto unico de entrada para las respuestas de los menus que se piden por chat
 * (anadir/quitar miembro, banear, mensajes de bienvenida, nombre de grupo, transferencia admin...).
 *
 * <h2>Por que existe</h2>
 * Hasta la 7.6.5 la unica forma de capturar la respuesta era el evento de Forge
 * {@code ServerChatEvent}. En un servidor <b>hibrido</b> (Mohist, Arclight, Magma) el chat del
 * jugador pasa primero por la tuberia de Bukkit ({@code AsyncPlayerChatEvent}), y cualquier plugin de
 * chat, rangos o anti-spam puede cancelarlo o reescribirlo. Cuando eso pasa el evento de Forge nunca
 * llega (o llega con el mensaje ya formateado), el prompt se queda colgado y "Anadir miembro" parece
 * no hacer nada.
 *
 * <h2>Como se arregla</h2>
 * Desde la 7.7.0 la <b>captura</b> se hace a nivel de paquete
 * ({@link com.claimblocks.mixin.ServerChatPromptMixin}), antes de que Bukkit o los plugins vean el
 * mensaje, y {@code ServerChatEvent} se queda como respaldo. Ambas rutas entran por
 * {@link #consume(ServerPlayer, String)}, y como el prompt se extrae con un {@code remove} atomico,
 * solo una puede procesarlo.
 *
 * <h2>Por que la captura no cancela el paquete</h2>
 * En 1.20.1 {@code handleChat} tambien hace la contabilidad de acuses de recibo del chat firmado
 * ({@code LastSeenMessagesValidator}). Si se cancelara el paquete en HEAD, esa contabilidad se
 * saltaria y el jugador acabaria expulsado con "Chat validation failed" en cuanto volviera a hablar.
 * Por eso se deja que el paquete siga su curso normal y la respuesta se oculta del chat publico
 * aparte, con {@link #shouldSuppress(UUID, String)}, en los puntos donde si es seguro cancelar.
 */
public final class ChatPromptRouter {

    /**
     * Margen para reconocer el mensaje ya capturado cuando llega al punto de publicacion. Se mantiene
     * corto a proposito: si ningun punto de cancelacion llega a ejecutarse el token caduca, y cuanto
     * mas breve sea la ventana menos posibilidad hay de tragarse por error un mensaje publico
     * posterior con el mismo texto.
     */
    private static final long SUPPRESS_WINDOW_MS = 2_000L;

    private static final Map<UUID, Suppression> suppressions = new ConcurrentHashMap<>();
    private static volatile boolean packetCaptureActive;

    private record Suppression(String rawText, long expiresAt) {
    }

    private ChatPromptRouter() {
    }

    /** True si el mixin de captura a nivel de paquete llego a ejecutarse al menos una vez. */
    public static boolean isPacketCaptureActive() {
        return packetCaptureActive;
    }

    /** ¿Este jugador tiene un prompt del mod esperando respuesta? */
    public static boolean hasPending(UUID playerId) {
        return playerId != null
                && (ClaimMenuHandler.hasPrompt(playerId) || AdminClaimSubMenuHandler.hasPendingTransfer(playerId));
    }

    /**
     * Intenta consumir un mensaje de chat como respuesta a un prompt del mod.
     *
     * <p>Puede llamarse desde el hilo de red: el trabajo real se reprograma en el hilo principal del
     * servidor, igual que hacia el manejador original.
     *
     * @return true si el mensaje era para nosotros. Quien llame decide si puede cancelar o no.
     */
    public static boolean consume(ServerPlayer sender, String rawMessage) {
        if (sender == null || rawMessage == null) {
            return false;
        }

        MinecraftServer server = sender.getServer();
        if (server == null) {
            return false;
        }

        UUID id = sender.getUUID();

        // Extraccion atomica: si dos rutas (paquete y ServerChatEvent) ven el mismo mensaje,
        // solo una obtiene el prompt y la otra se va sin hacer nada.
        UUID transferClaimId = AdminClaimSubMenuHandler.popPendingTransfer(id);
        if (transferClaimId != null) {
            // Se entrega el texto tal cual (solo sin caracteres de control): los prompts de texto
            // libre, como la bienvenida o el nombre de grupo, deben guardarse literales. El saneo de
            // nombres de jugador se hace en extractPlayerName, ya en cada manejador.
            String text = stripControlChars(rawMessage);
            markSuppressed(id, rawMessage);
            server.execute(() -> ClaimMenuHandler.dispatchAdminTransfer(sender, transferClaimId, text));
            return true;
        }

        ClaimMenuHandler.PendingChat prompt = ClaimMenuHandler.popPrompt(id);
        if (prompt != null) {
            String text = stripControlChars(rawMessage);
            markSuppressed(id, rawMessage);
            server.execute(() -> ClaimMenuHandler.dispatchPrompt(sender, prompt, text));
            return true;
        }

        return false;
    }

    /** Anota que el siguiente mensaje de este jugador con este texto no debe salir en el chat. */
    private static void markSuppressed(UUID playerId, String rawText) {
        suppressions.put(playerId, new Suppression(rawText, System.currentTimeMillis() + SUPPRESS_WINDOW_MS));
    }

    /**
     * ¿Este mensaje es una respuesta a un prompt que ya procesamos y por tanto no debe publicarse?
     *
     * <p>Se consulta desde sitios donde cancelar SI es seguro (el evento de Forge y el metodo de
     * publicacion), nunca desde el manejo del paquete.
     */
    public static boolean shouldSuppress(UUID playerId, String rawText) {
        if (playerId == null || rawText == null) {
            return false;
        }

        Suppression s = suppressions.get(playerId);
        if (s == null) {
            return false;
        }

        if (System.currentTimeMillis() > s.expiresAt()) {
            suppressions.remove(playerId, s);
            return false;
        }

        if (!s.rawText().equals(rawText)) {
            return false;
        }

        suppressions.remove(playerId, s);
        return true;
    }

    /** Limpieza al desconectar, para no dejar entradas colgadas. */
    public static void onPlayerDisconnect(UUID playerId) {
        if (playerId != null) {
            suppressions.remove(playerId);
        }
    }

    /** Marca que la captura por paquete esta operativa (lo llama el mixin la primera vez). */
    public static void markPacketCaptureActive() {
        if (!packetCaptureActive) {
            packetCaptureActive = true;
            ClaimBlocksMod.LOGGER.info(
                    "[ClaimBlocks] Captura de respuestas a nivel de paquete ACTIVA (compatible con Mohist y plugins de chat).");
        }
    }

    /**
     * Quita solo caracteres de control y recorta los extremos. Es la limpieza minima que se aplica a
     * TODOS los prompts, incluidos los de texto libre (bienvenida, mensaje de salida, nombre de
     * grupo), que deben conservar el texto tal y como lo escribio el jugador.
     */
    public static String stripControlChars(String raw) {
        if (raw == null) {
            return "";
        }

        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                out.append(' ');
            } else if (c >= ' ') {
                out.append(c);
            }
        }

        return out.toString().trim();
    }

    /**
     * Limpieza agresiva, <b>solo para nombres de jugador</b>: quita codigos de color legacy y
     * normaliza los espacios. Necesario porque en la ruta de respaldo un plugin de chat puede haber
     * inyectado formato en el mensaje.
     */
    public static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }

        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c == '\u00a7' || c == '&') && i + 1 < raw.length() && isColorCode(raw.charAt(i + 1))) {
                i++; // se salta el codigo de color completo
                continue;
            }
            if (c == '\n' || c == '\r' || c == '\t') {
                out.append(' ');
                continue;
            }
            if (c >= ' ') {
                out.append(c);
            }
        }

        return out.toString().trim().replaceAll("\\s{2,}", " ");
    }

    private static boolean isColorCode(char c) {
        return "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(c) >= 0;
    }

    /** ¿El jugador escribio "cancelar"/"cancel", o un comando? */
    public static boolean isCancel(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }

        String t = text.trim();
        return t.equalsIgnoreCase("cancelar") || t.equalsIgnoreCase("cancel") || t.startsWith("/");
    }

    /**
     * Saca un nombre de jugador de un texto que puede traer basura alrededor.
     *
     * <p>Con la captura por paquete el texto ya llega limpio, pero por la ruta de respaldo puede
     * venir como "[VIP] Pewez > nandaso3". Se devuelve el ultimo token que parece un nick valido de
     * Minecraft, que es el que el jugador acaba de escribir.
     */
    public static String extractPlayerName(String text) {
        String clean = sanitize(text);
        if (clean.isEmpty() || isValidName(clean)) {
            return clean;
        }

        String[] tokens = clean.split(" ");
        for (int i = tokens.length - 1; i >= 0; i--) {
            if (isValidName(tokens[i])) {
                return tokens[i];
            }
        }

        return clean;
    }

    private static boolean isValidName(String s) {
        if (s == null || s.length() < 3 || s.length() > 16) {
            return false;
        }

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
            if (!ok) {
                return false;
            }
        }

        return true;
    }
}
