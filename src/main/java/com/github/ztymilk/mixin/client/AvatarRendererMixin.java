package com.github.ztymilk.mixin.client;

import com.github.ztymilk.combat.AttackComboAccessor;
import com.github.ztymilk.combat.AttackComboData;
import com.github.ztymilk.combat.client.AttackAnimationHandler;
import com.github.ztymilk.combat.client.AttackComboRenderState;
import com.github.ztymilk.roll.RollData;
import com.github.ztymilk.roll.RollDataAccessor;
import com.github.ztymilk.roll.RollState;
import com.github.ztymilk.roll.client.RollRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link AvatarRenderer} (Minecraft 1.21.11 player entity renderer)
 * to extract roll & attack combo render states, and apply PoseStack rotations in setupRotations.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

    /**
     * Extracts dodge roll & weapon combo animation data into {@link AvatarRenderState}.
     */
    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL")
    )
    private void ourDungeonCraft$extractRenderStates(
            Avatar avatar,
            AvatarRenderState state,
            float partialTicks,
            CallbackInfo ci
    ) {
        // 1. Roll Render State Extraction
        if (avatar instanceof RollDataAccessor rollAccessor && state instanceof RollRenderState rollState) {
            RollData rollData = rollAccessor.getRollData();
            if (rollData.isRolling()) {
                rollState.ourDungeonCraft$setRolling(true);
                rollState.ourDungeonCraft$setRollYaw(rollData.getRollYaw());

                float progress;
                if (rollData.getState() == RollState.ACTIVE) {
                    float ticks = rollData.getRollTicks() + partialTicks;
                    progress = Mth.clamp(ticks / (float) RollData.ACTIVE_TICKS, 0.0f, 1.0f);
                } else if (rollData.getState() == RollState.RECOVERY) {
                    progress = 1.0f;
                } else {
                    progress = 0.0f;
                }
                rollState.ourDungeonCraft$setRollProgress(progress);
            } else {
                rollState.ourDungeonCraft$setRolling(false);
                rollState.ourDungeonCraft$setRollProgress(0.0f);
            }
        }

        // 2. Attack Combo Render State Extraction
        if (avatar instanceof AttackComboAccessor comboAccessor && state instanceof AttackComboRenderState comboState) {
            AttackComboData comboData = comboAccessor.ourDungeonCraft$getComboData();
            boolean attacking = comboData.isAttacking() || state.attackTime > 0.0001f;
            comboState.ourDungeonCraft$setCustomAttacking(attacking);
            comboState.ourDungeonCraft$setWeaponType(comboData.getWeaponType());
            comboState.ourDungeonCraft$setComboStep(comboData.getComboStep());
            comboState.ourDungeonCraft$setAttackProgress(state.attackTime);
        }
    }

    /**
     * Applies full 360-degree rotation around the player's center of mass in setupRotations:
     * - Somersault roll rotation during dodge roll
     * - Horizontal whirlwind spin slash during Double Axe combo
     */
    @Inject(
            method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("TAIL")
    )
    private void ourDungeonCraft$applyCustomRotations(
            AvatarRenderState state,
            PoseStack poseStack,
            float bodyRot,
            float scale,
            CallbackInfo ci
    ) {
        // A. Dodge Roll Somersault
        if (state instanceof RollRenderState rollState && rollState.ourDungeonCraft$isRolling()) {
            float progress = rollState.ourDungeonCraft$getRollProgress();
            if (progress > 0.0f && progress < 1.0f) {
                float rollYaw = rollState.ourDungeonCraft$getRollYaw();
                float deltaYaw = rollYaw - bodyRot;
                float centerY = state.boundingBoxHeight * 0.5f;
                float hop = (float) Math.sin(progress * Math.PI) * 0.15f;

                poseStack.translate(0.0f, centerY + hop, 0.0f);
                poseStack.mulPose(Axis.YP.rotationDegrees(deltaYaw));
                poseStack.mulPose(Axis.XP.rotationDegrees(-progress * 360.0f));
                poseStack.mulPose(Axis.YP.rotationDegrees(-deltaYaw));
                poseStack.translate(0.0f, -centerY, 0.0f);
                return; // Roll takes precedence over attack rotations
            }
        }

        // B. Combat Whirlwind Spin (Double Axe Step 1)
        if (state instanceof AttackComboRenderState comboState && comboState.ourDungeonCraft$isCustomAttacking()) {
            AttackAnimationHandler.applyFullBodyRotation(state, poseStack, comboState);
        }
    }
}
