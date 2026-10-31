package com.github.ztymilk.combat.client;

import com.github.ztymilk.sound.WeaponSoundHelper.MCDWeaponType;
import org.jetbrains.annotations.NotNull;

/**
 * Duck interface implemented on {@link net.minecraft.client.renderer.entity.state.AvatarRenderState}
 * to transfer combat combo parameters from the Entity to the Render pipeline in 1.21.11.
 */
public interface AttackComboRenderState {

    boolean ourDungeonCraft$isCustomAttacking();

    void ourDungeonCraft$setCustomAttacking(boolean attacking);

    @NotNull MCDWeaponType ourDungeonCraft$getWeaponType();

    void ourDungeonCraft$setWeaponType(@NotNull MCDWeaponType type);

    int ourDungeonCraft$getComboStep();

    void ourDungeonCraft$setComboStep(int step);

    float ourDungeonCraft$getAttackProgress();

    void ourDungeonCraft$setAttackProgress(float progress);
}
