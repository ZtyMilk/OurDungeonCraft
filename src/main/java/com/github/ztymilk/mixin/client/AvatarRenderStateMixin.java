package com.github.ztymilk.mixin.client;

import com.github.ztymilk.combat.client.AttackComboRenderState;
import com.github.ztymilk.roll.client.RollRenderState;
import com.github.ztymilk.sound.WeaponSoundHelper.MCDWeaponType;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements RollRenderState, AttackComboRenderState {

    @Unique
    private boolean ourDungeonCraft$rolling;
    @Unique
    private float ourDungeonCraft$rollProgress;
    @Unique
    private float ourDungeonCraft$rollYaw;

    @Unique
    private boolean ourDungeonCraft$customAttacking;
    @Unique
    private MCDWeaponType ourDungeonCraft$weaponType = MCDWeaponType.SWORD;
    @Unique
    private int ourDungeonCraft$comboStep;
    @Unique
    private float ourDungeonCraft$attackProgress;

    @Override
    public boolean ourDungeonCraft$isRolling() {
        return this.ourDungeonCraft$rolling;
    }

    @Override
    public void ourDungeonCraft$setRolling(boolean rolling) {
        this.ourDungeonCraft$rolling = rolling;
    }

    @Override
    public float ourDungeonCraft$getRollProgress() {
        return this.ourDungeonCraft$rollProgress;
    }

    @Override
    public void ourDungeonCraft$setRollProgress(float progress) {
        this.ourDungeonCraft$rollProgress = progress;
    }

    @Override
    public float ourDungeonCraft$getRollYaw() {
        return this.ourDungeonCraft$rollYaw;
    }

    @Override
    public void ourDungeonCraft$setRollYaw(float yaw) {
        this.ourDungeonCraft$rollYaw = yaw;
    }

    @Override
    public boolean ourDungeonCraft$isCustomAttacking() {
        return this.ourDungeonCraft$customAttacking;
    }

    @Override
    public void ourDungeonCraft$setCustomAttacking(boolean attacking) {
        this.ourDungeonCraft$customAttacking = attacking;
    }

    @Override
    public @NotNull MCDWeaponType ourDungeonCraft$getWeaponType() {
        return this.ourDungeonCraft$weaponType;
    }

    @Override
    public void ourDungeonCraft$setWeaponType(@NotNull MCDWeaponType type) {
        this.ourDungeonCraft$weaponType = type;
    }

    @Override
    public int ourDungeonCraft$getComboStep() {
        return this.ourDungeonCraft$comboStep;
    }

    @Override
    public void ourDungeonCraft$setComboStep(int step) {
        this.ourDungeonCraft$comboStep = step;
    }

    @Override
    public float ourDungeonCraft$getAttackProgress() {
        return this.ourDungeonCraft$attackProgress;
    }

    @Override
    public void ourDungeonCraft$setAttackProgress(float progress) {
        this.ourDungeonCraft$attackProgress = progress;
    }
}
