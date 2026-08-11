package com.claimblocks;

import com.claimblocks.chat.ChatPromptRouter;
import com.claimblocks.command.ClaimAdminCommands;
import com.claimblocks.command.ClaimCommands;
import com.claimblocks.data.Claim;
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
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
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

   public ClaimBlocksMod() {
      LOGGER.info("[ClaimBlocks] Inicializando v7.7.0 (Forge 1.20.1)...");
      IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
      ClaimItems.register(modBus);
      ClaimNetwork.init();
      MinecraftForge.EVENT_BUS.register(this);
      MinecraftForge.EVENT_BUS.register(new BlockProtectionEvents());
      MinecraftForge.EVENT_BUS.register(new EntityProtectionEvents());
      MinecraftForge.EVENT_BUS.register(new PlayerTracker());
      LOGGER.info("[ClaimBlocks] Eventos, items y red registrados.");
   }

   @SubscribeEvent
   public void onRegisterCommands(RegisterCommandsEvent event) {
      ClaimCommands.register(event.getDispatcher());
      ClaimAdminCommands.register(event.getDispatcher());
      registerMergeCommand(event.getDispatcher());
   }

   private static void registerMergeCommand(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("claimmerge")
                  .then(Commands.literal("accept").then(Commands.argument("code", StringArgumentType.word()).executes(ctx -> {
                     ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                     ClaimMenuHandler.acceptMerge(p, StringArgumentType.getString(ctx, "code"));
                     return 1;
                  }))))
               .then(Commands.literal("reject").then(Commands.argument("code", StringArgumentType.word()).executes(ctx -> {
                  ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                  ClaimMenuHandler.rejectMerge(p, StringArgumentType.getString(ctx, "code"));
                  return 1;
               }))))
            .then(Commands.literal("leave").executes(ctx -> {
               ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
               ClaimMenuHandler.leaveMerge(p);
               return 1;
            }))
      );
   }

   @SubscribeEvent
   public void onServerStarted(ServerStartedEvent event) {
      ClaimManager.getInstance().load(event.getServer());
      GlobalFlags.getInstance().load(event.getServer());
      LOGGER.info("[ClaimBlocks] Datos cargados.");
   }

   @SubscribeEvent
   public void onServerStopping(ServerStoppingEvent event) {
      ClaimManager.getInstance().save();
      GlobalFlags.getInstance().save(event.getServer());
      LOGGER.info("[ClaimBlocks] Datos guardados al apagar.");
   }

   @SubscribeEvent
   public void onPlayerJoin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer sp) {
         ClaimManager.getInstance().flushPendingTo(sp);
      }
   }

   @SubscribeEvent
   public void onPlayerLeave(PlayerLoggedOutEvent event) {
      // Se descarta cualquier prompt de menu a medias para que no reviva en la siguiente sesion.
      ClaimMenuHandler.clearPrompt(event.getEntity().getUUID());
      AdminClaimSubMenuHandler.clearPendingTransfer(event.getEntity().getUUID());
      ChatPromptRouter.onPlayerDisconnect(event.getEntity().getUUID());
      PlayerTracker.onDisconnect(event.getEntity().getUUID());
   }

   @SubscribeEvent
   public void onServerTick(ServerTickEvent event) {
      MinecraftServer server;
      if (event.phase == Phase.END && (server = ServerLifecycleHooks.getCurrentServer()) != null) {
         PlayerTracker.tick(server);
         BlockProtectionEvents.tickFireSweep(server);
         PassiveEffectsManager.tick(server);
         if (++particleCounter % 4 == 0) {
            renderClaimParticles(server);
         }

         if (particleCounter % 20 == 0) {
            sendBorderPackets(server);
         }
      }
   }

   private static void sendBorderPackets(MinecraftServer server) {
      for (ServerLevel level : server.getAllLevels()) {
         String dim = level.dimension().location().toString();

         for (ServerPlayer player : level.players()) {
            ArrayList<double[]> boxes = new ArrayList<>();
            HashSet<UUID> doneClaims = new HashSet<>();
            HashSet<UUID> doneGroups = new HashSet<>();
            Claim here = ClaimManager.getInstance().getClaimAt(level, player.blockPosition());
            if (here != null && here.getFlags().showBorder && here.canModify(player)) {
               addBorder(boxes, here, player, dim, doneClaims, doneGroups);
            }

            for (Claim owned : ClaimManager.getInstance().getClaimsOf(player.getUUID())) {
               if (owned.getWorld().equals(dim) && owned.getFlags().showBorder && ParticleBorder.withinRenderRange(player, owned)) {
                  addBorder(boxes, owned, player, dim, doneClaims, doneGroups);
               }
            }

            ClaimNetwork.sendTo(player, new ClaimBordersPacket(boxes));
         }
      }
   }

   private static void addBorder(ArrayList<double[]> boxes, Claim claim, ServerPlayer player, String dim, HashSet<UUID> doneClaims, HashSet<UUID> doneGroups) {
      if (claim.getGroupId() != null) {
         UUID gid = claim.getGroupId();
         if (doneGroups.contains(gid)) {
            return;
         }

         doneGroups.add(gid);
         addGroupOutline(boxes, gid, player, dim);
      } else {
         if (doneClaims.contains(claim.getClaimId())) {
            return;
         }

         doneClaims.add(claim.getClaimId());
         boxes.add(boxOf(claim));
      }
   }

   private static void addGroupOutline(ArrayList<double[]> boxes, UUID gid, ServerPlayer player, String dim) {
      ClaimManager mgr = ClaimManager.getInstance();
      Claim mother = mgr.getMotherClaim(gid);
      if (mother != null) {
         List<Claim> gc = new ArrayList<>();

         for (Claim c : mgr.getGroupClaims(gid)) {
            if (c.getWorld().equals(dim)) {
               gc.add(c);
            }
         }

         if (!gc.isEmpty()) {
            double minY = (double)(mother.getY() - mother.getOwnHeight());
            double maxY = (double)(mother.getY() + mother.getOwnHeight() + 1);
            float cr = 1.0F;
            float cg = 1.0F;
            float cb = 1.0F;
            if (mother.getTier() != null) {
               cr = mother.getTier().r;
               cg = mother.getTier().g;
               cb = mother.getTier().b;
            }

            int n = gc.size();
            int[] rx1 = new int[n];
            int[] rx2 = new int[n];
            int[] rz1 = new int[n];
            int[] rz2 = new int[n];
            TreeSet<Integer> xsSet = new TreeSet<>();
            TreeSet<Integer> zsSet = new TreeSet<>();

            for (int i = 0; i < n; i++) {
               Claim cx = gc.get(i);
               int r = cx.getRadius();
               rx1[i] = cx.getX() - r;
               rx2[i] = cx.getX() + r + 1;
               rz1[i] = cx.getZ() - r;
               rz2[i] = cx.getZ() + r + 1;
               xsSet.add(rx1[i]);
               xsSet.add(rx2[i]);
               zsSet.add(rz1[i]);
               zsSet.add(rz2[i]);
            }

            Integer[] xs = xsSet.toArray(new Integer[0]);
            Integer[] zs = zsSet.toArray(new Integer[0]);
            int nx = xs.length;
            int nz = zs.length;
            if (nx >= 2 && nz >= 2) {
               boolean[][] cov = new boolean[nx - 1][nz - 1];

               for (int i = 0; i < nx - 1; i++) {
                  double cxm = (double)(xs[i] + xs[i + 1]) / 2.0;

                  for (int j = 0; j < nz - 1; j++) {
                     double czm = (double)(zs[j] + zs[j + 1]) / 2.0;
                     boolean cx = false;

                     for (int k = 0; k < n; k++) {
                        if (cxm >= (double)rx1[k] && cxm < (double)rx2[k] && czm >= (double)rz1[k] && czm < (double)rz2[k]) {
                           cx = true;
                           break;
                        }
                     }

                     cov[i][j] = cx;
                  }
               }

               for (int i = 0; i < nx; i++) {
                  int j = 0;

                  while (j < nz - 1) {
                     boolean left = i > 0 && cov[i - 1][j];
                     boolean right = i < nx - 1 && cov[i][j];
                     if (left != right) {
                        int j0 = j;

                        while (j < nz - 1 && (i > 0 && cov[i - 1][j]) != (i < nx - 1 && cov[i][j])) {
                           j++;
                        }

                        boxes.add(
                           new double[]{
                              (double)xs[i].intValue() - 0.03,
                              minY,
                              (double)zs[j0].intValue(),
                              (double)xs[i].intValue() + 0.03,
                              maxY,
                              (double)zs[j].intValue(),
                              (double)cr,
                              (double)cg,
                              (double)cb
                           }
                        );
                     } else {
                        j++;
                     }
                  }
               }

               for (int j = 0; j < nz; j++) {
                  int i = 0;

                  while (i < nx - 1) {
                     boolean below = j > 0 && cov[i][j - 1];
                     boolean above = j < nz - 1 && cov[i][j];
                     if (below != above) {
                        int i0 = i;

                        while (i < nx - 1 && (j > 0 && cov[i][j - 1]) != (j < nz - 1 && cov[i][j])) {
                           i++;
                        }

                        boxes.add(
                           new double[]{
                              (double)xs[i0].intValue(),
                              minY,
                              (double)zs[j].intValue() - 0.03,
                              (double)xs[i].intValue(),
                              maxY,
                              (double)zs[j].intValue() + 0.03,
                              (double)cr,
                              (double)cg,
                              (double)cb
                           }
                        );
                     } else {
                        i++;
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean covered(List<Claim> gc, int x, int z) {
      for (Claim c : gc) {
         if (Math.abs(x - c.getX()) <= c.getRadius() && Math.abs(z - c.getZ()) <= c.getRadius()) {
            return true;
         }
      }

      return false;
   }

   private static double[] boxOf(Claim claim) {
      int r = claim.getRadius();
      int h = claim.getHeight();
      float cr = 1.0F;
      float cg = 1.0F;
      float cb = 1.0F;
      if (claim.getTier() != null) {
         cr = claim.getTier().r;
         cg = claim.getTier().g;
         cb = claim.getTier().b;
      }

      return new double[]{
         (double)(claim.getX() - r),
         (double)(claim.getY() - h),
         (double)(claim.getZ() - r),
         (double)(claim.getX() + r + 1),
         (double)(claim.getY() + h + 1),
         (double)(claim.getZ() + r + 1),
         (double)cr,
         (double)cg,
         (double)cb
      };
   }

   private static void renderClaimParticles(MinecraftServer server) {
      for (ServerLevel level : server.getAllLevels()) {
         String dim = level.dimension().location().toString();

         for (ServerPlayer player : level.players()) {
            HashSet<UUID> rendered = new HashSet<>();
            Claim here = ClaimManager.getInstance().getClaimAt(level, player.blockPosition());
            if (here != null && here.getFlags().showParticles && here.canModify(player)) {
               ParticleBorder.fillClaim(level, player, here);
               rendered.add(here.getClaimId());
            }

            for (Claim owned : ClaimManager.getInstance().getClaimsOf(player.getUUID())) {
               if (!rendered.contains(owned.getClaimId())
                  && owned.getFlags().showParticles
                  && owned.getWorld().equals(dim)
                  && ParticleBorder.withinRenderRange(player, owned)) {
                  ParticleBorder.fillClaim(level, player, owned);
                  rendered.add(owned.getClaimId());
               }
            }
         }
      }
   }

   @SubscribeEvent
   public void onServerChat(ServerChatEvent event) {
      ClaimMenuHandler.handleChat(event);
   }

   @SubscribeEvent
   public void onCommandEvent(CommandEvent var1) {
   }
}
