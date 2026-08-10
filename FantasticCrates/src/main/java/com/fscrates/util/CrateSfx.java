package com.fscrates.util;

import com.fscrates.config.Rarity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public final class CrateSfx {
    private CrateSfx() {
    }

    private static SoundEvent wailA() {
        return (SoundEvent)SoundEvents.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS.value();
    }

    private static SoundEvent wailM() {
        return (SoundEvent)SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value();
    }

    private static SoundEvent witherGroan() {
        return SoundEvents.WITHER_AMBIENT;
    }

    private static SoundEvent witherDeath() {
        return SoundEvents.WITHER_DEATH;
    }

    private static SoundEvent soulEscape() {
        return SoundEvents.SOUL_ESCAPE;
    }

    public static void unlock(CrateSfx.Sink s, Rarity r) {
        switch (r) {
            case COMMON:
                s.play(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.8F, 1.35F);
                s.play(SoundEvents.BEACON_POWER_SELECT, 0.7F, 1.25F);
                s.play(wailM(), 0.7F, 1.0F);
                break;
            case RARE:
                s.play(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.85F, 1.2F);
                s.play(SoundEvents.BEACON_POWER_SELECT, 0.7F, 1.15F);
                s.play(SoundEvents.ENCHANTMENT_TABLE_USE, 0.6F, 1.1F);
                s.play(wailM(), 0.75F, 1.0F);
                break;
            case EPIC:
                s.play(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.9F, 1.05F);
                s.play(SoundEvents.BEACON_ACTIVATE, 0.7F, 1.15F);
                s.play(SoundEvents.CONDUIT_ACTIVATE, 0.6F, 1.2F);
                s.play(wailA(), 0.75F, 1.0F);
                break;
            case LEGENDARY:
                s.play(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.9F, 0.95F);
                s.play(SoundEvents.BEACON_ACTIVATE, 0.8F, 1.0F);
                s.play(SoundEvents.CONDUIT_ACTIVATE, 0.7F, 1.1F);
                s.play(SoundEvents.WARDEN_SONIC_CHARGE, 0.5F, 1.3F);
                s.play(wailA(), 0.8F, 0.95F);
                break;
            case MYTHIC:
                s.play(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.9F, 0.8F);
                s.play(SoundEvents.CONDUIT_ACTIVATE, 0.8F, 0.9F);
                s.play(SoundEvents.END_PORTAL_FRAME_FILL, 0.8F, 0.9F);
                s.play(SoundEvents.WARDEN_SONIC_CHARGE, 0.6F, 1.1F);
                s.play(wailA(), 0.85F, 0.9F);
        }
    }

    public static void spiralCharge(CrateSfx.Sink s, Rarity r) {
        switch (r) {
            case COMMON:
                s.play(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.55F, 1.4F);
                break;
            case RARE:
                s.play(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.6F, 1.25F);
                s.play(SoundEvents.BEACON_AMBIENT, 0.5F, 1.2F);
                break;
            case EPIC:
                s.play(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.65F, 1.1F);
                s.play(SoundEvents.CONDUIT_AMBIENT, 0.55F, 1.1F);
                break;
            case LEGENDARY:
                s.play(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.7F, 1.0F);
                s.play(SoundEvents.WARDEN_SONIC_CHARGE, 0.45F, 1.0F);
                break;
            case MYTHIC:
                s.play(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.7F, 0.85F);
                s.play(SoundEvents.WARDEN_SONIC_CHARGE, 0.55F, 0.9F);
                s.play(SoundEvents.CONDUIT_AMBIENT, 0.5F, 0.9F);
        }
    }

    public static void spiralRise(CrateSfx.Sink s, Rarity r, float p) {
        float vol = Math.min(1.0F, 0.5F + p * 0.5F);
        switch (r) {
            case COMMON:
                s.play(SoundEvents.BEACON_POWER_SELECT, vol * 0.85F, Math.min(1.6F, 0.6F + p * 1.0F));
                break;
            case RARE:
                s.play(SoundEvents.BEACON_POWER_SELECT, vol * 0.85F, Math.min(1.55F, 0.6F + p * 0.95F));
                if (p > 0.55F) {
                    s.play(SoundEvents.CONDUIT_AMBIENT, vol * 0.35F, 0.9F + p * 0.4F);
                }
                break;
            case EPIC:
                s.play(SoundEvents.BEACON_POWER_SELECT, vol, Math.min(1.5F, 0.55F + p * 1.0F));
                if (p > 0.4F) {
                    s.play(SoundEvents.CONDUIT_ACTIVATE, vol * 0.3F, 0.7F + p * 0.5F);
                }
                break;
            case LEGENDARY:
                s.play(SoundEvents.BEACON_POWER_SELECT, vol, Math.min(1.4F, 0.45F + p * 0.9F));
                if (p > 0.4F) {
                    s.play(SoundEvents.WARDEN_SONIC_CHARGE, 0.2F + p * 0.3F, 0.7F + p * 0.45F);
                }
                break;
            case MYTHIC:
                s.play(SoundEvents.BEACON_POWER_SELECT, vol, Math.min(1.25F, 0.35F + p * 0.85F));
                if (p > 0.3F) {
                    s.play(SoundEvents.WARDEN_SONIC_CHARGE, 0.25F + p * 0.35F, 0.6F + p * 0.5F);
                }

                if (p > 0.75F) {
                    s.play(SoundEvents.CONDUIT_ACTIVATE, vol * 0.4F, 0.9F + p * 0.3F);
                }
        }
    }

    public static void spiralPeak(CrateSfx.Sink s, Rarity r) {
        s.play(wailA(), 0.6F, 0.6F);
        s.play(wailM(), 0.5F, 0.72F);
        s.play(soulEscape(), 0.5F, 0.8F);
        switch (r) {
            case COMMON:
                s.play(SoundEvents.BEACON_ACTIVATE, 0.8F, 1.5F);
                s.play(SoundEvents.CONDUIT_ACTIVATE, 0.6F, 1.6F);
                break;
            case RARE:
                s.play(SoundEvents.BEACON_ACTIVATE, 0.85F, 1.35F);
                s.play(SoundEvents.CONDUIT_ACTIVATE, 0.7F, 1.4F);
                s.play(SoundEvents.WARDEN_SONIC_CHARGE, 0.5F, 1.4F);
                break;
            case EPIC:
                s.play(SoundEvents.BEACON_ACTIVATE, 0.9F, 1.2F);
                s.play(SoundEvents.CONDUIT_ACTIVATE, 0.75F, 1.3F);
                s.play(SoundEvents.WARDEN_SONIC_CHARGE, 0.6F, 1.3F);
                s.play(wailA(), 0.7F, 0.9F);
                break;
            case LEGENDARY:
                s.play(SoundEvents.BEACON_ACTIVATE, 0.9F, 1.05F);
                s.play(SoundEvents.WARDEN_SONIC_CHARGE, 0.75F, 1.15F);
                s.play(SoundEvents.CONDUIT_ACTIVATE, 0.7F, 1.2F);
                s.play(wailA(), 0.75F, 0.85F);
                break;
            case MYTHIC:
                s.play(SoundEvents.WARDEN_SONIC_CHARGE, 0.85F, 1.0F);
                s.play(SoundEvents.BEACON_ACTIVATE, 0.8F, 0.95F);
                s.play(SoundEvents.CONDUIT_ACTIVATE, 0.7F, 1.1F);
                s.play(wailA(), 0.8F, 0.8F);
        }
    }

    public static void openAccent(CrateSfx.Sink s, Rarity r) {
        s.play(wailA(), 0.72F, 0.55F);
        s.play(wailM(), 0.6F, 0.68F);
        s.play(wailA(), 0.5F, 1.5F);
        s.play(wailM(), 0.58F, 0.9F);
        s.play(soulEscape(), 0.55F, 0.72F);
        switch (r) {
            case COMMON:
                s.play(SoundEvents.WARDEN_SONIC_BOOM, 0.9F, 1.5F);
                s.play(SoundEvents.WARDEN_SONIC_BOOM, 0.6F, 1.05F);
                s.play(SoundEvents.BEACON_ACTIVATE, 1.0F, 1.2F);
                s.play(SoundEvents.CONDUIT_ACTIVATE, 0.9F, 1.3F);
                s.play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 1.4F);
                s.play(wailM(), 0.85F, 1.0F);
                s.play(witherGroan(), 0.28F, 0.7F);
                s.play(soulEscape(), 0.6F, 1.05F);
                s.play(wailA(), 0.7F, 1.1F);
                s.play(wailM(), 0.6F, 1.3F);
                break;
            case RARE:
                s.play(SoundEvents.WARDEN_SONIC_BOOM, 1.0F, 1.3F);
                s.play(SoundEvents.WARDEN_SONIC_BOOM, 0.7F, 0.9F);
                s.play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.9F, 1.25F);
                s.play(SoundEvents.CONDUIT_ACTIVATE, 0.95F, 1.15F);
                s.play(SoundEvents.TRIDENT_THUNDER, 0.85F, 1.3F);
                s.play(SoundEvents.END_PORTAL_SPAWN, 0.55F, 1.35F);
                s.play(wailA(), 0.85F, 0.95F);
                s.play(witherGroan(), 0.3F, 0.68F);
                s.play(soulEscape(), 0.65F, 1.0F);
                s.play(wailM(), 0.65F, 1.15F);
                s.play(wailA(), 0.6F, 1.3F);
                s.play(soulEscape(), 0.45F, 0.85F);
                break;
            case EPIC:
                s.play(SoundEvents.WARDEN_SONIC_BOOM, 1.0F, 1.1F);
                s.play(SoundEvents.WARDEN_SONIC_BOOM, 0.78F, 0.82F);
                s.play(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0F, 1.1F);
                s.play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.7F, 0.8F);
                s.play(SoundEvents.TRIDENT_THUNDER, 0.9F, 1.2F);
                s.play(SoundEvents.BEACON_ACTIVATE, 0.9F, 1.25F);
                s.play(SoundEvents.END_PORTAL_SPAWN, 0.72F, 1.3F);
                s.play(wailA(), 0.9F, 0.9F);
                s.play(witherGroan(), 0.32F, 0.66F);
                s.play(soulEscape(), 0.7F, 0.95F);
                s.play(wailM(), 0.7F, 1.2F);
                s.play(wailA(), 0.62F, 1.35F);
                s.play(soulEscape(), 0.5F, 0.8F);
                break;
            case LEGENDARY:
                s.play(SoundEvents.WARDEN_SONIC_BOOM, 1.0F, 0.95F);
                s.play(SoundEvents.WARDEN_SONIC_BOOM, 0.85F, 0.68F);
                s.play(SoundEvents.WITHER_SPAWN, 0.9F, 0.95F);
                s.play(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0F, 1.0F);
                s.play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8F, 0.72F);
                s.play(SoundEvents.TRIDENT_THUNDER, 0.95F, 1.1F);
                s.play(SoundEvents.END_PORTAL_SPAWN, 0.82F, 1.1F);
                s.play(wailA(), 0.9F, 0.85F);
                s.play(witherGroan(), 0.35F, 0.64F);
                s.play(soulEscape(), 0.72F, 0.9F);
                s.play(wailM(), 0.75F, 1.2F);
                s.play(wailA(), 0.65F, 1.35F);
                s.play(soulEscape(), 0.52F, 0.78F);
                break;
            case MYTHIC:
                s.play(SoundEvents.WARDEN_SONIC_BOOM, 1.0F, 0.75F);
                s.play(SoundEvents.WARDEN_SONIC_BOOM, 0.92F, 0.55F);
                s.play(SoundEvents.WITHER_SPAWN, 1.0F, 0.9F);
                s.play(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0F, 0.85F);
                s.play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.85F, 0.6F);
                s.play(SoundEvents.END_PORTAL_SPAWN, 0.9F, 0.9F);
                s.play(SoundEvents.TRIDENT_THUNDER, 0.9F, 1.0F);
                s.play(SoundEvents.CONDUIT_ACTIVATE, 0.85F, 1.1F);
                s.play(wailA(), 0.95F, 0.9F);
                s.play(witherGroan(), 0.4F, 0.62F);
                s.play(soulEscape(), 0.75F, 0.85F);
                s.play(wailM(), 0.8F, 1.25F);
                s.play(wailA(), 0.7F, 1.4F);
                s.play(soulEscape(), 0.55F, 0.75F);
                s.play(wailM(), 0.6F, 1.45F);
        }
    }

    public static void openSustain(CrateSfx.Sink s, Rarity r, float p) {
        float v = 0.55F + p * 0.4F;
        s.play(SoundEvents.CONDUIT_AMBIENT, v * 0.7F, 1.1F + p * 0.5F);
        switch (r) {
            case COMMON:
            default:
                break;
            case RARE:
                s.play(SoundEvents.BEACON_AMBIENT, v * 0.5F, 1.1F);
                break;
            case EPIC:
                s.play(SoundEvents.BEACON_AMBIENT, v * 0.5F, 1.0F);
                s.play(SoundEvents.CONDUIT_ACTIVATE, v * 0.35F, 1.2F);
                break;
            case LEGENDARY:
                s.play(SoundEvents.BEACON_AMBIENT, v * 0.55F, 0.95F);
                s.play(SoundEvents.WARDEN_SONIC_CHARGE, v * 0.35F, 1.0F);
                break;
            case MYTHIC:
                s.play(SoundEvents.BEACON_AMBIENT, v * 0.55F, 0.9F);
                s.play(SoundEvents.WARDEN_SONIC_CHARGE, v * 0.45F, 0.9F);
        }
    }

    public static void win(CrateSfx.Sink s, Rarity r) {
        s.play(wailA(), 0.74F, 0.55F);
        s.play(wailM(), 0.6F, 0.7F);
        s.play(wailA(), 0.52F, 1.55F);
        s.play(wailM(), 0.58F, 0.9F);
        s.play(soulEscape(), 0.58F, 0.72F);
        switch (r) {
            case COMMON:
                s.play(SoundEvents.BEACON_POWER_SELECT, 0.85F, 1.5F);
                s.play(SoundEvents.BEACON_ACTIVATE, 0.7F, 1.3F);
                s.play(wailA(), 0.7F, 1.15F);
                s.play(witherGroan(), 0.25F, 0.72F);
                s.play(soulEscape(), 0.62F, 1.1F);
                s.play(wailM(), 0.6F, 1.2F);
                s.play(wailM(), 0.6F, 1.3F);
                break;
            case RARE:
                s.play(SoundEvents.BEACON_ACTIVATE, 0.85F, 1.2F);
                s.play(SoundEvents.CONDUIT_ACTIVATE, 0.8F, 1.3F);
                s.play(SoundEvents.ENCHANTMENT_TABLE_USE, 0.65F, 1.2F);
                s.play(wailA(), 0.75F, 1.15F);
                s.play(witherGroan(), 0.28F, 0.7F);
                s.play(soulEscape(), 0.66F, 1.05F);
                s.play(wailM(), 0.62F, 1.2F);
                s.play(wailA(), 0.6F, 1.3F);
                s.play(soulEscape(), 0.5F, 0.85F);
                break;
            case EPIC:
                s.play(SoundEvents.CONDUIT_ACTIVATE, 0.9F, 1.15F);
                s.play(SoundEvents.BEACON_ACTIVATE, 0.85F, 1.25F);
                s.play(SoundEvents.TRIDENT_THUNDER, 0.75F, 1.2F);
                s.play(SoundEvents.END_PORTAL_SPAWN, 0.6F, 1.2F);
                s.play(wailA(), 0.8F, 1.2F);
                s.play(witherGroan(), 0.3F, 0.68F);
                s.play(soulEscape(), 0.7F, 1.0F);
                s.play(wailM(), 0.68F, 1.25F);
                s.play(wailA(), 0.62F, 1.35F);
                s.play(soulEscape(), 0.55F, 0.8F);
                break;
            case LEGENDARY:
                s.play(SoundEvents.WARDEN_SONIC_BOOM, 0.85F, 1.1F);
                s.play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.9F, 1.05F);
                s.play(SoundEvents.TRIDENT_THUNDER, 0.85F, 1.15F);
                s.play(SoundEvents.CONDUIT_ACTIVATE, 0.8F, 1.2F);
                s.play(wailA(), 0.85F, 1.2F);
                s.play(witherGroan(), 0.33F, 0.66F);
                s.play(soulEscape(), 0.72F, 0.95F);
                s.play(wailM(), 0.72F, 1.2F);
                s.play(wailA(), 0.65F, 1.35F);
                s.play(soulEscape(), 0.55F, 0.78F);
                break;
            case MYTHIC:
                s.play(SoundEvents.WITHER_SPAWN, 0.9F, 0.95F);
                s.play(SoundEvents.WARDEN_SONIC_BOOM, 0.9F, 0.9F);
                s.play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.9F, 1.05F);
                s.play(SoundEvents.END_PORTAL_SPAWN, 0.8F, 1.0F);
                s.play(SoundEvents.CONDUIT_ACTIVATE, 0.8F, 1.1F);
                s.play(wailA(), 0.9F, 1.2F);
                s.play(witherGroan(), 0.36F, 0.64F);
                s.play(soulEscape(), 0.75F, 0.9F);
                s.play(wailM(), 0.76F, 1.25F);
                s.play(wailA(), 0.7F, 1.4F);
                s.play(soulEscape(), 0.6F, 0.75F);
                s.play(wailM(), 0.62F, 1.45F);
        }
    }

    public static void winTail(CrateSfx.Sink s, Rarity r) {
        switch (r) {
            case COMMON:
                s.play(SoundEvents.BEACON_POWER_SELECT, 0.55F, 1.8F);
                s.play(wailM(), 0.5F, 1.1F);
                break;
            case RARE:
                s.play(SoundEvents.CONDUIT_AMBIENT, 0.55F, 1.4F);
                s.play(SoundEvents.BEACON_POWER_SELECT, 0.5F, 1.7F);
                s.play(wailM(), 0.55F, 1.1F);
                break;
            case EPIC:
                s.play(SoundEvents.CONDUIT_AMBIENT, 0.6F, 1.25F);
                s.play(SoundEvents.ENCHANTMENT_TABLE_USE, 0.55F, 1.3F);
                s.play(wailM(), 0.6F, 1.05F);
                break;
            case LEGENDARY:
                s.play(SoundEvents.TRIDENT_RETURN, 0.6F, 1.1F);
                s.play(SoundEvents.CONDUIT_AMBIENT, 0.55F, 1.1F);
                s.play(SoundEvents.BEACON_ACTIVATE, 0.55F, 1.4F);
                s.play(wailA(), 0.6F, 1.0F);
                break;
            case MYTHIC:
                s.play(SoundEvents.END_PORTAL_SPAWN, 0.6F, 1.2F);
                s.play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.55F, 1.2F);
                s.play(SoundEvents.CONDUIT_AMBIENT, 0.55F, 0.95F);
                s.play(wailA(), 0.65F, 0.95F);
        }
    }

    public static void close(CrateSfx.Sink s, Rarity r) {
        switch (r) {
            case COMMON:
                s.play(SoundEvents.BEACON_DEACTIVATE, 0.5F, 1.1F);
                break;
            case RARE:
                s.play(SoundEvents.BEACON_DEACTIVATE, 0.5F, 1.0F);
                s.play(SoundEvents.CONDUIT_DEACTIVATE, 0.4F, 1.2F);
                break;
            case EPIC:
                s.play(SoundEvents.CONDUIT_DEACTIVATE, 0.5F, 1.0F);
                s.play(SoundEvents.BEACON_DEACTIVATE, 0.45F, 0.9F);
                break;
            case LEGENDARY:
                s.play(SoundEvents.CONDUIT_DEACTIVATE, 0.55F, 0.95F);
                s.play(SoundEvents.BEACON_DEACTIVATE, 0.5F, 0.85F);
                break;
            case MYTHIC:
                s.play(SoundEvents.CONDUIT_DEACTIVATE, 0.55F, 0.9F);
                s.play(SoundEvents.BEACON_DEACTIVATE, 0.5F, 0.8F);
        }
    }

    public interface Sink {
        void play(SoundEvent var1, float var2, float var3);
    }
}
