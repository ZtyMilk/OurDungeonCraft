package com.github.ztymilk.soul;

import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Data model for player soul storage.
 * In Minecraft Dungeons, souls are gathered from defeated mobs and used to power artifacts.
 * The default soul capacity is capped at 40.
 */
public class SoulData {
    public static final int DEFAULT_MAX_SOULS = 40;
    public static final String KEY_SOULS = "Souls";
    public static final String KEY_MAX_SOULS = "MaxSouls";

    private int souls = 0;
    private int maxSouls = DEFAULT_MAX_SOULS;

    public SoulData() {
    }

    public int getSouls() {
        return souls;
    }

    public void setSouls(int souls) {
        this.souls = Mth.clamp(souls, 0, this.maxSouls);
    }

    public int getMaxSouls() {
        return maxSouls;
    }

    public void setMaxSouls(int maxSouls) {
        this.maxSouls = Math.max(1, maxSouls);
        this.souls = Mth.clamp(this.souls, 0, this.maxSouls);
    }

    /**
     * Adds the specified amount of souls, clamped to maxSouls.
     *
     * @param amount Souls to add.
     * @return The actual number of souls successfully added.
     */
    public int addSouls(int amount) {
        if (amount <= 0) {
            return 0;
        }
        int prev = this.souls;
        this.souls = Math.min(this.maxSouls, this.souls + amount);
        return this.souls - prev;
    }

    /**
     * Deducts the specified amount of souls if the player has enough.
     *
     * @param amount Souls to deduct.
     * @return True if deducted successfully; false if player didn't have enough souls.
     */
    public boolean removeSouls(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (this.souls >= amount) {
            this.souls -= amount;
            return true;
        }
        return false;
    }

    /**
     * Forcibly removes souls, clamping to 0. Used by admin commands.
     *
     * @param amount Souls to forcibly deduct.
     */
    public void forceRemoveSouls(int amount) {
        if (amount <= 0) {
            return;
        }
        this.souls = Math.max(0, this.souls - amount);
    }

    public boolean isFull() {
        return this.souls >= this.maxSouls;
    }

    public boolean isEmpty() {
        return this.souls <= 0;
    }

    /**
     * Serializes soul data into Minecraft 1.21.11 ValueOutput.
     */
    public void save(@NotNull ValueOutput output) {
        Objects.requireNonNull(output, "output cannot be null");
        output.putInt(KEY_SOULS, this.souls);
        output.putInt(KEY_MAX_SOULS, this.maxSouls);
    }

    /**
     * Deserializes soul data from Minecraft 1.21.11 ValueInput.
     */
    public void load(@NotNull ValueInput input) {
        Objects.requireNonNull(input, "input cannot be null");
        this.maxSouls = input.getIntOr(KEY_MAX_SOULS, DEFAULT_MAX_SOULS);
        this.souls = Mth.clamp(input.getIntOr(KEY_SOULS, 0), 0, this.maxSouls);
    }
}
