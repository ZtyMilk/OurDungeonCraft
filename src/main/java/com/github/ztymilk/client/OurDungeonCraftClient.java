package com.github.ztymilk.client;

import com.github.ztymilk.client.hud.DungeonHudOverlay;
import com.github.ztymilk.combat.client.DamageNumberRenderer;
import com.github.ztymilk.entity.ModEntities;
import com.github.ztymilk.entity.client.SoulOrbRenderer;
import com.github.ztymilk.network.ModNetworkingClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OurDungeonCraftClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ourdungeoncraft-client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing OurDungeonCraft Client...");
        ModKeyBindings.init();
        ModNetworkingClient.initClient();
        RollInputHandler.init();
        DamageNumberRenderer.init();
        EntityRendererRegistry.register(ModEntities.SOUL_ORB, SoulOrbRenderer::new);
        DungeonHudOverlay.init();
    }
}
