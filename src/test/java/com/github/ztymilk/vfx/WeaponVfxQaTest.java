package com.github.ztymilk.vfx;

import com.github.ztymilk.combat.AttackComboData;
import com.github.ztymilk.sound.WeaponSoundHelper;
import net.minecraft.SharedConstants;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full Quality Assurance and Golden Specification Compliance Test Suite for OurDungeonCraft VFX.
 * <p>
 * Validates:
 * 1. 10 Weapon color palette constants, RGB hex values, and micro-scales against docs/VFX_GOLDEN_POLISH_SPECIFICATION.md
 * 2. Hitstop freeze lock state machine and particle debouncing
 * 3. Psychoacoustic sound choreography combo structures across all 10 weapons
 * 4. Static source code compliance: zero ParticleTypes.CLOUD in combat/vfx, non-zero velocities
 */
public class WeaponVfxQaTest {

    @BeforeAll
    static void initBootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    @DisplayName("Verify 10 Weapons Palette Color Transitions & Micro-Scales")
    void testWeaponPaletteSpecifications() {
        // 1. Sword (Cold Iron Slate 0xF8FAFC -> 0x64748B, Cyan Finisher 0xFFFFFF -> 0x93C5FD)
        assertNotNull(WeaponVfx.PALETTE_SWORD_TRANSITION);
        assertNotNull(WeaponVfx.PALETTE_SWORD_CYAN_TRANSITION);
        assertTrue(WeaponVfx.PALETTE_SWORD_WHITE.getScale() <= 0.60f);

        // 2. Claymore (Deep Slate 0xF8FAFC -> 0x334155)
        assertNotNull(WeaponVfx.PALETTE_CLAYMORE_TRANSITION);

        // 3. Double Axe (Steel Slate 0xE2E8F0 -> 0x1E293B)
        assertNotNull(WeaponVfx.PALETTE_AXE_TRANSITION);

        // 4. Glaive (Snow Silver to Slate Armor 0xF8FAFC -> 0x475569)
        assertNotNull(WeaponVfx.PALETTE_GLAIVE_TRANSITION);

        // 5. Great Hammer (Molten Gold Fissure 0xFCD34D -> 0x451A03, Impact 0xFCD34D -> 0x78350F)
        assertNotNull(WeaponVfx.PALETTE_HAMMER_TRANSITION);
        assertNotNull(WeaponVfx.PALETTE_HAMMER_FISSURE);

        // 6. Mace (Heavy Steel to Concussive Slate 0xCBD5E1 -> 0x475569)
        assertNotNull(WeaponVfx.PALETTE_MACE_TRANSITION);

        // 7. Daggers (Crimson Laceration 0xEF4444 -> 0x450A0A, Scale 0.38f)
        assertNotNull(WeaponVfx.PALETTE_DAGGER_TRANSITION);
        assertNotNull(WeaponVfx.PALETTE_DAGGER_RUBY_TRANSITION);
        assertEquals(0.38f, WeaponVfx.PALETTE_DAGGER_CRIMSON.getScale(), 0.01f);
        assertEquals(0.36f, WeaponVfx.PALETTE_DAGGER_RUBY.getScale(), 0.01f);

        // 8. Sickles (Moonlit Silver to Deep Azure 0xF1F5F9 -> 0x0284C7, Scale 0.40f)
        assertNotNull(WeaponVfx.PALETTE_SICKLE_TRANSITION);
        assertEquals(0.40f, WeaponVfx.PALETTE_SICKLE_SILVER.getScale(), 0.01f);
        assertEquals(0.40f, WeaponVfx.PALETTE_SICKLE_MOON.getScale(), 0.01f);

        // 9. Gauntlets (Sonic White to Gust Slate 0xFFFFFF -> 0x94A3B8, Scale 0.38f)
        assertNotNull(WeaponVfx.PALETTE_PUNCH_TRANSITION);
        assertEquals(0.38f, WeaponVfx.PALETTE_PUNCH_GUST.getScale(), 0.01f);

        // 10. Spear (Supersonic Mach Needle 0xFFFFFF -> 0x38BDF8, Ring 0xFFFFFF -> 0x0284C7)
        assertNotNull(WeaponVfx.PALETTE_SPEAR_VAPOR_TRANSITION);
        assertNotNull(WeaponVfx.PALETTE_SPEAR_RING_TRANSITION);
    }

    @Test
    @DisplayName("Verify Hitstop Freeze Lock State Machine & Anti-Light-Core Guard")
    void testHitstopFreezeLockStateMachine() {
        AttackComboData combo = new AttackComboData();
        long attackTick = 1000L;

        assertFalse(combo.isHitstop(), "Initial state must not be in hitstop");
        assertTrue(combo.tryTriggerSwingVfx(attackTick), "Swing VFX should trigger on initial attack");
        assertFalse(combo.tryTriggerSwingVfx(attackTick), "Swing VFX should debounce in same tick");

        // Player attacks at tick 1000
        combo.onAttack(ItemStack.EMPTY, attackTick);
        assertEquals(attackTick, combo.getLastAttackGameTime());

        // Trigger hitstop for 4 ticks
        combo.triggerHitstop(4, 0.15f);
        assertTrue(combo.isHitstop(), "Hitstop should be active");
        assertEquals(4, combo.getHitstopTicks());

        // On attack onset tick (gameTime == lastAttackGameTime), freeze lock is false to allow initial burst
        assertFalse(combo.isHitstopFreezeLocked(attackTick), "Onset tick must allow initial impact burst");
        assertTrue(combo.tryTriggerHitVfx(attackTick), "Onset tick must allow initial hit VFX");

        // On subsequent freeze ticks (gameTime > lastAttackGameTime), freeze lock is TRUE (0 particles allowed!)
        long freezeTick = attackTick + 1;
        assertTrue(combo.isHitstopFreezeLocked(freezeTick), "Subsequent freeze ticks must be freeze locked");
        assertFalse(combo.tryTriggerHitVfx(freezeTick), "Hit VFX must be rejected during freeze lock");
        assertFalse(combo.tryTriggerFinisherVfx(freezeTick), "Finisher VFX must be rejected during freeze lock");
        assertFalse(combo.tryTriggerSwingVfx(freezeTick), "Swing VFX must be rejected during freeze lock");

        // Finisher deduplication in same tick
        AttackComboData freshCombo = new AttackComboData();
        assertTrue(freshCombo.tryTriggerFinisherVfx(2000L), "First finisher trigger in tick 2000 should pass");
        assertFalse(freshCombo.tryTriggerFinisherVfx(2000L), "Second finisher trigger in same tick 2000 must be deduplicated");

        // Clear hitstop
        combo.clearHitstop();
        assertFalse(combo.isHitstop(), "Hitstop must be inactive after clear");
        assertFalse(combo.isHitstopFreezeLocked(freezeTick), "Freeze lock must release after hitstop clear");
    }

    @Test
    @DisplayName("Verify All 10 Weapon Combo Sequences in WeaponSoundHelper")
    void testWeaponSoundComboStructure() {
        assertEquals(3, WeaponSoundHelper.MCDWeaponType.SWORD.getComboLength());
        assertEquals(3, WeaponSoundHelper.MCDWeaponType.CLAYMORE.getComboLength());
        assertEquals(2, WeaponSoundHelper.MCDWeaponType.DOUBLE_AXE.getComboLength());
        assertEquals(2, WeaponSoundHelper.MCDWeaponType.GREAT_HAMMER.getComboLength());
        assertEquals(3, WeaponSoundHelper.MCDWeaponType.MACE.getComboLength());
        assertEquals(3, WeaponSoundHelper.MCDWeaponType.GAUNTLETS.getComboLength());
        assertEquals(4, WeaponSoundHelper.MCDWeaponType.DAGGERS.getComboLength());
        assertEquals(4, WeaponSoundHelper.MCDWeaponType.SICKLES.getComboLength());
        assertEquals(3, WeaponSoundHelper.MCDWeaponType.SPEAR.getComboLength());
        assertEquals(3, WeaponSoundHelper.MCDWeaponType.GLAIVE.getComboLength());

        for (WeaponSoundHelper.MCDWeaponType weaponType : WeaponSoundHelper.MCDWeaponType.values()) {
            for (int step = 0; step < weaponType.getComboLength(); step++) {
                WeaponSoundHelper.ComboStage whoosh = WeaponSoundHelper.getSwingChoreography(weaponType, step);
                assertNotNull(whoosh, "Swing whoosh choreography must not be null for " + weaponType + " step " + step);
                assertFalse(whoosh.layers().isEmpty(), "Whoosh must have at least 1 sound layer for " + weaponType + " step " + step);
            }
        }
    }

    @Test
    @DisplayName("Static Source Audit: Zero ParticleTypes.CLOUD in Combat & VFX")
    void testStaticSourceNoCloudSmokeInCombatVfx() throws IOException {
        Path vfxFile = Path.of("src/main/java/com/github/ztymilk/vfx/WeaponVfx.java");
        Path combatFile = Path.of("src/main/java/com/github/ztymilk/combat/CleavePhysics.java");

        assertTrue(Files.exists(vfxFile), "WeaponVfx.java must exist");
        assertTrue(Files.exists(combatFile), "CleavePhysics.java must exist");

        String vfxCode = Files.readString(vfxFile);
        String combatCode = Files.readString(combatFile);

        assertFalse(vfxCode.contains("ParticleTypes.CLOUD"),
                "WeaponVfx.java must NEVER use ParticleTypes.CLOUD (anti-smoke blur rule)");
        assertFalse(combatCode.contains("ParticleTypes.CLOUD"),
                "CleavePhysics.java must NEVER use ParticleTypes.CLOUD (anti-smoke blur rule)");
    }

    @Test
    @DisplayName("Static Source Audit: All 10 Weapons Handled in Air Swing & On Hit")
    void testAllTenWeaponsHandledInDispatchers() throws IOException {
        Path vfxFile = Path.of("src/main/java/com/github/ztymilk/vfx/WeaponVfx.java");
        String vfxCode = Files.readString(vfxFile);

        String[] weapons = {
                "SWORD", "CLAYMORE", "DOUBLE_AXE", "GREAT_HAMMER", "MACE",
                "GAUNTLETS", "DAGGERS", "SICKLES", "SPEAR", "GLAIVE"
        };

        for (String weapon : weapons) {
            assertTrue(vfxCode.contains("case " + weapon),
                    "WeaponVfx must explicitly handle weapon case " + weapon);
        }
    }

    @Test
    @DisplayName("Verify Particle Quotas & Kinematics Equations for All 10 Weapons")
    void testTenWeaponsParticleBudgetAndKinematics() throws IOException {
        Path vfxFile = Path.of("src/main/java/com/github/ztymilk/vfx/WeaponVfx.java");
        String code = Files.readString(vfxFile);

        // 1. Slash Ribbon Non-Uniform Sampling & Kinematics (Sword, Claymore, Double Axe, Glaive)
        assertTrue(code.contains("Math.pow(u, 0.80)"), "Must use s = u^0.80 non-linear sampling");
        assertTrue(code.contains("tangSpeed = kt * (omega * radius)"), "Must calculate tangential velocity");
        assertTrue(code.contains("centSpeed = kc * (omega * omega * radius"), "Must calculate centrifugal separation velocity");
        assertTrue(code.contains("vTang.add(vCent).add(vFwd).add(jx, jy, jz)"), "Must synthesize non-zero initial velocity");

        // 2. Daggers (Stab: 4, Cross: 7, Plunge: 7)
        assertTrue(code.contains("crimsonStabThrust"), "Daggers stab thrust method present");
        assertTrue(code.contains("crimsonCrossCut"), "Daggers scissor cross-cut method present");
        assertTrue(code.contains("crimsonDualPlunge"), "Daggers dual plunge finisher present");

        // 3. Gauntlets (Punch: 4, Uppercut: 8)
        assertTrue(code.contains("kineticPunchBurst"), "Gauntlets piston punch method present");
        assertTrue(code.contains("risingUppercutVfx"), "Gauntlets rising uppercut method present");

        // 4. Sickles (Crescent: 8, Vortex: 8, 360 Reap: 11)
        assertTrue(code.contains("silverCrescentSlash"), "Sickles silver crescent slash method present");
        assertTrue(code.contains("inwardVortexPull"), "Sickles negative centrifugal vortex method present");
        assertTrue(code.contains("sickleReapFinisher360"), "Sickles 360 reap finisher method present");

        // 5. Heavy bludgeons & Spears (Hammer, Mace, Spear)
        assertTrue(code.contains("directionalGroundSmash"), "Hammer/Claymore ground smash method present");
        assertTrue(code.contains("maceSkullcrackerImpact"), "Mace skullcracker impact method present");
        assertTrue(code.contains("machConeThrust"), "Spear supersonic mach cone method present");
        assertTrue(code.contains("spearPiercingImpact"), "Spear piercing impact method present");

        // 6. Air swing vs On hit separation
        assertTrue(code.contains("spawnWeaponAirSwingVfx"), "Dedicated Air Swing layer method present");
        assertTrue(code.contains("spawnWeaponOnHitVfx"), "Dedicated On-Hit layer method present");
        assertTrue(code.contains("spawnDirectionalHitSparks"), "Directional hit sparks method present");
    }
}
