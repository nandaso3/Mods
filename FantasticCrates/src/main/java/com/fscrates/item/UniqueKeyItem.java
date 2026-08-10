package com.fscrates.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class UniqueKeyItem extends Item {
    public UniqueKeyItem() {
        super(new Properties().stacksTo(64));
    }

    public static String keyName(ItemStack stack) {
        if (stack != null && stack.hasTag()) {
            CompoundTag root = stack.getTag().getCompound("fscrates");
            if (root.contains("keyName")) {
                return root.getString("keyName");
            }
        }

        return "";
    }

    public Component getName(ItemStack stack) {
        String n = keyName(stack);
        return n != null && !n.isBlank()
            ? Component.literal(n.replace('&', '\u00a7'))
            : Component.literal("\u2726 Llave de Crate \u2726").withStyle(ChatFormatting.AQUA);
    }

    public boolean isFoil(ItemStack stack) {
        return true;
    }

    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String crateName = "";
        String crateId = "";
        if (stack != null && stack.hasTag()) {
            CompoundTag root = stack.getTag().getCompound("fscrates");
            crateName = root.getString("crateName");
            crateId = root.getString("crateId");
        }

        String show = crateName != null && !crateName.isBlank() ? crateName.replace('&', '\u00a7') : crateId;
        tooltip.add(Component.literal("\u00a77Esta llave abre: \u00a7r" + show));
    }
}
