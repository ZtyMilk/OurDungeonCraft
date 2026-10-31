package com.github.ztymilk.roll;

import com.github.ztymilk.enchantment.ModEnchantments;
import com.github.ztymilk.network.ModNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Interface injected into {@link net.minecraft.world.entity.player.Player} via Mixin
 * to expose dodge roll capabilities, multi-charge state, and lifecycle handling.
 */
public interface RollDataAccessor {

    /**
     * Helper to safely cast a Player (or any object implementing RollDataAccessor) to RollDataAccessor.
     */
    static RollDataAccessor get(Object entity) {
        return entity instanceof RollDataAccessor accessor ? accessor : null;
    }

    /**
     * Retrieves the internal {@link RollData} instance tracking this player's roll state.
     *
     * @return {@link RollData} instance.
     */
    @NotNull
    RollData getRollData();

    /**
     * @return Current {@link RollState}.
     */
    default @NotNull RollState getRollState() {
        return getRollData().getState();
    }

    /**
     * Sets the current {@link RollState}.
     *
     * @param state New {@link RollState}.
     */
    default void setRollState(@NotNull RollState state) {
        getRollData().setState(state);
    }

    /**
     * @return Elapsed ticks in the current roll state.
     */
    default int getRollTicks() {
        return getRollData().getRollTicks();
    }

    /**
     * Sets elapsed ticks in the current roll state.
     *
     * @param ticks Elapsed ticks.
     */
    default void setRollTicks(int ticks) {
        getRollData().setRollTicks(ticks);
    }

    /**
     * @return Roll direction yaw angle in degrees.
     */
    default float getRollYaw() {
        return getRollData().getRollYaw();
    }

    /**
     * Sets roll direction yaw angle in degrees.
     *
     * @param yaw Yaw in degrees.
     */
    default void setRollYaw(float yaw) {
        getRollData().setRollYaw(yaw);
    }

    /**
     * @return Remaining recharge ticks before the next roll charge is restored.
     */
    default int getRollCooldown() {
        return getRollData().getRechargeCooldown();
    }

    /**
     * Sets recharge cooldown ticks.
     *
     * @param cooldown Cooldown ticks.
     */
    default void setRollCooldown(int cooldown) {
        getRollData().setRechargeCooldown(cooldown);
    }

    /**
     * @return Available roll charges (0 to maxCharges).
     */
    default int getRollCharges() {
        return getRollData().getCharges();
    }

    /**
     * Sets available roll charges.
     *
     * @param charges Available roll charges.
     */
    default void setRollCharges(int charges) {
        getRollData().setCharges(charges);
    }

    /**
     * @return Maximum roll charges (initial 1, capped at 4).
     */
    default int getMaxRollCharges() {
        return getRollData().getMaxCharges();
    }

    /**
     * Sets maximum roll charges.
     *
     * @param maxCharges Maximum roll charges.
     */
    default void setMaxRollCharges(int maxCharges) {
        getRollData().setMaxCharges(maxCharges);
    }

    /**
     * @return Remaining internal lockout cooldown ticks (0.1s / 2 ticks).
     */
    default int getRollInternalCooldown() {
        return getRollData().getInternalCooldown();
    }

    /**
     * Sets internal lockout cooldown ticks.
     *
     * @param ticks Lockout ticks.
     */
    default void setRollInternalCooldown(int ticks) {
        getRollData().setInternalCooldown(ticks);
    }

    /**
     * @return Recharge progress ratio [0.0f, 1.0f].
     */
    default float getRechargeProgress() {
        return getRollData().getRechargeProgress();
    }

    /**
     * @return Recharge cooldown ratio [1.0f -> 0.0f] for top-to-bottom sweep line rendering.
     */
    default float getCooldownRatio() {
        return getRollData().getCooldownRatio();
    }

    /**
     * @return True if currently recharging charges.
     */
    default boolean isRecharging() {
        return getRollData().isRecharging();
    }

    /**
     * Verifies if the player is currently allowed to initiate a dodge roll.
     * <p>
     * Requirements:
     * <ul>
     *   <li>Must have available charges: charges &gt; 0</li>
     *   <li>Internal lockout cooldown must be &lt;= 0 (prevents accidental double rolls within 0.1s)</li>
     *   <li>Current state must be {@link RollState#IDLE}</li>
     *   <li>Player is not in Spectator mode (!player.isSpectator())</li>
     *   <li>Player is not dead or dying (!player.isDeadOrDying())</li>
     *   <li>Player is not riding any vehicle/entity (!player.isPassenger())</li>
     *   <li>Player is not gliding with Elytra (!player.isFallFlying())</li>
     *   <li>Player is not swimming (!player.isSwimming())</li>
     * </ul>
     *
     * @return True if all prerequisites are met.
     */
    default boolean canRoll() {
        RollData data = getRollData();
        if (data.getCharges() <= 0 || data.getInternalCooldown() > 0 || data.getState() != RollState.IDLE) {
            return false;
        }

        if (this instanceof Player player) {
            return !player.isSpectator()
                    && !player.isDeadOrDying()
                    && !player.isPassenger()
                    && !player.isFallFlying()
                    && !player.isSwimming();
        }

        return true;
    }

    /**
     * Initiates a dodge roll towards the specified world yaw angle.
     * Consumes one charge, starts the independent recharge timer, sets the internal lockout CD,
     * and transitions state to {@link RollState#STARTUP}.
     *
     * @param yaw Direction in world degrees.
     * @return True if roll started successfully; false if {@link #canRoll()} failed.
     */
    default boolean startRoll(float yaw) {
        if (!canRoll()) {
            return false;
        }

        RollData data = getRollData();
        data.consumeCharge();
        data.start(yaw);

        if (this instanceof Player player) {
            // Sound cue when initiating dodge roll windup
            if (!player.level().isClientSide()) {
                player.level().playSound(
                        null,
                        player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_ATTACK_SWEEP,
                        SoundSource.PLAYERS,
                        0.5F,
                        1.5F
                );
            }
        }
        return true;
    }

    /**
     * Ticks the dodge roll state machine. Handles:
     * <ul>
     *   <li>Armor Roll Master enchantment recalculation &amp; hard cap lock (max 4)</li>
     *   <li>Independent recharge timer &amp; internal lockout countdown</li>
     *   <li>Edge cases: void falling or lethal damage resets to IDLE</li>
     *   <li>State progression: STARTUP (2t) -> ACTIVE (8t) -> RECOVERY (4t) -> IDLE</li>
     *   <li>Movement velocity: applies 3.5 blocks total over 8 active ticks</li>
     *   <li>Horizontal wall collision: immediately transitions from ACTIVE to RECOVERY</li>
     *   <li>Post-roll lockout CD (2 ticks / 0.1s) set upon returning to IDLE</li>
     *   <li>Network synchronization when state, charges, or maxCharges change</li>
     * </ul>
     */
    default void tickRoll() {
        RollData data = getRollData();

        int prevCharges = data.getCharges();
        int prevMaxCharges = data.getMaxCharges();
        RollState prevState = data.getState();

        // 1. Recalculate max charges from worn armor with Roll Master enchantment
        if (this instanceof Player player) {
            int bonus = ModEnchantments.calculateRollMasterBonus(player);
            int targetMax = Math.min(RollData.MAX_ALLOWED_CHARGES, RollData.DEFAULT_CHARGES + bonus);
            if (data.getMaxCharges() != targetMax) {
                data.setMaxCharges(targetMax);
            }
        }

        // 2. Decrement internal lockout CD and tick independent recharge timer
        data.tickRecharge();

        // 3. If not rolling, check sync and return
        if (!data.isRolling()) {
            checkAndSync(prevCharges, prevMaxCharges, prevState);
            return;
        }

        if (!(this instanceof Player player)) {
            return;
        }

        // Edge case: player fell into void or is dead/dying -> immediately abort roll
        if (player.isDeadOrDying() || player.getY() < player.level().getMinY() - 64) {
            resetRoll();
            checkAndSync(prevCharges, prevMaxCharges, prevState);
            return;
        }

        RollState state = data.getState();

        switch (state) {
            case STARTUP -> {
                // STARTUP phase (2 ticks): wind-up / telegraph
                data.incrementRollTicks();
                if (data.getRollTicks() >= RollData.STARTUP_TICKS) {
                    data.transitionTo(RollState.ACTIVE);

                    // Launch audio & visual effects on entering ACTIVE
                    if (!player.level().isClientSide()) {
                        player.level().playSound(
                                null,
                                player.getX(), player.getY(), player.getZ(),
                                SoundEvents.PLAYER_ATTACK_SWEEP,
                                SoundSource.PLAYERS,
                                0.8F,
                                1.2F
                        );
                        if (player.level() instanceof ServerLevel serverLevel) {
                            serverLevel.sendParticles(
                                    ParticleTypes.POOF,
                                    player.getX(), player.getY() + 0.1, player.getZ(),
                                    6, 0.2, 0.05, 0.2, 0.02
                            );
                        }
                    }
                }
            }
            case ACTIVE -> {
                // Edge Case: Player hits a wall (horizontalCollision) -> immediately transition to RECOVERY
                if (player.horizontalCollision) {
                    data.transitionTo(RollState.RECOVERY);

                    // Cancel horizontal momentum on impact
                    Vec3 vel = player.getDeltaMovement();
                    player.setDeltaMovement(0.0, vel.y, 0.0);

                    // Impact feedback
                    if (!player.level().isClientSide()) {
                        player.level().playSound(
                                null,
                                player.getX(), player.getY(), player.getZ(),
                                SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                                SoundSource.PLAYERS,
                                0.5F,
                                0.9F
                        );
                        if (player.level() instanceof ServerLevel serverLevel) {
                            serverLevel.sendParticles(
                                    ParticleTypes.POOF,
                                    player.getX(), player.getY() + 0.5, player.getZ(),
                                    5, 0.2, 0.2, 0.2, 0.05
                            );
                        }
                    }
                    checkAndSync(prevCharges, prevMaxCharges, prevState);
                    return;
                }

                // Active movement physics: 3.5 blocks total over 8 ticks = 0.4375 blocks/tick
                double speed = data.getSpeedPerActiveTick();
                double radians = Math.toRadians(data.getRollYaw());
                double vx = -Math.sin(radians) * speed;
                double vz = Math.cos(radians) * speed;

                // Retain vertical velocity to respect gravity, ledges, and falling
                double vy = player.getDeltaMovement().y;
                player.setDeltaMovement(vx, vy, vz);
                player.hurtMarked = true;

                // Trail particles during the tumble
                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(
                            ParticleTypes.CLOUD,
                            player.getX(), player.getY() + 0.1, player.getZ(),
                            2, 0.15, 0.05, 0.15, 0.01
                    );
                } else if (player.level().isClientSide()) {
                    player.level().addParticle(
                            ParticleTypes.CLOUD,
                            player.getX(), player.getY() + 0.1, player.getZ(),
                            0.0, 0.0, 0.0
                    );
                }

                data.incrementRollTicks();
                if (data.getRollTicks() >= RollData.ACTIVE_TICKS) {
                    data.transitionTo(RollState.RECOVERY);
                }
            }
            case RECOVERY -> {
                // RECOVERY phase (4 ticks): landing lag, momentum dampening
                Vec3 vel = player.getDeltaMovement();
                player.setDeltaMovement(vel.x * 0.7, vel.y, vel.z * 0.7);

                data.incrementRollTicks();
                if (data.getRollTicks() >= RollData.RECOVERY_TICKS) {
                    // Roll finished: transition to IDLE
                    data.transitionTo(RollState.IDLE);
                    // 0.1s (2 ticks) lockout internal cooldown between consecutive rolls to prevent accidental triggers
                    data.setInternalCooldown(RollData.INTERNAL_COOLDOWN_TICKS);
                }
            }
            case IDLE -> {
                // Idle state: do nothing
            }
        }

        checkAndSync(prevCharges, prevMaxCharges, prevState);
    }

    /**
     * Inspects if any significant state, charge count, or max charge changed on server,
     * and sends synchronization packets accordingly.
     */
    default void checkAndSync(int prevCharges, int prevMaxCharges, RollState prevState) {
        if (this instanceof ServerPlayer serverPlayer) {
            RollData data = getRollData();
            boolean stateChanged = data.getState() != prevState;
            boolean chargesChanged = data.getCharges() != prevCharges;
            boolean maxChargesChanged = data.getMaxCharges() != prevMaxCharges;

            if (stateChanged || chargesChanged || maxChargesChanged) {
                ModNetworking.syncToPlayer(serverPlayer, data);
                if (stateChanged) {
                    ModNetworking.syncToTracking(serverPlayer, data);
                }
            }
        }
    }

    /**
     * Resets the roll state immediately to {@link RollState#IDLE}.
     */
    default void resetRoll() {
        getRollData().reset();
    }

    /**
     * Checks if the player is currently invulnerable during the dodge roll.
     * True strictly during {@link RollState#ACTIVE}.
     *
     * @return True if i-frames are active.
     */
    default boolean isRollInvulnerable() {
        return getRollData().isInvulnerable();
    }
}
