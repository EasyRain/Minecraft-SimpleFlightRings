package com.flightring;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FlightRingMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FLIGHT_RINGS =
            TABS.register("flight_rings", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.simpleflightring"))
                    .icon(() -> new ItemStack(ModItems.WOOD_FLIGHT_RING.get()))
                    .displayItems((parameters, output) -> {
                        for (var ring : ModItems.ALL) {
                            output.accept(new ItemStack(ring.get()));
                        }
                        // The broken relic rings, right after the working ones they repair into.
                        for (var damaged : ModItems.DAMAGED_RELIC_RINGS.values()) {
                            output.accept(new ItemStack(damaged.get()));
                        }
                        // The soul-fed sculk ring is deliberately NOT listed here: it is a quest
                        // step, so it only exists once a player has fed it a Warden's soul.
                        // 云层之上的方块（M1）
                        output.accept(new ItemStack(ModItems.CLOUD_BLOCK_ITEM.get()));
                        // 云木（M5）
                        output.accept(new ItemStack(ModItems.CLOUD_LOG_ITEM.get()));
                        output.accept(new ItemStack(ModItems.CLOUD_WOOD_ITEM.get()));
                        output.accept(new ItemStack(ModItems.CLOUD_PLANKS_ITEM.get()));
                        output.accept(new ItemStack(ModItems.CLOUD_LEAVES_ITEM.get()));
                        output.accept(new ItemStack(ModItems.STRIPPED_CLOUD_LOG_ITEM.get()));
                        output.accept(new ItemStack(ModItems.STRIPPED_CLOUD_WOOD_ITEM.get()));
                        output.accept(new ItemStack(ModItems.CLOUD_SAPLING_ITEM.get()));
                        // 云母木变体（M5）
                        output.accept(new ItemStack(ModItems.CLOUD_STAIRS_ITEM.get()));
                        output.accept(new ItemStack(ModItems.CLOUD_SLAB_ITEM.get()));
                        output.accept(new ItemStack(ModItems.CLOUD_FENCE_ITEM.get()));
                        output.accept(new ItemStack(ModItems.CLOUD_FENCE_GATE_ITEM.get()));
                        output.accept(new ItemStack(ModItems.CLOUD_DOOR_ITEM.get()));
                        output.accept(new ItemStack(ModItems.CLOUD_TRAPDOOR_ITEM.get()));
                        output.accept(new ItemStack(ModItems.CLOUD_BUTTON_ITEM.get()));
                        output.accept(new ItemStack(ModItems.CLOUD_PRESSURE_PLATE_ITEM.get()));
                        output.accept(new ItemStack(ModItems.THICK_CLOUD_BLOCK_ITEM.get()));
                        output.accept(new ItemStack(ModItems.THICK_CLOUD_BRICKS_ITEM.get()));
                        output.accept(new ItemStack(ModItems.THICK_CLOUD_SLAB_ITEM.get()));
                        output.accept(new ItemStack(ModItems.THICK_CLOUD_STAIRS_ITEM.get()));
                        output.accept(new ItemStack(ModItems.THICK_CLOUD_WALL_ITEM.get()));
                        output.accept(new ItemStack(ModItems.THICK_CLOUD_DOOR_ITEM.get()));
                        output.accept(new ItemStack(ModItems.THICK_CLOUD_CHEST_ITEM.get()));
                        output.accept(new ItemStack(ModItems.THICK_CLOUD_BED_ITEM.get()));
                        output.accept(new ItemStack(ModItems.INDESTRUCTIBLE_CORE.get()));
                        output.accept(new ItemStack(ModItems.REFORGE_STONE.get()));
                        // The directed stone is one item whose name follows its mode, so show both kinds here.
                        for (ReforgeMode mode : new ReforgeMode[]{ReforgeMode.PREFIX, ReforgeMode.RARITY}) {
                            ItemStack directed = new ItemStack(ModItems.REFORGE_STONE_DIRECTED.get());
                            directed.set(ModDataComponents.REFORGE_MODE.get(), mode);
                            output.accept(directed);
                        }
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
