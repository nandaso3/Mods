package com.claimblocks.client;

import java.util.Collections;
import java.util.List;

public final class ClientBorderStore {
    private static final long STALE_AFTER_MS = 60000L;
    private static volatile List<double[]> boxes = Collections.emptyList();
    private static volatile long lastUpdate = 0L;

    private ClientBorderStore() {
    }

    public static void receive(List<double[]> list) {
        boxes = list == null ? Collections.emptyList() : list;
        lastUpdate = System.currentTimeMillis();
    }

    public static void clear() {
        boxes = Collections.emptyList();
        lastUpdate = 0L;
    }

    public static List<double[]> current() {
        List<double[]> list = boxes;
        if (list.isEmpty()) {
            return Collections.emptyList();
        } else {
            return System.currentTimeMillis() - lastUpdate > 60000L ? Collections.emptyList() : list;
        }
    }
}
