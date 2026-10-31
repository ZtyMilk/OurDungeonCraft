package com.github.ztymilk.combat;

import com.github.ztymilk.network.DamageNumberSyncPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 战斗伤害反馈追踪器与S2C网络同步中心。
 * 负责捕获攻击判定中的暴击标识、受击计算结果，并向视野内玩家广播伤害跳字。
 */
public final class DamageFeedbackTracker {

    private static final ThreadLocal<Boolean> CURRENT_ATTACK_CRIT = new ThreadLocal<>();

    private DamageFeedbackTracker() {}

    /**
     * 设置当前线程正在执行的攻击是否为暴击。
     */
    public static void setCurrentCrit(boolean isCrit) {
        CURRENT_ATTACK_CRIT.set(isCrit);
    }

    /**
     * 获取当前线程上下文中的暴击状态。
     */
    public static @Nullable Boolean getCurrentCrit() {
        return CURRENT_ATTACK_CRIT.get();
    }

    /**
     * 清理当前线程上下文中的暴击状态。
     */
    public static void clearCurrentCrit() {
        CURRENT_ATTACK_CRIT.remove();
    }

    /**
     * 受击计算完成后调用：广播伤害数字到攻击者、受击者及周围所有追踪玩家。
     *
     * @param target       受击实体
     * @param source       伤害来源
     * @param damageAmount 最终结算的伤害数值
     * @param isCrit       是否为暴击命中
     */
    public static void broadcastDamageNumber(
            @NotNull LivingEntity target,
            @NotNull DamageSource source,
            float damageAmount,
            boolean isCrit
    ) {
        if (target.level().isClientSide() || damageAmount < 0.1f) {
            return;
        }

        // 仅对实体攻击（近战、弹射物、投掷物、横扫）产生伤害跳字，忽略摔落、溺水、饥饿等非攻击类环境伤害
        if (source.getEntity() == null && source.getDirectEntity() == null) {
            return;
        }

        // 在受击生物头顶位置生成，叠加轻微水平扰动避免多段伤害重合
        double x = target.getX() + (target.getRandom().nextDouble() - 0.5) * 0.45;
        double y = target.getY() + target.getBbHeight() + 0.15 + target.getRandom().nextDouble() * 0.2;
        double z = target.getZ() + (target.getRandom().nextDouble() - 0.5) * 0.45;

        // 暴击命中数字初始高度稍高，呈现更鲜明的爆发感
        if (isCrit) {
            y += 0.25;
        }

        DamageNumberSyncPayload payload = new DamageNumberSyncPayload(
                target.getId(),
                x,
                y,
                z,
                damageAmount,
                isCrit
        );

        // 去重收集接收者：追踪该实体的客户端 + 受害者本人 + 攻击者本人
        Set<ServerPlayer> recipients = new HashSet<>(PlayerLookup.tracking(target));
        if (target instanceof ServerPlayer victimPlayer) {
            recipients.add(victimPlayer);
        }
        if (source.getEntity() instanceof ServerPlayer attackerPlayer) {
            recipients.add(attackerPlayer);
        }

        for (ServerPlayer player : recipients) {
            if (ServerPlayNetworking.canSend(player, DamageNumberSyncPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    /**
     * 将伤害数值格式化为显示文本。
     * 整数呈现原版数字风格（如 "7"），带小数呈现一位小数（如 "7.5"）。
     */
    public static @NotNull String formatDamage(float damage) {
        if (Math.abs(damage - Math.round(damage)) < 0.05f) {
            return String.valueOf(Math.round(damage));
        } else {
            return String.format(Locale.ROOT, "%.1f", damage);
        }
    }
}
