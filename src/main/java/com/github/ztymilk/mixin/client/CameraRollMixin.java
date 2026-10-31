package com.github.ztymilk.mixin.client;

import com.github.ztymilk.combat.AttackComboAccessor;
import com.github.ztymilk.combat.AttackComboData;
import com.github.ztymilk.combat.client.AttackAnimationHandler;
import com.github.ztymilk.roll.RollData;
import com.github.ztymilk.roll.RollDataAccessor;
import com.github.ztymilk.roll.RollState;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link Camera} to handle dynamic first-person camera feedback during dodge roll:
 * - Subtle chin-tuck pitch (-6°)
 * - Lateral banking tilt based on roll angle (up to ±5°)
 * - Center of mass vertical dip (-0.35m)
 * Decoupled from third-person somersault to avoid motion sickness.
 */
@Mixin(Camera.class)
public abstract class CameraRollMixin {

    @Shadow
    @Final
    private Quaternionf rotation;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    public abstract Vec3 position();

    @Inject(
            method = "setup(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At("TAIL")
    )
    private void ourDungeonCraft$applyRollCameraEffects(
            Level level,
            Entity entity,
            boolean detached,
            boolean thirdPersonReverse,
            float partialTickTime,
            CallbackInfo ci
    ) {
        double totalDipY = 0.0;

        // 1. First-person decoupled motion feedback during dodge roll:
        if (entity instanceof RollDataAccessor rollAccessor) {
            RollData data = rollAccessor.getRollData();
            if (data.isRolling() && data.getState() == RollState.ACTIVE) {
                float progress = (data.getRollTicks() + partialTickTime) / (float) RollData.ACTIVE_TICKS;
                progress = Mth.clamp(progress, 0.0f, 1.0f);
                float curve = (float) Math.sin(progress * Math.PI);

                if (!detached) {
                    totalDipY += -0.32 * curve;

                    // Subtle pitch tuck downward (peaking at 6 degrees)
                    float pitchTuck = 6.0f * curve;

                    // Lateral banking roll based on roll direction relative to view yaw
                    float deltaYaw = Mth.wrapDegrees(data.getRollYaw() - entity.getYRot());
                    float rollTilt = -(float) Math.sin(Math.toRadians(deltaYaw)) * 5.0f * curve;

                    this.rotation.rotateX((float) Math.toRadians(pitchTuck));
                    this.rotation.rotateZ((float) Math.toRadians(rollTilt));
                }
            }
        }

        // 2. First-person weapon attack kinetic momentum chain (shockwaves, dips, centrifugal banking, hitstop shake):
        if (!detached && entity instanceof Player player && player instanceof AttackComboAccessor comboAccessor) {
            AttackComboData comboData = comboAccessor.ourDungeonCraft$getComboData();
            if (comboData.isHitstop()) {
                comboData.applyHitstopCameraShake(this.rotation, partialTickTime);
            }
            float attackAnim = player.getAttackAnim(partialTickTime);
            if (attackAnim > 0.0001f && attackAnim < 1.0f) {
                totalDipY += AttackAnimationHandler.applyCameraFeedback(
                        comboData.getWeaponType(),
                        comboData.getComboStep(),
                        attackAnim,
                        this.rotation
                );
            }
        }

        if (!detached && Math.abs(totalDipY) > 0.0001) {
            Vec3 pos = this.position();
            this.setPosition(pos.x, pos.y + totalDipY, pos.z);
        }
    }
}
