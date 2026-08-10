package com.fscrates.animation;

public record CrateAnimation(String id, String displayName, CrateAnimation.Style style, CrateAnimation.Theme theme, int durationTicks, String description) {
    public boolean hasBeam() {
        return this.style != CrateAnimation.Style.INSTANT
            && (
                this.theme == CrateAnimation.Theme.CELESTIAL
                    || this.theme == CrateAnimation.Theme.MAGIC
                    || this.theme == CrateAnimation.Theme.NEON
                    || this.theme == CrateAnimation.Theme.ANCIENT
                    || this.theme == CrateAnimation.Theme.INFERNAL
            );
    }

    public static enum Style {
        ROULETTE,
        SLOT_MACHINE,
        INSTANT;
    }

    public static enum Theme {
        CLASSIC,
        CASINO,
        NEON,
        INFERNAL,
        CELESTIAL,
        MAGIC,
        NATURE,
        ANCIENT;
    }
}
