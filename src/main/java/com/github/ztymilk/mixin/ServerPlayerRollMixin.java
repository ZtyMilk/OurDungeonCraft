package com.github.ztymilk.mixin;

import com.github.ztymilk.network.ModNetworking;
import com.github.ztymilk.roll.RollDataAccessor;
import com.github.ztymilk.soul.SoulDataAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin injecting into {@link ServerPlayer} to handle dimension transitions and respawning.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerRollMixin {

    /**
     * Edge case: when a player changes dimension or teleports across levels, reset roll state to IDLE and sync.
     */
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("RETURN"))
    private void ourDungeonCraft$onDimensionTeleport(TeleportTransition teleportTransition, CallbackInfoReturnable<ServerPlayer> cir) {
        ServerPlayer player = cir.getReturnValue();
        if (player != null) {
            if ((Object) player instanceof RollDataAccessor accessor) {
                accessor.resetRoll();
                ModNetworking.syncToPlayer(player, accessor.getRollData());
            }
            if ((Object) player instanceof SoulDataAccessor) {
                ModNetworking.syncSouls(player);
            }
        }
    }

    /**
     * Edge case: when a player respawns or returns from an End portal transition, reset roll state to IDLE and sync.
     */
    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void ourDungeonCraft$onRestoreFrom(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            if ((Object) this instanceof RollDataAccessor accessor) {
                accessor.resetRoll();
                ModNetworking.syncToPlayer(player, accessor.getRollData());
            }
            if ((Object) this instanceof SoulDataAccessor soulAccessor && (Object) oldPlayer instanceof SoulDataAccessor oldSoulAccessor) {
                soulAccessor.setSouls(oldSoulAccessor.getSouls());
                soulAccessor.setMaxSouls(oldSoulAccessor.getMaxSouls());
                ModNetworking.syncSouls(player);
            }
        }
    }
}
