package com.github.ztymilk.entity;

import com.github.ztymilk.OurDungeonCraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {

    public static final ResourceKey<EntityType<?>> SOUL_ORB_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, OurDungeonCraft.id("soul_orb"));

    public static final EntityType<SoulOrbEntity> SOUL_ORB = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            SOUL_ORB_KEY,
            EntityType.Builder.<SoulOrbEntity>of(SoulOrbEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build(SOUL_ORB_KEY)
    );

    private ModEntities() {
    }

    public static void init() {
        // Classloading triggers static registration
    }
}
