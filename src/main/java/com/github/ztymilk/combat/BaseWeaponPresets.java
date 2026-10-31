package com.github.ztymilk.combat;

import java.util.List;

/**
 * Concrete combo presets matching the authentic combat mechanics and cadence of Minecraft Dungeons base weapons.
 * <p>
 * Re-calibrated under v2.0 Differentiation Specification:
 * - 4 Weapon Archetype Tiers: Light (2.2~3.2), Medium (1.4~1.6), Polearm (1.1~1.25), Heavy (0.6~0.9).
 * - Differentiated recovery ticks, dynamic knockback vectors, and authentic reach geometries.
 */
public final class BaseWeaponPresets {

    private BaseWeaponPresets() {}

    /**
     * 1. Dungeon Sword: Medium Versatile 3-hit rhythm (Slash L -> Backhand R -> Cleave Finisher).
     * Speed: 1.60 | Cadence: 8 -> 8 -> 12 recovery ticks | Total: 28 ticks (1.4s).
     * Reach: 3.0m baseline cones expanding up to 120° / 3.4m on finisher.
     */
    public static final ComboSequence SWORD = new ComboSequence(List.of(
            new ComboStep(0, 1.00f, 0.90f, 8, new CleaveShape.Cone(75.0f, 3.0f), false),
            new ComboStep(1, 1.15f, 1.00f, 8, new CleaveShape.Cone(90.0f, 3.0f), false),
            new ComboStep(2, 1.55f, 1.50f, 12, new CleaveShape.Cone(120.0f, 3.4f), true)
    ));

    /**
     * 2. Double Axe: Heavy 2-hit continuous whirlwind (360° Spin Cleave -> Heavy Downward Cleave).
     * Speed: 0.90 | Cadence: 15 -> 20 recovery ticks | Total: 35 ticks (1.75s).
     * Reach: 3.4m full 360° unhindered whirlwind cleave, culminating in a 135° downward slam.
     */
    public static final ComboSequence DOUBLE_AXE = new ComboSequence(List.of(
            new ComboStep(0, 1.15f, 1.10f, 15, new CleaveShape.Circle360(3.4f), false),
            new ComboStep(1, 1.65f, 1.80f, 20, new CleaveShape.Cone(135.0f, 3.2f), true)
    ));

    /**
     * 3. Great Hammer: Colossal 2-hit seismic bash (Concussive Bash -> Earthshaker Ground Smash).
     * Speed: 0.60 | Cadence: 22 -> 32 recovery ticks | Total: 54 ticks (2.7s).
     * Reach: 3.2m concussive bash into a massive 4.2m radius seismic shockwave with 2.5x knockback & stun.
     */
    public static final ComboSequence GREAT_HAMMER = new ComboSequence(List.of(
            new ComboStep(0, 1.20f, 1.30f, 22, new CleaveShape.Cone(65.0f, 3.2f), false),
            new ComboStep(1, 2.00f, 2.50f, 32, new CleaveShape.GroundSmash(4.2f, 0.50f), true)
    ));

    /**
     * 4. Claymore: Heavy Colossal 3-hit sweeping cadence (Broad Cleave L 130° -> Return Cleave R 140° -> Mountain Splitter 150°).
     * Speed: 0.75 | Cadence: 18 -> 18 -> 28 recovery ticks | Total: 64 ticks (3.2s).
     * Reach: 3.8m sweeping arcs up to 4.2m / 150° mountain splitter finisher with hyper-armor.
     */
    public static final ComboSequence CLAYMORE = new ComboSequence(List.of(
            new ComboStep(0, 1.20f, 1.20f, 18, new CleaveShape.Cone(130.0f, 3.8f), false),
            new ComboStep(1, 1.35f, 1.30f, 18, new CleaveShape.Cone(140.0f, 3.8f), false),
            new ComboStep(2, 2.00f, 2.20f, 28, new CleaveShape.Cone(150.0f, 4.2f), true)
    ));

    /**
     * 5. Daggers: Ultra-Fast Light 4-hit flurry (Left Stab -> Right Stab -> Cross Cut -> Dual Plunge).
     * Speed: 3.20 | Cadence: 4 -> 4 -> 5 -> 7 recovery ticks | Total: 20 ticks (1.0s).
     * Reach: 2.0m tight point-blank pocket, high critical scaling & lacerating bleed finisher.
     */
    public static final ComboSequence DAGGERS = new ComboSequence(List.of(
            new ComboStep(0, 0.90f, 0.40f, 4, new CleaveShape.None(), false),
            new ComboStep(1, 0.90f, 0.40f, 4, new CleaveShape.None(), false),
            new ComboStep(2, 1.15f, 0.60f, 5, new CleaveShape.Cone(50.0f, 2.0f), false),
            new ComboStep(3, 1.55f, 1.10f, 7, new CleaveShape.None(), true)
    ));

    /**
     * 6. Gauntlets: Fast Light 3-hit boxing combo (Left Jab -> Right Cross -> Rocket Uppercut).
     * Speed: 2.60 | Cadence: 5 -> 5 -> 9 recovery ticks | Total: 19 ticks (0.95s).
     * Reach: 2.1m boxing pocket, culminating in a 0.65 vertical impulse rising uppercut aerial launcher.
     */
    public static final ComboSequence GAUNTLETS = new ComboSequence(List.of(
            new ComboStep(0, 0.95f, 0.50f, 5, new CleaveShape.None(), false),
            new ComboStep(1, 1.05f, 0.65f, 5, new CleaveShape.None(), false),
            new ComboStep(2, 1.65f, 1.80f, 9, new CleaveShape.GroundSmash(2.2f, 0.65f), true)
    ));

    /**
     * 7. Spear: Long-Reach Polearm 3-hit linear thrusts (Jab 1 -> Jab 2 -> Power Lunge).
     * Speed: 1.25 | Cadence: 9 -> 9 -> 14 recovery ticks | Total: 32 ticks (1.6s).
     * Reach: 4.8m continuous piercing capsule into a devastating 5.5m extended power lunge.
     */
    public static final ComboSequence SPEAR = new ComboSequence(List.of(
            new ComboStep(0, 1.00f, 0.90f, 9, new CleaveShape.PiercingLine(4.8f, 0.8f), false),
            new ComboStep(1, 1.10f, 1.00f, 9, new CleaveShape.PiercingLine(4.8f, 0.8f), false),
            new ComboStep(2, 1.65f, 1.70f, 14, new CleaveShape.PiercingLine(5.5f, 1.2f), true)
    ));

    /**
     * 8. Mace: Medium Blunt 3-hit concussive combo (Downward Bash -> Backhand Strike -> Skullcracker Smash).
     * Speed: 1.40 | Cadence: 10 -> 10 -> 16 recovery ticks | Total: 36 ticks (1.8s).
     * Reach: 2.8m concussive blunt impact with armor fracture & shield-breaking shockwave.
     */
    public static final ComboSequence MACE = new ComboSequence(List.of(
            new ComboStep(0, 1.05f, 1.00f, 10, new CleaveShape.Cone(60.0f, 2.8f), false),
            new ComboStep(1, 1.15f, 1.10f, 10, new CleaveShape.Cone(70.0f, 2.8f), false),
            new ComboStep(2, 1.60f, 1.80f, 16, new CleaveShape.GroundSmash(3.0f, 0.35f), true)
    ));

    /**
     * 9. Sickles: Agile Light 4-hit reaping combo (Slash L -> Slash R -> Inward Hook Pull -> 360 Spin).
     * Speed: 2.20 | Cadence: 6 -> 6 -> 7 -> 10 recovery ticks | Total: 29 ticks (1.45s).
     * Reach: 2.5m twin sickle sweeps, inward vacuum hook pull (-0.45 kb), and 2.8m 360° whirlwind finisher.
     */
    public static final ComboSequence SICKLES = new ComboSequence(List.of(
            new ComboStep(0, 0.95f, 0.60f, 6, new CleaveShape.Cone(85.0f, 2.5f), false),
            new ComboStep(1, 0.95f, 0.60f, 6, new CleaveShape.Cone(85.0f, 2.5f), false),
            new ComboStep(2, 1.10f, -0.45f, 7, new CleaveShape.Cone(105.0f, 2.6f), false),
            new ComboStep(3, 1.50f, 1.40f, 10, new CleaveShape.Circle360(2.8f), true)
    ));

    /**
     * 10. Glaive: Sweeping Polearm 3-hit combo (Wide Sweep L -> Wide Sweep R -> Sweeping Lunge).
     * Speed: 1.10 | Cadence: 12 -> 12 -> 18 recovery ticks | Total: 42 ticks (2.1s).
     * Reach: 4.8m wide sweeping arcs (125° -> 135°), culminating in a 5.2m piercing lunge.
     */
    public static final ComboSequence GLAIVE = new ComboSequence(List.of(
            new ComboStep(0, 1.05f, 1.10f, 12, new CleaveShape.Cone(125.0f, 4.8f), false),
            new ComboStep(1, 1.15f, 1.20f, 12, new CleaveShape.Cone(135.0f, 4.8f), false),
            new ComboStep(2, 1.70f, 1.80f, 18, new CleaveShape.PiercingLine(5.2f, 1.4f), true)
    ));
}
