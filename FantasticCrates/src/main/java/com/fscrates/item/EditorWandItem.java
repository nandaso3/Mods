package com.fscrates.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class EditorWandItem extends Item {
    public EditorWandItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    public Component getName(ItemStack stack) {
        return Component.literal("\u2726 Varita del Editor \u2726").withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    public boolean isFoil(ItemStack stack) {
        return true;
    }

    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("\u00dasala en un cofre para editarlo.").withStyle(ChatFormatting.GRAY));
    }
}
