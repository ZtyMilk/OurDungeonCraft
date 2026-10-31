package com.github.ztymilk.entity;

import com.github.ztymilk.network.ModNetworking;
import com.github.ztymilk.soul.SoulDataAccessor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Dedicated soul entity dropped by defeated mobs in Minecraft Dungeons style.
 * <p>
 * Characteristics:
 * <ul>
 *   <li>Ascends with small-to-large scaling animation ("由小变大随机浮空升起")</li>
 *   <li>Hovering with zero gravity in mid-air ("空中无重力悬浮")</li>
 *   <li>Smooth acceleration and curving flight path towards target player ("平滑加速飞向玩家拾取")</li>
 * </ul>
 */
public class SoulOrbEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_SOUL_VALUE =
            SynchedEntityData.defineId(SoulOrbEntity.class, EntityDataSerializers.INT);

    public static final int MAX_LIFESPAN = 6000; // 5 minutes

    private int homingTicks = 0;
    private @Nullable UUID targetPlayerUuid;
    private @Nullable Player cachedTargetPlayer;

    public SoulOrbEntity(EntityType<? extends SoulOrbEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    public SoulOrbEntity(Level level, double x, double y, double z, int soulValue) {
        this(ModEntities.SOUL_ORB, level);
        this.setPos(x, y, z);
        this.setSoulValue(soulValue);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SOUL_VALUE, 1);
    }

    public int getSoulValue() {
        return this.entityData.get(DATA_SOUL_VALUE);
    }

    public void setSoulValue(int value) {
        this.entityData.set(DATA_SOUL_VALUE, Math.max(1, value));
    }

    public void setTargetPlayer(@Nullable Player player) {
        this.cachedTargetPlayer = player;
        this.targetPlayerUuid = player != null ? player.getUUID() : null;
    }

    /**
     * Interpolated scale from 0.15 to 1.0 over the first 14 ticks ("由小变大").
     */
    public float getScale(float partialTick) {
        float age = (float) this.tickCount + partialTick;
        if (age >= 14.0f) {
            return 1.0f;
        }
        float progress = Math.max(0.0f, age / 14.0f);
        // Cubic ease-out: 1 - (1 - t)^3
        float eased = 1.0f - (float) Math.pow(1.0f - progress, 3);
        return 0.15f + 0.85f * eased;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.PLAYERS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.tickCount >= MAX_LIFESPAN) {
            this.discard();
            return;
        }

        if (this.level().isClientSide()) {
            // Ambient cyan soul wisp particles
            if (this.random.nextFloat() < 0.30f) {
                this.level().addParticle(
                        ParticleTypes.SOUL,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.15,
                        this.getY() + (this.random.nextDouble() - 0.5) * 0.15,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.15,
                        0.0, 0.02, 0.0
                );
            }
            return;
        }

        // Server-side movement & homing state machine
        Vec3 vel = this.getDeltaMovement();

        // Phase 1: Rising & initial deceleration (0-14 ticks)
        if (this.tickCount < 14) {
            Vec3 damped = new Vec3(vel.x * 0.90, vel.y * 0.92, vel.z * 0.90);
            this.setDeltaMovement(damped);
            this.setPos(this.getX() + damped.x, this.getY() + damped.y, this.getZ() + damped.z);
            return;
        }

        // Phase 2: Player target validation and discovery
        Player target = resolveTargetPlayer();

        if (target == null) {
            // Gentle hovering sinusoidal bobbing in mid-air
            this.homingTicks = 0;
            double hoverVy = Math.sin(this.tickCount * 0.15) * 0.008;
            Vec3 hoverVel = new Vec3(vel.x * 0.85, hoverVy, vel.z * 0.85);
            this.setDeltaMovement(hoverVel);
            this.setPos(this.getX() + hoverVel.x, this.getY() + hoverVy, this.getZ() + hoverVel.z);
            return;
        }

        // Phase 3: Smooth acceleration homing towards player ("平滑加速飞向玩家拾取")
        this.homingTicks++;
        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 toPlayer = targetPos.subtract(this.position());
        double distSq = toPlayer.lengthSqr();

        // Collection radius: 0.9 blocks or bounding box intersection
        if (distSq < 0.9 * 0.9 || this.getBoundingBox().intersects(target.getBoundingBox())) {
            this.collect(target);
            return;
        }

        Vec3 dir = toPlayer.normalize();
        // Acceleration starts at 0.20, ramps up to 0.95 blocks/tick
        double speed = Math.min(0.95, 0.20 + this.homingTicks * 0.04);
        Vec3 desiredVel = dir.scale(speed);

        // Steering blend: smoothly turns towards target
        double turnFactor = Math.min(0.35, 0.15 + this.homingTicks * 0.015);
        Vec3 newVel = vel.scale(1.0 - turnFactor).add(desiredVel.scale(turnFactor));
        this.setDeltaMovement(newVel);
        this.setPos(this.getX() + newVel.x, this.getY() + newVel.y, this.getZ() + newVel.z);
    }

    private @Nullable Player resolveTargetPlayer() {
        if (this.cachedTargetPlayer != null
                && this.cachedTargetPlayer.isAlive()
                && !this.cachedTargetPlayer.isSpectator()
                && this.cachedTargetPlayer.level() == this.level()
                && this.cachedTargetPlayer.distanceToSqr(this) <= 32.0 * 32.0) {
            return this.cachedTargetPlayer;
        }

        if (this.targetPlayerUuid != null && this.level() instanceof ServerLevel serverLevel) {
            Player player = serverLevel.getPlayerByUUID(this.targetPlayerUuid);
            if (player != null && player.isAlive() && !player.isSpectator() && player.distanceToSqr(this) <= 32.0 * 32.0) {
                this.cachedTargetPlayer = player;
                return player;
            }
        }

        Player nearest = this.level().getNearestPlayer(this, 12.0);
        if (nearest != null && nearest.isAlive() && !nearest.isSpectator()) {
            this.cachedTargetPlayer = nearest;
            this.targetPlayerUuid = nearest.getUUID();
            return nearest;
        }

        return null;
    }

    /**
     * Collects the soul orb upon reaching the player.
     */
    public void collect(Player player) {
        if (this.isRemoved()) {
            return;
        }

        SoulDataAccessor accessor = SoulDataAccessor.get(player);
        if (accessor != null) {
            accessor.addSouls(this.getSoulValue());
            if (player instanceof ServerPlayer serverPlayer) {
                ModNetworking.syncSouls(serverPlayer);
            }
        }

        // Soul chime pickup sound
        this.level().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS,
                0.55F,
                1.55F + this.random.nextFloat() * 0.25F
        );

        // Particle burst on absorption
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.SCULK_SOUL,
                    this.getX(), this.getY(), this.getZ(),
                    6,
                    0.12, 0.12, 0.12,
                    0.03
            );
            serverLevel.sendParticles(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    this.getX(), this.getY(), this.getZ(),
                    4,
                    0.10, 0.10, 0.10,
                    0.02
            );
        }

        this.discard();
    }

    @Override
    public void playerTouch(Player player) {
        if (!this.level().isClientSide() && player.isAlive() && !player.isSpectator()) {
            this.collect(player);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setSoulValue(input.getIntOr("SoulValue", 1));
        this.tickCount = input.getIntOr("Age", 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("SoulValue", this.getSoulValue());
        output.putInt("Age", this.tickCount);
    }
}
