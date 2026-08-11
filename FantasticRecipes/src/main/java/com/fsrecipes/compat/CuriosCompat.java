package com.fsrecipes.compat;

import com.fsrecipes.DeepSweeper;
import com.fsrecipes.FSRecipes;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * Soporte para Curios.
 *
 * <p>Los slots de Curios no forman parte del inventario del jugador: van en una
 * capability aparte, asi que el barrido normal no los ve. Si alguien lleva una mochila
 * equipada en un slot de Curios, hay que entrar en ella igual que en cualquier otro
 * contenedor.
 *
 * <p>Se hace por reflexion a proposito, para no meter Curios como dependencia de
 * compilacion: si no esta instalado, o si cambian su API, esto se desactiva solo en vez
 * de tirar el juego. El resultado del enganche se escribe una vez en el log.
 */
public final class CuriosCompat {
   private static final String CAPABILITY_CLASS = "top.theillusivec4.curios.api.CuriosCapability";

   private static boolean initialized = false;
   private static Capability<Object> inventoryCap = null;

   private CuriosCompat() {
   }

   @SuppressWarnings("unchecked")
   private static synchronized boolean init() {
      if (initialized) {
         return inventoryCap != null;
      }

      initialized = true;

      if (!ModList.get().isLoaded("curios")) {
         return false;
      }

      try {
         Object cap = Class.forName(CAPABILITY_CLASS).getField("INVENTORY").get(null);
         inventoryCap = (Capability<Object>)cap;
         FSRecipes.LOGGER.info("[FantasticRecipes] Curios detectado: los slots de Curios entran en el barrido.");
      } catch (Throwable ex) {
         FSRecipes.LOGGER.warn(
            "[FantasticRecipes] Curios esta instalado pero no se pudo enganchar ({}). "
               + "Los items prohibidos dentro de mochilas equipadas en Curios no se borraran.",
            ex.toString()
         );
         inventoryCap = null;
      }

      return inventoryCap != null;
   }

   /**
    * Barre los slots de Curios del jugador. Los items prohibidos que esten equipados se
    * quitan, y a los que no lo esten (una mochila, por ejemplo) se les limpia el
    * contenido sin tocar el item en si.
    *
    * @return cuantos stacks prohibidos se eliminaron
    */
   public static int sweep(Player player) {
      if (player == null || !init()) {
         return 0;
      }

      try {
         Object inventory = player.getCapability(inventoryCap).orElse(null);
         if (inventory == null) {
            return 0;
         }

         Method getCurios = inventory.getClass().getMethod("getCurios");
         Object curios = getCurios.invoke(inventory);
         if (!(curios instanceof Map<?, ?> slots)) {
            return 0;
         }

         int removed = 0;

         for (Object stacksHandler : slots.values()) {
            if (stacksHandler == null) {
               continue;
            }

            removed += sweepVia(stacksHandler, "getStacks");
            removed += sweepVia(stacksHandler, "getCosmeticStacks");
         }

         return removed;
      } catch (Throwable ex) {
         FSRecipes.LOGGER.warn("[FantasticRecipes] Fallo al barrer los slots de Curios: {}", ex.toString());
         inventoryCap = null;
         return 0;
      }
   }

   /**
    * Los handlers de Curios ({@code IDynamicStackHandler}) son
    * {@link IItemHandlerModifiable}, asi que el barrido normal ya sabe tratarlos.
    */
   private static int sweepVia(Object stacksHandler, String methodName) {
      try {
         Object handler = stacksHandler.getClass().getMethod(methodName).invoke(stacksHandler);
         return handler instanceof IItemHandlerModifiable modifiable ? DeepSweeper.sweepHandler(modifiable, 0) : 0;
      } catch (NoSuchMethodException ignored) {
         // Versiones de Curios sin slots cosmeticos.
         return 0;
      } catch (Throwable ex) {
         FSRecipes.LOGGER.warn("[FantasticRecipes] Fallo al barrer {} de Curios: {}", methodName, ex.toString());
         return 0;
      }
   }
}
