package com.github.ztymilk.combat;

import com.github.ztymilk.item.DungeonWeaponItem;
import com.github.ztymilk.network.ModNetworking;
import com.github.ztymilk.sound.WeaponSoundHelper.MCDWeaponType;
import com.github.ztymilk.vfx.WeaponVfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Spatial hit-detection physics and multi-target cleave resolution for Minecraft Dungeons weapons.
 * <p>
 * Implements 10 weapon distinct physical combat mechanisms:
 * - Sickles: Vacuum inward hook pull & negative knockback.
 * - Gauntlets: Rocket uppercut vertical aerial juggle launcher.
 * - Spear & Glaive: Lossless linear 3D capsule piercing thrust with forward-locked knockback.
 * - Mace: Armor-bypassing criticals, 30% passive armor penetration, and skullcracker shield-break stun.
 * - Great Hammer: Earthshaker 4.2m seismic shockwave with Slowness IV root and ground debris.
 * - Double Axe: 360° unhindered whirlwind radial sweep.
 * - Claymore: Colossal sweeping chops with hyper-armor and ground slam debris.
 * - Daggers: Deep dual plunge lacerating bleed finisher.
 */
public final class CleavePhysics {

    private CleavePhysics() {}

    /**
     * Executes cleave damage and impulse against nearby entities matching the combo step's geometry.
     * Overload defaulting crit detection from player falling state.
     */
    public static void executeCleave(
            @NotNull Player attacker,
            @Nullable Entity primaryTarget,
            float baseDamage,
            @NotNull ComboStep step
    ) {
        boolean isCrit = (attacker.fallDistance > 0.0f && !attacker.onGround() && !attacker.onClimbable() && !attacker.isInWater());
        executeCleave(attacker, primaryTarget, baseDamage, step, isCrit);
    }

    /**
     * Executes cleave damage, exclusive signature combat mechanics, and impulse against nearby entities.
     *
     * @param attacker      The attacking player.
     * @param primaryTarget The primary hit entity.
     * @param baseDamage    The calculated attack damage of this strike.
     * @param step          The active combo step.
     * @param isCrit        Whether this strike is a critical hit.
     */
    public static void executeCleave(
            @NotNull Player attacker,
            @Nullable Entity primaryTarget,
            float baseDamage,
            @NotNull ComboStep step,
            boolean isCrit
    ) {
        if (attacker.level().isClientSide() || !(attacker.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        CleaveShape shape = step.cleaveShape();
        MCDWeaponType weaponType = (attacker.getMainHandItem().getItem() instanceof DungeonWeaponItem weapon)
                ? weapon.getWeaponType()
                : AttackComboData.resolveWeaponType(attacker.getMainHandItem());

        if (attacker instanceof AttackComboAccessor accessor) {
            accessor.ourDungeonCraft$getComboData().markVfxTriggered(serverLevel.getGameTime());
        }

        if (shape instanceof CleaveShape.None) {
            if (primaryTarget instanceof LivingEntity livingPrimary && livingPrimary.isAlive()) {
                applySingleTargetExclusiveMechanics(serverLevel, attacker, livingPrimary, weaponType, step, baseDamage, isCrit);
                WeaponVfx.spawnWeaponOnHitVfx(serverLevel, attacker, livingPrimary, weaponType, step.stepIndex(), step, isCrit);
            }
            return;
        }

        // Calculate maximum bounding search volume for this shape
        double maxDist = switch (shape) {
            case CleaveShape.Cone cone -> cone.radius() + 1.0;
            case CleaveShape.Circle360 circle -> circle.radius() + 1.0;
            case CleaveShape.GroundSmash smash -> smash.radius() + 1.0;
            case CleaveShape.PiercingLine line -> line.length() + 1.0;
            case CleaveShape.None none -> 0.0;
        };

        Vec3 center = (shape instanceof CleaveShape.GroundSmash)
                ? (primaryTarget != null ? primaryTarget.position() : attacker.position().add(attacker.getLookAngle().scale(1.8)))
                : attacker.position();

        AABB searchBox = new AABB(
                center.x - maxDist, center.y - 2.5, center.z - maxDist,
                center.x + maxDist, center.y + 2.5, center.z + maxDist
        );

        // Apply primary target exclusive physical impulse and mechanics
        float kb = step.knockbackMultiplier();
        if (primaryTarget instanceof LivingEntity livingPrimary && livingPrimary.isAlive()) {
            applyPrimaryStrikeMechanics(serverLevel, attacker, livingPrimary, weaponType, step, shape, baseDamage, isCrit, kb, center);
        }

        List<LivingEntity> potentialTargets = serverLevel.getEntitiesOfClass(LivingEntity.class, searchBox);
        DamageSource damageSource = attacker.damageSources().playerAttack(attacker);
        int secondaryHits = 0;

        for (LivingEntity target : potentialTargets) {
            if (target == attacker || target == primaryTarget || !target.isAlive() || target.isInvulnerable()) {
                continue;
            }

            // Friendly fire checks: allies and owned pets
            if (attacker.isAlliedTo(target)) {
                continue;
            }
            if (target instanceof TamableAnimal tamable && tamable.isTame() && tamable.getOwner() == attacker) {
                continue;
            }

            if (isWithinShape(attacker, target, shape, center)) {
                secondaryHits++;

                // 1. Calculate cleave damage
                float secondaryDamage;
                if (shape instanceof CleaveShape.PiercingLine) {
                    // Lossless 100% piercing raycast penetration!
                    secondaryDamage = baseDamage;
                    if (step.isFinisher() && (target.getArmorValue() > 10 || target.isBlocking())) {
                        secondaryDamage *= 1.25f; // +25% armor-piercing bonus on finisher
                    }
                } else if (weaponType == MCDWeaponType.GREAT_HAMMER && step.isFinisher()) {
                    // Devastating seismic ground smash: 85% base damage across the whole 4.2m radius!
                    secondaryDamage = Math.max(1.0f, baseDamage * 0.85f);
                } else {
                    secondaryDamage = Math.max(1.0f, baseDamage * 0.70f);
                }

                DamageFeedbackTracker.setCurrentCrit(isCrit);
                try {
                    target.hurtServer(serverLevel, damageSource, secondaryDamage);
                } finally {
                    DamageFeedbackTracker.clearCurrentCrit();
                }

                // 2. Apply movement physics & exclusive mechanisms
                if (kb < 0) {
                    // Inward Hook Pull (Sickles Step 2)
                    applyInwardHookPull(serverLevel, attacker, target, kb);
                } else if (weaponType == MCDWeaponType.GAUNTLETS && step.isFinisher()) {
                    // Rocket Uppercut Launcher (Gauntlets Step 2)
                    applyRocketUppercut(serverLevel, target, (shape instanceof CleaveShape.GroundSmash smash) ? smash.lift() : 0.65f);
                } else if (shape instanceof CleaveShape.PiercingLine) {
                    // Linear Thrust Knockback
                    applyLinearThrustKnockback(attacker, target, kb, step.isFinisher());
                    if (target.isBlocking()) {
                        target.stopUsingItem();
                    }
                } else if (weaponType == MCDWeaponType.GREAT_HAMMER && step.isFinisher()) {
                    // Earthshaker Seismic Stun
                    applyEarthshakerEffects(serverLevel, target, center);
                } else if (shape instanceof CleaveShape.Circle360) {
                    // 360° Whirlwind Radial Knockback
                    double dx = target.getX() - attacker.getX();
                    double dz = target.getZ() - attacker.getZ();
                    float force = step.isFinisher() ? (kb * 0.45f) : (kb * 0.35f);
                    target.knockback(force, -dx, -dz);
                    target.hurtMarked = true;
                } else {
                    // Standard Cone / Slash Knockback
                    applyStandardKnockback(attacker, target, kb, step.isFinisher());
                }

                // 3. Status effects for secondary targets
                if (weaponType == MCDWeaponType.MACE) {
                    applyMaceArmorBypass(serverLevel, attacker, target, baseDamage, isCrit);
                    if (step.isFinisher()) {
                        applySkullcrackerEffects(serverLevel, target);
                    }
                } else if (weaponType == MCDWeaponType.DAGGERS && (step.isFinisher() || isCrit)) {
                    applyDaggerBleed(serverLevel, target);
                }

                // 4. Directional on-hit spark cue (decoupled from air swing, Hitstop-guarded)
                WeaponVfx.spawnDirectionalHitSparks(serverLevel, attacker, target, isCrit);
            }
        }

        // Multi-target Hitstop diminishing returns extension
        if (secondaryHits > 0 && attacker instanceof AttackComboAccessor accessor) {
            AttackComboData comboData = accessor.ourDungeonCraft$getComboData();
            int extraTicks = Math.min(2, (secondaryHits + 1) / 2);
            if (extraTicks > 0) {
                comboData.triggerHitstop(comboData.getHitstopTicks() + extraTicks, comboData.getHitstopShake() + 0.04f * extraTicks);
                if (attacker instanceof ServerPlayer serverPlayer) {
                    ModNetworking.syncHitstop(serverPlayer, comboData.getHitstopTicks(), comboData.getHitstopShake());
                }
            }
        }

        // Spawn authentic deluxe on-hit VFX only when a strike actually connects with a living entity
        if ((primaryTarget instanceof LivingEntity livingPrimary && livingPrimary.isAlive()) || secondaryHits > 0) {
            WeaponVfx.spawnWeaponOnHitVfx(serverLevel, attacker, primaryTarget, weaponType, step.stepIndex(), step, isCrit);
        }
    }

    /**
     * Applies exclusive mechanics and dynamic knockback for the primary target.
     */
    private static void applyPrimaryStrikeMechanics(
            ServerLevel serverLevel,
            Player attacker,
            LivingEntity target,
            MCDWeaponType weaponType,
            ComboStep step,
            CleaveShape shape,
            float baseDamage,
            boolean isCrit,
            float kb,
            Vec3 center
    ) {
        // 1. Inward Hook Pull (Sickles Step 2, kb < 0)
        if (kb < 0) {
            applyInwardHookPull(serverLevel, attacker, target, kb);
            return;
        }

        // 2. Rocket Uppercut Launcher (Gauntlets Step 2)
        if (weaponType == MCDWeaponType.GAUNTLETS && step.isFinisher()) {
            applyRocketUppercut(serverLevel, target, (shape instanceof CleaveShape.GroundSmash smash) ? smash.lift() : 0.65f);
            return;
        }

        // 3. Linear Piercing Lance (Spear & Glaive PiercingLine)
        if (shape instanceof CleaveShape.PiercingLine) {
            applyLinearThrustKnockback(attacker, target, kb, step.isFinisher());
            if (step.isFinisher() && (target.getArmorValue() > 10 || target.isBlocking())) {
                target.hurtServer(serverLevel, attacker.damageSources().magic(), baseDamage * 0.25f);
            }
            if (target.isBlocking()) {
                target.stopUsingItem();
            }
            return;
        }

        // 4. Mace Armor Bypass & Skullcracker
        if (weaponType == MCDWeaponType.MACE) {
            applyMaceArmorBypass(serverLevel, attacker, target, baseDamage, isCrit);
            if (step.isFinisher()) {
                applySkullcrackerEffects(serverLevel, target);
            }
        }

        // 5. Great Hammer Seismic Stun
        if (weaponType == MCDWeaponType.GREAT_HAMMER && step.isFinisher()) {
            applyEarthshakerEffects(serverLevel, target, center);
            return;
        }

        // 6. Claymore Ground Chop Finisher
        if (weaponType == MCDWeaponType.CLAYMORE && step.isFinisher()) {
            target.push(0.0, 0.22, 0.0);
            // Block debris consolidated into WeaponVfx.spawnWeaponOnHitVfx / directionalGroundSmash
        }

        // 7. Daggers Bleed
        if (weaponType == MCDWeaponType.DAGGERS && (step.isFinisher() || isCrit)) {
            applyDaggerBleed(serverLevel, target);
        }

        // Standard Dynamic Knockback
        applyStandardKnockback(attacker, target, kb, step.isFinisher());
    }

    /**
     * Applies exclusive mechanics for single-target attacks (CleaveShape.None).
     */
    private static void applySingleTargetExclusiveMechanics(
            ServerLevel serverLevel,
            Player attacker,
            LivingEntity target,
            MCDWeaponType weaponType,
            ComboStep step,
            float baseDamage,
            boolean isCrit
    ) {
        float kb = step.knockbackMultiplier();
        if (weaponType == MCDWeaponType.DAGGERS && (step.isFinisher() || isCrit)) {
            applyDaggerBleed(serverLevel, target);
        }
        applyStandardKnockback(attacker, target, kb, step.isFinisher());
    }

    /**
     * Sickles Step 2 Signature Mechanism: Inward Hook Pull & Vacuum Reel.
     * Computes inward centripetal velocity drawing mobs toward the player's front pocket.
     */
    private static void applyInwardHookPull(ServerLevel level, Player attacker, LivingEntity target, float kb) {
        Vec3 attackerPos = attacker.position();
        Vec3 targetPos = target.position();
        double dx = attackerPos.x - targetPos.x;
        double dz = attackerPos.z - targetPos.z;
        double dist = Math.hypot(dx, dz);

        double pullScale = Math.abs(kb) * 0.933; // 0.45 * 0.933 ≈ 0.42
        if (dist > 0.05) {
            double clampFactor = Math.clamp(dist - 1.1, 0.1, 1.0);
            double pullX = (dx / dist) * pullScale * clampFactor;
            double pullZ = (dz / dist) * pullScale * clampFactor;
            double pullY = 0.12;

            target.setDeltaMovement(
                    target.getDeltaMovement().x * 0.3 + pullX,
                    Math.max(target.getDeltaMovement().y, pullY),
                    target.getDeltaMovement().z * 0.3 + pullZ
            );
            target.hurtMarked = true;
        }
        WeaponVfx.inwardVortexPull(level, target.position(), attacker.position());
    }

    /**
     * Gauntlets Step 2 Signature Mechanism: Rocket Uppercut Launcher & Aerial Juggle.
     * Flings target vertically upward (2.5m~3.5m) into an airborne juggle state.
     */
    private static void applyRocketUppercut(ServerLevel level, LivingEntity target, float lift) {
        double upwardVel = Math.max(target.getDeltaMovement().y + lift, (double) lift);
        target.setDeltaMovement(target.getDeltaMovement().x * 0.25, upwardVel, target.getDeltaMovement().z * 0.25);
        target.hurtMarked = true;

        WeaponVfx.risingUppercutVfx(level, target.position());
    }

    /**
     * Spear & Glaive Signature Mechanism: Linear Piercing Thrust Knockback.
     * Knocks enemies directly backward along the player's horizontal look vector, not radially.
     */
    private static void applyLinearThrustKnockback(Player attacker, LivingEntity target, float kb, boolean isFinisher) {
        Vec3 look = attacker.getLookAngle();
        double horizLen = Math.hypot(look.x, look.z);
        double kx, kz;
        if (horizLen > 1e-4) {
            kx = look.x / horizLen;
            kz = look.z / horizLen;
        } else {
            float yawRad = attacker.getYRot() * ((float) Math.PI / 180F);
            kx = -net.minecraft.util.Mth.sin(yawRad);
            kz = net.minecraft.util.Mth.cos(yawRad);
        }
        float thrustForce = isFinisher ? (kb * 0.55f) : (kb * 0.35f);
        target.knockback(thrustForce, -kx, -kz);
        target.hurtMarked = true;
    }

    /**
     * Mace Signature Mechanism: Armor Fracture & Armor-Bypassing Crits.
     * Passively ignores 30% of target armor toughness; crits completely bypass armor mitigation!
     */
    private static void applyMaceArmorBypass(ServerLevel level, Player attacker, LivingEntity target, float baseDamage, boolean isCrit) {
        float armor = (float) target.getArmorValue();
        float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        if (armor > 0.0f) {
            float f = 2.0F + toughness / 4.0F;
            float clampedArmor = Math.clamp(armor - baseDamage / f, armor * 0.2F, 20.0F);
            float mitigationRatio = clampedArmor / 25.0F;

            // Critical hits bypass 100% of armor mitigation; normal hits ignore 30% armor toughness
            float bypassBonus = isCrit ? (baseDamage * mitigationRatio) : (baseDamage * mitigationRatio * 0.30f);
            if (bypassBonus > 0.5f) {
                DamageFeedbackTracker.setCurrentCrit(isCrit);
                try {
                    target.hurtServer(level, attacker.damageSources().playerAttack(attacker), bypassBonus);
                } finally {
                    DamageFeedbackTracker.clearCurrentCrit();
                }
            }
        }
    }

    /**
     * Mace Step 2 Signature Mechanism: Skullcracker Smash.
     * Applies Slowness II (4s), Weakness I (4s), and breaks target shield block.
     */
    private static void applySkullcrackerEffects(ServerLevel level, LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0, false, true, true));

        if (target instanceof Player playerTarget) {
            if (playerTarget.isUsingItem()) {
                playerTarget.getCooldowns().addCooldown(playerTarget.getUseItem(), 100);
            }
            if (playerTarget.getOffhandItem().is(Items.SHIELD)) {
                playerTarget.getCooldowns().addCooldown(playerTarget.getOffhandItem(), 100);
            }
            if (playerTarget.getMainHandItem().is(Items.SHIELD)) {
                playerTarget.getCooldowns().addCooldown(playerTarget.getMainHandItem(), 100);
            }
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.0f, 0.9f);
        }
        if (target.isBlocking()) {
            target.stopUsingItem();
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 1.0f, 0.9f);
        }
        // Downward concussive sparks & debris consolidated into WeaponVfx.spawnWeaponOnHitVfx / maceSkullcrackerImpact
    }

    /**
     * Great Hammer Step 1 Signature Mechanism: Earthshaker Seismic Stun.
     * Roots target with Slowness IV (30 ticks = 1.5s) and applies concussive ground shockwave.
     */
    private static void applyEarthshakerEffects(ServerLevel level, LivingEntity target, Vec3 center) {
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 3, false, true, true));

        double distCenter = Math.max(0.1, target.position().distanceTo(center));
        double falloff = Math.max(0.35, 1.0 - (distCenter / 4.2));
        double upward = 0.35 * falloff;
        target.setDeltaMovement(target.getDeltaMovement().x * 0.2, upward, target.getDeltaMovement().z * 0.2);
        target.hurtMarked = true;
    }

    /**
     * Spawns ground block shattering debris for Earthshaker ground smash.
     * <p>
     * Note: Consolidated into {@link WeaponVfx#directionalGroundSmash} to eradicate duplicate particle spam
     * and eliminate blinding CLOUD smoke plumes per VFX Golden Polish Specification Section 5.3 & 6.4.
     */
    private static void spawnEarthshakerGroundVfx(ServerLevel level, Vec3 center) {
        // Consolidated into WeaponVfx.spawnWeaponOnHitVfx / directionalGroundSmash (12 ballistic blocks + 4 adaptive fissures)
    }

    /**
     * Daggers Step 3 Signature Mechanism: Lacerating Bleed Finisher.
     */
    private static void applyDaggerBleed(ServerLevel level, LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0, false, false, false));
        // Lacerating ruby droplets consolidated into WeaponVfx.spawnWeaponOnHitVfx
    }

    /**
     * Spawns block shatter debris particles beneath a heavy impact position.
     * <p>
     * Note: Consolidated into {@link WeaponVfx#directionalGroundSmash} to eradicate duplicate particle spam.
     */
    private static void spawnGroundDebris(ServerLevel level, Vec3 pos, int count) {
        // Consolidated into WeaponVfx.spawnWeaponOnHitVfx / directionalGroundSmash
    }

    /**
     * Standard directional knockback for sweeping strikes.
     */
    private static void applyStandardKnockback(Player attacker, LivingEntity target, float kb, boolean isFinisher) {
        float force = isFinisher ? (kb * 0.48f) : (kb * 0.25f);
        double dx = target.getX() - attacker.getX();
        double dz = target.getZ() - attacker.getZ();
        if (Math.abs(dx) < 1e-4 && Math.abs(dz) < 1e-4) {
            float yawRad = attacker.getYRot() * ((float) Math.PI / 180F);
            dx = -net.minecraft.util.Mth.sin(yawRad);
            dz = net.minecraft.util.Mth.cos(yawRad);
        }
        target.knockback(force, -dx, -dz);
        if (isFinisher) {
            target.push(0.0, 0.16, 0.0);
        }
        target.hurtMarked = true;
    }

    /**
     * Determines whether a candidate entity falls inside the cleave shape volume.
     */
    private static boolean isWithinShape(
            @NotNull Player attacker,
            @NotNull LivingEntity target,
            @NotNull CleaveShape shape,
            @NotNull Vec3 center
    ) {
        Vec3 targetPos = target.position();

        return switch (shape) {
            case CleaveShape.None none -> false;

            case CleaveShape.Circle360 circle -> {
                double dx = targetPos.x - attacker.getX();
                double dz = targetPos.z - attacker.getZ();
                double centerDist = Math.hypot(dx, dz);
                double effectiveDist = Math.max(0.0, centerDist - target.getBbWidth() * 0.5);
                if (effectiveDist > circle.radius()) {
                    yield false;
                }
                double attackMinY = attacker.getY() - 1.2;
                double attackMaxY = attacker.getY() + attacker.getBbHeight() + 1.2;
                yield target.getBoundingBox().maxY >= attackMinY && target.getBoundingBox().minY <= attackMaxY;
            }

            case CleaveShape.Cone cone -> {
                Vec3 toTarget = targetPos.subtract(attacker.position());
                double dist = Math.hypot(toTarget.x, toTarget.z);
                if (dist > cone.radius() || Math.abs(toTarget.y) > 2.5) {
                    yield false;
                }
                if (dist <= 0.6) {
                    // Direct point-blank contact within attacker bounding box
                    yield true;
                }
                Vec3 look = attacker.getLookAngle();
                Vec3 horizLook = new Vec3(look.x, 0, look.z).normalize();
                Vec3 horizToTarget = new Vec3(toTarget.x, 0, toTarget.z).normalize();
                double dot = horizLook.dot(horizToTarget);
                yield dot >= Math.cos(Math.toRadians(cone.angleDegrees() * 0.5));
            }

            case CleaveShape.GroundSmash smash -> {
                double dx = targetPos.x - center.x;
                double dz = targetPos.z - center.z;
                double dy = Math.abs(targetPos.y - center.y);
                yield dy <= 2.5 && (dx * dx + dz * dz) <= (smash.radius() * smash.radius());
            }

            case CleaveShape.PiercingLine line -> {
                Vec3 eyePos = attacker.getEyePosition();
                Vec3 lookDir = attacker.getLookAngle().normalize();
                Vec3 endPos = eyePos.add(lookDir.scale(line.length()));

                // Inflate target bounding box by capsule half-width to test full 3D entity geometry
                double halfWidth = line.width() * 0.5;
                AABB targetBox = target.getBoundingBox().inflate(halfWidth, halfWidth * 0.5, halfWidth);

                // Cleanly pierce all entities whose 3D volume clips the forward thrust line or point-blank eye position
                yield targetBox.contains(eyePos) || targetBox.clip(eyePos, endPos).isPresent();
            }
        };
    }
}
