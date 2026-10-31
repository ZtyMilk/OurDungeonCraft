package com.github.ztymilk.network;

import com.github.ztymilk.OurDungeonCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S 数据包：客户端按下翻滚按键向服务端请求翻滚
 * @param yaw 客户端计算好的世界朝向翻滚角度
 */
public record RollRequestPayload(float yaw) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RollRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(OurDungeonCraft.id("roll_request"));

    public static final StreamCodec<ByteBuf, RollRequestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT,
            RollRequestPayload::yaw,
            RollRequestPayload::new
    );

    @Override
    public CustomPacketPayload.Type<RollRequestPayload> type() {
        return TYPE;
    }
}
