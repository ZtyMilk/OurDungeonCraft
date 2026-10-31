package com.github.ztymilk.roll;

public final class RollConstants {
    private RollConstants() {}

    /** 初始翻滚充能次数 */
    public static final int DEFAULT_CHARGES = 1;

    /** 翻滚充能次数硬上限 (锁死4次) */
    public static final int MAX_ALLOWED_CHARGES = 4;

    /** 连滚防误触内置CD (2 ticks = 0.1秒) */
    public static final int INTERNAL_COOLDOWN_TICKS = 2;

    /** 前摇 tick 数 (发力姿势、音效与起步扬尘) */
    public static final int STARTUP_TICKS = 2;

    /** 核心位移与无敌帧 tick 数 (高速推进、全身 360° 翻滚、无敌状态) */
    public static final int ACTIVE_TICKS = 8;

    /** 后摇 tick 数 (减速缓冲、落地起身) */
    public static final int RECOVERY_TICKS = 4;

    /** 翻滚总时长 (14 tick = 0.7秒) */
    public static final int TOTAL_ROLL_TICKS = STARTUP_TICKS + ACTIVE_TICKS + RECOVERY_TICKS;

    /** 翻滚总位移 (约 3.6 格，ACTIVE 阶段每 tick 推进约 0.45 格) */
    public static final double TOTAL_DISTANCE = 3.6;

    /** 翻滚单次充能冷却时间 (30 tick = 1.5秒) */
    public static final int COOLDOWN_TICKS = 30;

    /** 翻滚单次充能冷却时间别名 (30 tick = 1.5秒) */
    public static final int RECHARGE_TICKS = COOLDOWN_TICKS;
}
