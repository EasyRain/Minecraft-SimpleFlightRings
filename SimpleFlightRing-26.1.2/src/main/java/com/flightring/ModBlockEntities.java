package com.flightring;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

/** Block entity types of the mod. Currently only the cloud chest. */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FlightRingMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ThickCloudChestBlockEntity>>
            THICK_CLOUD_CHEST = BLOCK_ENTITIES.register("thick_cloud_chest",
            () -> new BlockEntityType<>(ThickCloudChestBlockEntity::new,
                    Set.of(ModBlocks.THICK_CLOUD_CHEST.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ThickCloudBedBlockEntity>>
            THICK_CLOUD_BED = BLOCK_ENTITIES.register("thick_cloud_bed",
            () -> new BlockEntityType<>(ThickCloudBedBlockEntity::new,
                    Set.of(ModBlocks.THICK_CLOUD_BED.get())));

    private ModBlockEntities() {
    }
}
