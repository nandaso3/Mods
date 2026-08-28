package com.claimblocks;

import com.claimblocks.chat.ChatPromptRouter;
import com.claimblocks.command.ClaimAdminCommands;
import com.claimblocks.command.ClaimCommands;
import com.claimblocks.data.Claim;
import com.claimblocks.data.ClaimConfig;
import com.claimblocks.data.ClaimManager;
import com.claimblocks.data.GlobalFlags;
import com.claimblocks.event.BlockProtectionEvents;
import com.claimblocks.event.EntityProtectionEvents;
import com.claimblocks.event.PassiveEffectsManager;
import com.claimblocks.event.PlayerTracker;
import com.claimblocks.gui.AdminClaimSubMenuHandler;
import com.claimblocks.gui.ClaimMenuHandler;
import com.claimblocks.item.ClaimItems;
import com.claimblocks.net.ClaimBordersPacket;
import com.claimblocks.net.ClaimNetwork;
import com.claimblocks.render.ParticleBorder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

@Mod("claimblocks")
public class ClaimBlocksMod {
    public static final String MOD_ID = "claimblocks";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static int particleCounter = 0;
    private static final Map<UUID, Integer> lastBorderHash = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastBorderSend = new ConcurrentHashMap<>();
    private static final long BORDER_KEEPALIVE_MS = 2000L;

    private static int borderHash(List<double[]> list) {
        int i = 1;

        for (double[] adouble : list) {
            i = 31 * i + Arrays.hashCode(adouble);
        }

        return i;
    }

    public ClaimBlocksMod() {
        LOGGER.info("[FantasticClaims] Fantastic Claims v7.9.1 (Forge 1.20.1)...");
        IEventBus ieventbus = FMLJavaModLoadingContext.get().getModEventBus();
        ClaimItems.register(ieventbus);
        ClaimNetwork.init();
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new BlockProtectionEvents());
        MinecraftForge.EVENT_BUS.register(new EntityProtectionEvents());
        MinecraftForge.EVENT_BUS.register(new PlayerTracker());
        LOGGER.info("[FantasticClaims] Eventos, items y red registrados.");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent registercommandsevent) {
        ClaimCommands.register(registercommandsevent.getDispatcher());
        ClaimAdminCommands.register(registercommandsevent.getDispatcher());
        registerMergeCommand(registercommandsevent.getDispatcher());
    }

    private static void registerMergeCommand(CommandDispatcher<CommandSourceStack> commanddispatcher) {
        commanddispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("fsclaimmerge")
                        .then(Commands.literal("accept").then(Commands.argument("code", StringArgumentType.word()).executes(commandcontext -> {
                            ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
                            ClaimMenuHandler.acceptMerge(serverplayer, StringArgumentType.getString(commandcontext, "code"));
                            return 1;
                        }))))
                    .then(Commands.literal("reject").then(Commands.argument("code", StringArgumentType.word()).executes(commandcontext -> {
                        ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
                        ClaimMenuHandler.rejectMerge(serverplayer, StringArgumentType.getString(commandcontext, "code"));
                        return 1;
                    }))))
                .then(Commands.literal("leave").executes(commandcontext -> {
                    ServerPlayer serverplayer = ((CommandSourceStack)commandcontext.getSource()).getPlayerOrException();
                    ClaimMenuHandler.leaveMerge(serverplayer);
                    return 1;
                }))
        );
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent serverstartedevent) {
        ClaimManager.getInstance().load(serverstartedevent.getServer());
        GlobalFlags.getInstance().load(serverstartedevent.getServer());
        LOGGER.info("[FantasticClaims] Datos cargados.");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent serverstoppingevent) {
        ClaimManager.getInstance().saveNow();
        GlobalFlags.getInstance().save(serverstoppingevent.getServer());
        LOGGER.info("[FantasticClaims] Datos guardados al apagar.");
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerLoggedInEvent playerloggedinevent) {
        if (playerloggedinevent.getEntity() instanceof ServerPlayer serverplayer) {
            ClaimManager.getInstance().flushPendingTo(serverplayer);
        }
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerLoggedOutEvent playerloggedoutevent) {
        ClaimMenuHandler.clearPrompt(playerloggedoutevent.getEntity().getUUID());
        lastBorderHash.remove(playerloggedoutevent.getEntity().getUUID());
        lastBorderSend.remove(playerloggedoutevent.getEntity().getUUID());
        AdminClaimSubMenuHandler.clearPendingTransfer(playerloggedoutevent.getEntity().getUUID());
        ChatPromptRouter.onPlayerDisconnect(playerloggedoutevent.getEntity().getUUID());
        PlayerTracker.onDisconnect(playerloggedoutevent.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent servertickevent) {
        MinecraftServer minecraftserver;
        if (servertickevent.phase == Phase.END && (minecraftserver = ServerLifecycleHooks.getCurrentServer()) != null) {
            PlayerTracker.tick(minecraftserver);
            BlockProtectionEvents.tickFireSweep(minecraftserver);
            PassiveEffectsManager.tick(minecraftserver);
            if (++particleCounter % ClaimConfig.get().particleIntervalTicks == 0) {
                renderClaimParticles(minecraftserver);
            }

            if (particleCounter % ClaimConfig.get().borderIntervalTicks == 0) {
                sendBorderPackets(minecraftserver);
            }
        }
    }

    private static void sendBorderPackets(MinecraftServer minecraftserver) {
        for (ServerLevel serverlevel : minecraftserver.getAllLevels()) {
            String s = serverlevel.dimension().location().toString();

            for (ServerPlayer serverplayer : serverlevel.players()) {
                ArrayList arraylist = new ArrayList();
                HashSet hashset = new HashSet();
                HashSet hashset1 = new HashSet();
                Claim claim = ClaimManager.getInstance().getClaimAt(serverlevel, serverplayer.blockPosition());
                if (claim != null && claim.getFlags().showBorder && claim.canModify(serverplayer)) {
                    addBorder(arraylist, claim, serverplayer, s, hashset, hashset1);
                }

                for (Claim claim1 : ClaimManager.getInstance().getClaimsOf(serverplayer.getUUID())) {
                    if (claim1.getWorld().equals(s) && claim1.getFlags().showBorder && ParticleBorder.withinRenderRange(serverplayer, claim1)) {
                        addBorder(arraylist, claim1, serverplayer, s, hashset, hashset1);
                    }
                }

                int j = borderHash(arraylist);
                Integer integer = lastBorderHash.get(serverplayer.getUUID());
                long i = System.currentTimeMillis();
                Long olong = lastBorderSend.get(serverplayer.getUUID());
                boolean flag = integer == null || integer != j;
                boolean flag1 = olong == null || i - olong >= 2000L;
                if (flag || flag1) {
                    lastBorderHash.put(serverplayer.getUUID(), j);
                    lastBorderSend.put(serverplayer.getUUID(), i);
                    ClaimNetwork.sendTo(serverplayer, new ClaimBordersPacket(arraylist));
                }
            }
        }
    }

    private static void addBorder(
        ArrayList<double[]> arraylist, Claim claim, ServerPlayer serverplayer, String s, HashSet<UUID> hashset, HashSet<UUID> hashset1
    ) {
        if (claim.getGroupId() != null) {
            UUID uuid = claim.getGroupId();
            if (hashset1.contains(uuid)) {
                return;
            }

            hashset1.add(uuid);
            addGroupOutline(arraylist, uuid, serverplayer, s);
        } else {
            if (hashset.contains(claim.getClaimId())) {
                return;
            }

            hashset.add(claim.getClaimId());
            arraylist.add(boxOf(claim));
        }
    }

    private static void addGroupOutline(ArrayList<double[]> arraylist, UUID uuid, ServerPlayer serverplayer, String s) {
        ClaimManager claimmanager = ClaimManager.getInstance();
        Claim claim = claimmanager.getMotherClaim(uuid);
        if (claim != null) {
            ArrayList arraylist1 = new ArrayList();

            for (Claim claim1 : claimmanager.getGroupClaims(uuid)) {
                if (claim1.getWorld().equals(s)) {
                    arraylist1.add(claim1);
                }
            }

            if (!arraylist1.isEmpty()) {
                double d3 = (double)(claim.getY() - claim.getOwnHeight());
                double d0 = (double)(claim.getY() + claim.getOwnHeight() + 1);
                float f = 1.0F;
                float f1 = 1.0F;
                float f2 = 1.0F;
                if (claim.getTier() != null) {
                    f = claim.getTier().r;
                    f1 = claim.getTier().g;
                    f2 = claim.getTier().b;
                }

                int i = arraylist1.size();
                int[] aint = new int[i];
                int[] aint1 = new int[i];
                int[] aint2 = new int[i];
                int[] aint3 = new int[i];
                TreeSet<Integer> treeset = new TreeSet<>();
                TreeSet<Integer> treeset1 = new TreeSet<>();

                for (int j = 0; j < i; j++) {
                    Claim claim2 = (Claim)arraylist1.get(j);
                    int k = claim2.getRadius();
                    aint[j] = claim2.getX() - k;
                    aint1[j] = claim2.getX() + k + 1;
                    aint2[j] = claim2.getZ() - k;
                    aint3[j] = claim2.getZ() + k + 1;
                    treeset.add(aint[j]);
                    treeset.add(aint1[j]);
                    treeset1.add(aint2[j]);
                    treeset1.add(aint3[j]);
                }

                Integer[] ainteger = treeset.toArray(new Integer[0]);
                Integer[] ainteger1 = treeset1.toArray(new Integer[0]);
                int l1 = ainteger.length;
                int l = ainteger1.length;
                if (l1 >= 2 && l >= 2) {
                    boolean[][] aboolean = new boolean[l1 - 1][l - 1];

                    for (int i1 = 0; i1 < l1 - 1; i1++) {
                        double d1 = (double)(ainteger[i1] + ainteger[i1 + 1]) / 2.0;

                        for (int j1 = 0; j1 < l - 1; j1++) {
                            double d2 = (double)(ainteger1[j1] + ainteger1[j1 + 1]) / 2.0;
                            boolean flag1 = false;

                            for (int k1 = 0; k1 < i; k1++) {
                                if (d1 >= (double)aint[k1] && d1 < (double)aint1[k1] && d2 >= (double)aint2[k1] && d2 < (double)aint3[k1]) {
                                    flag1 = true;
                                    break;
                                }
                            }

                            aboolean[i1][j1] = flag1;
                        }
                    }

                    for (int i2 = 0; i2 < l1; i2++) {
                        int k2 = 0;

                        while (k2 < l - 1) {
                            boolean flag = i2 > 0 && aboolean[i2 - 1][k2];
                            boolean flag3 = i2 < l1 - 1 && aboolean[i2][k2];
                            if (flag != flag3) {
                                int i3 = k2;

                                while (k2 < l - 1 && (i2 > 0 && aboolean[i2 - 1][k2]) != (i2 < l1 - 1 && aboolean[i2][k2])) {
                                    k2++;
                                }

                                arraylist.add(
                                    new double[]{
                                        (double)ainteger[i2].intValue() - 0.03,
                                        d3,
                                        (double)ainteger1[i3].intValue(),
                                        (double)ainteger[i2].intValue() + 0.03,
                                        d0,
                                        (double)ainteger1[k2].intValue(),
                                        (double)f,
                                        (double)f1,
                                        (double)f2
                                    }
                                );
                            } else {
                                k2++;
                            }
                        }
                    }

                    for (int j2 = 0; j2 < l; j2++) {
                        int l2 = 0;

                        while (l2 < l1 - 1) {
                            boolean flag2 = j2 > 0 && aboolean[l2][j2 - 1];
                            boolean flag4 = j2 < l - 1 && aboolean[l2][j2];
                            if (flag2 != flag4) {
                                int j3 = l2;

                                while (l2 < l1 - 1 && (j2 > 0 && aboolean[l2][j2 - 1]) != (j2 < l - 1 && aboolean[l2][j2])) {
                                    l2++;
                                }

                                arraylist.add(
                                    new double[]{
                                        (double)ainteger[j3].intValue(),
                                        d3,
                                        (double)ainteger1[j2].intValue() - 0.03,
                                        (double)ainteger[l2].intValue(),
                                        d0,
                                        (double)ainteger1[j2].intValue() + 0.03,
                                        (double)f,
                                        (double)f1,
                                        (double)f2
                                    }
                                );
                            } else {
                                l2++;
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean covered(List<Claim> list, int i, int j) {
        for (Claim claim : list) {
            if (Math.abs(i - claim.getX()) <= claim.getRadius() && Math.abs(j - claim.getZ()) <= claim.getRadius()) {
                return true;
            }
        }

        return false;
    }

    private static double[] boxOf(Claim claim) {
        int i = claim.getRadius();
        int j = claim.getHeight();
        float f = 1.0F;
        float f1 = 1.0F;
        float f2 = 1.0F;
        if (claim.getTier() != null) {
            f = claim.getTier().r;
            f1 = claim.getTier().g;
            f2 = claim.getTier().b;
        }

        return new double[]{
            (double)(claim.getX() - i),
            (double)(claim.getY() - j),
            (double)(claim.getZ() - i),
            (double)(claim.getX() + i + 1),
            (double)(claim.getY() + j + 1),
            (double)(claim.getZ() + i + 1),
            (double)f,
            (double)f1,
            (double)f2
        };
    }

    private static void renderClaimParticles(MinecraftServer minecraftserver) {
        for (ServerLevel serverlevel : minecraftserver.getAllLevels()) {
            String s = serverlevel.dimension().location().toString();

            for (ServerPlayer serverplayer : serverlevel.players()) {
                HashSet hashset = new HashSet();
                Claim claim = ClaimManager.getInstance().getClaimAt(serverlevel, serverplayer.blockPosition());
                if (claim != null && claim.getFlags().showParticles && claim.canModify(serverplayer)) {
                    ParticleBorder.fillClaim(serverlevel, serverplayer, claim);
                    hashset.add(claim.getClaimId());
                }

                for (Claim claim1 : ClaimManager.getInstance().getClaimsOf(serverplayer.getUUID())) {
                    if (!hashset.contains(claim1.getClaimId())
                        && claim1.getFlags().showParticles
                        && claim1.getWorld().equals(s)
                        && ParticleBorder.withinRenderRange(serverplayer, claim1)) {
                        ParticleBorder.fillClaim(serverlevel, serverplayer, claim1);
                        hashset.add(claim1.getClaimId());
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent serverchatevent) {
        ClaimMenuHandler.handleChat(serverchatevent);
    }

    @SubscribeEvent
    public void onCommandEvent(CommandEvent commandevent) {
    }
}
