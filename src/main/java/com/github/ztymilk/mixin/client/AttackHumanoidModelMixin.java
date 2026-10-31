package com.github.ztymilk.mixin.client;

import com.github.ztymilk.combat.client.AttackAnimationHandler;
import com.github.ztymilk.combat.client.AttackComboRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin injecting into {@link HumanoidModel#setupAttackAnimation(HumanoidRenderState)}
 * to override third-person attack poses for Minecraft Dungeons base weapon combos.
 */
@Mixin(HumanoidModel.class)
public abstract class AttackHumanoidModelMixin<T extends HumanoidRenderState> {

    @Inject(method = "setupAttackAnimation", at = @At("HEAD"), cancellable = true)
    private void ourDungeonCraft$customSetupAttackAnimation(T state, CallbackInfo ci) {
        if (state instanceof AttackComboRenderState comboState && comboState.ourDungeonCraft$isCustomAttacking()) {
            if (state instanceof AvatarRenderState avatarState) {
                HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
                AttackAnimationHandler.applyThirdPersonAttack(model, avatarState, comboState);
                ci.cancel(); // Cancel vanilla WHACK arm swing
            }
        }
    }
}
