package com.github.ztymilk.client.hud;

import com.github.ztymilk.OurDungeonCraft;
import com.github.ztymilk.roll.RollData;
import com.github.ztymilk.roll.RollDataAccessor;
import com.github.ztymilk.soul.SoulData;
import com.github.ztymilk.soul.SoulDataAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * 地下城HUD全套界面套件 (1.pdf 核心机制实装)：
 * <ul>
 *   <li>物品栏左侧翻滚UI：灰化冷却 + 自顶向下扫描冷却线 + 右下角翻滚可用次数；</li>
 *   <li>饱食度上方10格灵魂条UI：40上限，右侧满/左侧空按比例截取，双色动态呼吸变色渐变；</li>
 *   <li>饱食度右侧/翻滚上侧箭袋UI：透明黑底，右下角实时统计背包总箭矢数；</li>
 *   <li>严格限制仅在生存/冒险模式渲染，创造/旁观模式隐藏；</li>
 *   <li>贴图资源统一在 textures/gui/ 开放管理。</li>
 * </ul>
 */
public final class DungeonHudOverlay {

    public static final Identifier ROLL_TEXTURE = OurDungeonCraft.id("textures/gui/roll_indicator.png");
    public static final Identifier ARROW_TEXTURE = OurDungeonCraft.id("textures/gui/arrow_counter.png");
    public static final Identifier SOUL_EMPTY_TEXTURE = OurDungeonCraft.id("textures/gui/soul_empty.png");
    public static final Identifier SOUL_FULL_TEXTURE = OurDungeonCraft.id("textures/gui/soul_full.png");
    public static final Identifier SOUL_C1_TEXTURE = OurDungeonCraft.id("textures/gui/soul_full_c1.png");
    public static final Identifier SOUL_C2_TEXTURE = OurDungeonCraft.id("textures/gui/soul_full_c2.png");

    public enum ArrowHudPosition {
        ABOVE_ROLL,    // 翻滚UI上侧 (左侧堆叠)
        RIGHT_OF_FOOD  // 饱食度UI右侧 (右侧对称)
    }

    public static ArrowHudPosition arrowHudPosition = ArrowHudPosition.ABOVE_ROLL;
    public static boolean isHudVisible = true;

    private DungeonHudOverlay() {
    }

    public static void init() {
        HudRenderCallback.EVENT.register(DungeonHudOverlay::render);
    }

    /**
     * 判断是否满足HUD渲染前置条件：
     * 严格限制仅生存/冒险模式渲染，创造/旁观模式隐藏；F1隐藏或死亡时不渲染。
     */
    public static boolean shouldRender(Minecraft mc, Player player) {
        if (!isHudVisible) return false;
        if (mc == null || mc.options == null || mc.options.hideGui) return false;
        if (mc.gameMode == null || player == null) return false;
        if (player.isDeadOrDying()) return false;

        GameType gameMode = mc.gameMode.getPlayerMode();
        return gameMode == GameType.SURVIVAL || gameMode == GameType.ADVENTURE;
    }

    public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (!shouldRender(mc, player)) {
            return;
        }

        renderRollHud(guiGraphics, deltaTracker, mc, player);
        renderSoulBarHud(guiGraphics, deltaTracker, mc, player);
        renderArrowCounterHud(guiGraphics, deltaTracker, mc, player);
    }

    /**
     * 1. 物品栏左侧翻滚UI：
     * 灰化冷却 + 自顶向下扫描冷却线 + 右下角翻滚可用次数
     */
    public static void renderRollHud(GuiGraphics guiGraphics, DeltaTracker deltaTracker, Minecraft mc, Player player) {
        RollDataAccessor accessor = RollDataAccessor.get(player);
        if (accessor == null) return;
        RollData rollData = accessor.getRollData();

        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();

        boolean hasOffhand = !player.getOffhandItem().isEmpty();
        int rollX = getRollHudX(screenWidth, hasOffhand);
        int rollY = getRollHudY(screenHeight);
        int slotSize = 22;

        // 透明黑底边框背景
        guiGraphics.fill(rollX, rollY, rollX + slotSize, rollY + slotSize, 0x80000000);
        guiGraphics.renderOutline(rollX, rollY, slotSize, slotSize, 0x40FFFFFF);

        int iconW = 18;
        int iconH = 18;
        int iconX = rollX + (slotSize - iconW) / 2;
        int iconY = rollY + (slotSize - iconH) / 2;

        int charges = rollData.getCharges();
        int maxCharges = rollData.getMaxCharges();
        int rechargeCooldown = rollData.getRechargeCooldown();
        int rechargeMaxTicks = Math.max(1, rollData.getRechargeMaxTicks());

        boolean isRecharging = charges < maxCharges && rechargeCooldown > 0;
        float pt = (deltaTracker != null) ? deltaTracker.getGameTimeDeltaPartialTick(true) : 0.0f;
        float progress = calculateRollCooldownProgress(rechargeCooldown, rechargeMaxTicks, pt);

        if (charges == 0) {
            // 灰化冷却：所有次数耗尽，进入全冷却
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, ROLL_TEXTURE, iconX, iconY, 0.0f, 0.0f, iconW, iconH, 32, 32, 0xFF555555);

            // 自顶向下扫描冷却线：随进度向下推进，扫描线上方揭示亮色图标
            if (progress > 0.0f) {
                int scanY = iconY + Math.round(progress * iconH);
                guiGraphics.enableScissor(iconX, iconY, iconX + iconW, scanY);
                guiGraphics.blit(RenderPipelines.GUI_TEXTURED, ROLL_TEXTURE, iconX, iconY, 0.0f, 0.0f, iconW, iconH, 32, 32, 0xFFFFFFFF);
                guiGraphics.disableScissor();

                // 亮白与青蓝色扫描发光线
                guiGraphics.fill(iconX, scanY - 1, iconX + iconW, scanY, 0xFFFFFFFF);
                guiGraphics.fill(iconX, scanY, iconX + iconW, scanY + 1, 0xFF80D8FF);
            }
        } else {
            // 尚有翻滚可用次数：正常渲染高亮贴图
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, ROLL_TEXTURE, iconX, iconY, 0.0f, 0.0f, iconW, iconH, 32, 32, 0xFFFFFFFF);

            // 如果在为下一段充能，从顶部向下扫描冷却线指示下一次充能进度
            if (isRecharging && progress > 0.0f) {
                int scanY = iconY + Math.round(progress * iconH);
                guiGraphics.fill(iconX, scanY - 1, iconX + iconW, scanY, 0xD0FFFFFF);
                guiGraphics.fill(iconX, scanY, iconX + iconW, scanY + 1, 0xD080E5FF);
            }
        }

        // 右下角（跟显示物品数量相同的位置）显示翻滚次数
        String chargeStr = String.valueOf(charges);
        Font font = mc.font;
        int strW = font.width(chargeStr);
        int textX = rollX + slotSize - strW - 2;
        int textY = rollY + slotSize - 8 - 1;
        int textColor = (charges == 0) ? 0xFFFF5555 : 0xFFFFFFFF;
        guiGraphics.drawString(font, chargeStr, textX, textY, textColor, true);
    }

    /**
     * 2. 饱食度上方10格灵魂条UI：
     * 40上限，右侧满/左侧空按比例截取，双色动态呼吸变色渐变
     */
    public static void renderSoulBarHud(GuiGraphics guiGraphics, DeltaTracker deltaTracker, Minecraft mc, Player player) {
        SoulDataAccessor accessor = SoulDataAccessor.get(player);
        int souls = (accessor != null) ? accessor.getSouls() : 0;
        int maxSouls = (accessor != null) ? accessor.getMaxSouls() : SoulData.DEFAULT_MAX_SOULS;
        if (maxSouls <= 0) maxSouls = SoulData.DEFAULT_MAX_SOULS;

        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();

        // 饱食度条右侧基准对齐
        int right = screenWidth / 2 + 91;
        int foodY = screenHeight - 39;
        boolean hasAir = player.getAirSupply() < player.getMaxAirSupply();
        int soulY = foodY - (hasAir ? 20 : 10);

        // 10个图标排成一排，每个间隔8px，总跨度81px
        int totalWidth = 81;

        // 1. 渲染空槽背景 (10个排成一排)
        for (int i = 0; i < 10; i++) {
            int ix = getSoulBarX(right, i);
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, SOUL_EMPTY_TEXTURE, ix, soulY, 0.0f, 0.0f, 9, 9, 17, 16);
        }

        // 2. 按比例截取贴图显示（右侧满、左侧空，与饱食度一致）
        float ratio = Mth.clamp((float) souls / (float) maxSouls, 0.0f, 1.0f);
        if (ratio > 0.0f) {
            int filledWidth = calculateSoulFilledWidth(souls, maxSouls, totalWidth);
            int clipLeft = right - filledWidth;
            int clipRight = right;

            // 启用剪裁区域
            guiGraphics.enableScissor(clipLeft, soulY - 1, clipRight, soulY + 11);

            // 动态变色/双色呼吸渐变效果 (Color 1 Cyan 与 Color 2 Mint 平滑呼吸波动)
            float pt = (deltaTracker != null) ? deltaTracker.getGameTimeDeltaPartialTick(true) : 0.0f;
            float tickTime = (float) mc.gui.getGuiTicks() + pt;
            float breathe = (Mth.sin(tickTime * 0.08f) + 1.0f) * 0.5f; // 0.0 ~ 1.0
            int tintAlpha = (int) (breathe * 255.0f);
            int c2Tint = ARGB.color(tintAlpha, 255, 255, 255);

            for (int i = 0; i < 10; i++) {
                int ix = getSoulBarX(right, i);
                // 基础帧 Color 1 (Cyan / #85FFEA)
                guiGraphics.blit(RenderPipelines.GUI_TEXTURED, SOUL_FULL_TEXTURE, ix, soulY, 0.0f, 0.0f, 9, 9, 17, 16, 17, 32, 0xFFFFFFFF);
                // 呼吸帧 Color 2 (Mint / #73EAA3) 随呼吸振幅叠合
                guiGraphics.blit(RenderPipelines.GUI_TEXTURED, SOUL_FULL_TEXTURE, ix, soulY, 0.0f, 16.0f, 9, 9, 17, 16, 17, 32, c2Tint);
            }

            guiGraphics.disableScissor();
        }
    }

    /**
     * 3. 饱食度右侧/翻滚上侧箭袋UI：
     * 透明黑底，右下角统计背包总箭矢数
     */
    public static void renderArrowCounterHud(GuiGraphics guiGraphics, DeltaTracker deltaTracker, Minecraft mc, Player player) {
        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();
        int slotSize = 22;

        boolean hasOffhand = !player.getOffhandItem().isEmpty();
        int arrowX = getArrowHudX(screenWidth, hasOffhand, arrowHudPosition);
        int arrowY = getArrowHudY(screenHeight, arrowHudPosition);

        // 1. 透明黑底 (translucent black background) 与边框
        guiGraphics.fill(arrowX, arrowY, arrowX + slotSize, arrowY + slotSize, 0x80000000);
        guiGraphics.renderOutline(arrowX, arrowY, slotSize, slotSize, 0x40FFFFFF);

        // 2. 居中渲染箭袋/箭矢贴图
        int iconW = 18;
        int iconH = 18;
        int iconX = arrowX + (slotSize - iconW) / 2;
        int iconY = arrowY + (slotSize - iconH) / 2;
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, ARROW_TEXTURE, iconX, iconY, 0.0f, 0.0f, iconW, iconH, 32, 32, 0xFFFFFFFF);

        // 3. 右下角统计背包与副手总箭矢数
        int arrowCount = countTotalArrows(player);
        String countStr = String.valueOf(arrowCount);
        Font font = mc.font;
        int strW = font.width(countStr);
        int textX = arrowX + slotSize - strW - 2;
        int textY = arrowY + slotSize - 8 - 1;
        int textColor = (arrowCount > 0) ? 0xFFFFFFFF : 0xFFAAAAAA;
        guiGraphics.drawString(font, countStr, textX, textY, textColor, true);
    }

    /**
     * 统计玩家背包所有槽位与副手的箭矢总数
     */
    public static int countTotalArrows(Player player) {
        if (player == null) return 0;
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        if (inv == null) return 0;
        int count = 0;
        int size = inv.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && (stack.is(ItemTags.ARROWS) || stack.getItem() instanceof ArrowItem)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    // --- 纯函数式坐标与计算工具 (支持独立单元测试) ---

    public static int getRollHudX(int screenWidth, boolean hasOffhand) {
        int offhandOffset = hasOffhand ? 29 : 0;
        return screenWidth / 2 - 91 - 24 - offhandOffset;
    }

    public static int getRollHudY(int screenHeight) {
        return screenHeight - 22;
    }

    public static int getArrowHudX(int screenWidth, boolean hasOffhand, ArrowHudPosition pos) {
        if (pos == ArrowHudPosition.ABOVE_ROLL) {
            return getRollHudX(screenWidth, hasOffhand);
        } else {
            return screenWidth / 2 + 91 + 4;
        }
    }

    public static int getArrowHudY(int screenHeight, ArrowHudPosition pos) {
        if (pos == ArrowHudPosition.ABOVE_ROLL) {
            return getRollHudY(screenHeight) - 24;
        } else {
            return screenHeight - 40;
        }
    }

    public static int getSoulBarX(int right, int index) {
        return right - index * 8 - 9;
    }

    public static int calculateSoulFilledWidth(int souls, int maxSouls, int totalWidth) {
        if (maxSouls <= 0 || souls <= 0) return 0;
        float ratio = Mth.clamp((float) souls / (float) maxSouls, 0.0f, 1.0f);
        return Math.round(totalWidth * ratio);
    }

    public static float calculateRollCooldownProgress(int rechargeCooldown, int rechargeMaxTicks, float partialTick) {
        if (rechargeMaxTicks <= 0 || rechargeCooldown <= 0) return 1.0f;
        float ticksLeft = Math.max(0.0f, rechargeCooldown - partialTick);
        return Mth.clamp(1.0f - (ticksLeft / (float) rechargeMaxTicks), 0.0f, 1.0f);
    }
}
