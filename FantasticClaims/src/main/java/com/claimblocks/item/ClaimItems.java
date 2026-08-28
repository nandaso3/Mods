package com.claimblocks.item;

import com.claimblocks.data.ClaimTier;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ClaimItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "claimblocks");
    private static final Map<String, RegistryObject<Item>> BY_TIER = new HashMap<>();

    private ClaimItems() {
    }

    public static void register(IEventBus ieventbus) {
        ITEMS.register(ieventbus);
    }

    public static Item itemFor(ClaimTier claimtier) {
        if (claimtier == null) {
            return null;
        } else {
            RegistryObject registryobject = BY_TIER.get(claimtier.id);
            return registryobject != null && registryobject.isPresent() ? (Item)registryobject.get() : null;
        }
    }

    public static String registryName(ClaimTier claimtier) {
        return claimtier == null ? "" : "claimblocks:proteccion_" + claimtier.label().toLowerCase(Locale.ROOT);
    }

    static {
        for (ClaimTier claimtier : ClaimTier.VALUES) {
            String s = "proteccion_" + claimtier.label().toLowerCase(Locale.ROOT);
            BY_TIER.put(claimtier.id, ITEMS.register(s, () -> new ProtectionItem(claimtier)));
        }
    }
}
