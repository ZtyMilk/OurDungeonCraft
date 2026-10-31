package com.github.ztymilk.item;

import com.github.ztymilk.OurDungeonCraft;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

/**
 * Creative mode tabs for OurDungeonCraft weapons and artifacts.
 */
public final class ModCreativeModeTabs {

    private ModCreativeModeTabs() {}

    public static final ResourceKey<CreativeModeTab> WEAPONS_TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, OurDungeonCraft.id("weapons_tab"));

    public static final CreativeModeTab WEAPONS_TAB = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            WEAPONS_TAB_KEY,
            FabricItemGroup.builder()
                    .title(Component.translatable("itemGroup.ourdungeoncraft.weapons"))
                    .icon(() -> new ItemStack(ModItems.SWORD))
                    .displayItems((parameters, output) -> {
                        for (DungeonWeaponItem weapon : ModItems.ALL_BASE_WEAPONS) {
                            output.accept(weapon);
                        }
                    })
                    .build()
    );

    public static void init() {
        // Also append all base weapons to vanilla Combat tab for convenience
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> {
            for (DungeonWeaponItem weapon : ModItems.ALL_BASE_WEAPONS) {
                entries.accept(weapon);
            }
        });
    }
}
