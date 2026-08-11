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

   public ProtectionItem(ClaimTier tier) {
      super(new Properties().stacksTo(64));
      this.tier = tier;
   }

   public boolean isFoil(ItemStack stack) {
      return true;
   }

   public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
      ChatFormatting color = ClaimBlocks.colorForTier(this.tier);
      tooltip.add(Component.literal("Radio: " + this.tier.radius + " · Altura: ±" + this.tier.height).withStyle(ChatFormatting.GRAY));
      tooltip.add(Component.literal("Coloca para crear una protección").withStyle(color));
   }
}
