package com.github.ztymilk.mixin;

import com.github.ztymilk.soul.SoulDropHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin injecting into {@link LivingEntity#die(DamageSource)}
 * to calculate and trigger floating soul orb drops when mobs are defeated.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDeathMixin {

    @Inject(method = "die", at = @At("HEAD"))
    private void ourDungeonCraft$onEntityDeath(DamageSource damageSource, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        SoulDropHandler.onLivingEntityDeath(self, damageSource);
    }
}
