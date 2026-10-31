package com.github.ztymilk.network;

import com.github.ztymilk.OurDungeonCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C 数据包：服务端同步指定实体的翻滚状态、tick 进度、充能次数、最大充能、冷却以及翻滚角度给客户端
 */
public record RollSyncPayload(
        int entityId,
        int stateOrdinal,
        int rollTicks,
        int cooldownTicks,
        float yaw,
        int charges,
        int maxCharges,
        int internalCooldown
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RollSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(OurDungeonCraft.id("roll_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RollSyncPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId());
                buf.writeVarInt(payload.stateOrdinal());
                buf.writeVarInt(payload.rollTicks());
                buf.writeVarInt(payload.cooldownTicks());
                buf.writeFloat(payload.yaw());
                buf.writeVarInt(payload.charges());
                buf.writeVarInt(payload.maxCharges());
                buf.writeVarInt(payload.internalCooldown());
            },
            buf -> new RollSyncPayload(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readFloat(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt()
            )
    );

    @Override
    public CustomPacketPayload.Type<RollSyncPayload> type() {
        return TYPE;
    }
}
