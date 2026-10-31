package com.github.ztymilk.soul;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Duck-typing interface injected into {@link Player} via Mixin
 * to expose soul tracking capabilities and synchronization.
 */
public interface SoulDataAccessor {

    /**
     * Safely casts an entity (such as Player) to SoulDataAccessor.
     */
    static @Nullable SoulDataAccessor get(Object entity) {
        return entity instanceof SoulDataAccessor accessor ? accessor : null;
    }

    /**
     * Helper to read current souls from a Player.
     */
    static int getSouls(Player player) {
        SoulDataAccessor accessor = get(player);
        return accessor != null ? accessor.getSouls() : 0;
    }

    /**
     * Helper to read maximum souls from a Player.
     */
    static int getMaxSouls(Player player) {
        SoulDataAccessor accessor = get(player);
        return accessor != null ? accessor.getMaxSouls() : SoulData.DEFAULT_MAX_SOULS;
    }

    /**
     * Returns the internal SoulData instance.
     */
    @NotNull
    SoulData getSoulData();

    default int getSouls() {
        return getSoulData().getSouls();
    }

    default void setSouls(int souls) {
        getSoulData().setSouls(souls);
    }

    default int getMaxSouls() {
        return getSoulData().getMaxSouls();
    }

    default void setMaxSouls(int maxSouls) {
        getSoulData().setMaxSouls(maxSouls);
    }

    default int addSouls(int amount) {
        return getSoulData().addSouls(amount);
    }

    default boolean removeSouls(int amount) {
        return getSoulData().removeSouls(amount);
    }
}
