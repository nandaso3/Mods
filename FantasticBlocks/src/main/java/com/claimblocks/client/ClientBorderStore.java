package com.claimblocks.client;

import java.util.Collections;
import java.util.List;

public final class ClientBorderStore {
   private static volatile List<double[]> boxes = Collections.emptyList();
   private static volatile long lastUpdate = 0L;

   private ClientBorderStore() {
   }

   public static void receive(List<double[]> incoming) {
      boxes = incoming;
      lastUpdate = System.currentTimeMillis();
   }

   public static List<double[]> current() {
      return System.currentTimeMillis() - lastUpdate > 3000L ? Collections.emptyList() : boxes;
   }
}
