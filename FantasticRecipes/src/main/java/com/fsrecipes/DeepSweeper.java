package com.fsrecipes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * Borra items prohibidos alla donde esten, no solo en los slots de primer nivel.
 *
 * <p>Para llegar a inventarios anidados (mochilas de Sophisticated Backpacks, shulkers,
 * bolsas de otros mods...) se usan dos vias complementarias:
 *
 * <ol>
 *   <li>La capability {@code ITEM_HANDLER} del ItemStack o del bloque/entidad. Es la via
 *       buena: el mod se entera del cambio y lo guarda. Cubre practicamente cualquier
 *       contenedor de mod, incluidos los que guardan el contenido fuera del propio item
 *       (Sophisticated Backpacks con almacenamiento por UUID).</li>
 *   <li>Un barrido crudo del NBT, como red de seguridad para mods que no exponen la
 *       capability. Los stacks encontrados se neutralizan a {@code minecraft:air} en el
 *       sitio, sin borrar la entrada, para no descolocar los indices de slot.</li>
 * </ol>
 */
public final class DeepSweeper {
   /** Tope de anidamiento (mochila dentro de shulker dentro de mochila...). */
   private static final int MAX_DEPTH = 8;

   private static final Direction[] ALL_SIDES = Direction.values();

   private DeepSweeper() {
   }

   // ------------------------------------------------------------------ item stacks

   /**
    * Limpia los inventarios que lleve dentro este stack. NO comprueba el stack en si:
    * de eso se encarga quien lo tenga (que es el unico que puede vaciar su slot).
    *
    * @return cuantos stacks prohibidos se eliminaron
    */
   public static int cleanNested(ItemStack stack) {
      return cleanNested(stack, 0);
   }

   private static int cleanNested(ItemStack stack, int depth) {
      if (stack == null || stack.isEmpty() || depth > MAX_DEPTH) {
         return 0;
      }

      int removed = 0;

      IItemHandler handler = stack.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
      if (handler != null) {
         removed += sweepHandler(handler, depth + 1);
      }

      if (stack.hasTag()) {
         removed += sweepTag(stack.getTag(), depth + 1);
      }

      return removed;
   }

   /** Barre un IItemHandler (la via que usan casi todos los mods de almacenamiento). */
   public static int sweepHandler(IItemHandler handler, int depth) {
      if (handler == null || depth > MAX_DEPTH) {
         return 0;
      }

      int removed = 0;

      for (int i = 0; i < handler.getSlots(); i++) {
         ItemStack in = handler.getStackInSlot(i);
         if (in == null || in.isEmpty()) {
            continue;
         }

         if (RecipeBans.isItemBanned(in)) {
            if (handler instanceof IItemHandlerModifiable mod) {
               mod.setStackInSlot(i, ItemStack.EMPTY);
            } else {
               handler.extractItem(i, in.getCount(), false);
            }

            removed++;
         } else {
            removed += cleanNested(in, depth);
         }
      }

      return removed;
   }

   // ------------------------------------------------------------------ NBT crudo

   /**
    * Recorre el NBT buscando stacks en formato estandar ({@code id} + {@code Count}) y
    * neutraliza los prohibidos. Cubre shulkers, {@code BlockEntityTag}, bundles y los
    * mods que serializan inventarios a mano.
    */
   public static int sweepTag(CompoundTag tag, int depth) {
      if (tag == null || depth > MAX_DEPTH) {
         return 0;
      }

      int removed = 0;

      for (String key : new ArrayList<>(tag.getAllKeys())) {
         Tag child = tag.get(key);
         if (child instanceof CompoundTag compound) {
            if (isBannedStackTag(compound)) {
               neutralize(compound);
               removed++;
            } else {
               removed += sweepTag(compound, depth + 1);
            }
         } else if (child instanceof ListTag list) {
            removed += sweepList(list, depth + 1);
         }
      }

      return removed;
   }

   private static int sweepList(ListTag list, int depth) {
      if (depth > MAX_DEPTH) {
         return 0;
      }

      int removed = 0;

      for (int i = 0; i < list.size(); i++) {
         Tag element = list.get(i);
         if (element instanceof CompoundTag compound) {
            if (isBannedStackTag(compound)) {
               neutralize(compound);
               removed++;
            } else {
               removed += sweepTag(compound, depth + 1);
            }
         } else if (element instanceof ListTag nested) {
            removed += sweepList(nested, depth + 1);
         }
      }

      return removed;
   }

   /** Un compound es un ItemStack serializado si tiene {@code id} (string) y {@code Count}. */
   private static boolean isBannedStackTag(CompoundTag tag) {
      if (!tag.contains("id", Tag.TAG_STRING) || !tag.contains("Count")) {
         return false;
      }

      return RecipeBans.isItemBanned(net.minecraft.resources.ResourceLocation.tryParse(tag.getString("id")));
   }

   /**
    * Deja la entrada como un slot vacio sin quitarla de la lista: si la quitasemos,
    * los formatos que usan la posicion como indice de slot se descolocarian.
    */
   private static void neutralize(CompoundTag tag) {
      tag.putString("id", "minecraft:air");
      tag.putByte("Count", (byte)0);
      tag.remove("tag");
   }

   // ------------------------------------------------------------------ contenedores

   public static int sweepContainer(Container container) {
      if (container == null) {
         return 0;
      }

      int removed = 0;

      for (int i = 0; i < container.getContainerSize(); i++) {
         ItemStack st = container.getItem(i);
         if (st == null || st.isEmpty()) {
            continue;
         }

         if (RecipeBans.isItemBanned(st)) {
            container.setItem(i, ItemStack.EMPTY);
            removed++;
         } else {
            removed += cleanNested(st);
         }
      }

      if (removed > 0) {
         container.setChanged();
      }

      return removed;
   }

   /** Barre el menu abierto (sirve para cofres, maquinas de mods y GUIs raras). */
   public static int sweepMenu(AbstractContainerMenu menu) {
      if (menu == null) {
         return 0;
      }

      int removed = 0;

      for (Slot slot : menu.slots) {
         ItemStack st = slot.getItem();
         if (st == null || st.isEmpty()) {
            continue;
         }

         if (RecipeBans.isItemBanned(st)) {
            slot.set(ItemStack.EMPTY);
            slot.setChanged();
            removed++;
         } else {
            int nested = cleanNested(st);
            if (nested > 0) {
               slot.setChanged();
               removed += nested;
            }
         }
      }

      if (RecipeBans.isItemBanned(menu.getCarried())) {
         menu.setCarried(ItemStack.EMPTY);
         removed++;
      } else {
         removed += cleanNested(menu.getCarried());
      }

      if (removed > 0) {
         menu.broadcastChanges();
      }

      return removed;
   }

   public static int sweepBlockEntity(BlockEntity be) {
      if (be == null || be.isRemoved()) {
         return 0;
      }

      int removed = 0;

      // Una misma BE puede devolver el mismo handler para varias caras.
      Set<IItemHandler> seen = Collections.newSetFromMap(new IdentityHashMap<>());
      IItemHandler noSide = be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
      if (noSide != null && seen.add(noSide)) {
         removed += sweepHandler(noSide, 0);
      }

      for (Direction side : ALL_SIDES) {
         IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
         if (handler != null && seen.add(handler)) {
            removed += sweepHandler(handler, 0);
         }
      }

      if (be instanceof Container container) {
         removed += sweepContainer(container);
      }

      if (removed > 0) {
         be.setChanged();
      }

      return removed;
   }

   public static int sweepEntity(Entity entity) {
      if (entity == null || entity.isRemoved()) {
         return 0;
      }

      int removed = 0;

      if (entity instanceof ItemEntity ie) {
         ItemStack st = ie.getItem();
         if (RecipeBans.isItemBanned(st)) {
            ie.discard();
            return 1;
         }

         removed += cleanNested(st);
      }

      IItemHandler handler = entity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
      if (handler != null) {
         removed += sweepHandler(handler, 0);
      }

      if (entity instanceof Container container) {
         removed += sweepContainer(container);
      }

      if (entity instanceof LivingEntity living) {
         for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack st = living.getItemBySlot(slot);
            if (st == null || st.isEmpty()) {
               continue;
            }

            if (RecipeBans.isItemBanned(st)) {
               living.setItemSlot(slot, ItemStack.EMPTY);
               removed++;
            } else {
               removed += cleanNested(st);
            }
         }
      }

      if (entity instanceof ItemFrame frame) {
         ItemStack st = frame.getItem();
         if (RecipeBans.isItemBanned(st)) {
            frame.setItem(ItemStack.EMPTY);
            removed++;
         } else {
            removed += cleanNested(st);
         }
      }

      return removed;
   }

   /** Barre todos los block entities ya instanciados de un chunk cargado. */
   public static int sweepChunk(LevelChunk chunk) {
      if (chunk == null) {
         return 0;
      }

      int removed = 0;

      for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
         removed += sweepBlockEntity(be);
      }

      return removed;
   }
}
