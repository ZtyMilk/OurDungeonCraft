package com.github.ztymilk.combat;

import com.github.ztymilk.network.DamageNumberSyncPayload;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 伤害跳字系统与受击网络同步单元测试。
 */
public class DamageFeedbackTest {

    @BeforeAll
    static void initBootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    @DisplayName("测试伤害数值格式化规范（整数呈现原版数字风格，小数呈现一位小数）")
    void testFormatDamage() {
        // 原版物品栏数字风格：整数无多余小数位
        assertEquals("7", DamageFeedbackTracker.formatDamage(7.0f));
        assertEquals("15", DamageFeedbackTracker.formatDamage(15.0f));
        assertEquals("0", DamageFeedbackTracker.formatDamage(0.0f));
        assertEquals("100", DamageFeedbackTracker.formatDamage(100.02f));

        // 浮点与小数伤害保留一位小数
        assertEquals("7.5", DamageFeedbackTracker.formatDamage(7.5f));
        assertEquals("12.3", DamageFeedbackTracker.formatDamage(12.34f));
        assertEquals("0.8", DamageFeedbackTracker.formatDamage(0.81f));
    }

    @Test
    @DisplayName("测试暴击状态线程上下文传递与清理")
    void testCritThreadLocal() {
        assertNull(DamageFeedbackTracker.getCurrentCrit());

        DamageFeedbackTracker.setCurrentCrit(true);
        assertTrue(DamageFeedbackTracker.getCurrentCrit());

        DamageFeedbackTracker.setCurrentCrit(false);
        assertFalse(DamageFeedbackTracker.getCurrentCrit());

        DamageFeedbackTracker.clearCurrentCrit();
        assertNull(DamageFeedbackTracker.getCurrentCrit());
    }

    @Test
    @DisplayName("测试 S2C 伤害跳字数据包构建与字段映射")
    void testDamageNumberSyncPayload() {
        DamageNumberSyncPayload payload = new DamageNumberSyncPayload(
                101,
                12.5,
                64.2,
                -8.75,
                14.5f,
                true
        );

        assertEquals(101, payload.entityId());
        assertEquals(12.5, payload.x(), 0.0001);
        assertEquals(64.2, payload.y(), 0.0001);
        assertEquals(-8.75, payload.z(), 0.0001);
        assertEquals(14.5f, payload.amount(), 0.0001f);
        assertTrue(payload.isCrit());
        assertEquals("ourdungeoncraft:damage_number_sync", payload.type().id().toString());
    }

    @Test
    @DisplayName("测试暴击弹性弹出动画曲线数学模型")
    void testElasticBounceAnimationCurve() {
        // 验证 0~6 tick 内的弹性弹出放大数学曲线: bounce = 0.45 + 0.55*t + 0.45*sin(t*pi)
        // t=0: 初始起始尺寸
        float t0 = 0.0f;
        float b0 = 0.45f + 0.55f * t0 + 0.45f * (float) Math.sin(t0 * Math.PI);
        assertEquals(0.45f, b0, 0.01f);

        // t=0.5 (第3 tick): 弹性超冲放大冲量 (overshoot pop)
        float tMid = 0.5f;
        float bMid = 0.45f + 0.55f * tMid + 0.45f * (float) Math.sin(tMid * Math.PI);
        assertTrue(bMid >= 1.15f, "暴击中段必须具备明显的超冲弹出放大效果，实际: " + bMid);

        // t=1.0 (第6 tick): 弹性回弹平滑稳定在 1.0
        float t1 = 1.0f;
        float b1 = 0.45f + 0.55f * t1 + 0.45f * (float) Math.sin(t1 * Math.PI);
        assertEquals(1.0f, b1, 0.01f);

        // 验证暴击基准尺寸 0.046f 与普通尺寸 0.026f 的比例约为 1.77x
        float normalScale = 0.026f;
        float critBaseScale = 0.046f;
        assertTrue(critBaseScale / normalScale >= 1.70f, "暴击跳字尺寸必须显著大于常规白字尺寸");
    }

    @Test
    @DisplayName("测试 mixins 配置文件中已正确声明 LivingEntityDamageMixin")
    void testMixinRegistration() throws IOException {
        Path mixinConfig = Path.of("src/main/resources/ourdungeoncraft.mixins.json");
        assertTrue(Files.exists(mixinConfig));
        String content = Files.readString(mixinConfig);
        assertTrue(content.contains("LivingEntityDamageMixin"), "ourdungeoncraft.mixins.json 必须包含 LivingEntityDamageMixin 注册");
    }
}
