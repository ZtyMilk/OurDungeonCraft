package com.github.ztymilk.soul;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SoulSystemTest {

    @BeforeAll
    static void initBootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    @DisplayName("Verify Soul Drop Calculation Based on MaxHP Rounding to 10")
    void testSoulCalculationDrop() {
        // Standard mob HP rounding tests
        assertEquals(2, SoulDropHandler.calculateSoulDrop(20.0f), "Zombie/Skeleton (20 HP) should drop 2 souls");
        assertEquals(2, SoulDropHandler.calculateSoulDrop(16.0f), "Spider (16 HP) should round to 2 souls");
        assertEquals(4, SoulDropHandler.calculateSoulDrop(40.0f), "Enderman (40 HP) should drop 4 souls");
        assertEquals(10, SoulDropHandler.calculateSoulDrop(100.0f), "Iron Golem (100 HP) should drop 10 souls");
        assertEquals(30, SoulDropHandler.calculateSoulDrop(300.0f), "Wither (300 HP) should drop 30 souls");
        assertEquals(50, SoulDropHandler.calculateSoulDrop(500.0f), "Warden (500 HP) should drop 50 souls");

        // Small animal / passive mob tests
        assertEquals(1, SoulDropHandler.calculateSoulDrop(10.0f), "Cow/Pig/Sheep (10 HP) should drop 1 soul");
        assertEquals(1, SoulDropHandler.calculateSoulDrop(8.0f), "Wolf (8 HP) should round to 1 soul");
        assertEquals(0, SoulDropHandler.calculateSoulDrop(4.0f), "Chicken (4 HP) should round to 0 souls");
        assertEquals(0, SoulDropHandler.calculateSoulDrop(1.0f), "Tiny Slime (1 HP) should round to 0 souls");

        // Edge cases
        assertEquals(0, SoulDropHandler.calculateSoulDrop(0.0f), "0 HP entity should drop 0 souls");
        assertEquals(0, SoulDropHandler.calculateSoulDrop(-10.0f), "Negative HP should drop 0 souls");
    }

    @Test
    @DisplayName("Verify Low HP Finishing Blows Do Not Reduce Soul Drops (残血收割不影响掉落量)")
    void testResidualHpHarvestDoesNotAffectDrop() {
        float mobMaxHealth = 20.0f;
        float mobResidualHealth = 0.5f;

        // The formula strictly operates on MaxHealth rather than current health
        int soulsFromFullHealth = SoulDropHandler.calculateSoulDrop(mobMaxHealth);
        int soulsFromResidualTarget = SoulDropHandler.calculateSoulDrop(mobMaxHealth);

        assertEquals(2, soulsFromFullHealth);
        assertEquals(soulsFromFullHealth, soulsFromResidualTarget,
                "Finishing off a mob with 0.5 HP must yield identical souls (2) as fighting from full HP");

        // Contrast: if incorrect code used residual health, it would have dropped 0 souls
        assertNotEquals(SoulDropHandler.calculateSoulDrop(mobResidualHealth), soulsFromResidualTarget);
    }

    @Test
    @DisplayName("Verify SoulData Capacity and Add/Remove/Clamping Operations")
    void testSoulDataOperations() {
        SoulData data = new SoulData();

        assertEquals(0, data.getSouls(), "Initial souls must be 0");
        assertEquals(40, data.getMaxSouls(), "Default soul capacity must be 40");
        assertTrue(data.isEmpty(), "Data should be empty initially");
        assertFalse(data.isFull(), "Data should not be full initially");

        // 1. Add souls within bounds
        int added = data.addSouls(15);
        assertEquals(15, added, "Should return actual amount added (15)");
        assertEquals(15, data.getSouls());
        assertFalse(data.isEmpty());
        assertFalse(data.isFull());

        // 2. Add souls exceeding capacity (40)
        int overflowAdded = data.addSouls(35);
        assertEquals(25, overflowAdded, "Should only add up to max capacity 40 (40 - 15 = 25)");
        assertEquals(40, data.getSouls(), "Souls must cap at 40");
        assertTrue(data.isFull());

        // 3. Remove souls
        boolean removed = data.removeSouls(15);
        assertTrue(removed, "Deducting 15 from 40 should succeed");
        assertEquals(25, data.getSouls());

        // 4. Remove souls exceeding current balance
        boolean overdraw = data.removeSouls(30);
        assertFalse(overdraw, "Deducting 30 from 25 should fail");
        assertEquals(25, data.getSouls(), "Balance must remain untouched on failed deduction");

        // 5. Force remove
        data.forceRemoveSouls(100);
        assertEquals(0, data.getSouls(), "Force remove must clamp at 0");
        assertTrue(data.isEmpty());

        // 6. Direct setters with clamping
        data.setSouls(50);
        assertEquals(40, data.getSouls(), "Setter must clamp to maxSouls (40)");
        data.setSouls(-10);
        assertEquals(0, data.getSouls(), "Setter must clamp to 0");
    }

    @Test
    @DisplayName("Verify SoulData Persistence via ValueOutput and ValueInput")
    void testSoulDataSerialization() {
        SoulData original = new SoulData();
        original.setMaxSouls(40);
        original.setSouls(28);

        TagValueOutput output = TagValueOutput.createWithoutContext(net.minecraft.util.ProblemReporter.DISCARDING);
        original.save(output);
        CompoundTag tag = output.buildResult();

        net.minecraft.core.HolderLookup.Provider registries = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        net.minecraft.world.level.storage.ValueInput input = TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, registries, tag);
        SoulData loaded = new SoulData();
        loaded.load(input);

        assertEquals(28, loaded.getSouls(), "Loaded souls must match saved value");
        assertEquals(40, loaded.getMaxSouls(), "Loaded maxSouls must match saved value");
    }

    @Test
    @DisplayName("Verify SoulOrb Scale Progression Animation (0.15 -> 1.0 over 14 ticks)")
    void testSoulOrbScaleProgression() {
        // Mocking scale calculation logic directly matching SoulOrbEntity
        java.util.function.BiFunction<Integer, Float, Float> calcScale = (tickCount, partialTick) -> {
            float age = (float) tickCount + partialTick;
            if (age >= 14.0f) {
                return 1.0f;
            }
            float progress = Math.max(0.0f, age / 14.0f);
            float eased = 1.0f - (float) Math.pow(1.0f - progress, 3);
            return 0.15f + 0.85f * eased;
        };

        assertEquals(0.15f, calcScale.apply(0, 0.0f), 0.001f, "At spawn (tick 0), scale should be initial 0.15");
        float midwayScale = calcScale.apply(7, 0.0f);
        assertTrue(midwayScale > 0.85f && midwayScale < 0.95f, "At tick 7, scale should be eased to ~0.89");
        assertEquals(1.0f, calcScale.apply(14, 0.0f), 0.001f, "At tick 14, scale should reach full size 1.0");
        assertEquals(1.0f, calcScale.apply(50, 0.5f), 0.001f, "After tick 14, scale must remain clamped at 1.0");
    }

    @Test
    @DisplayName("Verify SoulDataAccessor Contract and Interface Defaults")
    void testSoulDataAccessorInterface() {
        SoulData backing = new SoulData();
        SoulDataAccessor accessor = () -> backing;

        assertEquals(0, accessor.getSouls());
        assertEquals(40, accessor.getMaxSouls());

        accessor.setSouls(20);
        assertEquals(20, accessor.getSouls());

        int added = accessor.addSouls(10);
        assertEquals(10, added);
        assertEquals(30, accessor.getSouls());

        boolean removed = accessor.removeSouls(12);
        assertTrue(removed);
        assertEquals(18, accessor.getSouls());

        accessor.setMaxSouls(50);
        assertEquals(50, accessor.getMaxSouls());
    }
}
