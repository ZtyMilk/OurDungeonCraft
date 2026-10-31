package com.github.ztymilk.combat;

import org.jetbrains.annotations.NotNull;

/**
 * Encapsulates the physics and combat parameters for a single attack step in a weapon combo.
 *
 * @param stepIndex          0-based index of this step in the combo sequence.
 * @param damageMultiplier   Damage multiplier applied to weapon base damage (e.g. 1.0 = 100%, 1.4 = 140%).
 * @param knockbackMultiplier Multiplier applied to outgoing knockback force.
 * @param recoveryTicks      Attack cooldown / recovery delay in ticks (20 ticks = 1.0s).
 * @param cleaveShape        Multi-target cleave geometry.
 * @param isFinisher         Whether this step is the climactic finisher of the combo sequence.
 */
public record ComboStep(
        int stepIndex,
        float damageMultiplier,
        float knockbackMultiplier,
        int recoveryTicks,
        @NotNull CleaveShape cleaveShape,
        boolean isFinisher
) {}
