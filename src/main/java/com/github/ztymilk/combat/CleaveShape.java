package com.github.ztymilk.combat;

/**
 * Geometric shape describing the multi-target hit detection volume of a weapon swing.
 */
public sealed interface CleaveShape {

    /**
     * No cleave - single target only (e.g. focused gauntlet punches or dagger thrusts).
     */
    record None() implements CleaveShape {}

    /**
     * Horizontal sector / cone in front of the player.
     *
     * @param angleDegrees Spread angle in degrees (e.g. 70° for normal sword, 140° for claymore).
     * @param radius       Maximum distance from player center.
     */
    record Cone(float angleDegrees, float radius) implements CleaveShape {}

    /**
     * Full 360-degree radial circle around the player (e.g. Double Axe whirlwind spin).
     *
     * @param radius Maximum radial distance in blocks.
     */
    record Circle360(float radius) implements CleaveShape {}

    /**
     * Concussive ground smash around the impact point, lifting mobs upward.
     *
     * @param radius Impact shockwave radius in blocks.
     * @param lift   Upward vertical velocity applied to affected targets.
     */
    record GroundSmash(float radius, float lift) implements CleaveShape {}

    /**
     * Linear piercing raycast capsule in front of the player (e.g. Spear & Glaive thrusts).
     *
     * @param length Forward reach length in blocks.
     * @param width  Lateral thickness / capsule radius.
     */
    record PiercingLine(float length, float width) implements CleaveShape {}
}
