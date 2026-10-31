package com.github.ztymilk.hud;

import com.github.ztymilk.client.hud.DungeonHudOverlay;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class DungeonHudTest {

    @BeforeAll
    static void initBootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    @DisplayName("Verify Soul Bar Proportional Clipping (1.pdf 25点灵魂右侧5/8满左侧3/8空)")
    void testSoulBarProportionalClipping() {
        int maxSouls = 40;
        int totalWidth = 81;

        // 0 souls
        assertEquals(0, DungeonHudOverlay.calculateSoulFilledWidth(0, maxSouls, totalWidth));

        // 25 souls: 25 / 40 = 5/8 = 0.625, 81 * 0.625 = 50.625 -> Math.round is 51
        int filledWidth25 = DungeonHudOverlay.calculateSoulFilledWidth(25, maxSouls, totalWidth);
        assertEquals(51, filledWidth25, "25/40 souls should calculate 51px filled width (5/8 of 81px bar)");

        // 20 souls: 20 / 40 = 0.5, 81 * 0.5 = 40.5 -> 41
        assertEquals(41, DungeonHudOverlay.calculateSoulFilledWidth(20, maxSouls, totalWidth));

        // 40 souls: 100% full
        assertEquals(81, DungeonHudOverlay.calculateSoulFilledWidth(40, maxSouls, totalWidth));

        // Clamping overflow
        assertEquals(81, DungeonHudOverlay.calculateSoulFilledWidth(50, maxSouls, totalWidth));
        assertEquals(0, DungeonHudOverlay.calculateSoulFilledWidth(-5, maxSouls, totalWidth));
    }

    @Test
    @DisplayName("Verify Soul Bar 10 Icons X Coordinates (Aligned right-to-left with food bar)")
    void testSoulBarCoordinates() {
        int right = 300; // Simulated right coordinate of hunger bar
        // 10 icons: index 0 to 9
        assertEquals(right - 9, DungeonHudOverlay.getSoulBarX(right, 0), "Rightmost icon index 0");
        assertEquals(right - 17, DungeonHudOverlay.getSoulBarX(right, 1));
        assertEquals(right - 81, DungeonHudOverlay.getSoulBarX(right, 9), "Leftmost icon index 9");

        int span = (right - 9 + 9) - (right - 81);
        assertEquals(81, span, "Total span of 10 icons must exactly match 81px food bar width");
    }

    @Test
    @DisplayName("Verify Roll Cooldown Progress and Scanning Line Math")
    void testRollCooldownProgressMath() {
        int rechargeMaxTicks = 30;

        // CD just triggered: 30 ticks remaining -> progress = 0.0 (scan line at top)
        float progStart = DungeonHudOverlay.calculateRollCooldownProgress(30, rechargeMaxTicks, 0.0f);
        assertEquals(0.0f, progStart, 0.001f);

        // CD half-way: 15 ticks remaining -> progress = 0.5 (scan line at middle)
        float progMid = DungeonHudOverlay.calculateRollCooldownProgress(15, rechargeMaxTicks, 0.0f);
        assertEquals(0.5f, progMid, 0.001f);

        // CD with partialTick: 15 ticks left with 0.5 partialTick -> 14.5 left -> progress = 1.0 - (14.5/30) = 0.5167
        float progPartial = DungeonHudOverlay.calculateRollCooldownProgress(15, rechargeMaxTicks, 0.5f);
        assertEquals(1.0f - (14.5f / 30.0f), progPartial, 0.001f);

        // CD finished: 0 ticks remaining -> progress = 1.0 (scan line reached bottom)
        float progEnd = DungeonHudOverlay.calculateRollCooldownProgress(0, rechargeMaxTicks, 0.0f);
        assertEquals(1.0f, progEnd, 0.001f);
    }

    @Test
    @DisplayName("Verify Roll HUD and Arrow HUD Position Layout Coordinates")
    void testHudLayoutPositions() {
        int screenWidth = 800;
        int screenHeight = 600;

        // Roll HUD without offhand: left of hotbar slot 1
        int rollXNoOffhand = DungeonHudOverlay.getRollHudX(screenWidth, false);
        assertEquals(800 / 2 - 91 - 24, rollXNoOffhand);
        assertEquals(600 - 22, DungeonHudOverlay.getRollHudY(screenHeight));

        // Roll HUD with offhand: shifted left by 29px to prevent overlapping offhand slot
        int rollXWithOffhand = DungeonHudOverlay.getRollHudX(screenWidth, true);
        assertEquals(rollXNoOffhand - 29, rollXWithOffhand);

        // Arrow HUD with ABOVE_ROLL: stacked vertically above Roll HUD
        int arrowXAbove = DungeonHudOverlay.getArrowHudX(screenWidth, false, DungeonHudOverlay.ArrowHudPosition.ABOVE_ROLL);
        int arrowYAbove = DungeonHudOverlay.getArrowHudY(screenHeight, DungeonHudOverlay.ArrowHudPosition.ABOVE_ROLL);
        assertEquals(rollXNoOffhand, arrowXAbove);
        assertEquals(600 - 22 - 24, arrowYAbove);

        // Arrow HUD with RIGHT_OF_FOOD: placed right of hunger bar
        int arrowXFood = DungeonHudOverlay.getArrowHudX(screenWidth, false, DungeonHudOverlay.ArrowHudPosition.RIGHT_OF_FOOD);
        int arrowYFood = DungeonHudOverlay.getArrowHudY(screenHeight, DungeonHudOverlay.ArrowHudPosition.RIGHT_OF_FOOD);
        assertEquals(800 / 2 + 91 + 4, arrowXFood);
        assertEquals(600 - 40, arrowYFood);
    }

    @Test
    @DisplayName("Verify Texture Files Archive in textures/gui/ (1.pdf 开放贴图管理要求)")
    void testTextureAssetsExistAndArchived() {
        String baseDir = "src/main/resources/assets/ourdungeoncraft/textures/gui/";

        File rollTex = new File(baseDir + "roll_indicator.png");
        assertTrue(rollTex.exists(), "roll_indicator.png must exist in textures/gui/");
        assertTrue(rollTex.length() > 0, "roll_indicator.png must not be empty");

        File arrowTex = new File(baseDir + "arrow_counter.png");
        assertTrue(arrowTex.exists(), "arrow_counter.png must exist in textures/gui/");
        assertTrue(arrowTex.length() > 0, "arrow_counter.png must not be empty");

        File soulEmptyTex = new File(baseDir + "soul_empty.png");
        assertTrue(soulEmptyTex.exists(), "soul_empty.png must exist in textures/gui/");
        assertTrue(soulEmptyTex.length() > 0, "soul_empty.png must not be empty");

        File soulFullTex = new File(baseDir + "soul_full.png");
        assertTrue(soulFullTex.exists(), "soul_full.png must exist in textures/gui/");
        assertTrue(soulFullTex.length() > 0, "soul_full.png must not be empty");

        File soulC1Tex = new File(baseDir + "soul_full_c1.png");
        assertTrue(soulC1Tex.exists(), "soul_full_c1.png must exist in textures/gui/");
        assertTrue(soulC1Tex.length() > 0, "soul_full_c1.png must not be empty");

        File soulC2Tex = new File(baseDir + "soul_full_c2.png");
        assertTrue(soulC2Tex.exists(), "soul_full_c2.png must exist in textures/gui/");
        assertTrue(soulC2Tex.length() > 0, "soul_full_c2.png must not be empty");
    }
}
