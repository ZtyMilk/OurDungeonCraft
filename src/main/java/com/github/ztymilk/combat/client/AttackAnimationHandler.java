package com.github.ztymilk.combat.client;

import com.github.ztymilk.combat.AttackComboAccessor;
import com.github.ztymilk.combat.AttackComboData;
import com.github.ztymilk.item.DungeonWeaponItem;
import com.github.ztymilk.sound.WeaponSoundHelper.MCDWeaponType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Ease;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * Mathematical transform engine and animation choreography for Minecraft Dungeons base weapons.
 * <p>
 * Implements physically grounded, zero-boundary continuous biomechanics:
 * - At swing progress t = 0.0: all angular and spatial offsets are strictly 0.0 (no initial snap).
 * - Multi-phase kinetic curve: Coil / Anticipation -> Explosive Release / Strike -> Recoil / Settle.
 * - At swing progress t = 1.0: all offsets smoothly settle to 0.0 (zero boundary jitter).
 * - Full 360° whole-body centrifugal spin for Double Axe (step 0) and Sickles (step 3).
 * - Dual weapon alternating off-hand / main-hand coordination for Daggers, Gauntlets, and Sickles.
 */
public final class AttackAnimationHandler {

    private AttackAnimationHandler() {
        // Utility
    }

    /**
     * Clamps a value into [0, 1] across a designated sub-window [start, end].
     */
    public static float window(float t, float start, float end) {
        if (t <= start) return 0.0f;
        if (t >= end) return 1.0f;
        return (t - start) / (end - start);
    }

    /**
     * Continuous 3-phase kinetic trajectory (Anticipation -> Strike -> Settle).
     * Strictly guaranteed: f(0) == 0.0 and f(1) == 0.0.
     */
    public static float kineticTrack(float t, float tWindup, float tStrike, float vWindup, float vStrike) {
        if (t <= 0.0f || t >= 1.0f) return 0.0f;
        if (t <= tWindup) {
            float p = Ease.inQuad(t / tWindup);
            return vWindup * p;
        } else if (t <= tStrike) {
            float p = Ease.outCubic((t - tWindup) / (tStrike - tWindup));
            return vWindup + (vStrike - vWindup) * p;
        } else {
            float p = (t - tStrike) / (1.0f - tStrike);
            return vStrike * (1.0f - Ease.outCubic(p));
        }
    }

    /**
     * Heavy kinetic impact trajectory with elastic ground recoil.
     * Strictly guaranteed: f(0) == 0.0 and f(1) == 0.0.
     */
    public static float kineticRecoil(float t, float tWindup, float tStrike, float vWindup, float vStrike, float recoilFactor) {
        if (t <= 0.0f || t >= 1.0f) return 0.0f;
        if (t <= tWindup) {
            float p = Ease.inQuad(t / tWindup);
            return vWindup * p;
        } else if (t <= tStrike) {
            float p = Ease.outExpo((t - tWindup) / (tStrike - tWindup));
            return vWindup + (vStrike - vWindup) * p;
        } else {
            float p = (t - tStrike) / (1.0f - tStrike);
            float decay = (1.0f - p) * (1.0f - p) * (1.0f + recoilFactor * Mth.sin(p * (float) Math.PI));
            return vStrike * decay;
        }
    }

    /**
     * High-speed linear piston thrust curve for jabs, thrusts, and punches.
     * Strictly guaranteed: f(0) == 0.0 and f(1) == 0.0.
     */
    public static float pistonThrust(float t, float tPeak) {
        if (t <= 0.0f || t >= 1.0f) return 0.0f;
        if (t <= tPeak) {
            return Ease.outExpo(t / tPeak);
        } else {
            float p = (t - tPeak) / (1.0f - tPeak);
            return 1.0f - Ease.outCubic(p);
        }
    }

    /**
     * Calculates kinetic centrifugal lag curve (拧腰送肩与离心滞后阻尼):
     * - Phase 1 (Lagged Windup): Torso leads while arms/weapon lag behind with cubic drag.
     * - Phase 2 (Explosive Release): Kinetic whip rapidly overtakes torso with high angular velocity.
     * - Phase 3 (Elastic Settle): Harmonic overshoot with quadratic damping decay.
     * Strictly guaranteed: f(0) == 0.0 and f(1) == 0.0.
     */
    public static float centrifugalLag(float t, float tWindup, float tStrike, float vWindup, float vStrike, float dampFactor) {
        if (t <= 0.0f || t >= 1.0f) return 0.0f;
        if (t <= tWindup) {
            float p = t / tWindup;
            return vWindup * (p * p * p);
        } else if (t <= tStrike) {
            float p = Ease.outExpo((t - tWindup) / (tStrike - tWindup));
            return vWindup + (vStrike - vWindup) * p;
        } else {
            float p = (t - tStrike) / (1.0f - tStrike);
            float decay = (1.0f - p) * (1.0f - p);
            return vStrike * decay * (1.0f + dampFactor * Mth.sin(p * (float) Math.PI));
        }
    }

    /**
     * Sinks the player's center of mass (pelvis and torso) downward into dynamic combat stances (Pelvis Dip).
     * In Minecraft model coordinates: y increases downward.
     *
     * @param model Model parts
     * @param dip   Dip amount in model units (1.0 to 4.5 units, approx 0.06m to 0.28m)
     */
    public static void applyPelvisDip(HumanoidModel<?> model, float dip) {
        if (Math.abs(dip) <= 0.0001f) return;
        model.body.y += dip;
        model.head.y += dip;
        model.rightArm.y += dip;
        model.leftArm.y += dip;
    }

    /* ========================================================================= */
    /*  THIRD-PERSON POSE CHOREOGRAPHY (HumanoidModel / PlayerModel)             */
    /* ========================================================================= */

    public static void applyThirdPersonAttack(
            @NotNull HumanoidModel<?> model,
            @NotNull AvatarRenderState state,
            @NotNull AttackComboRenderState comboState
    ) {
        float t = state.attackTime;
        if (t <= 0.0001f || t >= 1.0f) {
            return;
        }

        MCDWeaponType weapon = comboState.ourDungeonCraft$getWeaponType();
        int step = comboState.ourDungeonCraft$getComboStep();
        boolean isRightArm = (state.attackArm == HumanoidArm.RIGHT);

        switch (weapon) {
            case SWORD -> applySwordThirdPerson(model, t, step, isRightArm);
            case DOUBLE_AXE -> applyDoubleAxeThirdPerson(model, t, step, isRightArm);
            case GREAT_HAMMER -> applyGreatHammerThirdPerson(model, t, step, isRightArm);
            case CLAYMORE -> applyClaymoreThirdPerson(model, t, step, isRightArm);
            case DAGGERS -> applyDaggersThirdPerson(model, t, step, isRightArm);
            case SICKLES -> applySicklesThirdPerson(model, t, step, isRightArm);
            case GAUNTLETS -> applyGauntletsThirdPerson(model, t, step, isRightArm);
            case SPEAR, SPEAR_GLAIVE -> applySpearThirdPerson(model, t, step, isRightArm);
            case GLAIVE -> applyGlaiveThirdPerson(model, t, step, isRightArm);
            case MACE -> applyMaceThirdPerson(model, t, step, isRightArm);
            default -> applySwordThirdPerson(model, t, step, isRightArm);
        }
    }

    /* ------------------------------------------------------------------------- */
    /*  1. Sword Third-Person                                                    */
    /* ------------------------------------------------------------------------- */
    private static void applySwordThirdPerson(HumanoidModel<?> model, float t, int step, boolean rightArm) {
        ModelPart mainArm = rightArm ? model.rightArm : model.leftArm;
        ModelPart offArm = rightArm ? model.leftArm : model.rightArm;
        float armSign = rightArm ? 1.0f : -1.0f;

        switch (step % 3) {
            case 0 -> {
                // Step 0: Diagonal Forehand Slash (L -> R)
                float env = (float) Math.sin(t * Math.PI);
                applyPelvisDip(model, 1.4f * env);
                model.rightLeg.xRot = (rightArm ? -0.20f : 0.14f) * env;
                model.leftLeg.xRot = (rightArm ? 0.14f : -0.20f) * env;

                float twist = centrifugalLag(t, 0.16f, 0.52f, -0.24f, 0.54f, 0.28f) * armSign;
                model.body.yRot = twist;
                model.head.yRot -= twist * 0.65f;
                mainArm.z = -Mth.sin(twist) * 3.6f;
                offArm.z = Mth.sin(twist) * 3.6f;

                mainArm.xRot = kineticTrack(t, 0.16f, 0.52f, -0.48f, 0.58f);
                mainArm.yRot = kineticTrack(t, 0.16f, 0.52f, -0.48f, 0.88f) * armSign + twist;
                mainArm.zRot = kineticTrack(t, 0.16f, 0.52f, 0.16f, 0.42f) * armSign;

                offArm.xRot = kineticTrack(t, 0.16f, 0.52f, 0.14f, 0.40f);
                offArm.yRot = -0.25f * env * armSign - twist * 0.45f;
                offArm.zRot = -0.20f * env * armSign;
            }
            case 1 -> {
                // Step 1: Diagonal Backhand Cut (R -> L)
                float env = (float) Math.sin(t * Math.PI);
                applyPelvisDip(model, 1.4f * env);
                model.rightLeg.xRot = (rightArm ? 0.14f : -0.20f) * env;
                model.leftLeg.xRot = (rightArm ? -0.20f : 0.14f) * env;

                float twist = centrifugalLag(t, 0.14f, 0.48f, 0.24f, -0.56f, 0.28f) * armSign;
                model.body.yRot = twist;
                model.head.yRot -= twist * 0.65f;
                mainArm.z = -Mth.sin(twist) * 3.6f;
                offArm.z = Mth.sin(twist) * 3.6f;

                mainArm.xRot = kineticTrack(t, 0.14f, 0.48f, -0.38f, 0.48f);
                mainArm.yRot = kineticTrack(t, 0.14f, 0.48f, 0.48f, -0.88f) * armSign + twist;
                mainArm.zRot = kineticTrack(t, 0.14f, 0.48f, -0.16f, -0.38f) * armSign;

                offArm.xRot = kineticTrack(t, 0.14f, 0.48f, -0.12f, -0.32f);
                offArm.yRot = 0.25f * env * armSign - twist * 0.45f;
            }
            default -> {
                // Step 2: Overhead Heavy Cleave Finisher
                float chopWindow = window(t, 0.20f, 0.80f);
                float dip = 2.8f * (float) Math.sin(chopWindow * Math.PI);
                applyPelvisDip(model, dip);
                model.rightLeg.xRot = -0.26f * dip / 2.8f;
                model.leftLeg.xRot = -0.26f * dip / 2.8f;
                model.rightLeg.zRot = 0.08f * dip / 2.8f;
                model.leftLeg.zRot = -0.08f * dip / 2.8f;

                model.body.xRot = kineticRecoil(t, 0.22f, 0.52f, -0.30f, 0.55f, 0.35f);
                float armPitch = kineticRecoil(t, 0.22f, 0.52f, -1.72f, 1.40f, 0.30f);
                mainArm.xRot = armPitch;
                mainArm.yRot = 0.10f * (float) Math.sin(t * Math.PI) * armSign;

                offArm.xRot = armPitch * 0.92f;
                offArm.yRot = 0.35f * (float) Math.sin(t * Math.PI) * armSign;
            }
        }
    }

    /* ------------------------------------------------------------------------- */
    /*  2. Double Axe Third-Person                                               */
    /* ------------------------------------------------------------------------- */
    private static void applyDoubleAxeThirdPerson(HumanoidModel<?> model, float t, int step, boolean rightArm) {
        ModelPart mainArm = rightArm ? model.rightArm : model.leftArm;
        ModelPart offArm = rightArm ? model.leftArm : model.rightArm;
        float armSign = rightArm ? 1.0f : -1.0f;

        if (step % 2 == 0) {
            // Step 0: 360° Whirlwind Spin (Centrifugal pose smoothly blended from rest)
            float env = (float) Math.sin(t * Math.PI);
            applyPelvisDip(model, 2.4f * env);
            model.rightLeg.zRot = 0.18f * env;
            model.leftLeg.zRot = -0.18f * env;
            model.rightLeg.xRot = -0.15f * env;
            model.leftLeg.xRot = 0.15f * env;

            mainArm.xRot = -0.22f * env;
            mainArm.yRot = 1.45f * armSign * env;
            mainArm.zRot = 0.72f * armSign * env;

            offArm.xRot = -0.22f * env;
            offArm.yRot = -1.45f * armSign * env;
            offArm.zRot = -0.72f * armSign * env;

            // Centrifugal whole-body banking roll and slight forward posture
            model.body.zRot = Mth.sin(t * (float) Math.PI * 2.0f) * 0.18f * env;
            model.body.xRot = 0.12f * env;
        } else {
            // Step 1: Heavy Overhead Downward Cleave Finisher
            float cleaveWindow = window(t, 0.20f, 0.85f);
            float dip = 3.8f * (float) Math.sin(cleaveWindow * Math.PI);
            applyPelvisDip(model, dip);
            float factor = dip / 3.8f;
            model.rightLeg.xRot = -0.36f * factor;
            model.leftLeg.xRot = -0.36f * factor;
            model.rightLeg.zRot = 0.14f * factor;
            model.leftLeg.zRot = -0.14f * factor;

            model.body.xRot = kineticRecoil(t, 0.24f, 0.55f, -0.38f, 0.65f, 0.42f);
            float armPitch = kineticRecoil(t, 0.24f, 0.55f, -1.95f, 1.55f, 0.38f);
            mainArm.xRot = armPitch;
            offArm.xRot = armPitch * 0.96f;
            offArm.yRot = 0.38f * (float) Math.sin(t * Math.PI) * armSign;
        }
    }

    /* ------------------------------------------------------------------------- */
    /*  3. Great Hammer Third-Person                                             */
    /* ------------------------------------------------------------------------- */
    private static void applyGreatHammerThirdPerson(HumanoidModel<?> model, float t, int step, boolean rightArm) {
        ModelPart mainArm = rightArm ? model.rightArm : model.leftArm;
        ModelPart offArm = rightArm ? model.leftArm : model.rightArm;
        float armSign = rightArm ? 1.0f : -1.0f;

        if (step % 2 == 0) {
            // Step 0: Concussive Heavy Bash (Momentum Torso Twist & Lateral Swing)
            float env = (float) Math.sin(t * Math.PI);
            applyPelvisDip(model, 2.8f * env);

            float twist = centrifugalLag(t, 0.26f, 0.60f, -0.45f, 0.75f, 0.35f) * armSign;
            model.body.yRot = twist;
            model.body.xRot = kineticTrack(t, 0.26f, 0.60f, -0.20f, 0.42f);
            model.head.yRot -= twist * 0.70f;

            mainArm.z = -8.5f * env;
            mainArm.x = (rightArm ? -4.0f : 4.0f) * env;
            mainArm.xRot = kineticTrack(t, 0.26f, 0.60f, -1.55f, 1.25f);
            mainArm.yRot = kineticTrack(t, 0.26f, 0.60f, -0.45f, 0.60f) * armSign + twist;
            mainArm.zRot = kineticTrack(t, 0.26f, 0.60f, 0.25f, 0.32f) * armSign;

            offArm.xRot = mainArm.xRot * 0.88f;
            offArm.yRot = (0.45f * env) * armSign + twist * 0.5f;
            offArm.z = -mainArm.z * 0.35f;

            model.rightLeg.xRot = (rightArm ? -0.40f : 0.30f) * env;
            model.leftLeg.xRot = (rightArm ? 0.30f : -0.40f) * env;
            model.rightLeg.zRot = 0.12f * env;
            model.leftLeg.zRot = -0.12f * env;
        } else {
            // Step 1: Earthshaker Ground Smash Finisher (Maximum Pelvis Dip & Titanic Slam)
            float smashWindow = window(t, 0.22f, 0.88f);
            float dip = 4.2f * (float) Math.sin(smashWindow * Math.PI);
            applyPelvisDip(model, dip);

            float stomp = (float) Math.sin(smashWindow * Math.PI);
            model.rightLeg.xRot = -0.45f * stomp;
            model.leftLeg.xRot = -0.45f * stomp;
            model.rightLeg.zRot = 0.18f * stomp;
            model.leftLeg.zRot = -0.18f * stomp;

            model.body.xRot = kineticRecoil(t, 0.30f, 0.56f, -0.48f, 0.75f, 0.48f);
            float armPitch = kineticRecoil(t, 0.30f, 0.56f, -2.25f, 1.70f, 0.48f);
            mainArm.xRot = armPitch;
            offArm.xRot = armPitch;
            mainArm.yRot = -0.12f * armSign * (float) Math.sin(t * Math.PI);
            offArm.yRot = 0.12f * armSign * (float) Math.sin(t * Math.PI);
            mainArm.zRot = 0.15f * armSign * (float) Math.sin(t * Math.PI);
            offArm.zRot = -0.15f * armSign * (float) Math.sin(t * Math.PI);
            model.head.xRot = -model.body.xRot * 0.40f;
        }
    }

    /* ------------------------------------------------------------------------- */
    /*  4. Claymore Third-Person                                                 */
    /* ------------------------------------------------------------------------- */
    private static void applyClaymoreThirdPerson(HumanoidModel<?> model, float t, int step, boolean rightArm) {
        ModelPart mainArm = rightArm ? model.rightArm : model.leftArm;
        ModelPart offArm = rightArm ? model.leftArm : model.rightArm;
        float armSign = rightArm ? 1.0f : -1.0f;

        switch (step % 3) {
            case 0 -> {
                // Step 0: Colossal Horizontal Cleave Left (130°)
                float env = (float) Math.sin(t * Math.PI);
                applyPelvisDip(model, 3.0f * env);
                model.rightLeg.xRot = -0.30f * env;
                model.leftLeg.xRot = 0.20f * env;
                model.rightLeg.zRot = 0.14f * env;
                model.leftLeg.zRot = -0.14f * env;

                float twist = centrifugalLag(t, 0.25f, 0.60f, -0.52f, 0.85f, 0.32f) * armSign;
                model.body.yRot = twist;
                model.body.xRot = 0.18f * env;
                model.head.yRot -= twist * 0.70f;
                mainArm.z = -Mth.sin(twist) * 5.0f;
                offArm.z = Mth.sin(twist) * 5.0f;

                mainArm.xRot = kineticTrack(t, 0.25f, 0.60f, -0.45f, 0.35f);
                mainArm.yRot = kineticTrack(t, 0.25f, 0.60f, -1.25f, 1.45f) * armSign + twist;
                mainArm.zRot = 0.30f * env * armSign;

                offArm.xRot = mainArm.xRot + 0.08f;
                offArm.yRot = mainArm.yRot - 0.22f * armSign;
                offArm.zRot = mainArm.zRot * 0.85f;
            }
            case 1 -> {
                // Step 1: Return Colossal Horizontal Cleave Right (140°)
                float env = (float) Math.sin(t * Math.PI);
                applyPelvisDip(model, 3.0f * env);
                model.rightLeg.xRot = 0.20f * env;
                model.leftLeg.xRot = -0.30f * env;
                model.rightLeg.zRot = 0.14f * env;
                model.leftLeg.zRot = -0.14f * env;

                float twist = centrifugalLag(t, 0.24f, 0.58f, 0.52f, -0.90f, 0.32f) * armSign;
                model.body.yRot = twist;
                model.body.xRot = 0.18f * env;
                model.head.yRot -= twist * 0.70f;
                mainArm.z = -Mth.sin(twist) * 5.0f;
                offArm.z = Mth.sin(twist) * 5.0f;

                mainArm.xRot = kineticTrack(t, 0.24f, 0.58f, -0.45f, 0.35f);
                mainArm.yRot = kineticTrack(t, 0.24f, 0.58f, 1.25f, -1.55f) * armSign + twist;
                mainArm.zRot = -0.30f * env * armSign;

                offArm.xRot = mainArm.xRot + 0.08f;
                offArm.yRot = mainArm.yRot + 0.22f * armSign;
                offArm.zRot = mainArm.zRot * 0.85f;
            }
            default -> {
                // Step 2: Cataclysmic Ground Chop (150° Finisher)
                float chopWindow = window(t, 0.24f, 0.88f);
                float dip = 4.0f * (float) Math.sin(chopWindow * Math.PI);
                applyPelvisDip(model, dip);

                float stomp = (float) Math.sin(chopWindow * Math.PI);
                model.rightLeg.xRot = -0.42f * stomp;
                model.leftLeg.xRot = -0.42f * stomp;
                model.rightLeg.zRot = 0.15f * stomp;
                model.leftLeg.zRot = -0.15f * stomp;

                model.body.xRot = kineticRecoil(t, 0.28f, 0.55f, -0.45f, 0.68f, 0.45f);
                float armPitch = kineticRecoil(t, 0.28f, 0.55f, -2.15f, 1.55f, 0.42f);
                mainArm.xRot = armPitch;
                offArm.xRot = armPitch;
                mainArm.yRot = -0.10f * armSign * (float) Math.sin(t * Math.PI);
                offArm.yRot = 0.10f * armSign * (float) Math.sin(t * Math.PI);
                offArm.zRot = 0.12f * armSign * (float) Math.sin(t * Math.PI);
            }
        }
    }

    /* ------------------------------------------------------------------------- */
    /*  5. Daggers Third-Person                                                  */
    /* ------------------------------------------------------------------------- */
    private static void applyDaggersThirdPerson(HumanoidModel<?> model, float t, int step, boolean rightArm) {
        ModelPart leadArm = rightArm ? model.leftArm : model.rightArm;
        ModelPart rearArm = rightArm ? model.rightArm : model.leftArm;
        ModelPart leadLeg = rightArm ? model.leftLeg : model.rightLeg;
        ModelPart rearLeg = rightArm ? model.rightLeg : model.leftLeg;
        float armSign = rightArm ? 1.0f : -1.0f;

        switch (step % 4) {
            case 0 -> {
                // Step 0: Lead Quick Thrust (Ultra-fast piston thrust, Rear Coil Guard)
                float punch = pistonThrust(t, 0.18f);
                applyPelvisDip(model, 0.8f * punch);
                leadArm.xRot = -1.58f * punch;
                leadArm.z = -8.2f * punch;
                leadArm.yRot = -0.15f * punch * armSign;
                leadArm.zRot = 0.20f * punch * armSign;

                // Rear arm in high combat coil guard at chin (Z=+0.10m, Y=+0.05m equivalent)
                rearArm.xRot = -0.92f * punch;
                rearArm.yRot = 0.35f * punch * armSign;
                rearArm.z = 1.8f * punch;
                rearArm.y = -0.8f * punch;

                model.body.yRot = 0.35f * punch * armSign;
                model.head.yRot = -0.22f * punch * armSign;
                leadLeg.xRot = -0.24f * punch;
                rearLeg.xRot = 0.20f * punch;
            }
            case 1 -> {
                // Step 1: Rear Quick Thrust (Ultra-fast piston thrust, Lead Coil Guard)
                float punch = pistonThrust(t, 0.18f);
                applyPelvisDip(model, 0.8f * punch);
                rearArm.xRot = -1.58f * punch;
                rearArm.z = -8.2f * punch;
                rearArm.yRot = 0.15f * punch * armSign;
                rearArm.zRot = -0.20f * punch * armSign;

                // Lead arm in high combat coil guard at chin
                leadArm.xRot = -0.92f * punch;
                leadArm.yRot = -0.35f * punch * armSign;
                leadArm.z = 1.8f * punch;
                leadArm.y = -0.8f * punch;

                model.body.yRot = -0.35f * punch * armSign;
                model.head.yRot = 0.22f * punch * armSign;
                rearLeg.xRot = -0.24f * punch;
                leadLeg.xRot = 0.20f * punch;
            }
            case 2 -> {
                // Step 2: Cross-Slash Scissors Cut (Both daggers cross in front of chest)
                float slash = (float) Math.sin(t * Math.PI);
                applyPelvisDip(model, 1.4f * slash);

                model.rightArm.xRot = -1.25f * slash;
                model.rightArm.yRot = -0.75f * slash;
                model.rightArm.zRot = 0.35f * slash;
                model.rightArm.z = -4.0f * slash;

                model.leftArm.xRot = -1.25f * slash;
                model.leftArm.yRot = 0.75f * slash;
                model.leftArm.zRot = -0.35f * slash;
                model.leftArm.z = -4.0f * slash;

                model.body.xRot = 0.18f * slash;
                model.rightLeg.xRot = -0.15f * slash;
                model.leftLeg.xRot = 0.15f * slash;
            }
            default -> {
                // Step 3: Dual Plunge Finisher (Both blades converge and plunge deep)
                float plunge = pistonThrust(t, 0.26f);
                applyPelvisDip(model, 2.4f * plunge);

                model.rightArm.xRot = -1.68f * plunge;
                model.leftArm.xRot = -1.68f * plunge;
                model.rightArm.z = -9.2f * plunge;
                model.leftArm.z = -9.2f * plunge;
                model.rightArm.yRot = -0.18f * plunge;
                model.leftArm.yRot = 0.18f * plunge;
                model.rightArm.zRot = -0.15f * plunge;
                model.leftArm.zRot = 0.15f * plunge;

                model.body.xRot = 0.42f * plunge;
                leadLeg.xRot = -0.38f * plunge;
                rearLeg.xRot = 0.30f * plunge;
            }
        }
    }

    /* ------------------------------------------------------------------------- */
    /*  5b. Sickles Third-Person                                                 */
    /* ------------------------------------------------------------------------- */
    private static void applySicklesThirdPerson(HumanoidModel<?> model, float t, int step, boolean rightArm) {
        ModelPart mainArm = rightArm ? model.rightArm : model.leftArm;
        ModelPart offArm = rightArm ? model.leftArm : model.rightArm;
        float armSign = rightArm ? 1.0f : -1.0f;

        switch (step % 4) {
            case 0 -> {
                // Step 0: Crescent Diagonal Reaping Slash L
                float env = (float) Math.sin(t * Math.PI);
                applyPelvisDip(model, 1.4f * env);

                float twist = centrifugalLag(t, 0.15f, 0.52f, -0.22f, 0.48f, 0.26f) * armSign;
                model.body.yRot = twist;
                model.head.yRot -= twist * 0.65f;

                mainArm.xRot = kineticTrack(t, 0.15f, 0.52f, -0.45f, 0.50f);
                mainArm.yRot = kineticTrack(t, 0.15f, 0.52f, -0.45f, 0.85f) * armSign + twist;
                offArm.xRot = kineticTrack(t, 0.15f, 0.52f, 0.12f, 0.32f);
                offArm.yRot = -twist * 0.40f;
            }
            case 1 -> {
                // Step 1: Inward Backhand Reaping Sweep R
                float env = (float) Math.sin(t * Math.PI);
                applyPelvisDip(model, 1.4f * env);

                float twist = centrifugalLag(t, 0.14f, 0.50f, 0.22f, -0.50f, 0.26f) * armSign;
                model.body.yRot = twist;
                model.head.yRot -= twist * 0.65f;

                mainArm.xRot = kineticTrack(t, 0.14f, 0.50f, -0.35f, 0.45f);
                mainArm.yRot = kineticTrack(t, 0.14f, 0.50f, 0.45f, -0.85f) * armSign + twist;
                offArm.xRot = kineticTrack(t, 0.14f, 0.50f, -0.10f, -0.25f);
                offArm.yRot = -twist * 0.40f;
            }
            case 2 -> {
                // Step 2: Hook Pull & Vacuum Reel (Reaches forward then bites and violently yanks inward)
                float reach = Ease.outCubic(window(t, 0.0f, 0.30f)) * (1.0f - Ease.outExpo(window(t, 0.30f, 0.65f)));
                float hookPull = Ease.outExpo(window(t, 0.30f, 0.65f)) * (1.0f - Ease.outCubic(window(t, 0.65f, 1.0f)));

                applyPelvisDip(model, 1.4f * reach + 2.4f * hookPull);
                mainArm.xRot = -1.48f * reach + 0.88f * hookPull;
                mainArm.z = -8.2f * reach + 3.8f * hookPull;
                mainArm.yRot = (-0.35f * reach + 0.70f * hookPull) * armSign;
                mainArm.zRot = (0.25f * reach - 0.50f * hookPull) * armSign;

                offArm.xRot = -1.48f * reach + 0.88f * hookPull;
                offArm.z = -8.2f * reach + 3.8f * hookPull;
                offArm.yRot = (0.35f * reach - 0.70f * hookPull) * armSign;
                offArm.zRot = (-0.25f * reach + 0.50f * hookPull) * armSign;

                model.body.xRot = 0.16f * reach - 0.34f * hookPull; // Leans back to haul monsters in
                model.rightLeg.xRot = -0.28f * hookPull;
                model.leftLeg.xRot = 0.28f * hookPull;
            }
            default -> {
                // Step 3: 360° Reaping Whirlwind (Centrifugal spin pose)
                float env = (float) Math.sin(t * Math.PI);
                applyPelvisDip(model, 1.8f * env);
                mainArm.xRot = -0.25f * env;
                mainArm.yRot = 1.35f * armSign * env;
                mainArm.zRot = 0.60f * armSign * env;

                offArm.xRot = -0.25f * env;
                offArm.yRot = -1.35f * armSign * env;
                offArm.zRot = -0.60f * armSign * env;

                model.body.zRot = Mth.sin(t * (float) Math.PI * 2.0f) * 0.12f * env;
            }
        }
    }

    /* ------------------------------------------------------------------------- */
    /*  6. Gauntlets Third-Person                                                */
    /* ------------------------------------------------------------------------- */
    private static void applyGauntletsThirdPerson(HumanoidModel<?> model, float t, int step, boolean rightArm) {
        ModelPart leadArm = rightArm ? model.leftArm : model.rightArm;
        ModelPart rearArm = rightArm ? model.rightArm : model.leftArm;
        ModelPart mainArm = rightArm ? model.rightArm : model.leftArm;
        ModelPart offArm = rightArm ? model.leftArm : model.rightArm;
        ModelPart leadLeg = rightArm ? model.leftLeg : model.rightLeg;
        ModelPart rearLeg = rightArm ? model.rightLeg : model.leftLeg;
        float armSign = rightArm ? 1.0f : -1.0f;

        switch (step % 3) {
            case 0 -> {
                // Step 0: Lead Straight Jab (Ultra-fast piston thrust with corkscrew wrist rotation)
                float punch = pistonThrust(t, 0.18f);
                applyPelvisDip(model, 0.8f * punch);
                leadArm.xRot = -1.58f * punch;
                leadArm.z = -8.5f * punch;
                leadArm.zRot = 0.65f * punch * armSign; // Corkscrew pronation

                // Rear arm in tight high chin guard
                rearArm.xRot = -1.20f * punch;
                rearArm.yRot = 0.35f * punch * armSign;
                rearArm.z = 2.0f * punch;
                rearArm.y = -1.0f * punch;

                model.body.yRot = 0.42f * punch * armSign;
                model.head.yRot = -0.28f * punch * armSign;
                leadLeg.xRot = -0.25f * punch;
                rearLeg.xRot = 0.20f * punch;
            }
            case 1 -> {
                // Step 1: Power Cross Punch (Torso drive, explosive cross through center line)
                float punch = pistonThrust(t, 0.22f);
                applyPelvisDip(model, 1.2f * punch);
                rearArm.xRot = -1.58f * punch;
                rearArm.z = -9.5f * punch;
                rearArm.zRot = -0.70f * punch * armSign;
                rearArm.x = (rightArm ? -5.0f : 5.0f) + 3.0f * punch * armSign; // Explosive shoulder drive

                // Lead arm in cheek guard
                leadArm.xRot = -1.20f * punch;
                leadArm.yRot = -0.35f * punch * armSign;
                leadArm.z = 2.0f * punch;
                leadArm.y = -1.0f * punch;

                model.body.yRot = -0.52f * punch * armSign;
                model.head.yRot = 0.32f * punch * armSign;
                rearLeg.xRot = -0.32f * punch;
                leadLeg.xRot = 0.25f * punch;
            }
            default -> {
                // Step 2: Rocket Uppercut Finisher (Deep crouch anticipation -> Explosive vertical launch)
                float duck = Ease.inQuad(window(t, 0.0f, 0.20f)) * (1.0f - Ease.outExpo(window(t, 0.20f, 0.50f)));
                float explode = Ease.outExpo(window(t, 0.20f, 0.50f)) * (1.0f - Ease.outCubic(window(t, 0.50f, 1.0f)));

                // Crouch phase
                applyPelvisDip(model, 3.6f * duck);
                model.rightLeg.xRot = -0.38f * duck;
                model.leftLeg.xRot = -0.38f * duck;

                // Explosive launch: body rockets vertically into the air (Pelvis Dip inverted into negative lift)
                float lift = -7.5f * explode;
                model.body.y += lift;
                model.head.y += lift;
                model.rightArm.y += lift;
                model.leftArm.y += lift;
                model.rightLeg.y = 12.0f + lift;
                model.leftLeg.y = 12.0f + lift;

                // Main arm rockets straight UP toward zenith
                mainArm.xRot = -0.30f * duck - 2.65f * explode;
                mainArm.z = 1.0f * duck - 2.8f * explode;
                mainArm.yRot = -0.15f * armSign * explode;
                mainArm.zRot = 0.25f * armSign * explode;

                // Off arm counter-braces for balance
                offArm.xRot = -0.95f * explode;
                offArm.zRot = -0.35f * armSign * explode;
                model.body.xRot = 0.25f * duck - 0.35f * explode;

                // Airborne knee lift / trail leg dynamic pose
                model.rightLeg.xRot += (rightArm ? 0.45f : -0.28f) * explode;
                model.leftLeg.xRot += (rightArm ? -0.28f : 0.45f) * explode;
            }
        }
    }

    /* ------------------------------------------------------------------------- */
    /*  7. Spear Third-Person                                                    */
    /* ------------------------------------------------------------------------- */
    private static void applySpearThirdPerson(HumanoidModel<?> model, float t, int step, boolean rightArm) {
        ModelPart mainArm = rightArm ? model.rightArm : model.leftArm;
        ModelPart offArm = rightArm ? model.leftArm : model.rightArm;

        switch (step % 3) {
            case 0 -> {
                // Step 0: Rapid Linear Jab 1 (Spear Stance & High-Speed Linear Thrust)
                float stroke = pistonThrust(t, 0.26f);
                applyPelvisDip(model, 2.2f * stroke);
                mainArm.xRot = -1.60f * stroke;
                mainArm.z = -9.5f * stroke;
                offArm.xRot = -1.50f * stroke;
                offArm.z = -6.0f * stroke;
                model.body.xRot = 0.25f * stroke;

                model.rightLeg.xRot = (rightArm ? -0.42f : 0.32f) * stroke;
                model.leftLeg.xRot = (rightArm ? 0.32f : -0.42f) * stroke;
                model.rightLeg.z = (rightArm ? -2.0f : 2.0f) * stroke;
                model.leftLeg.z = (rightArm ? 2.0f : -2.0f) * stroke;
            }
            case 1 -> {
                // Step 1: Rapid Linear Jab 2 (Alternating Foot Drive)
                float stroke = pistonThrust(t, 0.24f);
                applyPelvisDip(model, 2.2f * stroke);
                mainArm.xRot = -1.62f * stroke;
                mainArm.z = -10.0f * stroke;
                offArm.xRot = -1.52f * stroke;
                offArm.z = -6.5f * stroke;
                model.body.xRot = 0.28f * stroke;

                model.rightLeg.xRot = (rightArm ? 0.32f : -0.42f) * stroke;
                model.leftLeg.xRot = (rightArm ? -0.42f : 0.32f) * stroke;
                model.rightLeg.z = (rightArm ? 2.0f : -2.0f) * stroke;
                model.leftLeg.z = (rightArm ? -2.0f : 2.0f) * stroke;
            }
            default -> {
                // Step 2: Extended Power Lunge Finisher (5.5m Extreme Piercing Lance)
                float stroke = pistonThrust(t, 0.32f);
                applyPelvisDip(model, 4.0f * stroke);
                mainArm.xRot = -1.68f * stroke;
                mainArm.z = -13.5f * stroke;
                offArm.xRot = -1.56f * stroke;
                offArm.z = -8.5f * stroke;
                model.body.xRot = 0.45f * stroke;

                if (rightArm) {
                    model.rightLeg.xRot = -0.68f * stroke;
                    model.rightLeg.z = -4.5f * stroke;
                    model.leftLeg.xRot = 0.58f * stroke;
                    model.leftLeg.z = 4.5f * stroke;
                } else {
                    model.leftLeg.xRot = -0.68f * stroke;
                    model.leftLeg.z = -4.5f * stroke;
                    model.rightLeg.xRot = 0.58f * stroke;
                    model.rightLeg.z = 4.5f * stroke;
                }
            }
        }
    }

    /* ------------------------------------------------------------------------- */
    /*  7b. Glaive Third-Person                                                  */
    /* ------------------------------------------------------------------------- */
    private static void applyGlaiveThirdPerson(HumanoidModel<?> model, float t, int step, boolean rightArm) {
        ModelPart mainArm = rightArm ? model.rightArm : model.leftArm;
        ModelPart offArm = rightArm ? model.leftArm : model.rightArm;
        float armSign = rightArm ? 1.0f : -1.0f;

        switch (step % 3) {
            case 0 -> {
                // Step 0: Wide Arc Cleave L (125° Sweeping Polearm Stance)
                float env = (float) Math.sin(t * Math.PI);
                applyPelvisDip(model, 2.6f * env);
                model.rightLeg.xRot = -0.24f * env;
                model.leftLeg.xRot = -0.24f * env;
                model.rightLeg.zRot = 0.12f * env;
                model.leftLeg.zRot = -0.12f * env;

                float twist = centrifugalLag(t, 0.22f, 0.58f, -0.32f, 0.82f, 0.30f) * armSign;
                model.body.yRot = twist;
                model.head.yRot -= twist * 0.68f;
                mainArm.z = -Mth.sin(twist) * 4.0f;
                offArm.z = Mth.sin(twist) * 4.0f;
                mainArm.xRot = kineticTrack(t, 0.22f, 0.58f, -0.45f, 0.32f);
                mainArm.yRot = kineticTrack(t, 0.22f, 0.58f, -1.18f, 1.38f) * armSign + twist;
                offArm.xRot = mainArm.xRot + 0.12f;
                offArm.yRot = mainArm.yRot - 0.28f * armSign;
            }
            case 1 -> {
                // Step 1: Wide Arc Cleave R (135° Return Polearm Sweep)
                float env = (float) Math.sin(t * Math.PI);
                applyPelvisDip(model, 2.6f * env);
                model.rightLeg.xRot = -0.24f * env;
                model.leftLeg.xRot = -0.24f * env;
                model.rightLeg.zRot = 0.12f * env;
                model.leftLeg.zRot = -0.12f * env;

                float twist = centrifugalLag(t, 0.20f, 0.56f, 0.32f, -0.90f, 0.30f) * armSign;
                model.body.yRot = twist;
                model.head.yRot -= twist * 0.68f;
                mainArm.z = -Mth.sin(twist) * 4.0f;
                offArm.z = Mth.sin(twist) * 4.0f;
                mainArm.xRot = kineticTrack(t, 0.20f, 0.56f, -0.45f, 0.32f);
                mainArm.yRot = kineticTrack(t, 0.20f, 0.56f, 1.18f, -1.48f) * armSign + twist;
                offArm.xRot = mainArm.xRot + 0.12f;
                offArm.yRot = mainArm.yRot + 0.28f * armSign;
            }
            default -> {
                // Step 2: Sweeping Piercing Lunge Finisher (Wide Sweep into 5.2m Linear Thrust)
                float stroke = pistonThrust(t, 0.30f);
                applyPelvisDip(model, 3.8f * stroke);
                mainArm.xRot = -1.62f * stroke;
                mainArm.z = -12.5f * stroke;
                offArm.xRot = -1.52f * stroke;
                offArm.z = -8.0f * stroke;
                model.body.xRot = 0.42f * stroke;

                if (rightArm) {
                    model.rightLeg.xRot = -0.62f * stroke;
                    model.rightLeg.z = -3.8f * stroke;
                    model.leftLeg.xRot = 0.52f * stroke;
                    model.leftLeg.z = 3.8f * stroke;
                } else {
                    model.leftLeg.xRot = -0.62f * stroke;
                    model.leftLeg.z = -3.8f * stroke;
                    model.rightLeg.xRot = 0.52f * stroke;
                    model.rightLeg.z = 3.8f * stroke;
                }
            }
        }
    }

    /* ------------------------------------------------------------------------- */
    /*  8. Mace Third-Person                                                     */
    /* ------------------------------------------------------------------------- */
    private static void applyMaceThirdPerson(HumanoidModel<?> model, float t, int step, boolean rightArm) {
        ModelPart mainArm = rightArm ? model.rightArm : model.leftArm;
        ModelPart offArm = rightArm ? model.leftArm : model.rightArm;
        float armSign = rightArm ? 1.0f : -1.0f;

        switch (step % 3) {
            case 0 -> {
                // Step 0: Downward Concussive Bash
                float env = (float) Math.sin(t * Math.PI);
                applyPelvisDip(model, 1.6f * env);

                float twist = kineticTrack(t, 0.18f, 0.54f, -0.18f, 0.38f) * armSign;
                model.body.yRot = twist;
                model.body.xRot = kineticTrack(t, 0.18f, 0.54f, -0.12f, 0.25f);

                mainArm.xRot = kineticTrack(t, 0.18f, 0.54f, -0.95f, 0.95f);
                mainArm.yRot = kineticTrack(t, 0.18f, 0.54f, -0.25f, 0.40f) * armSign;
                mainArm.zRot = kineticTrack(t, 0.18f, 0.54f, 0.20f, -0.25f) * armSign;

                offArm.xRot = kineticTrack(t, 0.18f, 0.54f, 0.14f, 0.38f);
                offArm.yRot = -twist * 0.70f;
            }
            case 1 -> {
                // Step 1: Backhand Concussion Strike
                float env = (float) Math.sin(t * Math.PI);
                applyPelvisDip(model, 1.6f * env);

                float twist = kineticTrack(t, 0.16f, 0.52f, 0.20f, -0.45f) * armSign;
                model.body.yRot = twist;
                model.head.yRot -= twist * 0.65f;

                mainArm.xRot = kineticTrack(t, 0.16f, 0.52f, -0.45f, 0.45f);
                mainArm.yRot = kineticTrack(t, 0.16f, 0.52f, 0.70f, -0.90f) * armSign;
                offArm.xRot = kineticTrack(t, 0.16f, 0.52f, -0.12f, -0.28f);
            }
            default -> {
                // Step 2: Heavy Overhead Skull-Crusher Slam (Finisher)
                float slamWindow = window(t, 0.22f, 0.82f);
                float dip = 3.4f * (float) Math.sin(slamWindow * Math.PI);
                applyPelvisDip(model, dip);
                model.rightLeg.xRot = -0.30f * dip / 3.4f;
                model.leftLeg.xRot = -0.30f * dip / 3.4f;

                model.body.xRot = kineticRecoil(t, 0.24f, 0.54f, -0.32f, 0.58f, 0.42f);
                float armPitch = kineticRecoil(t, 0.24f, 0.54f, -1.85f, 1.35f, 0.40f);
                mainArm.xRot = armPitch;
                offArm.xRot = armPitch * 0.90f;
                offArm.yRot = 0.35f * (float) Math.sin(t * Math.PI) * armSign;
            }
        }
    }

    /* ========================================================================= */
    /*  WHOLE-BODY ROTATIONS (AvatarRenderer.setupRotations)                     */
    /* ========================================================================= */

    public static void applyFullBodyRotation(
            @NotNull AvatarRenderState state,
            @NotNull PoseStack poseStack,
            @NotNull AttackComboRenderState comboState
    ) {
        if (!comboState.ourDungeonCraft$isCustomAttacking()) {
            return;
        }

        if (comboState.ourDungeonCraft$getWeaponType() == MCDWeaponType.DOUBLE_AXE
                && comboState.ourDungeonCraft$getComboStep() % 2 == 0) {
            float t = state.attackTime;
            if (t > 0.0001f && t < 1.0f) {
                float spinAngle = 360.0f * Ease.inOutCubic(t);
                poseStack.mulPose(Axis.YP.rotationDegrees(-spinAngle));
            }
        }

        if (comboState.ourDungeonCraft$getWeaponType() == MCDWeaponType.SICKLES
                && comboState.ourDungeonCraft$getComboStep() % 4 == 3) {
            float t = state.attackTime;
            if (t > 0.0001f && t < 1.0f) {
                float spinAngle = 360.0f * Ease.inOutCubic(t);
                poseStack.mulPose(Axis.YP.rotationDegrees(-spinAngle));
            }
        }
    }

    /* ========================================================================= */
    /*  FIRST-PERSON CAMERA KINETIC FEEDBACK (CameraRollMixin)                  */
    /* ========================================================================= */

    /* ========================================================================= */
    /*  FIRST-PERSON CAMERA KINETIC FEEDBACK (CameraRollMixin)                  */
    /* ========================================================================= */

    /**
     * Applies camera momentum transfer (impulse, centrifugal banking, vertical dip, elastic recoil)
     * according to COMBAT_SYSTEM_SPECIFICATION.md Section 4.1.
     *
     * @param weapon      Weapon type
     * @param step        Current combo step
     * @param attackAnim  Normalized attack animation progress [0..1]
     * @param rotation    Camera rotation quaternion to apply pitch/roll/yaw impulses
     * @return First-person vertical camera position displacement in meters (dipY)
     */
    public static double applyCameraFeedback(
            @NotNull MCDWeaponType weapon,
            int step,
            float attackAnim,
            @NotNull Quaternionf rotation
    ) {
        if (attackAnim <= 0.0001f || attackAnim >= 1.0f) {
            return 0.0;
        }

        double dipY = 0.0;

        switch (weapon) {
            case GREAT_HAMMER -> {
                if (step % 2 == 0) { // Concussive Lateral Bash
                    float stroke = pistonThrust(attackAnim, 0.26f);
                    rotation.rotateX((float) Math.toRadians(-1.5f * stroke));
                    rotation.rotateZ((float) Math.toRadians(1.2f * stroke));
                    dipY = -0.06 * stroke;
                } else { // Earthshaker Ground Smash Finisher
                    dipY = calculateHeavyCleaveCameraFeedback(attackAnim, 1.8f, -4.8f, 2.4f, -0.16, rotation);
                    // High-frequency ground impact seismic micro-shake
                    float impactWindow = window(attackAnim, 0.45f, 0.85f);
                    if (impactWindow > 0.0f && impactWindow < 1.0f) {
                        float shakeDecay = (1.0f - impactWindow);
                        rotation.rotateZ((float) Math.toRadians(0.55f * Mth.sin(attackAnim * 95.0f) * shakeDecay));
                    }
                }
            }
            case CLAYMORE -> {
                if (step % 3 == 2) { // Cataclysmic Ground Chop Finisher (Mountain Splitter)
                    dipY = calculateHeavyCleaveCameraFeedback(attackAnim, 1.4f, -4.5f, 2.0f, -0.14, rotation);
                    float impactWindow = window(attackAnim, 0.48f, 0.82f);
                    if (impactWindow > 0.0f && impactWindow < 1.0f) {
                        float shakeDecay = (1.0f - impactWindow);
                        rotation.rotateZ((float) Math.toRadians(0.35f * Mth.sin(attackAnim * 80.0f) * shakeDecay));
                    }
                } else { // Colossal Horizontal Cleaves
                    float env = (float) Math.sin(attackAnim * Math.PI);
                    float rollSign = (step % 3 == 0) ? 1.0f : -1.0f;
                    rotation.rotateZ((float) Math.toRadians(rollSign * 2.2f * env));
                    rotation.rotateX((float) Math.toRadians(-1.2f * env));
                    dipY = -0.05 * env;
                }
            }
            case DOUBLE_AXE -> {
                if (step % 2 == 0) { // 360 Whirlwind Spin (Centrifugal Bank)
                    // Section 4.1 & 7.1: Delta Roll = -3.2° * sin(t * 2pi)
                    float bankRoll = -3.2f * Mth.sin(attackAnim * (float) Math.PI * 2.0f);
                    rotation.rotateZ((float) Math.toRadians(bankRoll));
                    dipY = -0.06 * Math.sin(attackAnim * Math.PI);
                } else { // Heavy Overhead Downward Cleave Finisher
                    dipY = calculateHeavyCleaveCameraFeedback(attackAnim, 1.2f, -3.8f, 1.8f, -0.12, rotation);
                }
            }
            case SICKLES -> {
                if (step % 4 == 3) { // 360 Whirlwind Spin (Centrifugal Bank)
                    float bankRoll = -2.5f * Mth.sin(attackAnim * (float) Math.PI * 2.0f);
                    rotation.rotateZ((float) Math.toRadians(bankRoll));
                    dipY = -0.04 * Math.sin(attackAnim * Math.PI);
                } else if (step % 4 == 2) { // Inward Hook Pull & Vacuum Reel
                    float reach = Ease.outCubic(window(attackAnim, 0.0f, 0.30f)) * (1.0f - Ease.outExpo(window(attackAnim, 0.30f, 0.65f)));
                    float hookPull = Ease.outExpo(window(attackAnim, 0.30f, 0.65f)) * (1.0f - Ease.outCubic(window(attackAnim, 0.65f, 1.0f)));
                    rotation.rotateX((float) Math.toRadians(-1.2f * reach + 1.8f * hookPull));
                    dipY = -0.02 * reach - 0.05 * hookPull;
                } else { // Crescent Diagonal Reaping
                    float env = (float) Math.sin(attackAnim * Math.PI);
                    float rollSign = (step % 4 == 0) ? 1.2f : -1.2f;
                    rotation.rotateZ((float) Math.toRadians(rollSign * env));
                    dipY = -0.02 * env;
                }
            }
            case GAUNTLETS -> {
                if (step % 3 == 0) { // Lead Straight Jab
                    float thrust = pistonThrust(attackAnim, 0.18f);
                    rotation.rotateX((float) Math.toRadians(-0.7f * thrust));
                    dipY = -0.02 * thrust;
                } else if (step % 3 == 1) { // Power Cross Punch
                    float thrust = pistonThrust(attackAnim, 0.22f);
                    rotation.rotateX((float) Math.toRadians(-1.0f * thrust));
                    rotation.rotateZ((float) Math.toRadians(-1.4f * thrust));
                    dipY = -0.03 * thrust;
                } else { // Rocket Uppercut Finisher
                    if (attackAnim <= 0.20f) {
                        float p = attackAnim / 0.20f;
                        float duck = (float) Math.sin(p * Math.PI * 0.5);
                        rotation.rotateX((float) Math.toRadians(1.2f * duck));
                        dipY = -0.07 * duck;
                    } else if (attackAnim <= 0.56f) {
                        float p = (attackAnim - 0.20f) / 0.36f;
                        float explode = (float) Math.sin(p * Math.PI);
                        rotation.rotateX((float) Math.toRadians(-3.2f * explode));
                        dipY = 0.12 * explode; // Rocket skyward!
                    } else {
                        float p = (attackAnim - 0.56f) / 0.44f;
                        float settle = (1.0f - Ease.outCubic(p));
                        rotation.rotateX((float) Math.toRadians(-0.6f * settle));
                    }
                }
            }
            case MACE -> {
                if (step % 3 == 2) { // Overhead Skull Crusher Finisher
                    dipY = calculateHeavyCleaveCameraFeedback(attackAnim, 1.2f, -3.2f, 1.6f, -0.10, rotation);
                } else if (step % 3 == 0) { // Downward Concussive Bash
                    float env = (float) Math.sin(attackAnim * Math.PI);
                    rotation.rotateX((float) Math.toRadians(-1.4f * env));
                    dipY = -0.05 * env;
                } else { // Backhand Concussion Strike
                    float env = (float) Math.sin(attackAnim * Math.PI);
                    rotation.rotateZ((float) Math.toRadians(1.4f * env));
                    dipY = -0.03 * env;
                }
            }
            case SPEAR, SPEAR_GLAIVE -> {
                if (step % 3 == 2) { // Extended Power Lunge Finisher (5.5m Piercing Lance)
                    float thrust = pistonThrust(attackAnim, 0.32f);
                    rotation.rotateX((float) Math.toRadians(-2.5f * thrust));
                    rotation.rotateZ((float) Math.toRadians(0.5f * thrust));
                    dipY = -0.08 * thrust;
                } else { // Rapid Linear Jab
                    float thrust = pistonThrust(attackAnim, 0.26f);
                    rotation.rotateX((float) Math.toRadians(-1.2f * thrust));
                    dipY = -0.04 * thrust;
                }
            }
            case GLAIVE -> {
                if (step % 3 == 2) { // Sweeping Piercing Lunge Finisher
                    float thrust = pistonThrust(attackAnim, 0.30f);
                    rotation.rotateX((float) Math.toRadians(-2.2f * thrust));
                    rotation.rotateZ((float) Math.toRadians(1.4f * (1.0f - thrust)));
                    dipY = -0.07 * thrust;
                } else { // Wide Arc Cleave
                    float env = (float) Math.sin(attackAnim * Math.PI);
                    float rollSign = (step % 3 == 0) ? 1.8f : -1.8f;
                    rotation.rotateZ((float) Math.toRadians(rollSign * env));
                    rotation.rotateX((float) Math.toRadians(-1.0f * env));
                    dipY = -0.04 * env;
                }
            }
            case DAGGERS -> {
                if (step % 4 == 3) { // Dual Plunge Finisher
                    float plunge = pistonThrust(attackAnim, 0.26f);
                    rotation.rotateX((float) Math.toRadians(-2.4f * plunge));
                    dipY = -0.08 * plunge;
                } else if (step % 4 == 2) { // Scissors Cross-Cut
                    float slash = (float) Math.sin(attackAnim * Math.PI);
                    rotation.rotateX((float) Math.toRadians(-1.2f * slash));
                    dipY = -0.03 * slash;
                } else { // Ultra-fast Stabs
                    float thrust = pistonThrust(attackAnim, 0.18f);
                    rotation.rotateX((float) Math.toRadians(-0.6f * thrust));
                    dipY = -0.015 * thrust;
                }
            }
            case SWORD -> {
                if (step % 3 == 2) { // Overhead Heavy Cleave Finisher
                    dipY = calculateHeavyCleaveCameraFeedback(attackAnim, 0.8f, -2.6f, 1.3f, -0.08, rotation);
                } else { // Diagonal Slashes
                    float slash = (float) Math.sin(attackAnim * Math.PI);
                    float rollSign = (step % 3 == 0) ? 1.4f : -1.4f;
                    rotation.rotateZ((float) Math.toRadians(rollSign * slash));
                    rotation.rotateX((float) Math.toRadians(-0.7f * slash));
                    dipY = -0.02 * slash;
                }
            }
            default -> {}
        }

        return dipY;
    }

    /**
     * Standard Heavy Downward Cleave camera momentum curve:
     * - Anticipation (t in [0.0, 0.25]): sight leans slightly back (+pitchWindup), center of mass coils back.
     * - Release (t in [0.25, 0.50]): sight plunges down (-pitchStrike), center of mass sinks (dipMax).
     * - Elastic Recoil (t in [0.50, 0.85]): ground impact bounces back (+pitchRecoil) with quadratic damping decay.
     */
    private static double calculateHeavyCleaveCameraFeedback(
            float t,
            float pitchWindup,
            float pitchStrike,
            float pitchRecoil,
            double dipMax,
            Quaternionf rotation
    ) {
        float pitch;
        double dipY;

        if (t <= 0.25f) {
            float p = t / 0.25f;
            float curve = (float) Math.sin(p * Math.PI * 0.5);
            pitch = pitchWindup * curve;
            dipY = -dipMax * 0.15 * curve;
        } else if (t <= 0.50f) {
            float p = (t - 0.25f) / 0.25f;
            pitch = pitchWindup * (1.0f - p) + pitchStrike * p;
            dipY = dipMax * Math.sin(p * Math.PI * 0.5);
        } else if (t <= 0.85f) {
            float p = (t - 0.50f) / 0.35f;
            float decay = (1.0f - p) * (1.0f - p);
            pitch = (pitchStrike * (1.0f - p) + pitchRecoil * Mth.sin(p * (float) Math.PI)) * decay;
            dipY = dipMax * decay;
        } else {
            pitch = 0.0f;
            dipY = 0.0;
        }

        rotation.rotateX((float) Math.toRadians(pitch));
        return dipY;
    }

    /* ========================================================================= */
    /*  FIRST-PERSON ATTACK CHOREOGRAPHY (ItemInHandRenderer)                    */
    /* ========================================================================= */

    public static boolean shouldApplyFirstPerson(
            @NotNull AbstractClientPlayer player,
            @NotNull ItemStack stack
    ) {
        if (!(player instanceof AttackComboAccessor accessor)) {
            return false;
        }
        AttackComboData data = accessor.ourDungeonCraft$getComboData();
        return data.isAttacking() || player.getAttackAnim(1.0f) > 0.0001f;
    }

    /**
     * Determines whether an item stack corresponds to a dual-wield weapon
     * (Daggers, Sickles, Gauntlets) that requires simultaneous virtual off-hand rendering
     * and coordinated two-hand alternating choreography.
     */
    public static boolean isDualWieldWeapon(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof DungeonWeaponItem weapon) {
            MCDWeaponType type = weapon.getWeaponType();
            return type == MCDWeaponType.DAGGERS || type == MCDWeaponType.SICKLES || type == MCDWeaponType.GAUNTLETS;
        }
        return false;
    }

    public static void applyFirstPersonAttack(
            @NotNull ItemStack stack,
            float swingProgress,
            @NotNull PoseStack poseStack,
            int side,
            @NotNull HumanoidArm arm,
            @NotNull AbstractClientPlayer player
    ) {
        if (swingProgress <= 0.0001f || swingProgress >= 1.0f) {
            return;
        }

        AttackComboData comboData = ((AttackComboAccessor) player).ourDungeonCraft$getComboData();
        MCDWeaponType weapon = comboData.getWeaponType();
        if (weapon == null && stack.getItem() instanceof DungeonWeaponItem dwi) {
            weapon = dwi.getWeaponType();
        }
        if (weapon == null) {
            weapon = MCDWeaponType.SWORD;
        }
        int step = comboData.getComboStep();

        switch (weapon) {
            case SWORD -> applySwordFirstPerson(poseStack, swingProgress, step, side);
            case DOUBLE_AXE -> applyDoubleAxeFirstPerson(poseStack, swingProgress, step, side);
            case GREAT_HAMMER -> applyGreatHammerFirstPerson(poseStack, swingProgress, step, side);
            case CLAYMORE -> applyClaymoreFirstPerson(poseStack, swingProgress, step, side);
            case DAGGERS -> applyDaggersFirstPerson(poseStack, swingProgress, step, side, arm, player);
            case SICKLES -> applySicklesFirstPerson(poseStack, swingProgress, step, side, arm, player);
            case GAUNTLETS -> applyGauntletsFirstPerson(poseStack, swingProgress, step, side, arm, player);
            case SPEAR -> applySpearFirstPerson(poseStack, swingProgress, step, side);
            case SPEAR_GLAIVE -> {
                if (isGlaive(stack)) {
                    applyGlaiveFirstPerson(poseStack, swingProgress, step, side);
                } else {
                    applySpearFirstPerson(poseStack, swingProgress, step, side);
                }
            }
            case GLAIVE -> applyGlaiveFirstPerson(poseStack, swingProgress, step, side);
            case MACE -> applyMaceFirstPerson(poseStack, swingProgress, step, side);
            default -> applySwordFirstPerson(poseStack, swingProgress, step, side);
        }
    }

    private static boolean isGlaive(@NotNull ItemStack stack) {
        return stack.getItem().toString().toLowerCase().contains("glaive");
    }

    /* ------------------------------------------------------------------------- */
    /*  1. Sword First-Person                                                    */
    /* ------------------------------------------------------------------------- */
    private static void applySwordFirstPerson(PoseStack poseStack, float t, int step, int side) {
        switch (step % 3) {
            case 0 -> {
                // Step 0: Diagonal Forehand Slash (L -> R)
                float tx = side * kineticTrack(t, 0.16f, 0.52f, 0.15f, -0.35f);
                float ty = kineticTrack(t, 0.16f, 0.52f, 0.10f, -0.28f);
                float tz = -0.45f * (float) Math.sin(t * Math.PI);
                poseStack.translate(tx, ty, tz);

                poseStack.mulPose(Axis.YP.rotationDegrees(side * centrifugalLag(t, 0.16f, 0.52f, 35.0f, -65.0f, 0.25f)));
                poseStack.mulPose(Axis.ZP.rotationDegrees(side * kineticTrack(t, 0.16f, 0.52f, -15.0f, 45.0f)));
                poseStack.mulPose(Axis.XP.rotationDegrees(kineticTrack(t, 0.16f, 0.52f, -20.0f, 35.0f)));
            }
            case 1 -> {
                // Step 1: Diagonal Backhand Cut (R -> L)
                float tx = side * kineticTrack(t, 0.14f, 0.48f, -0.20f, 0.40f);
                float ty = kineticTrack(t, 0.14f, 0.48f, -0.08f, 0.22f);
                float tz = -0.40f * (float) Math.sin(t * Math.PI);
                poseStack.translate(tx, ty, tz);

                poseStack.mulPose(Axis.YP.rotationDegrees(side * centrifugalLag(t, 0.14f, 0.48f, -35.0f, 75.0f, 0.25f)));
                poseStack.mulPose(Axis.ZP.rotationDegrees(side * kineticTrack(t, 0.14f, 0.48f, 25.0f, -50.0f)));
                poseStack.mulPose(Axis.XP.rotationDegrees(kineticTrack(t, 0.14f, 0.48f, -30.0f, 25.0f)));
            }
            default -> {
                // Step 2: Overhead Heavy Cleave Finisher
                float ty = kineticRecoil(t, 0.22f, 0.52f, 0.32f, -0.60f, 0.35f);
                float tz = -0.50f * (float) Math.sin(t * Math.PI);
                poseStack.translate(side * 0.05f * (float) Math.sin(t * Math.PI), ty, tz);

                poseStack.mulPose(Axis.XP.rotationDegrees(kineticRecoil(t, 0.22f, 0.52f, -80.0f, 70.0f, 0.30f)));
                poseStack.mulPose(Axis.YP.rotationDegrees(side * kineticTrack(t, 0.22f, 0.52f, 10.0f, -8.0f)));
            }
        }
    }

    /* ------------------------------------------------------------------------- */
    /*  2. Double Axe First-Person                                               */
    /* ------------------------------------------------------------------------- */
    private static void applyDoubleAxeFirstPerson(PoseStack poseStack, float t, int step, int side) {
        if (step % 2 == 0) {
            // Step 0: 360° Whirlwind Orbital Spin
            float progress = Ease.inOutCubic(t);
            float angle = progress * (float) Math.PI * 2.0f;
            float radius = 0.40f;

            float tx = side * (radius * Mth.sin(angle));
            float ty = -0.12f * Mth.sin(angle);
            float tz = -0.55f * (float) Math.sin(t * Math.PI);
            poseStack.translate(tx, ty, tz);

            poseStack.mulPose(Axis.YP.rotationDegrees(side * (-360.0f * progress)));
            poseStack.mulPose(Axis.XP.rotationDegrees(-24.0f * (float) Math.sin(t * Math.PI)));
        } else {
            // Step 1: Heavy Overhead Downward Cleave Finisher
            float ty = kineticRecoil(t, 0.24f, 0.55f, 0.38f, -0.72f, 0.38f);
            float tz = -0.50f * (float) Math.sin(t * Math.PI);
            poseStack.translate(side * 0.04f * (float) Math.sin(t * Math.PI), ty, tz);

            poseStack.mulPose(Axis.XP.rotationDegrees(kineticRecoil(t, 0.24f, 0.55f, -75.0f, 82.0f, 0.32f)));
            poseStack.mulPose(Axis.YP.rotationDegrees(side * kineticTrack(t, 0.24f, 0.55f, 12.0f, -12.0f)));
        }
    }

    /* ------------------------------------------------------------------------- */
    /*  3. Great Hammer First-Person                                             */
    /* ------------------------------------------------------------------------- */
    private static void applyGreatHammerFirstPerson(PoseStack poseStack, float t, int step, int side) {
        if (step % 2 == 0) {
            // Step 0: Heavy Concussive Swing (Lateral Inertia Bash)
            float tx = side * kineticTrack(t, 0.26f, 0.60f, 0.32f, -0.52f);
            float ty = kineticTrack(t, 0.26f, 0.60f, 0.20f, -0.42f);
            float tz = -0.50f * (float) Math.sin(t * Math.PI);
            poseStack.translate(tx, ty, tz);

            poseStack.mulPose(Axis.XP.rotationDegrees(kineticTrack(t, 0.26f, 0.60f, -40.0f, 55.0f)));
            poseStack.mulPose(Axis.YP.rotationDegrees(side * kineticTrack(t, 0.26f, 0.60f, 45.0f, -75.0f)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * kineticTrack(t, 0.26f, 0.60f, -25.0f, 40.0f)));
        } else {
            // Step 1: Earthshaker Ground Smash Finisher (Titanic Slam & Elastic Recoil)
            float ty = kineticRecoil(t, 0.30f, 0.56f, 0.55f, -0.95f, 0.48f);
            float tz = -0.60f * (float) Math.sin(t * Math.PI);
            poseStack.translate(side * 0.06f * (float) Math.sin(t * Math.PI), ty, tz);

            poseStack.mulPose(Axis.XP.rotationDegrees(kineticRecoil(t, 0.30f, 0.56f, -95.0f, 105.0f, 0.42f)));
            poseStack.mulPose(Axis.YP.rotationDegrees(side * kineticTrack(t, 0.30f, 0.56f, 15.0f, -15.0f)));
        }
    }

    /* ------------------------------------------------------------------------- */
    /*  4. Claymore First-Person                                                 */
    /* ------------------------------------------------------------------------- */
    private static void applyClaymoreFirstPerson(PoseStack poseStack, float t, int step, int side) {
        switch (step % 3) {
            case 0 -> {
                // Step 0: Colossal Cleave Left (130° Broad Slash)
                float tx = side * kineticTrack(t, 0.25f, 0.60f, 0.40f, -0.60f);
                float ty = kineticTrack(t, 0.25f, 0.60f, 0.18f, -0.32f);
                float tz = -0.58f * (float) Math.sin(t * Math.PI);
                poseStack.translate(tx, ty, tz);

                poseStack.mulPose(Axis.YP.rotationDegrees(side * centrifugalLag(t, 0.25f, 0.60f, 65.0f, -98.0f, 0.35f)));
                poseStack.mulPose(Axis.ZP.rotationDegrees(side * centrifugalLag(t, 0.25f, 0.60f, -30.0f, 60.0f, 0.35f)));
                poseStack.mulPose(Axis.XP.rotationDegrees(kineticTrack(t, 0.25f, 0.60f, -32.0f, 28.0f)));
            }
            case 1 -> {
                // Step 1: Return Colossal Cleave Right (140° Reverse Arc)
                float tx = side * kineticTrack(t, 0.24f, 0.58f, -0.40f, 0.60f);
                float ty = kineticTrack(t, 0.24f, 0.58f, -0.12f, 0.28f);
                float tz = -0.58f * (float) Math.sin(t * Math.PI);
                poseStack.translate(tx, ty, tz);

                poseStack.mulPose(Axis.YP.rotationDegrees(side * centrifugalLag(t, 0.24f, 0.58f, -65.0f, 105.0f, 0.35f)));
                poseStack.mulPose(Axis.ZP.rotationDegrees(side * centrifugalLag(t, 0.24f, 0.58f, 30.0f, -60.0f, 0.35f)));
                poseStack.mulPose(Axis.XP.rotationDegrees(kineticTrack(t, 0.24f, 0.58f, -32.0f, 28.0f)));
            }
            default -> {
                // Step 2: Massive Overhead Ground Chop (Mountain Splitter Finisher)
                float ty = kineticRecoil(t, 0.28f, 0.55f, 0.48f, -0.85f, 0.42f);
                float tz = -0.60f * (float) Math.sin(t * Math.PI);
                poseStack.translate(side * 0.05f * (float) Math.sin(t * Math.PI), ty, tz);

                poseStack.mulPose(Axis.XP.rotationDegrees(kineticRecoil(t, 0.28f, 0.55f, -90.0f, 98.0f, 0.38f)));
                poseStack.mulPose(Axis.YP.rotationDegrees(side * kineticTrack(t, 0.28f, 0.55f, 10.0f, -10.0f)));
            }
        }
    }

    /* ------------------------------------------------------------------------- */
    /*  5. Daggers First-Person (Dual-Wield Alternating Choreography)             */
    /* ------------------------------------------------------------------------- */
    private static void applyDaggersFirstPerson(
            PoseStack poseStack,
            float t,
            int step,
            int side,
            HumanoidArm arm,
            AbstractClientPlayer player
    ) {
        boolean isMainHand = (arm == player.getMainArm());

        // Base combat stance orientation: daggers angled symmetrically inward
        poseStack.mulPose(Axis.YP.rotationDegrees(side * 45.0f));

        switch (step % 4) {
            case 0 -> {
                // Step 0: Left Thrust (Lead Off-Hand Piston Thrust, Main-Hand Guard Coil)
                if (!isMainHand) {
                    // Off-hand (Left): explosive piston thrust straight forward
                    float punch = pistonThrust(t, 0.18f);
                    poseStack.translate(side * (-0.16f * punch), -0.04f * punch, -0.80f * punch);
                    poseStack.mulPose(Axis.YP.rotationDegrees(side * -25.0f * punch));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-20.0f * punch));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(side * 18.0f * punch));
                } else {
                    // Main-hand (Right): guard coil at chest, micro-recoil (Z=+0.10m, Y=+0.05m)
                    float coil = (float) Math.sin(t * Math.PI);
                    poseStack.translate(side * 0.05f * coil, 0.04f * coil, 0.10f * coil);
                    poseStack.mulPose(Axis.YP.rotationDegrees(side * -14.0f * coil));
                    poseStack.mulPose(Axis.XP.rotationDegrees(12.0f * coil));
                }
            }
            case 1 -> {
                // Step 1: Right Thrust (Main-Hand Piston Thrust, Off-Hand Guard Coil)
                if (isMainHand) {
                    // Main-hand (Right): explosive piston thrust straight forward
                    float punch = pistonThrust(t, 0.18f);
                    poseStack.translate(side * (-0.16f * punch), -0.04f * punch, -0.80f * punch);
                    poseStack.mulPose(Axis.YP.rotationDegrees(side * -25.0f * punch));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-20.0f * punch));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(side * 18.0f * punch));
                } else {
                    // Off-hand (Left): guard coil at chest
                    float coil = (float) Math.sin(t * Math.PI);
                    poseStack.translate(side * 0.05f * coil, 0.04f * coil, 0.10f * coil);
                    poseStack.mulPose(Axis.YP.rotationDegrees(side * -14.0f * coil));
                    poseStack.mulPose(Axis.XP.rotationDegrees(12.0f * coil));
                }
            }
            case 2 -> {
                // Step 2: Scissors Cross-Cut (Both hands slash diagonally across center at 45°)
                float cut = (float) Math.sin(t * Math.PI);
                float crossX = -side * 0.48f * cut;
                float crossY = (0.12f - 0.28f * t) * cut;
                float crossZ = -0.55f * cut;
                poseStack.translate(crossX, crossY, crossZ);

                poseStack.mulPose(Axis.ZP.rotationDegrees(side * 48.0f * cut));
                poseStack.mulPose(Axis.YP.rotationDegrees(side * -38.0f * cut));
                poseStack.mulPose(Axis.XP.rotationDegrees(-24.0f * cut));
            }
            default -> {
                // Step 3: Dual Plunge Finisher (Both hands lift up then plunge forward/downward and converge)
                float lift = Ease.inQuad(window(t, 0.0f, 0.20f)) * (1.0f - Ease.outQuad(window(t, 0.20f, 0.50f)));
                float plunge = Ease.outExpo(window(t, 0.20f, 0.50f)) * (1.0f - Ease.outCubic(window(t, 0.50f, 1.0f)));

                float ty = 0.26f * lift - 0.44f * plunge;
                float tz = 0.06f * lift - 0.90f * plunge;
                float tx = -side * 0.20f * plunge; // Converge inward towards screen center
                poseStack.translate(tx, ty, tz);

                poseStack.mulPose(Axis.XP.rotationDegrees(24.0f * lift - 70.0f * plunge));
                poseStack.mulPose(Axis.YP.rotationDegrees(side * (-22.0f * plunge)));
                poseStack.mulPose(Axis.ZP.rotationDegrees(side * 15.0f * plunge));
            }
        }
    }

    /* ------------------------------------------------------------------------- */
    /*  5b. Sickles First-Person (Dual-Wield Alternating Reaping Choreography)    */
    /* ------------------------------------------------------------------------- */
    private static void applySicklesFirstPerson(
            PoseStack poseStack,
            float t,
            int step,
            int side,
            HumanoidArm arm,
            AbstractClientPlayer player
    ) {
        boolean isMainHand = (arm == player.getMainArm());

        // Base sickle reaping guard stance orientation
        poseStack.mulPose(Axis.YP.rotationDegrees(side * 45.0f));

        switch (step % 4) {
            case 0 -> {
                // Step 0: Left Crescent Reaping Slash (Off-Hand Reaps, Main-Hand Coils)
                if (!isMainHand) {
                    float cut = (float) Math.sin(t * Math.PI);
                    float sweepX = side * (-0.34f * cut);
                    float sweepY = kineticTrack(t, 0.15f, 0.50f, 0.10f, -0.24f);
                    float sweepZ = -0.50f * cut;
                    poseStack.translate(sweepX, sweepY, sweepZ);

                    poseStack.mulPose(Axis.YP.rotationDegrees(side * -44.0f * cut));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(side * -36.0f * cut));
                    poseStack.mulPose(Axis.XP.rotationDegrees(26.0f * cut));
                } else {
                    float coil = (float) Math.sin(t * Math.PI);
                    poseStack.translate(side * 0.05f * coil, 0.03f * coil, 0.08f * coil);
                    poseStack.mulPose(Axis.YP.rotationDegrees(side * -12.0f * coil));
                }
            }
            case 1 -> {
                // Step 1: Right Crescent Reaping Slash (Main-Hand Reaps, Off-Hand Coils)
                if (isMainHand) {
                    float cut = (float) Math.sin(t * Math.PI);
                    float sweepX = side * (-0.34f * cut);
                    float sweepY = kineticTrack(t, 0.15f, 0.50f, 0.10f, -0.24f);
                    float sweepZ = -0.50f * cut;
                    poseStack.translate(sweepX, sweepY, sweepZ);

                    poseStack.mulPose(Axis.YP.rotationDegrees(side * -44.0f * cut));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(side * -36.0f * cut));
                    poseStack.mulPose(Axis.XP.rotationDegrees(26.0f * cut));
                } else {
                    float coil = (float) Math.sin(t * Math.PI);
                    poseStack.translate(side * 0.05f * coil, 0.03f * coil, 0.08f * coil);
                    poseStack.mulPose(Axis.YP.rotationDegrees(side * -12.0f * coil));
                }
            }
            case 2 -> {
                // Step 2: Inward Hook Pull & Vacuum Reel (Both sickles reach out Z=-0.78m, then hook inward and drag back)
                float reach = Ease.outCubic(window(t, 0.0f, 0.30f)) * (1.0f - Ease.outExpo(window(t, 0.30f, 0.65f)));
                float hookPull = Ease.outExpo(window(t, 0.30f, 0.65f)) * (1.0f - Ease.outCubic(window(t, 0.65f, 1.0f)));

                float tx = -side * 0.24f * hookPull + side * 0.08f * reach;
                float ty = 0.06f * reach - 0.20f * hookPull;
                float tz = -0.78f * reach + 0.30f * hookPull;
                poseStack.translate(tx, ty, tz);

                poseStack.mulPose(Axis.XP.rotationDegrees(-38.0f * reach + 70.0f * hookPull));
                poseStack.mulPose(Axis.YP.rotationDegrees(side * (22.0f * reach - 50.0f * hookPull)));
                poseStack.mulPose(Axis.ZP.rotationDegrees(side * (-18.0f * reach + 28.0f * hookPull)));
            }
            default -> {
                // Step 3: 360° Reaping Whirlwind (Both hands spread wide in centrifugal orbital spin)
                float progress = Ease.inOutCubic(t);
                float angle = progress * (float) Math.PI * 2.0f;
                float radius = 0.38f;

                float tx = side * (0.18f + radius * Mth.sin(angle));
                float ty = -0.10f * Mth.sin(angle);
                float tz = -0.50f * (float) Math.sin(t * Math.PI);
                poseStack.translate(tx, ty, tz);

                poseStack.mulPose(Axis.YP.rotationDegrees(side * (-360.0f * progress)));
                poseStack.mulPose(Axis.XP.rotationDegrees(-22.0f * (float) Math.sin(t * Math.PI)));
                poseStack.mulPose(Axis.ZP.rotationDegrees(side * 20.0f * (float) Math.sin(t * Math.PI)));
            }
        }
    }

    /* ------------------------------------------------------------------------- */
    /*  6. Gauntlets First-Person (Dual-Wield Boxing Choreography)               */
    /* ------------------------------------------------------------------------- */
    private static void applyGauntletsFirstPerson(
            PoseStack poseStack,
            float t,
            int step,
            int side,
            HumanoidArm arm,
            AbstractClientPlayer player
    ) {
        boolean isMainHand = (arm == player.getMainArm());

        // Base boxing guard stance orientation
        poseStack.mulPose(Axis.YP.rotationDegrees(side * 45.0f));

        switch (step % 3) {
            case 0 -> {
                // Step 0: Left Straight Jab (Lead Off-Hand Jab, Main-Hand Chin Guard)
                if (!isMainHand) {
                    // Off-hand (Left): ultra-fast straight jab with corkscrew rotation
                    float jab = pistonThrust(t, 0.18f);
                    poseStack.translate(side * (-0.18f * jab), 0.04f * jab, -0.82f * jab);
                    poseStack.mulPose(Axis.YP.rotationDegrees(side * -22.0f * jab));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(side * 30.0f * jab)); // Corkscrew fist pronation
                    poseStack.mulPose(Axis.XP.rotationDegrees(-16.0f * jab));
                } else {
                    // Main-hand (Right): tight chin guard
                    float guard = (float) Math.sin(t * Math.PI);
                    poseStack.translate(side * 0.05f * guard, 0.06f * guard, 0.07f * guard);
                    poseStack.mulPose(Axis.YP.rotationDegrees(side * -14.0f * guard));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(side * 16.0f * guard));
                }
            }
            case 1 -> {
                // Step 1: Right Cross Punch (Power Main-Hand Cross, Off-Hand Chin Guard)
                if (isMainHand) {
                    // Main-hand (Right): explosive heavy cross punch through center
                    float cross = pistonThrust(t, 0.22f);
                    poseStack.translate(side * (-0.22f * cross), 0.05f * cross, -0.86f * cross);
                    poseStack.mulPose(Axis.YP.rotationDegrees(side * -34.0f * cross));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(side * 32.0f * cross)); // Power torque
                    poseStack.mulPose(Axis.XP.rotationDegrees(-20.0f * cross));
                } else {
                    // Off-hand (Left): tight guard at cheek
                    float guard = (float) Math.sin(t * Math.PI);
                    poseStack.translate(side * 0.05f * guard, 0.06f * guard, 0.07f * guard);
                    poseStack.mulPose(Axis.YP.rotationDegrees(side * -14.0f * guard));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(side * 16.0f * guard));
                }
            }
            default -> {
                // Step 2: Rocket Uppercut Finisher (Deep crouch -> Rocket skyward)
                if (isMainHand) {
                    // Main-hand (Right): dips down then rockets skyward (Y = +0.72m)
                    float duck = Ease.inQuad(window(t, 0.0f, 0.20f)) * (1.0f - Ease.outExpo(window(t, 0.20f, 0.50f)));
                    float rocket = Ease.outExpo(window(t, 0.20f, 0.50f)) * (1.0f - Ease.outCubic(window(t, 0.50f, 1.0f)));

                    float ty = -0.32f * duck + 0.72f * rocket;
                    float tz = 0.08f * duck - 0.55f * rocket;
                    float tx = -side * 0.15f * rocket;
                    poseStack.translate(tx, ty, tz);

                    poseStack.mulPose(Axis.XP.rotationDegrees(25.0f * duck - 88.0f * rocket));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(side * (-15.0f * duck + 24.0f * rocket)));
                    poseStack.mulPose(Axis.YP.rotationDegrees(side * -18.0f * rocket));
                } else {
                    // Off-hand (Left): dips and braces body counterbalance
                    float duck = Ease.inQuad(window(t, 0.0f, 0.20f)) * (1.0f - Ease.outExpo(window(t, 0.20f, 0.50f)));
                    float brace = Ease.outExpo(window(t, 0.20f, 0.50f)) * (1.0f - Ease.outCubic(window(t, 0.50f, 1.0f)));
                    poseStack.translate(side * 0.04f * duck, -0.08f * duck + 0.05f * brace, 0.06f * brace);
                    poseStack.mulPose(Axis.YP.rotationDegrees(side * -12.0f * brace));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(side * 18.0f * brace));
                }
            }
        }
    }

    /* ------------------------------------------------------------------------- */
    /*  7. Spear First-Person                                                    */
    /* ------------------------------------------------------------------------- */
    private static void applySpearFirstPerson(PoseStack poseStack, float t, int step, int side) {
        switch (step % 3) {
            case 0 -> {
                // Jab 1: High-Speed Linear Thrust
                float stroke = pistonThrust(t, 0.26f);
                poseStack.translate(0.0f, -0.05f * stroke, -1.05f * stroke);
                poseStack.mulPose(Axis.XP.rotationDegrees(-28.0f * stroke));
            }
            case 1 -> {
                // Jab 2: Rapid Consecutive Piercing Thrust
                float stroke = pistonThrust(t, 0.24f);
                poseStack.translate(0.0f, -0.06f * stroke, -1.15f * stroke);
                poseStack.mulPose(Axis.XP.rotationDegrees(-32.0f * stroke));
            }
            default -> {
                // Step 2: Extended Power Lunge Finisher (5.5m Extreme Piercing Lance)
                float stroke = pistonThrust(t, 0.32f);
                poseStack.translate(0.0f, -0.10f * stroke, -1.55f * stroke);
                poseStack.mulPose(Axis.XP.rotationDegrees(-45.0f * stroke));
            }
        }
    }

    /* ------------------------------------------------------------------------- */
    /*  7b. Glaive First-Person                                                  */
    /* ------------------------------------------------------------------------- */
    private static void applyGlaiveFirstPerson(PoseStack poseStack, float t, int step, int side) {
        switch (step % 3) {
            case 0 -> {
                // Step 0: Wide Arc Cleave L (125° Sweeping Polearm)
                float tx = side * kineticTrack(t, 0.22f, 0.58f, 0.38f, -0.50f);
                float ty = kineticTrack(t, 0.22f, 0.58f, 0.14f, -0.30f);
                float tz = -0.50f * (float) Math.sin(t * Math.PI);
                poseStack.translate(tx, ty, tz);

                poseStack.mulPose(Axis.YP.rotationDegrees(side * centrifugalLag(t, 0.22f, 0.58f, 60.0f, -90.0f, 0.32f)));
                poseStack.mulPose(Axis.ZP.rotationDegrees(side * kineticTrack(t, 0.22f, 0.58f, -28.0f, 50.0f)));
                poseStack.mulPose(Axis.XP.rotationDegrees(kineticTrack(t, 0.22f, 0.58f, -28.0f, 22.0f)));
            }
            case 1 -> {
                // Step 1: Wide Arc Cleave R (135° Sweeping Polearm)
                float tx = side * kineticTrack(t, 0.20f, 0.56f, -0.38f, 0.52f);
                float ty = kineticTrack(t, 0.20f, 0.56f, -0.12f, 0.28f);
                float tz = -0.50f * (float) Math.sin(t * Math.PI);
                poseStack.translate(tx, ty, tz);

                poseStack.mulPose(Axis.YP.rotationDegrees(side * centrifugalLag(t, 0.20f, 0.56f, -60.0f, 98.0f, 0.32f)));
                poseStack.mulPose(Axis.ZP.rotationDegrees(side * kineticTrack(t, 0.20f, 0.56f, 28.0f, -50.0f)));
                poseStack.mulPose(Axis.XP.rotationDegrees(kineticTrack(t, 0.20f, 0.56f, -28.0f, 22.0f)));
            }
            default -> {
                // Step 2: Sweeping Piercing Lunge Finisher (Wide Sweep into 5.2m Linear Thrust)
                float stroke = pistonThrust(t, 0.30f);
                poseStack.translate(0.0f, -0.10f * stroke, -1.35f * stroke);
                poseStack.mulPose(Axis.XP.rotationDegrees(-38.0f * stroke));
                poseStack.mulPose(Axis.YP.rotationDegrees(side * (35.0f * (1.0f - 2.0f * t) * stroke)));
            }
        }
    }

    /* ------------------------------------------------------------------------- */
    /*  8. Mace First-Person                                                     */
    /* ------------------------------------------------------------------------- */
    private static void applyMaceFirstPerson(PoseStack poseStack, float t, int step, int side) {
        switch (step % 3) {
            case 0 -> {
                // Step 0: Downward Concussive Bash
                float tx = side * kineticTrack(t, 0.18f, 0.56f, 0.15f, -0.20f);
                float ty = kineticTrack(t, 0.18f, 0.56f, 0.12f, -0.40f);
                float tz = -0.38f * (float) Math.sin(t * Math.PI);
                poseStack.translate(tx, ty, tz);

                poseStack.mulPose(Axis.XP.rotationDegrees(kineticTrack(t, 0.18f, 0.56f, -25.0f, 65.0f)));
                poseStack.mulPose(Axis.YP.rotationDegrees(side * kineticTrack(t, 0.18f, 0.56f, 20.0f, -25.0f)));
                poseStack.mulPose(Axis.ZP.rotationDegrees(side * kineticTrack(t, 0.18f, 0.56f, 15.0f, -25.0f)));
            }
            case 1 -> {
                // Step 1: Backhand Concussion Strike
                float tx = side * kineticTrack(t, 0.16f, 0.54f, -0.20f, 0.35f);
                float ty = kineticTrack(t, 0.16f, 0.54f, -0.06f, 0.20f);
                float tz = -0.38f * (float) Math.sin(t * Math.PI);
                poseStack.translate(tx, ty, tz);

                poseStack.mulPose(Axis.XP.rotationDegrees(kineticTrack(t, 0.16f, 0.54f, -20.0f, 25.0f)));
                poseStack.mulPose(Axis.YP.rotationDegrees(side * kineticTrack(t, 0.16f, 0.54f, -30.0f, 65.0f)));
                poseStack.mulPose(Axis.ZP.rotationDegrees(side * kineticTrack(t, 0.16f, 0.54f, 20.0f, -35.0f)));
            }
            default -> {
                // Step 2: Overhead Heavy Slam (Skullcracker Smash Finisher)
                float ty = kineticRecoil(t, 0.24f, 0.54f, 0.38f, -0.72f, 0.38f);
                float tz = -0.52f * (float) Math.sin(t * Math.PI);
                poseStack.translate(side * 0.04f * (float) Math.sin(t * Math.PI), ty, tz);

                poseStack.mulPose(Axis.XP.rotationDegrees(kineticRecoil(t, 0.24f, 0.54f, -75.0f, 90.0f, 0.32f)));
                poseStack.mulPose(Axis.YP.rotationDegrees(side * kineticTrack(t, 0.24f, 0.54f, 8.0f, -8.0f)));
            }
        }
    }
}
