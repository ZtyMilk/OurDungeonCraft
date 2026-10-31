package com.github.ztymilk;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.ztymilk.command.SetSoulCommand;
import com.github.ztymilk.enchantment.ModEnchantments;
import com.github.ztymilk.entity.ModEntities;
import com.github.ztymilk.item.ModCreativeModeTabs;
import com.github.ztymilk.item.ModItems;
import com.github.ztymilk.network.ModNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class OurDungeonCraft implements ModInitializer {
    public static final String MOD_ID = "ourdungeoncraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModNetworking.init();
        ModEntities.init();
        ModItems.init();
        ModCreativeModeTabs.init();
        ModEnchantments.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            SetSoulCommand.register(dispatcher);
        });
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
