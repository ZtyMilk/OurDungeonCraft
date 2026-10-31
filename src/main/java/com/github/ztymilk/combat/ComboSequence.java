package com.github.ztymilk.combat;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Immutable sequence of attack steps defining a weapon's combo chain.
 */
public final class ComboSequence {

    private final List<ComboStep> steps;

    public ComboSequence(@NotNull List<ComboStep> steps) {
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("ComboSequence must contain at least one ComboStep");
        }
        this.steps = Collections.unmodifiableList(steps);
    }

    public int getLength() {
        return this.steps.size();
    }

    public @NotNull ComboStep getStep(int index) {
        if (index < 0) index = 0;
        return this.steps.get(index % this.steps.size());
    }

    public boolean isFinisher(int index) {
        return getStep(index).isFinisher();
    }

    public List<ComboStep> getSteps() {
        return this.steps;
    }
}
