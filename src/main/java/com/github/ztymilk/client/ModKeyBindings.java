package com.github.ztymilk.client;

import com.github.ztymilk.OurDungeonCraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class ModKeyBindings {
    public static final KeyMapping.Category ROLL_CATEGORY = KeyMapping.Category.register(
            OurDungeonCraft.id("movement")
    );

    public static KeyMapping rollKey;

    public static void init() {
        rollKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.ourdungeoncraft.roll",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                ROLL_CATEGORY
        ));
    }
}
