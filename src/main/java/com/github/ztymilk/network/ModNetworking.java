package com.github.ztymilk.network;

import com.github.ztymilk.roll.RollData;
import com.github.ztymilk.roll.RollDataAccessor;
import com.github.ztymilk.soul.SoulData;
import com.github.ztymilk.soul.SoulDataAccessor;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class ModNetworking {
    public static void init() {
        // 在双端通用初始化阶段注册 C2S 和 S2C 的 Payload 类型及编解码器
        PayloadTypeRegistry.playC2S().register(RollRequestPayload.TYPE, RollRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RollSyncPayload.TYPE, RollSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HitstopSyncPayload.TYPE, HitstopSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SoulSyncPayload.TYPE, SoulSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DamageNumberSyncPayload.TYPE, DamageNumberSyncPayload.CODEC);

        // 注册服务端 C2S 接收器
        ServerPlayNetworking.registerGlobalReceiver(RollRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            RollDataAccessor accessor = RollDataAccessor.get(player);
            if (accessor != null && accessor.canRoll()) {
                accessor.startRoll(payload.yaw());
                RollData data = accessor.getRollData();
                // 同步给发起翻滚的玩家本人
                syncToPlayer(player, data);
                // 同步给附近追踪该玩家的其他客户端
                syncToTracking(player, data);
            }
        });

        // 玩家连接加入游戏时同步初始翻滚状态与灵魂数据
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            RollDataAccessor accessor = RollDataAccessor.get(player);
            if (accessor != null) {
                syncToPlayer(player, accessor.getRollData());
            }
            syncSouls(player);
        });

        // 其他玩家开始追踪时同步该实体的翻滚状态
        EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) -> {
            if (trackedEntity instanceof ServerPlayer trackedPlayer) {
                RollDataAccessor accessor = RollDataAccessor.get(trackedPlayer);
                if (accessor != null) {
                    RollData data = accessor.getRollData();
                    if (ServerPlayNetworking.canSend(player, RollSyncPayload.TYPE)) {
                        ServerPlayNetworking.send(player, createPayload(trackedPlayer, data));
                    }
                }
            }
        });
    }

    public static RollSyncPayload createPayload(ServerPlayer player, RollData data) {
        return new RollSyncPayload(
                player.getId(),
                data.getState().ordinal(),
                data.getRollTicks(),
                data.getRechargeCooldown(),
                data.getRollYaw(),
                data.getCharges(),
                data.getMaxCharges(),
                data.getInternalCooldown()
        );
    }

    public static void syncToPlayer(ServerPlayer player, RollData data) {
        if (ServerPlayNetworking.canSend(player, RollSyncPayload.TYPE)) {
            ServerPlayNetworking.send(player, createPayload(player, data));
        }
    }

    public static void syncHitstop(ServerPlayer player, int hitstopTicks, float shakeIntensity) {
        if (ServerPlayNetworking.canSend(player, HitstopSyncPayload.TYPE)) {
            ServerPlayNetworking.send(player, new HitstopSyncPayload(player.getId(), hitstopTicks, shakeIntensity));
        }
    }

    public static void syncToTracking(ServerPlayer player, RollData data) {
        RollSyncPayload payload = createPayload(player, data);
        for (ServerPlayer trackingPlayer : PlayerLookup.tracking(player)) {
            if (ServerPlayNetworking.canSend(trackingPlayer, RollSyncPayload.TYPE)) {
                ServerPlayNetworking.send(trackingPlayer, payload);
            }
        }
    }

    public static void syncSouls(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, SoulSyncPayload.TYPE)) {
            SoulDataAccessor accessor = SoulDataAccessor.get(player);
            int souls = accessor != null ? accessor.getSouls() : 0;
            int maxSouls = accessor != null ? accessor.getMaxSouls() : SoulData.DEFAULT_MAX_SOULS;
            ServerPlayNetworking.send(player, new SoulSyncPayload(souls, maxSouls));
        }
    }
}
