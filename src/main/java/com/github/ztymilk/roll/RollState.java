package com.github.ztymilk.roll;

import org.jetbrains.annotations.NotNull;

/**
 * Represents the distinct states in the Minecraft Dungeons Dodge Roll state machine.
 * <p>
 * Full lifecycle:
 * <pre>
 *   IDLE ──(startRoll)──> STARTUP (2 ticks) ──> ACTIVE (8 ticks) ──> RECOVERY (4 ticks) ──> IDLE (cooldown: 30 ticks)
 *                             │                     │                     │
 *                             │           (wall collision)                │
 *                             │                     └──> RECOVERY         │
 *                             │                                           │
 *                             └───────────(death / void / dimension)──────┴───────────────> IDLE
 * </pre>
 * Total roll duration: 2 (STARTUP) + 8 (ACTIVE) + 4 (RECOVERY) = 14 ticks.
 */
public enum RollState {
    /**
     * Default state. Player is stationary or moving normally, not dodging.
     * Cooldown may be ticking down.
     */
    IDLE(0, false, false),

    /**
     * Initial wind-up / telegraphing phase.
     * Lasts 2 ticks. Player prepares momentum; not yet invulnerable.
     */
    STARTUP(2, false, false),

    /**
     * Active tumble / evasion phase.
     * Lasts 8 ticks. Player gains boosted directional velocity (3.5 blocks total).
     * Invulnerability frames (i-frames) are active.
     * If the player collides horizontally with a block/wall, transitions immediately to {@link #RECOVERY}.
     */
    ACTIVE(8, true, true),

    /**
     * Post-roll recovery / landing lag phase.
     * Lasts 4 ticks. Momentum dampens; invulnerability frames end.
     */
    RECOVERY(4, false, false);

    private final int durationTicks;
    private final boolean invulnerable;
    private final boolean movementBoost;

    RollState(int durationTicks, boolean invulnerable, boolean movementBoost) {
        this.durationTicks = durationTicks;
        this.invulnerable = invulnerable;
        this.movementBoost = movementBoost;
    }

    /**
     * @return Duration of this state in Minecraft game ticks.
     */
    public int getDurationTicks() {
        return durationTicks;
    }

    /**
     * @return True if the player is currently in any roll phase (not IDLE).
     */
    public boolean isRolling() {
        return this != IDLE;
    }

    /**
     * @return True if damage should be negated during this state (only ACTIVE).
     */
    public boolean isInvulnerable() {
        return invulnerable;
    }

    /**
     * @return True if this state applies directional dodge movement boost.
     */
    public boolean hasMovementBoost() {
        return movementBoost;
    }

    /**
     * Returns the next sequential state in a normal roll lifecycle.
     *
     * @return Next {@link RollState}.
     */
    public @NotNull RollState getNextState() {
        return switch (this) {
            case STARTUP -> ACTIVE;
            case ACTIVE -> RECOVERY;
            case RECOVERY, IDLE -> IDLE;
        };
    }
}
