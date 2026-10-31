package com.github.ztymilk.network;

import com.github.ztymilk.OurDungeonCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C 数据包：向附近客户端同步攻击伤害跳字（常规白字 / 大号粗体鲜红暴击）
 */
public record DamageNumberSyncPayload(
        int entityId,
        double x,
        double y,
        double z,
        float amount,
        boolean isCrit
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DamageNumberSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(OurDungeonCraft.id("damage_number_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DamageNumberSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            DamageNumberSyncPayload::entityId,
            ByteBufCodecs.DOUBLE,
            DamageNumberSyncPayload::x,
            ByteBufCodecs.DOUBLE,
            DamageNumberSyncPayload::y,
            ByteBufCodecs.DOUBLE,
            DamageNumberSyncPayload::z,
            ByteBufCodecs.FLOAT,
            DamageNumberSyncPayload::amount,
            ByteBufCodecs.BOOL,
            DamageNumberSyncPayload::isCrit,
            DamageNumberSyncPayload::new
    );

    @Override
    public CustomPacketPayload.Type<DamageNumberSyncPayload> type() {
        return TYPE;
    }
}
