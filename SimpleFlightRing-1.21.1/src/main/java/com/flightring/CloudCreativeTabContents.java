package com.flightring;

import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * 把云层相关的方块挂进本模组的创造栏。
 * <p>
 * 之前是在 {@code CreativeModeTab.builder().displayItems(...)} 里直接 {@code output.accept}，
 * 但新增的方块（去皮云木）在创造栏里始终看不到，而 {@code /give} 又能拿到 ——
 * 说明注册没问题、就是这一段没生效。改成 NeoForge 的
 * {@link BuildCreativeModeTabContentsEvent}：它在标签页构建完成后再往里塞，
 * 与注册顺序、建造时机都无关，是最稳的写法。
 */
@EventBusSubscriber(modid = FlightRingMod.MODID)
public final class CloudCreativeTabContents {

    private CloudCreativeTabContents() {
    }

    @SubscribeEvent
    public static void onBuildContents(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(ModCreativeTabs.FLIGHT_RINGS.getKey())) {
            return;
        }
        // 云层之上的方块（M1）
        accept(event, ModItems.CLOUD_BLOCK_ITEM.get());
        // 云木（M5）
        accept(event, ModItems.CLOUD_LOG_ITEM.get());
        accept(event, ModItems.STRIPPED_CLOUD_LOG_ITEM.get());
        accept(event, ModItems.CLOUD_WOOD_ITEM.get());
        accept(event, ModItems.STRIPPED_CLOUD_WOOD_ITEM.get());
        accept(event, ModItems.CLOUD_PLANKS_ITEM.get());
        accept(event, ModItems.CLOUD_LEAVES_ITEM.get());
        accept(event, ModItems.CLOUD_SAPLING_ITEM.get());
        // 云木变体
        accept(event, ModItems.CLOUD_STAIRS_ITEM.get());
        accept(event, ModItems.CLOUD_SLAB_ITEM.get());
        accept(event, ModItems.CLOUD_FENCE_ITEM.get());
        accept(event, ModItems.CLOUD_FENCE_GATE_ITEM.get());
        accept(event, ModItems.CLOUD_DOOR_ITEM.get());
        accept(event, ModItems.CLOUD_TRAPDOOR_ITEM.get());
        accept(event, ModItems.CLOUD_BUTTON_ITEM.get());
        accept(event, ModItems.CLOUD_PRESSURE_PLATE_ITEM.get());
        accept(event, ModItems.CLOUD_SIGN_ITEM.get());
        accept(event, ModItems.CLOUD_HANGING_SIGN_ITEM.get());
        // 厚云材料（M1/M2）
        accept(event, ModItems.THICK_CLOUD_BLOCK_ITEM.get());
        accept(event, ModItems.THICK_CLOUD_BRICKS_ITEM.get());
        accept(event, ModItems.THICK_CLOUD_SLAB_ITEM.get());
        accept(event, ModItems.THICK_CLOUD_STAIRS_ITEM.get());
        accept(event, ModItems.THICK_CLOUD_WALL_ITEM.get());
        accept(event, ModItems.THICK_CLOUD_CHEST_ITEM.get());
        accept(event, ModItems.THICK_CLOUD_BED_ITEM.get());
    }

    private static void accept(BuildCreativeModeTabContentsEvent event, net.minecraft.world.item.Item item) {
        event.accept(new ItemStack(item), net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }
}
