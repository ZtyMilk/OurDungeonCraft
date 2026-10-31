package com.github.ztymilk.client;

import com.github.ztymilk.network.ModNetworkingClient;
import com.github.ztymilk.roll.RollDataAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

public final class RollInputHandler {
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null || client.isPaused()) {
                return;
            }

            LocalPlayer player = client.player;
            while (ModKeyBindings.rollKey.consumeClick()) {
                RollDataAccessor accessor = RollDataAccessor.get(player);
                if (accessor != null && accessor.canRoll()) {
                    float targetRollYaw = calculateRollYaw(player);

                    // 客户端预测立即生效，消除网络延迟感
                    accessor.startRoll(targetRollYaw);

                    // 向服务端发送翻滚请求
                    ModNetworkingClient.sendRollRequest(targetRollYaw);
                }
            }
        });
    }

    /**
     * 根据当前移动键输入向量 (W/A/S/D) 和玩家当前视角水平偏航角计算绝对翻滚方向
     */
    public static float calculateRollYaw(LocalPlayer player) {
        float baseYaw = player.getYRot();
        Vec2 move = player.input.getMoveVector(); // x: 左(+1)/右(-1), y: 前(+1)/后(-1)

        if (move.x == 0.0f && move.y == 0.0f) {
            return baseYaw;
        }

        // -atan2(x, y) 对应原版朝向逻辑：
        // 前(0, 1) -> 0°
        // 后(0, -1) -> 180°
        // 左(1, 0) -> -90°
        // 右(-1, 0) -> +90°
        // 前左(1, 1) -> -45°
        // 前右(-1, 1) -> +45°
        // 后左(1, -1) -> -135°
        // 后右(-1, -1) -> +135°
        float angleOffset = (float) Math.toDegrees(-Math.atan2(move.x, move.y));
        return Mth.wrapDegrees(baseYaw + angleOffset);
    }
}
