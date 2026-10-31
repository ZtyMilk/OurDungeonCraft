package com.github.ztymilk.mixin.client;

import com.github.ztymilk.combat.client.AttackAnimationHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin into {@link ItemInHandRenderer} to customize first-person attack swing animations
 * per weapon type and combo step, as well as injecting virtual off-hand render passes
 * for dual-wield weapons (Daggers, Sickles, Gauntlets).
 */
@Mixin(ItemInHandRenderer.class)
public abstract class AttackItemInHandRendererMixin {

    @Shadow private ItemStack mainHandItem;
    @Shadow private float mainHandHeight;
    @Shadow private float oMainHandHeight;
    @Shadow private ItemModelResolver itemModelResolver;

    /**
     * Intercepts renderArmWithItem invocations in renderHandsWithItems to inject a virtual off-hand
     * rendering pass for dual-wield weapons (Daggers, Sickles, Gauntlets) when the off-hand is empty.
     */
    @WrapOperation(
            method = "renderHandsWithItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"
            )
    )
    private void ourDungeonCraft$wrapRenderArmWithItem(
            ItemInHandRenderer instance,
            AbstractClientPlayer player,
            float partialTicks,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack stack,
            float equipProgress,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int light,
            Operation<Void> original
    ) {
        if (hand == InteractionHand.OFF_HAND && stack.isEmpty() && AttackAnimationHandler.isDualWieldWeapon(this.mainHandItem)) {
            float attackAnim = player.getAttackAnim(partialTicks);
            float virtualEquipProgress = this.itemModelResolver.swapAnimationScale(this.mainHandItem)
                    * (1.0f - Mth.lerp(partialTicks, this.oMainHandHeight, this.mainHandHeight));
            original.call(
                    instance,
                    player,
                    partialTicks,
                    pitch,
                    InteractionHand.OFF_HAND,
                    attackAnim,
                    this.mainHandItem,
                    virtualEquipProgress,
                    poseStack,
                    submitNodeCollector,
                    light
            );
            return;
        }

        original.call(instance, player, partialTicks, pitch, hand, swingProgress, stack, equipProgress, poseStack, submitNodeCollector, light);
    }

    /**
     * Intercepts vanilla swingArm invocation for standard (WHACK) attacks.
     */
    @WrapOperation(
            method = "renderArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;swingArm(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V"
            )
    )
    private void ourDungeonCraft$wrapFirstPersonSwingArm(
            ItemInHandRenderer instance,
            float swingProgress,
            PoseStack poseStack,
            int side,
            HumanoidArm arm,
            Operation<Void> original,
            @Local(argsOnly = true) AbstractClientPlayer player,
            @Local(argsOnly = true) ItemStack stack
    ) {
        if (AttackAnimationHandler.shouldApplyFirstPerson(player, stack)) {
            AttackAnimationHandler.applyFirstPersonAttack(stack, swingProgress, poseStack, side, arm, player);
        } else {
            original.call(instance, swingProgress, poseStack, side, arm);
        }
    }

    /**
     * Intercepts SpearAnimations.firstPersonAttack invocation for thrust (STAB) attacks.
     */
    @WrapOperation(
            method = "renderArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/effects/SpearAnimations;firstPersonAttack(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V"
            )
    )
    private void ourDungeonCraft$wrapFirstPersonSpearAttack(
            float swingProgress,
            PoseStack poseStack,
            int side,
            HumanoidArm arm,
            Operation<Void> original,
            @Local(argsOnly = true) AbstractClientPlayer player,
            @Local(argsOnly = true) ItemStack stack
    ) {
        if (AttackAnimationHandler.shouldApplyFirstPerson(player, stack)) {
            AttackAnimationHandler.applyFirstPersonAttack(stack, swingProgress, poseStack, side, arm, player);
        } else {
            original.call(swingProgress, poseStack, side, arm);
        }
    }
}
