package com.github.ztymilk.network;

import com.github.ztymilk.combat.AttackComboAccessor;
import com.github.ztymilk.combat.client.DamageNumberRenderer;
import com.github.ztymilk.roll.RollData;
import com.github.ztymilk.roll.RollDataAccessor;
import com.github.ztymilk.roll.RollState;
import com.github.ztymilk.soul.SoulDataAccessor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class ModNetworkingClient {
    public static void initClient() {
        // 注册客户端接收 S2C 同步包
        ClientPlayNetworking.registerGlobalReceiver(RollSyncPayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            if (client.level == null) return;

            Entity entity = client.level.getEntity(payload.entityId());
            if (entity instanceof Player player) {
                RollDataAccessor accessor = RollDataAccessor.get(player);
                if (accessor != null) {
                    RollData data = accessor.getRollData();
                    RollState[] states = RollState.values();
                    int stateOrd = payload.stateOrdinal();
                    RollState newState = (stateOrd >= 0 && stateOrd < states.length) ? states[stateOrd] : RollState.IDLE;

                    data.setState(newState);
                    data.setRollTicks(payload.rollTicks());
                    data.setRechargeCooldown(payload.cooldownTicks());
                    data.setRollYaw(payload.yaw());
                    data.setMaxCharges(payload.maxCharges());
                    data.setCharges(payload.charges());
                    data.setInternalCooldown(payload.internalCooldown());
                }
            }
        });

        // 注册客户端接收 Hitstop 同步包
        ClientPlayNetworking.registerGlobalReceiver(HitstopSyncPayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            if (client.level == null) return;

            Entity entity = client.level.getEntity(payload.entityId());
            if (entity instanceof Player player && player instanceof AttackComboAccessor accessor) {
                accessor.ourDungeonCraft$getComboData().triggerHitstop(payload.hitstopTicks(), payload.shakeIntensity());
            }
        });

        // 注册客户端接收灵魂数据同步包
        ClientPlayNetworking.registerGlobalReceiver(SoulSyncPayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            if (client.player != null) {
                SoulDataAccessor accessor = SoulDataAccessor.get(client.player);
                if (accessor != null) {
                    accessor.setSouls(payload.souls());
                    accessor.setMaxSouls(payload.maxSouls());
                }
            }
        });

        // 注册客户端接收伤害跳字同步包
        ClientPlayNetworking.registerGlobalReceiver(DamageNumberSyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                DamageNumberRenderer.spawn(payload.x(), payload.y(), payload.z(), payload.amount(), payload.isCrit());
            });
        });
    }

    public static void sendRollRequest(float yaw) {
        if (ClientPlayNetworking.canSend(RollRequestPayload.TYPE)) {
            ClientPlayNetworking.send(new RollRequestPayload(yaw));
        }
    }
}
