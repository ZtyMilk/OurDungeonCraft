package com.github.ztymilk.combat;

import org.jetbrains.annotations.NotNull;

/**
 * Duck interface implemented on {@link net.minecraft.world.entity.player.Player}
 * to grant access to the player's active {@link AttackComboData}.
 */
public interface AttackComboAccessor {
    @NotNull AttackComboData ourDungeonCraft$getComboData();
}
