package com.github.ztymilk.mixin;

import com.github.ztymilk.combat.AttackComboAccessor;
import com.github.ztymilk.combat.AttackComboData;
import com.github.ztymilk.combat.ComboStep;
import com.github.ztymilk.item.DungeonWeaponItem;
import com.github.ztymilk.sound.WeaponSoundHelper;
import com.github.ztymilk.vfx.WeaponVfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin injecting into {@link LivingEntity#swing(InteractionHand, boolean)}
 * to progress combat combo steps and trigger weapon audio choreography on swing initiation.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingMixin {

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"))
    private void ourDungeonCraft$onMainHandSwing(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
        if (hand == InteractionHand.MAIN_HAND && ((Object) this) instanceof Player player) {
            if (player instanceof AttackComboAccessor accessor) {
                AttackComboData data = accessor.ourDungeonCraft$getComboData();

                // Hitstop protection lock: freeze combo progression, whoosh audio, and air swing VFX during impact freeze
                if (data.isHitstop()) {
                    return;
                }

                data.onAttack(player.getMainHandItem(), player.level().getGameTime());

                // Play combat whoosh audio choreography (pure aerodynamic air displacement)
                if (player.level().isClientSide()) {
                    WeaponSoundHelper.getSwingChoreography(data.getWeaponType(), data.getComboStep())
                            .play(player.level(), player.getX(), player.getY() + (player.getEyeHeight() * 0.8), player.getZ(), player.getRandom());
                } else if (player.level() instanceof ServerLevel serverLevel) {
                    if (player.getMainHandItem().getItem() instanceof DungeonWeaponItem weapon) {
                        ComboStep step = weapon.getComboSequence().getStep(data.getComboStep());
                        if (data.tryTriggerSwingVfx(serverLevel.getGameTime())) {
                            WeaponVfx.spawnWeaponAirSwingVfx(serverLevel, player, weapon.getWeaponType(), data.getComboStep(), step);
                        }
                    }
                }
            }
        }
    }

    /**
     * Freezes swing animation progression when player is experiencing Hitstop.
     * Prevents swingTime and attackAnim from advancing for 1-2 ticks to create
     * authentic weapon impact resistance / hit freeze.
     */
    @Inject(method = "updateSwingTime", at = @At("HEAD"), cancellable = true)
    private void ourDungeonCraft$freezeSwingDuringHitstop(CallbackInfo ci) {
        if (((Object) this) instanceof Player player && player instanceof AttackComboAccessor accessor) {
            AttackComboData data = accessor.ourDungeonCraft$getComboData();
            if (data.isHitstop()) {
                ci.cancel();
            }
        }
    }
}
