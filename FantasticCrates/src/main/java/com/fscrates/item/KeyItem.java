package com.fscrates.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class KeyItem extends Item {
    public KeyItem() {
        super(new Properties().stacksTo(64));
    }

    public Component getName(ItemStack stack) {
        return Component.literal("\u2726 Fantastic Key \u2726").withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    public boolean isFoil(ItemStack stack) {
        return true;
    }

    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Abre cualquier crate.").withStyle(ChatFormatting.GRAY));
        tooltip.add(
            Component.literal("La fortuna favorece a los valientes.").withStyle(new ChatFormatting[]{ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC})
        );
    }
}
