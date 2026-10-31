package com.github.ztymilk.network;

import com.github.ztymilk.OurDungeonCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C payload to synchronize combat hitstop duration and camera shake intensity to the client.
 */
public record HitstopSyncPayload(int entityId, int hitstopTicks, float shakeIntensity) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HitstopSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(OurDungeonCraft.id("hitstop_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HitstopSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            HitstopSyncPayload::entityId,
            ByteBufCodecs.VAR_INT,
            HitstopSyncPayload::hitstopTicks,
            ByteBufCodecs.FLOAT,
            HitstopSyncPayload::shakeIntensity,
            HitstopSyncPayload::new
    );

    @Override
    public CustomPacketPayload.Type<HitstopSyncPayload> type() {
        return TYPE;
    }
}
