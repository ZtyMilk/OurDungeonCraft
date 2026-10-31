package com.github.ztymilk.roll;

import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Data model encapsulating the player's dodge roll state, timings, movement vector, multi-charges, and cooldowns.
 * Configured according to Minecraft Dungeons dodge roll physics:
 * <ul>
 *   <li>Total duration: 14 ticks (2 startup + 8 active + 4 recovery)</li>
 *   <li>Distance: 3.5 blocks total traversed over the 8 active ticks (~0.4375 blocks/tick)</li>
 *   <li>Invulnerability: active strictly during {@link RollState#ACTIVE}</li>
 *   <li>Default recharge time: 30 ticks (1.5 seconds at 20 TPS) per charge</li>
 *   <li>Multi-charge architecture: initial 1 charge, maximum 4 charges (via Roll Master enchantment)</li>
 *   <li>Independent recharge timer: starts counting down upon first roll consumption without reset on consecutive rolls</li>
 *   <li>Internal lockout cooldown: 2 ticks (0.1 seconds) anti-accidental-trigger between rolls</li>
 * </ul>
 */
public class RollData {
    // Timing & Physics constants
    public static final int STARTUP_TICKS = RollConstants.STARTUP_TICKS; // 2
    public static final int ACTIVE_TICKS = RollConstants.ACTIVE_TICKS;   // 8
    public static final int RECOVERY_TICKS = RollConstants.RECOVERY_TICKS; // 4
    public static final int DEFAULT_MAX_ROLL_TICKS = RollConstants.TOTAL_ROLL_TICKS; // 14 ticks
    public static final int DEFAULT_COOLDOWN_TICKS = RollConstants.COOLDOWN_TICKS; // 30 ticks (1.5 seconds)
    public static final int DEFAULT_RECHARGE_TICKS = DEFAULT_COOLDOWN_TICKS;
    public static final double DEFAULT_DISTANCE = 3.5; // 3.5 blocks total

    // Multi-charge & lockout constants
    public static final int DEFAULT_CHARGES = RollConstants.DEFAULT_CHARGES; // 1
    public static final int MAX_ALLOWED_CHARGES = RollConstants.MAX_ALLOWED_CHARGES; // 4
    public static final int INTERNAL_COOLDOWN_TICKS = RollConstants.INTERNAL_COOLDOWN_TICKS; // 2 ticks (0.1s)

    // Serialization keys
    public static final String KEY_STATE = "RollState";
    public static final String KEY_ROLL_TICKS = "RollTicks";
    public static final String KEY_MAX_ROLL_TICKS = "MaxRollTicks";
    public static final String KEY_COOLDOWN_TICKS = "RollCooldownTicks";
    public static final String KEY_ROLL_YAW = "RollYaw";
    public static final String KEY_DISTANCE = "RollDistance";
    public static final String KEY_CHARGES = "RollCharges";
    public static final String KEY_MAX_CHARGES = "RollMaxCharges";
    public static final String KEY_RECHARGE_COOLDOWN = "RollRechargeCooldown";
    public static final String KEY_INTERNAL_COOLDOWN = "RollInternalCooldown";

    @NotNull
    private RollState state = RollState.IDLE;

    /**
     * Elapsed ticks in the current roll state phase.
     * Resets to 0 upon transitioning between states (e.g. STARTUP -> ACTIVE).
     */
    private int rollTicks = 0;

    /**
     * Maximum total roll duration in ticks (sum of STARTUP, ACTIVE, RECOVERY = 14).
     */
    private int maxRollTicks = DEFAULT_MAX_ROLL_TICKS;

    /**
     * World direction angle (in degrees) for the roll trajectory.
     */
    private float rollYaw = 0.0f;

    /**
     * Total distance in blocks traversed across the 8 ACTIVE ticks.
     */
    private double distance = DEFAULT_DISTANCE;

    /**
     * Current available roll charges (0 to maxCharges).
     */
    private int charges = DEFAULT_CHARGES;

    /**
     * Maximum roll charges (initial 1, hard cap 4).
     */
    private int maxCharges = DEFAULT_CHARGES;

    /**
     * Remaining recharge ticks until the next roll charge is restored (30 down to 0).
     */
    private int rechargeCooldown = 0;

    /**
     * Total ticks required to restore one charge.
     */
    private int rechargeMaxTicks = DEFAULT_RECHARGE_TICKS;

    /**
     * Internal lockout cooldown (2 ticks / 0.1s) to prevent accidental double-rolls.
     */
    private int internalCooldown = 0;

    public RollData() {
    }

    /**
     * Checks if the player is currently invulnerable due to rolling.
     * True strictly during {@link RollState#ACTIVE}.
     */
    public boolean isInvulnerable() {
        return state.isInvulnerable();
    }

    /**
     * Checks if the player is currently executing any roll phase.
     */
    public boolean isRolling() {
        return state.isRolling();
    }

    /**
     * Horizontal speed in blocks per tick during the ACTIVE state.
     *
     * @return 3.5 / 8.0 = 0.4375 blocks/tick.
     */
    public double getSpeedPerActiveTick() {
        int activeDuration = RollState.ACTIVE.getDurationTicks();
        return activeDuration > 0 ? distance / activeDuration : 0.0;
    }

    /**
     * Initiates a roll heading towards {@code yaw}.
     * Transitions state to {@link RollState#STARTUP} and resets elapsed ticks.
     * Note: charge consumption is handled by {@link #consumeCharge()}.
     *
     * @param yaw Direction in world degrees.
     */
    public void start(float yaw) {
        this.state = RollState.STARTUP;
        this.rollTicks = 0;
        this.rollYaw = yaw;
    }

    /**
     * Consumes one roll charge, triggers internal lockout CD (2 ticks),
     * and starts the independent recharge timer if not already active.
     *
     * @return true if a charge was successfully consumed.
     */
    public boolean consumeCharge() {
        if (this.charges <= 0) {
            return false;
        }
        this.charges--;
        this.internalCooldown = INTERNAL_COOLDOWN_TICKS;

        // "使用首个翻滚即启动独立充能倒计时": if recharge timer was idle, start it immediately.
        // If already ticking, preserve the existing progress!
        if (this.rechargeCooldown <= 0) {
            this.rechargeCooldown = this.rechargeMaxTicks;
        }
        return true;
    }

    /**
     * Ticks down internal lockout cooldown and the independent recharge timer.
     * Restores charges one by one until {@code maxCharges} is reached.
     */
    public void tickRecharge() {
        if (this.internalCooldown > 0) {
            this.internalCooldown--;
        }

        if (this.charges < this.maxCharges) {
            if (this.rechargeCooldown <= 0) {
                this.rechargeCooldown = this.rechargeMaxTicks;
            } else {
                this.rechargeCooldown--;
                if (this.rechargeCooldown <= 0) {
                    this.charges++;
                    if (this.charges < this.maxCharges) {
                        this.rechargeCooldown = this.rechargeMaxTicks;
                    } else {
                        this.rechargeCooldown = 0;
                    }
                }
            }
        } else {
            this.rechargeCooldown = 0;
        }
    }

    /**
     * Transitions to a new state and resets elapsed state ticks.
     *
     * @param newState Target {@link RollState}.
     */
    public void transitionTo(@NotNull RollState newState) {
        this.state = Objects.requireNonNull(newState, "newState cannot be null");
        this.rollTicks = 0;
    }

    /**
     * Resets the roll state to {@link RollState#IDLE} and resets elapsed ticks.
     * Charges and cooldown are preserved.
     */
    public void reset() {
        this.state = RollState.IDLE;
        this.rollTicks = 0;
        this.rollYaw = 0.0f;
    }

    /**
     * Resets all roll state, refills charges to max, and clears all cooldowns.
     */
    public void resetAll() {
        reset();
        this.charges = this.maxCharges;
        this.rechargeCooldown = 0;
        this.internalCooldown = 0;
    }

    // --- Multi-Charge Getters & Setters ---

    public int getCharges() {
        return charges;
    }

    public void setCharges(int charges) {
        this.charges = Mth.clamp(charges, 0, this.maxCharges);
    }

    public int getMaxCharges() {
        return maxCharges;
    }

    public void setMaxCharges(int maxCharges) {
        this.maxCharges = Mth.clamp(maxCharges, 1, MAX_ALLOWED_CHARGES);
        if (this.charges > this.maxCharges) {
            this.charges = this.maxCharges;
        }
    }

    public int getRechargeCooldown() {
        return rechargeCooldown;
    }

    public void setRechargeCooldown(int rechargeCooldown) {
        this.rechargeCooldown = Math.max(0, rechargeCooldown);
    }

    public int getRechargeMaxTicks() {
        return rechargeMaxTicks;
    }

    public void setRechargeMaxTicks(int rechargeMaxTicks) {
        this.rechargeMaxTicks = Math.max(1, rechargeMaxTicks);
    }

    public int getInternalCooldown() {
        return internalCooldown;
    }

    public void setInternalCooldown(int internalCooldown) {
        this.internalCooldown = Math.max(0, internalCooldown);
    }

    public boolean isRecharging() {
        return charges < maxCharges && rechargeCooldown > 0;
    }

    /**
     * Returns the recharge progress from 0.0f (just started recharge) to 1.0f (fully recharged).
     */
    public float getRechargeProgress() {
        if (charges >= maxCharges || rechargeCooldown <= 0) {
            return 1.0f;
        }
        return Mth.clamp((float) (rechargeMaxTicks - rechargeCooldown) / (float) rechargeMaxTicks, 0.0f, 1.0f);
    }

    /**
     * Returns the recharge cooldown ratio from 1.0f (full CD) descending to 0.0f (finished).
     * Ideal for top-to-bottom cooldown sweep line UI rendering.
     */
    public float getCooldownRatio() {
        if (charges >= maxCharges || rechargeCooldown <= 0) {
            return 0.0f;
        }
        return Mth.clamp((float) rechargeCooldown / (float) rechargeMaxTicks, 0.0f, 1.0f);
    }

    // --- Backward Compatibility methods ---

    public int getCooldownTicks() {
        return rechargeCooldown;
    }

    public void setCooldownTicks(int cooldownTicks) {
        this.rechargeCooldown = Math.max(0, cooldownTicks);
    }

    public void decrementCooldown() {
        tickRecharge();
    }

    // --- State & Movement Getters & Setters ---

    @NotNull
    public RollState getState() {
        return state;
    }

    public void setState(@NotNull RollState state) {
        this.state = Objects.requireNonNull(state, "state cannot be null");
    }

    public int getRollTicks() {
        return rollTicks;
    }

    public void setRollTicks(int rollTicks) {
        this.rollTicks = Math.max(0, rollTicks);
    }

    public void incrementRollTicks() {
        this.rollTicks++;
    }

    public int getMaxRollTicks() {
        return maxRollTicks;
    }

    public void setMaxRollTicks(int maxRollTicks) {
        this.maxRollTicks = Math.max(1, maxRollTicks);
    }

    public float getRollYaw() {
        return rollYaw;
    }

    public void setRollYaw(float rollYaw) {
        this.rollYaw = rollYaw;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = Math.max(0.0, distance);
    }

    // --- Persistence (Minecraft 1.21.11 ValueOutput / ValueInput) ---

    public void save(@NotNull ValueOutput output) {
        Objects.requireNonNull(output, "output cannot be null");
        output.putString(KEY_STATE, state.name());
        output.putInt(KEY_ROLL_TICKS, rollTicks);
        output.putInt(KEY_MAX_ROLL_TICKS, maxRollTicks);
        output.putInt(KEY_COOLDOWN_TICKS, rechargeCooldown);
        output.putFloat(KEY_ROLL_YAW, rollYaw);
        output.putDouble(KEY_DISTANCE, distance);
        output.putInt(KEY_CHARGES, charges);
        output.putInt(KEY_MAX_CHARGES, maxCharges);
        output.putInt(KEY_RECHARGE_COOLDOWN, rechargeCooldown);
        output.putInt(KEY_INTERNAL_COOLDOWN, internalCooldown);
    }

    public void load(@NotNull ValueInput input) {
        Objects.requireNonNull(input, "input cannot be null");
        String stateName = input.getStringOr(KEY_STATE, RollState.IDLE.name());
        try {
            this.state = RollState.valueOf(stateName);
        } catch (IllegalArgumentException e) {
            this.state = RollState.IDLE;
        }
        this.rollTicks = input.getIntOr(KEY_ROLL_TICKS, 0);
        this.maxRollTicks = input.getIntOr(KEY_MAX_ROLL_TICKS, DEFAULT_MAX_ROLL_TICKS);
        this.rollYaw = input.getFloatOr(KEY_ROLL_YAW, 0.0f);
        this.distance = input.getDoubleOr(KEY_DISTANCE, DEFAULT_DISTANCE);

        this.maxCharges = Mth.clamp(input.getIntOr(KEY_MAX_CHARGES, DEFAULT_CHARGES), 1, MAX_ALLOWED_CHARGES);
        this.charges = Mth.clamp(input.getIntOr(KEY_CHARGES, this.maxCharges), 0, this.maxCharges);
        this.rechargeCooldown = Math.max(0, input.getIntOr(KEY_RECHARGE_COOLDOWN, input.getIntOr(KEY_COOLDOWN_TICKS, 0)));
        this.internalCooldown = Math.max(0, input.getIntOr(KEY_INTERNAL_COOLDOWN, 0));
    }
}
