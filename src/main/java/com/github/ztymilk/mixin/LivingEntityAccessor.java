package com.github.ztymilk.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin for {@link LivingEntity} to read and update protected combat tickers.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    @Accessor("attackStrengthTicker")
    int ourDungeonCraft$getAttackStrengthTicker();

    @Accessor("attackStrengthTicker")
    void ourDungeonCraft$setAttackStrengthTicker(int value);
}
