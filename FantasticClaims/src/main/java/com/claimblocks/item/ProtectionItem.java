package com.claimblocks.item;

import com.claimblocks.ClaimBlocks;
import com.claimblocks.data.ClaimTier;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

public class ProtectionItem extends Item {
    public final ClaimTier tier;

    public ProtectionItem(ClaimTier claimtier) {
        super(new Properties().stacksTo(64));
        this.tier = claimtier;
    }

    public boolean isFoil(ItemStack itemstack) {
        return true;
    }

    public void appendHoverText(ItemStack itemstack, Level level, List<Component> list, TooltipFlag tooltipflag) {
        ChatFormatting chatformatting = ClaimBlocks.colorForTier(this.tier);
        list.add(Component.literal("Radio: " + this.tier.radius + " · Altura: ±" + this.tier.height).withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("Coloca para crear una protección").withStyle(chatformatting));
    }
}
