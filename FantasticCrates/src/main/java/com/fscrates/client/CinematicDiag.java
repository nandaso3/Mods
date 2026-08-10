package com.fscrates.client;

public final class CinematicDiag {
    public static volatile long lastCullNanos = 0L;
    public static volatile long cullFrames = 0L;

    private CinematicDiag() {
    }

    public static void markCull() {
        lastCullNanos = System.nanoTime();
        cullFrames++;
    }
}
