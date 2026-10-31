package com.github.ztymilk.network;

import com.github.ztymilk.OurDungeonCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C payload syncing player souls and soul capacity to the client.
 */
public record SoulSyncPayload(int souls, int maxSouls) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SoulSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(OurDungeonCraft.id("soul_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SoulSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            SoulSyncPayload::souls,
            ByteBufCodecs.VAR_INT,
            SoulSyncPayload::maxSouls,
            SoulSyncPayload::new
    );

    @Override
    public CustomPacketPayload.Type<SoulSyncPayload> type() {
        return TYPE;
    }
}
