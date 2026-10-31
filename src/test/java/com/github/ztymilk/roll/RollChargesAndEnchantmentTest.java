package com.github.ztymilk.roll;

import com.github.ztymilk.enchantment.ModEnchantments;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RollChargesAndEnchantmentTest {

    @BeforeAll
    static void initBootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    @DisplayName("Verify Initial RollData Default Configuration")
    void testInitialRollDataConfiguration() {
        RollData data = new RollData();
        assertEquals(RollState.IDLE, data.getState());
        assertEquals(1, data.getCharges(), "Initial roll charges must be 1");
        assertEquals(1, data.getMaxCharges(), "Initial max roll charges must be 1");
        assertEquals(0, data.getRechargeCooldown(), "Initial recharge cooldown must be 0");
        assertEquals(0, data.getInternalCooldown(), "Initial internal lockout CD must be 0");
        assertFalse(data.isRolling(), "Must not be rolling initially");
        assertFalse(data.isRecharging(), "Must not be recharging initially");
        assertEquals(1.0f, data.getRechargeProgress(), 0.001f, "Progress must be 1.0 when fully charged");
        assertEquals(0.0f, data.getCooldownRatio(), 0.001f, "Cooldown ratio must be 0.0 when ready");
    }

    @Test
    @DisplayName("Verify Charge Consumption, Lockout CD (0.1s/2t), and Recharge Cycle")
    void testChargeConsumptionAndRecharge() {
        RollData data = new RollData();

        // 1. Consume charge
        boolean consumed = data.consumeCharge();
        assertTrue(consumed, "First charge consumption must succeed");
        assertEquals(0, data.getCharges(), "Charges should decrease to 0");
        assertEquals(RollData.INTERNAL_COOLDOWN_TICKS, data.getInternalCooldown(), "Internal lockout CD must be 2 ticks");
        assertEquals(RollData.DEFAULT_RECHARGE_TICKS, data.getRechargeCooldown(), "Recharge countdown must start at 30 ticks");
        assertTrue(data.isRecharging(), "Should report recharging state");

        // 2. Second consumption should fail because charges == 0
        assertFalse(data.consumeCharge(), "Cannot consume charge when charges == 0");

        // 3. Tick progress over 2 ticks (lockout window)
        data.tickRecharge();
        assertEquals(1, data.getInternalCooldown(), "Lockout CD decrements to 1");
        assertEquals(29, data.getRechargeCooldown());

        data.tickRecharge();
        assertEquals(0, data.getInternalCooldown(), "Lockout CD expires at tick 2 (0.1s)");
        assertEquals(28, data.getRechargeCooldown());

        // 4. Tick remaining 28 ticks to complete recharge
        for (int i = 0; i < 28; i++) {
            data.tickRecharge();
        }

        assertEquals(1, data.getCharges(), "Charge must be restored to 1");
        assertEquals(0, data.getRechargeCooldown(), "Recharge cooldown must finish at 0");
        assertFalse(data.isRecharging(), "Should no longer be recharging when full");
        assertEquals(1.0f, data.getRechargeProgress(), 0.001f);
    }

    @Test
    @DisplayName("Verify Multi-Charge Independent Recharge Countdown & Consecutive Rolling")
    void testMultiChargeAndIndependentRechargeTimer() {
        RollData data = new RollData();
        data.setMaxCharges(3);
        data.setCharges(3);
        assertEquals(3, data.getCharges());
        assertEquals(3, data.getMaxCharges());

        // Roll 1 at t=0
        assertTrue(data.consumeCharge());
        assertEquals(2, data.getCharges());
        assertEquals(30, data.getRechargeCooldown());
        assertEquals(2, data.getInternalCooldown());

        // Tick 10 game ticks: recharge timer counts down 30 -> 20
        for (int i = 0; i < 10; i++) {
            data.tickRecharge();
        }
        assertEquals(20, data.getRechargeCooldown(), "Recharge timer must be at 20 ticks");
        assertEquals(0, data.getInternalCooldown(), "Lockout CD expired");

        // Roll 2 at t=10: independent countdown rule
        assertTrue(data.consumeCharge());
        assertEquals(1, data.getCharges(), "Charges decrease to 1");
        assertEquals(20, data.getRechargeCooldown(), "CRITICAL: Independent countdown MUST NOT reset to 30!");
        assertEquals(2, data.getInternalCooldown(), "Lockout CD reset to 2 ticks");

        // Tick 20 more ticks: first recharge cycle completes (20 -> 0)
        for (int i = 0; i < 20; i++) {
            data.tickRecharge();
        }

        assertEquals(2, data.getCharges(), "1st charge restored: charges 1 -> 2");
        assertEquals(30, data.getRechargeCooldown(), "Next charge recharge cycle immediately starts from 30 ticks");

        // Tick 30 more ticks: second recharge cycle completes
        for (int i = 0; i < 30; i++) {
            data.tickRecharge();
        }
        assertEquals(3, data.getCharges(), "2nd charge restored: charges 2 -> 3 (full)");
        assertEquals(0, data.getRechargeCooldown(), "Recharge timer stops when full");
    }

    @Test
    @DisplayName("Verify Roll Master Enchantment Stacking and Hard Cap Lock at 4")
    void testRollMasterEnchantmentStackingAndHardCap() {
        // Base without enchantment: 1 charge
        int baseCharges = RollData.DEFAULT_CHARGES;
        assertEquals(1, baseCharges);

        // Level I gives +1 -> 2 charges
        int bonusL1 = 1;
        int maxL1 = Math.min(RollData.MAX_ALLOWED_CHARGES, baseCharges + bonusL1);
        assertEquals(2, maxL1, "Roll Master I must provide 2 max charges");

        // Level II gives +2 -> 3 charges
        int bonusL2 = 2;
        int maxL2 = Math.min(RollData.MAX_ALLOWED_CHARGES, baseCharges + bonusL2);
        assertEquals(3, maxL2, "Roll Master II must provide 3 max charges");

        // Level III gives +3 -> 4 charges
        int bonusL3 = 3;
        int maxL3 = Math.min(RollData.MAX_ALLOWED_CHARGES, baseCharges + bonusL3);
        assertEquals(4, maxL3, "Roll Master III must provide 4 max charges");

        // Stacking: Helmet (I: +1) + Boots (II: +2) = 1 + 1 + 2 = 4 charges
        int stackedBonus = 1 + 2;
        int maxStacked = Math.min(RollData.MAX_ALLOWED_CHARGES, baseCharges + stackedBonus);
        assertEquals(4, maxStacked, "Stacked armor must sum bonuses up to 4");

        // Hard Cap Lock: Full set of Roll Master III (4 * 3 = 12 bonus) -> capped strictly at 4
        int overkillBonus = 3 + 3 + 3 + 3;
        int maxOverkill = Math.min(RollData.MAX_ALLOWED_CHARGES, baseCharges + overkillBonus);
        assertEquals(4, maxOverkill, "Overkill bonus must be strictly locked at 4 charges maximum");
    }

    @Test
    @DisplayName("Verify RollData Persistence Serialization and Deserialization")
    void testRollDataPersistence() {
        RollData original = new RollData();
        original.setState(RollState.ACTIVE);
        original.setRollTicks(5);
        original.setMaxCharges(3);
        original.setCharges(2);
        original.setRechargeCooldown(18);
        original.setInternalCooldown(1);
        original.setRollYaw(135.5f);
        original.setDistance(3.5);

        // Serialize to TagValueOutput
        TagValueOutput output = TagValueOutput.createWithoutContext(net.minecraft.util.ProblemReporter.DISCARDING);
        original.save(output);
        CompoundTag tag = output.buildResult();

        // Deserialize from TagValueInput
        net.minecraft.core.HolderLookup.Provider provider = net.minecraft.data.registries.VanillaRegistries.createLookup();
        net.minecraft.world.level.storage.ValueInput input = TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, provider, tag);
        RollData restored = new RollData();
        restored.load(input);

        assertEquals(RollState.ACTIVE, restored.getState());
        assertEquals(5, restored.getRollTicks());
        assertEquals(3, restored.getMaxCharges());
        assertEquals(2, restored.getCharges());
        assertEquals(18, restored.getRechargeCooldown());
        assertEquals(1, restored.getInternalCooldown());
        assertEquals(135.5f, restored.getRollYaw(), 0.01f);
        assertEquals(3.5, restored.getDistance(), 0.01);
    }
}
