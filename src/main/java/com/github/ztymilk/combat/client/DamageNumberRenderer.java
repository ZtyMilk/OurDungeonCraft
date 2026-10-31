package com.github.ztymilk.combat.client;

import com.github.ztymilk.combat.DamageFeedbackTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 客户端伤害跳字渲染引擎。
 * 普通命中：呈现原版物品栏数字风格(白字带阴影)，轻柔上浮淡出。
 * 暴击命中：呈现大号粗体鲜红爆鸣数字，带弹性弹出放大动画。
 */
public final class DamageNumberRenderer {

    private static final List<DamageNumberInstance> INSTANCES = new CopyOnWriteArrayList<>();
    private static final int MAX_ACTIVE_NUMBERS = 256;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        WorldRenderEvents.AFTER_ENTITIES.register(DamageNumberRenderer::render);
    }

    /**
     * 在指定世界坐标生成一个伤害跳字。
     *
     * @param x      世界 X 坐标
     * @param y      世界 Y 坐标
     * @param z      世界 Z 坐标
     * @param damage 伤害数值
     * @param isCrit 是否为暴击
     */
    public static void spawn(double x, double y, double z, float damage, boolean isCrit) {
        if (INSTANCES.size() >= MAX_ACTIVE_NUMBERS) {
            INSTANCES.remove(0);
        }

        String formatted = DamageFeedbackTracker.formatDamage(damage);
        Component textComponent = isCrit
                ? Component.literal(formatted).withStyle(ChatFormatting.BOLD)
                : Component.literal(formatted);

        // 为多段连击添加细微水平散射速度，防止跳字完全重叠
        double vx = (Math.random() - 0.5) * (isCrit ? 0.008 : 0.005);
        double vz = (Math.random() - 0.5) * (isCrit ? 0.008 : 0.005);
        int maxAge = isCrit ? 26 : 22; // 暴击驻留时间稍长 (1.3s vs 1.1s)

        INSTANCES.add(new DamageNumberInstance(x, y, z, vx, vz, textComponent, isCrit, maxAge));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            INSTANCES.clear();
            return;
        }

        Iterator<DamageNumberInstance> it = INSTANCES.iterator();
        while (it.hasNext()) {
            DamageNumberInstance inst = it.next();
            inst.age++;
            if (inst.age >= inst.maxAge) {
                INSTANCES.remove(inst);
            }
        }
    }

    private static void render(WorldRenderContext context) {
        LevelRenderState worldState = context.worldState();
        if (worldState == null) return;

        CameraRenderState camera = worldState.cameraRenderState;
        if (camera == null || !camera.initialized || camera.pos == null || camera.orientation == null) {
            return;
        }

        PoseStack poseStack = context.matrices();
        SubmitNodeCollector commandQueue = context.commandQueue();
        if (poseStack == null || commandQueue == null) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 camPos = camera.pos;
        Quaternionf camRot = camera.orientation;

        for (DamageNumberInstance inst : INSTANCES) {
            float currentAge = inst.age + partialTick;
            if (currentAge > inst.maxAge) continue;

            double curX = inst.startX + inst.vx * currentAge;
            double curZ = inst.startZ + inst.vz * currentAge;
            double curY;
            float scale;
            int color;
            int outlineColor;

            if (!inst.isCrit) {
                // 普通命中：原版物品栏数字风格(白字带阴影)，轻柔上浮淡出
                float progress = Math.min(1.0f, currentAge / (float) inst.maxAge);
                curY = inst.startY + Math.sin(progress * Math.PI * 0.5) * 0.65;
                scale = 0.026f;

                // 后半段轻柔淡出
                float alphaProgress = progress < 0.55f ? 1.0f : (1.0f - (progress - 0.55f) / 0.45f);
                int alpha = Math.clamp((int) (alphaProgress * 255.0f), 4, 255);
                color = (alpha << 24) | 0x00FFFFFF; // 白字带阴影
                outlineColor = 0; // 无描边，纯阴影
            } else {
                // 暴击命中：大号粗体鲜红爆鸣数字，带弹性弹出放大动画
                float progress = Math.min(1.0f, currentAge / (float) inst.maxAge);
                curY = inst.startY + Math.sin(progress * Math.PI * 0.5) * 0.85;

                // 弹性弹出曲线 (0~6 tick 内从 0.45 弹性冲击放大到 1.25 后平滑回弹稳定)
                float bounce;
                if (currentAge < 6.0f) {
                    float t = currentAge / 6.0f;
                    bounce = 0.45f + 0.55f * t + 0.45f * (float) Math.sin(t * Math.PI);
                } else {
                    bounce = 1.0f;
                }
                scale = 0.046f * bounce; // 大号尺寸约为常规尺寸的 1.8x

                // 终段淡出
                float alphaProgress = progress < 0.65f ? 1.0f : (1.0f - (progress - 0.65f) / 0.35f);
                int alpha = Math.clamp((int) (alphaProgress * 255.0f), 4, 255);
                color = (alpha << 24) | 0x00FF2222; // 鲜红爆鸣主色
                outlineColor = ((int) (alpha * 0.70f) << 24) | 0x00330000; // 暗红高对比描边
            }

            double relX = curX - camPos.x;
            double relY = curY - camPos.y;
            double relZ = curZ - camPos.z;

            // 视距剔除 (48格以外不渲染)
            if (relX * relX + relY * relY + relZ * relZ > 2304.0) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(relX, relY, relZ);
            poseStack.mulPose(camRot);
            poseStack.scale(scale, -scale, scale);

            float halfWidth = font.width(inst.textComponent) / 2.0f;
            commandQueue.submitText(
                    poseStack,
                    -halfWidth,
                    0.0f,
                    inst.textComponent.getVisualOrderText(),
                    true, // dropShadow
                    Font.DisplayMode.NORMAL,
                    0xF000F0, // 全亮光照
                    color,
                    0, // 背景透明
                    outlineColor
            );
            poseStack.popPose();
        }
    }

    private static class DamageNumberInstance {
        final double startX;
        final double startY;
        final double startZ;
        final double vx;
        final double vz;
        final Component textComponent;
        final boolean isCrit;
        final int maxAge;
        int age;

        DamageNumberInstance(double x, double y, double z, double vx, double vz, Component textComponent, boolean isCrit, int maxAge) {
            this.startX = x;
            this.startY = y;
            this.startZ = z;
            this.vx = vx;
            this.vz = vz;
            this.textComponent = textComponent;
            this.isCrit = isCrit;
            this.maxAge = maxAge;
            this.age = 0;
        }
    }
}
