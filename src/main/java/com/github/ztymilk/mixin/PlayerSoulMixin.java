package com.github.ztymilk.mixin;

import com.github.ztymilk.soul.SoulData;
import com.github.ztymilk.soul.SoulDataAccessor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin injecting {@link SoulDataAccessor} into {@link Player}.
 * Handles soul data storage, NBT persistence, and accessor exposure.
 */
@Mixin(Player.class)
public abstract class PlayerSoulMixin implements SoulDataAccessor {

    @Unique
    private final SoulData ourDungeonCraft$soulData = new SoulData();

    @Override
    public @NotNull SoulData getSoulData() {
        return this.ourDungeonCraft$soulData;
    }

    /**
     * Persists soul count and capacity to player save data.
     */
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void ourDungeonCraft$saveSoulData(ValueOutput output, CallbackInfo ci) {
        this.ourDungeonCraft$soulData.save(output);
    }

    /**
     * Restores soul count and capacity from player save data.
     */
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void ourDungeonCraft$loadSoulData(ValueInput input, CallbackInfo ci) {
        this.ourDungeonCraft$soulData.load(input);
    }
}
