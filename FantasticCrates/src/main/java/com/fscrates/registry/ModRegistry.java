package com.fscrates.registry;

import com.fscrates.block.CrateBlock;
import com.fscrates.block.CrateBlockEntity;
import com.fscrates.item.CrateBlockItem;
import com.fscrates.item.EditorWandItem;
import com.fscrates.item.KeyItem;
import com.fscrates.item.UniqueKeyItem;
import com.mojang.datafixers.types.Type;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityType.Builder;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "fscrates");
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "fscrates");
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "fscrates");
    public static final RegistryObject<Block> CRATE_BLOCK = BLOCKS.register("crate", CrateBlock::new);
    public static final RegistryObject<Item> CRATE_ITEM = ITEMS.register("crate", () -> new CrateBlockItem(CRATE_BLOCK.get(), new Properties()));
    public static final RegistryObject<Item> EDITOR_WAND = ITEMS.register("editor_wand", () -> new EditorWandItem());
    public static final RegistryObject<Item> FANTASTIC_KEY = ITEMS.register("fantastic_key", () -> new KeyItem());
    public static final RegistryObject<Item> UNIQUE_KEY = ITEMS.register("unique_key", () -> new UniqueKeyItem());
    public static final RegistryObject<BlockEntityType<CrateBlockEntity>> CRATE_BE = BLOCK_ENTITIES.register(
        "crate", () -> Builder.of(CrateBlockEntity::new, new Block[]{CRATE_BLOCK.get()}).build((Type)null)
    );

    private ModRegistry() {
    }

    public static Item key() {
        return FANTASTIC_KEY.get();
    }

    public static Item uniqueKey() {
        return UNIQUE_KEY.get();
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}
