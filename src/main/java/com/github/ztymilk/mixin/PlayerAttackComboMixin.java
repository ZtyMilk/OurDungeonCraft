package com.github.ztymilk.mixin;

import com.github.ztymilk.combat.AttackComboAccessor;
import com.github.ztymilk.combat.AttackComboData;
import com.github.ztymilk.combat.CleavePhysics;
import com.github.ztymilk.combat.ComboStep;
import com.github.ztymilk.combat.DamageFeedbackTracker;
import com.github.ztymilk.item.DungeonWeaponItem;
import com.github.ztymilk.sound.WeaponSoundHelper;
import com.github.ztymilk.network.ModNetworking;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import com.github.ztymilk.sound.WeaponSoundHelper.MCDWeaponType;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin injecting {@link AttackComboAccessor} into {@link Player}.
 * Handles combo progression, custom recovery ticks, hit audio choreography, and cleave physics.
 */
@Mixin(Player.class)
public abstract class PlayerAttackComboMixin implements AttackComboAccessor {

    @Unique
    private final AttackComboData ourDungeonCraft$comboData = new AttackComboData();

    @Override
    public @NotNull AttackComboData ourDungeonCraft$getComboData() {
        return this.ourDungeonCraft$comboData;
    }

    /**
     * Freezes weapon attack strength cooldown progression during Hitstop.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void ourDungeonCraft$freezeCooldownDuringHitstop(CallbackInfo ci) {
        if (this.ourDungeonCraft$comboData.isHitstop() && ((Object) this) instanceof LivingEntityAccessor accessor) {
            accessor.ourDungeonCraft$setAttackStrengthTicker(Math.max(0, accessor.ourDungeonCraft$getAttackStrengthTicker() - 1));
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void ourDungeonCraft$tickComboData(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        this.ourDungeonCraft$comboData.tick(player.getAttackAnim(1.0f));
    }

    /**
     * Overrides vanilla attack recovery delay to match per-step combo cadence.
     */
    @Inject(method = "getCurrentItemAttackStrengthDelay", at = @At("HEAD"), cancellable = true)
    private void ourDungeonCraft$customAttackDelay(CallbackInfoReturnable<Float> cir) {
        Player player = (Player) (Object) this;
        if (player.getMainHandItem().getItem() instanceof DungeonWeaponItem weapon) {
            ComboStep step = weapon.getComboSequence().getStep(this.ourDungeonCraft$comboData.getComboStep());
            cir.setReturnValue((float) step.recoveryTicks());
        }
    }

    /**
     * Intercepts successful hit in Player.attack to trigger authentic MCD hit sounds, Hitstop, and cleave physics.
     */
    @WrapOperation(
            method = "attack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z")
    )
    private boolean ourDungeonCraft$wrapHurt(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
        Player player = (Player) (Object) this;
        boolean isCrit = (player.fallDistance > 0.0f && !player.onGround() && !player.onClimbable() && !player.isInWater() && !player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS) && !player.isPassenger());

        DamageFeedbackTracker.setCurrentCrit(isCrit);
        boolean hit;
        try {
            hit = original.call(target, source, amount);
        } finally {
            DamageFeedbackTracker.clearCurrentCrit();
        }

        if (hit) {
            if (player.getMainHandItem().getItem() instanceof DungeonWeaponItem weapon) {
                // Ensure combo step is registered for this attack
                this.ourDungeonCraft$comboData.onAttack(player.getMainHandItem(), player.level().getGameTime());

                int stepIndex = this.ourDungeonCraft$comboData.getComboStep();
                ComboStep step = weapon.getComboSequence().getStep(stepIndex);

                // Trigger Hitstop (attacker local action freeze + camera shake)
                int hitstopTicks = AttackComboData.calculateHitstopTicks(weapon.getWeaponType(), stepIndex, step.isFinisher(), isCrit);
                float shake = AttackComboData.calculateShakeIntensity(weapon.getWeaponType(), stepIndex, step.isFinisher(), isCrit);
                this.ourDungeonCraft$comboData.triggerHitstop(hitstopTicks, shake);

                // Plant player momentum upon solid impact (damping horizontal sliding during hitstop)
                player.setDeltaMovement(player.getDeltaMovement().multiply(0.45, 1.0, 0.45));

                // Synchronize Hitstop to client player for LocalPlayer swing freeze and camera shake
                if (player instanceof ServerPlayer serverPlayer) {
                    ModNetworking.syncHitstop(serverPlayer, hitstopTicks, shake);
                }

                // Multi-layered acoustic hit feedback
                WeaponSoundHelper.playHit(player, target, weapon.getWeaponType(), stepIndex, step.isFinisher(), isCrit);

                // Multi-target cleave physics, exclusive mechanics, and dynamic step-based knockback
                CleavePhysics.executeCleave(player, target, amount, step, isCrit);
            }
        }
        return hit;
    }

    /**
     * Suppresses vanilla's flat extra knockback in favor of OurDungeonCraft's dynamic step-based knockback.
     */
    @WrapOperation(
            method = "attack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;causeExtraKnockback(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/phys/Vec3;)V")
    )
    private void ourDungeonCraft$suppressVanillaKnockback(Player instance, Entity target, float strength, Vec3 deltaMovement, Operation<Void> original) {
        if (instance.getMainHandItem().getItem() instanceof DungeonWeaponItem) {
            // Suppressed: OurDungeonCraft handles authentic combo-step dynamic knockback
            return;
        }
        original.call(instance, target, strength, deltaMovement);
    }

    /**
     * Disables vanilla flat 1.0 sweeping attack when wielding a DungeonWeaponItem.
     */
    @Inject(method = "doSweepAttack", at = @At("HEAD"), cancellable = true)
    private void ourDungeonCraft$cancelVanillaSweep(Entity target, float damage, DamageSource damageSource, float attackStrengthScale, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (player.getMainHandItem().getItem() instanceof DungeonWeaponItem) {
            ci.cancel();
        }
    }

    /**
     * Claymore Step 2 Hyper-Armor: 40% damage reduction during colossal chop swing.
     */
    @ModifyVariable(
            method = "actuallyHurt",
            at = @At("HEAD"),
            argsOnly = true
    )
    private float ourDungeonCraft$applyClaymoreHyperArmor(float damage) {
        Player player = (Player) (Object) this;
        if (player.getMainHandItem().getItem() instanceof DungeonWeaponItem weapon
                && weapon.getWeaponType() == MCDWeaponType.CLAYMORE
                && this.ourDungeonCraft$comboData.getComboStep() == 2
                && this.ourDungeonCraft$comboData.isAttacking()) {
            return damage * 0.60f; // 40% damage reduction during Step 2 Hyper-Armor!
        }
        return damage;
    }
}
