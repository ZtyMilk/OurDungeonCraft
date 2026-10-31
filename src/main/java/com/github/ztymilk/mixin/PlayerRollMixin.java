package com.github.ztymilk.mixin;

import com.github.ztymilk.roll.RollData;
import com.github.ztymilk.roll.RollDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin injecting {@link RollDataAccessor} into {@link Player}.
 * Implements roll data storage, tick progression, invulnerability handling, and death cleanup.
 */
@Mixin(Player.class)
public abstract class PlayerRollMixin implements RollDataAccessor {

    @Unique
    private final RollData ourDungeonCraft$rollData = new RollData();

    @Override
    public @NotNull RollData getRollData() {
        return this.ourDungeonCraft$rollData;
    }

    /**
     * Ticks the dodge roll state machine every player tick.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void ourDungeonCraft$onTick(CallbackInfo ci) {
        this.tickRoll();
    }

    /**
     * Handles i-frame damage negation during {@link com.github.ztymilk.roll.RollState#ACTIVE}.
     * Edge case: lethal out-of-world damage and tags bypassing invulnerability are NOT blocked.
     */
    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void ourDungeonCraft$handleRollInvulnerability(
            ServerLevel serverLevel,
            DamageSource damageSource,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (this.isRollInvulnerable()) {
            if (damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                    || damageSource.is(DamageTypes.FELL_OUT_OF_WORLD)
                    || damageSource.is(DamageTypes.GENERIC_KILL)) {
                // Out-of-world lethal damage penetrates roll invulnerability
                return;
            }
            cir.setReturnValue(true);
        }
    }

    /**
     * Edge case: when player dies, immediately reset roll state to IDLE.
     */
    @Inject(method = "die", at = @At("HEAD"))
    private void ourDungeonCraft$onDeath(DamageSource damageSource, CallbackInfo ci) {
        this.resetRoll();
    }

    /**
     * Persists dodge roll cooldown and state to player save data.
     */
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void ourDungeonCraft$saveRollData(ValueOutput output, CallbackInfo ci) {
        this.ourDungeonCraft$rollData.save(output);
    }

    /**
     * Restores dodge roll cooldown and state from player save data.
     */
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void ourDungeonCraft$loadRollData(ValueInput input, CallbackInfo ci) {
        this.ourDungeonCraft$rollData.load(input);
    }
}
