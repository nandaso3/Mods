package com.fsrecipes.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public final class Sfx {
   private Sfx() {
   }

   private static void play(SoundEvent sound, float pitch, float volume) {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null) {
         mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
      }
   }

   public static void click() {
      play((SoundEvent)SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.15F);
   }

   public static void select() {
   }

   public static void success() {
      play((SoundEvent)SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 0.2F);
   }
}
