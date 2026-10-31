package com.github.ztymilk.roll.client;

/**
 * Interface injected into {@link net.minecraft.client.renderer.entity.state.AvatarRenderState}
 * to bridge dodge roll animation state from entity extraction to render execution.
 */
public interface RollRenderState {
    boolean ourDungeonCraft$isRolling();
    void ourDungeonCraft$setRolling(boolean rolling);

    float ourDungeonCraft$getRollProgress();
    void ourDungeonCraft$setRollProgress(float progress);

    float ourDungeonCraft$getRollYaw();
    void ourDungeonCraft$setRollYaw(float yaw);
}
