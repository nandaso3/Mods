package com.fscrates.crate;

import com.fscrates.config.CrateConfig;
import com.fscrates.config.Rarity;
import com.fscrates.config.RewardEntry;
import com.fscrates.item.CrateItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class LootEngine {
    private LootEngine() {
    }

    public static List<RewardEntry> roll(CrateConfig crate, Random random) {
        ArrayList<RewardEntry> result = new ArrayList<>();

        for (RewardEntry r : crate.rewards) {
            if (r.guaranteed) {
                result.add(r);
            }
        }

        for (int i = 0; i < Math.max(1, crate.rolls); i++) {
            Rarity rolled = crate.rollRarity(random);
            ArrayList<RewardEntry> pool = new ArrayList<>();
            double total = 0.0;

            for (RewardEntry rx : crate.rewards) {
                if (!rx.guaranteed && rx.effectiveRarity(crate.rarity) == rolled) {
                    pool.add(rx);
                    total += Math.max(0.0, rx.chance);
                }
            }

            if (pool.isEmpty()) {
                for (RewardEntry rxx : crate.rewards) {
                    if (!rxx.guaranteed) {
                        pool.add(rxx);
                        total += Math.max(0.0, rxx.chance);
                    }
                }
            }

            if (!pool.isEmpty()) {
                RewardEntry chosen = null;
                if (total > 0.0) {
                    double pick = random.nextDouble() * total;
                    double cursor = 0.0;

                    for (RewardEntry rxxx : pool) {
                        cursor += Math.max(0.0, rxxx.chance);
                        if (pick < cursor) {
                            chosen = rxxx;
                            break;
                        }
                    }
                }

                if (chosen == null) {
                    chosen = pool.get(random.nextInt(pool.size()));
                }

                result.add(chosen);
            }
        }

        return result;
    }

    public static List<RewardEntry> poolFor(CrateConfig crate, Rarity rarity) {
        ArrayList<RewardEntry> pool = new ArrayList<>();

        for (RewardEntry r : crate.rewards) {
            if (!r.guaranteed && r.effectiveRarity(crate.rarity) == rarity) {
                pool.add(r);
            }
        }

        return pool;
    }

    public static List<RewardEntry> poolForDisplay(CrateConfig crate, Rarity rarity) {
        ArrayList<RewardEntry> pool = new ArrayList<>();

        for (RewardEntry r : crate.rewards) {
            if (r.effectiveRarity(crate.rarity) == rarity) {
                pool.add(r);
            }
        }

        return pool;
    }

    public static Rarity resolveRarityWithItems(CrateConfig crate, Rarity rarity) {
        if (!poolFor(crate, rarity).isEmpty()) {
            return rarity;
        } else {
            Rarity[] all = Rarity.values();
            int base = rarity.ordinal();

            for (int d = 1; d < all.length; d++) {
                int hi = base + d;
                int lo = base - d;
                if (hi < all.length && !poolFor(crate, all[hi]).isEmpty()) {
                    return all[hi];
                }

                if (lo >= 0 && !poolFor(crate, all[lo]).isEmpty()) {
                    return all[lo];
                }
            }

            return rarity;
        }
    }

    public static RewardEntry pickFromPool(CrateConfig crate, Rarity rarity, Random random) {
        ArrayList<RewardEntry> pool = new ArrayList<>();
        double total = 0.0;

        for (RewardEntry r : crate.rewards) {
            if (!r.guaranteed && r.effectiveRarity(crate.rarity) == rarity) {
                pool.add(r);
                total += Math.max(0.0, r.chance);
            }
        }

        if (pool.isEmpty()) {
            for (RewardEntry rx : crate.rewards) {
                if (!rx.guaranteed) {
                    pool.add(rx);
                    total += Math.max(0.0, rx.chance);
                }
            }
        }

        if (pool.isEmpty()) {
            return null;
        } else {
            if (total > 0.0) {
                double pick = random.nextDouble() * total;
                double cursor = 0.0;

                for (RewardEntry rxx : pool) {
                    cursor += Math.max(0.0, rxx.chance);
                    if (pick < cursor) {
                        return rxx;
                    }
                }
            }

            return pool.get(random.nextInt(pool.size()));
        }
    }

    public static void deliver(ServerPlayer player, CrateConfig crate, List<RewardEntry> rolled) {
        ServerLevel level = player.serverLevel();
        Random random = new Random();

        for (RewardEntry r : rolled) {
            int amount = r.minAmount + (r.maxAmount > r.minAmount ? random.nextInt(r.maxAmount - r.minAmount + 1) : 0);
            amount = Math.max(1, amount);
            switch (r.type) {
                case ITEM:
                    giveItem(player, r.item, amount);
                    break;
                case KEY:
                    giveItem(player, CrateItems.buildKey(), amount);
                    break;
                case XP:
                    player.giveExperiencePoints(r.xp * amount);
                    break;
                case EFFECT:
                    applyEffect(player, r);
            }
        }

        if (crate.broadcast && level.getServer() != null) {
            String rewards = rolled.isEmpty() ? "nada" : rolled.get(rolled.size() - 1).describe();
            level.getServer()
                .getPlayerList()
                .broadcastSystemMessage(
                    Component.literal(
                        "\u00a7d[Crates] \u00a7f"
                            + player.getName().getString()
                            + " abri\u00f3 "
                            + colorize(crate.displayName)
                            + "\u00a7r\u00a7f y obtuvo \u00a7e"
                            + rewards
                    ),
                    false
                );
        }
    }

    public static String colorize(String s) {
        if (s != null && s.indexOf(38) >= 0) {
            char[] c = s.toCharArray();

            for (int i = 0; i < c.length - 1; i++) {
                if (c[i] == '&' && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(c[i + 1]) >= 0) {
                    c[i] = 167;
                }
            }

            return new String(c);
        } else {
            return s;
        }
    }

    private static void giveItem(ServerPlayer player, ItemStack template, int amount) {
        if (template != null && !template.isEmpty()) {
            int max = template.getMaxStackSize();
            int remaining = amount;

            while (remaining > 0) {
                int take = Math.min(remaining, max);
                ItemStack stack = template.copy();
                stack.setCount(take);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }

                remaining -= take;
            }
        }
    }

    private static void applyEffect(ServerPlayer player, RewardEntry r) {
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(safe(r.effectId));
        if (effect != null) {
            player.addEffect(new MobEffectInstance(effect, Math.max(1, r.effectDuration), Math.max(0, r.effectAmplifier)));
        }
    }

    private static ResourceLocation safe(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id == null ? "" : id);
        return rl == null ? new ResourceLocation("minecraft", "luck") : rl;
    }
}
