package com.github.ztymilk.sound;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Combat Audio Choreography Helper for Minecraft Dungeons Weapons in Minecraft 1.21.11 (Fabric).
 * <p>
 * Minecraft Dungeons' combat audio achieves its signature weight, visceral crunch, and kinetic feedback
 * through a strict 4-layer psychoacoustic architecture and progressive combo cadence:
 * <ol>
 *   <li><b>Transient Attack Layer (1.5 - 8 kHz sharp edge / snap):</b>
 *       Immediate acoustic onset delivering tactile sharpness (crit cutting edge, metal deflect pings,
 *       trident point snaps, puncture clicks).</li>
 *   <li><b>Body Impact Layer (250 Hz - 1.2 kHz punchy crunch / flesh / bone):</b>
 *       Tactile acoustic mass representing physical penetration, bone cracking, armor denting, and flesh tearing
 *       (player attack strong thuds, mace ground crunches, knockback thumps).</li>
 *   <li><b>Sub-bass Resonance Layer (40 - 150 Hz chest thump / seismic vibration):</b>
 *       Visceral low-frequency shockwave providing perceived weight and physical punch
 *       (mace heavy smash rumbles, low-pitch conduit pulses, seismic thunder rumblings).</li>
 *   <li><b>Kinetic Wake / Air Displacement Layer (whoosh / gust / vortex):</b>
 *       Aerodynamic shearing and displaced atmospheric volume
 *       (sweep whooshes, breeze slides, whirlwind turbulence, wind charge compression bursts).</li>
 * </ol>
 * <p>
 * <b>Pitch Progression & Combo Cadence:</b>
 * <ul>
 *   <li><b>Opening Hits:</b> Crisp, agile, higher pitch (1.10 - 1.35) with controlled bass for nimble response.</li>
 *   <li><b>Intermediate Strikes:</b> Escalating tension, deepening body presence, and transitioning pitch.</li>
 *   <li><b>Finishers:</b> Thunderous, lower pitch (0.65 - 0.90) accompanied by massive sub-bass weight.</li>
 * </ul>
 */
public final class WeaponSoundHelper {

    private WeaponSoundHelper() {
        // Utility class
    }

    /* ========================================================================= */
    /*  SOUND LAYER & COMBO STAGE PRIMITIVES                                    */
    /* ========================================================================= */

    /**
     * Represents an individual audio layer within a multi-sound acoustic composite.
     *
     * @param soundSupplier Supplies the vanilla {@link SoundEvent}.
     * @param volume        Base volume level (1.0 = standard vanilla range).
     * @param pitch         Base playback pitch (0.5 to 2.0).
     * @param pitchJitter   Micro-detuning spread to prevent auditory fatigue (e.g., 0.04 = ±0.04 pitch variation).
     */
    public record SoundLayer(
            @NotNull Supplier<SoundEvent> soundSupplier,
            float volume,
            float pitch,
            float pitchJitter
    ) {
        public SoundLayer(@NotNull SoundEvent sound, float volume, float pitch, float pitchJitter) {
            this(() -> sound, volume, pitch, pitchJitter);
        }

        public SoundLayer(@NotNull Holder<SoundEvent> soundHolder, float volume, float pitch, float pitchJitter) {
            this(soundHolder::value, volume, pitch, pitchJitter);
        }

        public SoundLayer(@NotNull SoundEvent sound, float volume, float pitch) {
            this(sound, volume, pitch, 0.04f);
        }

        public SoundLayer(@NotNull Holder<SoundEvent> soundHolder, float volume, float pitch) {
            this(soundHolder, volume, pitch, 0.04f);
        }

        /**
         * Calculates a randomized pitch within [pitch - pitchJitter, pitch + pitchJitter],
         * strictly clamped to Minecraft's audible range [0.5, 2.0].
         */
        public float getCalculatedPitch(@NotNull RandomSource random) {
            if (this.pitchJitter <= 0.0001f) {
                return Math.clamp(this.pitch, 0.5f, 2.0f);
            }
            float delta = (random.nextFloat() - 0.5f) * 2.0f * this.pitchJitter;
            return Math.clamp(this.pitch + delta, 0.5f, 2.0f);
        }

        /**
         * Plays this sound layer at the given spatial coordinates.
         */
        public void play(@NotNull Level level, double x, double y, double z, @Nullable RandomSource random) {
            SoundEvent event = this.soundSupplier.get();
            if (event == null) {
                return;
            }
            RandomSource rng = (random != null) ? random : level.getRandom();
            float randomizedPitch = this.getCalculatedPitch(rng);

            if (level.isClientSide()) {
                level.playLocalSound(x, y, z, event, SoundSource.PLAYERS, this.volume, randomizedPitch, false);
            } else {
                level.playSound(null, x, y, z, event, SoundSource.PLAYERS, this.volume, randomizedPitch);
            }
        }
    }

    /**
     * An immutable composite of multiple {@link SoundLayer}s triggered simultaneously
     * to form a single punchy attack effect across the 4-layer psychoacoustic architecture.
     */
    public record ComboStage(@NotNull List<SoundLayer> layers) {
        public static ComboStage of(SoundLayer... layers) {
            return new ComboStage(List.of(layers));
        }

        /**
         * Explicit 4-layer psychoacoustic factory:
         * 1. Transient Attack Layer (1.5 - 8 kHz sharp edge / snap)
         * 2. Body Impact Layer (250 Hz - 1.2 kHz punchy crunch / flesh / bone)
         * 3. Sub-bass Resonance Layer (40 - 150 Hz chest thump)
         * 4. Kinetic Wake / Air Displacement Layer (whoosh / gust)
         *
         * @param transientLayer   Sharp edge, clink, deflection ring, or point snap.
         * @param bodyLayer        Punchy flesh/bone/armor crunch.
         * @param subBassLayer     Deep seismic rumble, chest thump, or sub-bass pulse.
         * @param kineticWakeLayer Aerodynamic air displacement, slipstream hiss, or vortex.
         * @param extraLayers      Supplemental harmonic or secondary resonance layers.
         */
        public static ComboStage ofLayers(
                @NotNull SoundLayer transientLayer,
                @NotNull SoundLayer bodyLayer,
                @NotNull SoundLayer subBassLayer,
                @NotNull SoundLayer kineticWakeLayer,
                @NotNull SoundLayer... extraLayers
        ) {
            if (extraLayers.length == 0) {
                return new ComboStage(List.of(transientLayer, bodyLayer, subBassLayer, kineticWakeLayer));
            }
            List<SoundLayer> list = new ArrayList<>(4 + extraLayers.length);
            list.add(transientLayer);
            list.add(bodyLayer);
            list.add(subBassLayer);
            list.add(kineticWakeLayer);
            list.addAll(List.of(extraLayers));
            return new ComboStage(List.copyOf(list));
        }

        public void play(@NotNull Level level, double x, double y, double z, @Nullable RandomSource random) {
            for (SoundLayer layer : this.layers) {
                layer.play(level, x, y, z, random);
            }
        }
    }

    /* ========================================================================= */
    /*  MINECRAFT DUNGEONS WEAPON ARCHETYPES                                    */
    /* ========================================================================= */

    public enum MCDWeaponType {
        /**
         * 1. Swords (Diamond Sword, Hawkbrand, Sinister Sword):
         * Balanced 3-hit combo. Slash L -> Slash R -> Heavy Cleave Finisher.
         */
        SWORD(3),

        /**
         * 2. Heavy Two-Handed Blades (Claymore, Broadsword, Heartstealer, Great Axeblade):
         * Massive 3-hit cadence. Broad Cleave L -> Return Cleave R -> Ground Chop Finisher.
         */
        CLAYMORE(3),

        /**
         * 3. Double Axes (Double Axe, Whirlwind, Cursed Axe):
         * 2-hit continuous 360-degree whirlwind cleave. Spin 1 -> Vortex Blast Finisher.
         */
        DOUBLE_AXE(2),

        /**
         * 4. Hammers & Bludgeons (Great Hammer, Stormlander, Bone Cudgel):
         * Devastating 2-hit kinetic slam. Downward Smash -> Cataclysmic Ground Quake Finisher.
         */
        GREAT_HAMMER(2),

        /**
         * 5. Maces & Flails (Mace, Flail, Sun's Grace):
         * 3-hit blunt concussive combo. Downward Bash -> Backhand Strike -> Skull-Crusher Finisher.
         */
        MACE(3),

        /**
         * 6. Gauntlets (Gauntlets, Fighter's Bindings, Maulers):
         * Rapid 3-hit boxing barrage. Jab L -> Cross R -> Rocket Uppercut Finisher.
         */
        GAUNTLETS(3),

        /**
         * 7. Daggers (Daggers, Fangs of Frost, Moon Daggers, Sheer Daggers):
         * Ultra-fast 4-hit flurry. Left Stab -> Right Stab -> Cross Cut -> Dual Plunge Finisher.
         */
        DAGGERS(4),

        /**
         * 8. Sickles (Sickles, Nightmare's Bite, The Last Laugh):
         * Fast 4-hit reaping combo. Slash L -> Slash R -> Inward Hook Pull -> 360 Spin Finisher.
         */
        SICKLES(4),

        /**
         * 9. Spears (Spear, Fortune Spear, Whispering Spear):
         * Long-reach 3-hit piercing combo. Penetrating Thrust 1 -> Thrust 2 -> Power Lunge Finisher.
         */
        SPEAR(3),

        /**
         * 10. Glaives (Glaive, Venom Glaive, Grave Bane):
         * Long-reach 3-hit sweeping polearm combo. Wide Sweep L -> Wide Sweep R -> Sweeping Lunge Finisher.
         */
        GLAIVE(3),

        /**
         * Legacy shared archetype for Spear & Glaive backward compatibility.
         */
        @Deprecated
        SPEAR_GLAIVE(3),

        /**
         * Standard Axes (Axe, Firebrand, Highland Axe):
         * 2-hit downward chop and lateral execution cleave.
         */
        AXE(2),

        /**
         * Soul Scythes (Soul Scythe, Frost Scythe, Jailor's Scythe):
         * 2-hit eerie soul-reaping arc and spiritual extraction burst.
         */
        SOUL_SCYTHE(2),

        /**
         * Whips (Whip, Vine Whip):
         * 1-hit supersonic crack with high-velocity tip compression.
         */
        WHIP(1),

        /**
         * Anchors (Anchor, Encrusted Anchor):
         * 1-hit colossal tectonic drop with rolling sub-bass thunder.
         */
        ANCHOR(1),

        /**
         * Katanas (Katana, Master's Katana):
         * 3-hit precision iai combo. Dash Slash -> Reverse Cut -> Master Cross-Cut.
         */
        KATANA(3),

        /**
         * Rapiers (Rapier, Bee Stinger):
         * Rapid 4-hit needle thrust flurry with piercing finisher.
         */
        RAPIER(4);

        private final int comboLength;

        MCDWeaponType(int comboLength) {
            this.comboLength = comboLength;
        }

        public int getComboLength() {
            return this.comboLength;
        }
    }

    /* ========================================================================= */
    /*  ELEMENTAL & ENCHANTMENT PROC TYPES                                      */
    /* ========================================================================= */

    public enum ProcType {
        /**
         * Thundering (Stormlander / Thundering enchantment):
         * High voltage crackle, thunder clap, and atmospheric sizzle.
         */
        THUNDERING(ComboStage.of(
                new SoundLayer(SoundEvents.LIGHTNING_BOLT_IMPACT, 1.00f, 1.25f, 0.05f),
                new SoundLayer(SoundEvents.TRIDENT_THUNDER, 0.90f, 1.05f, 0.04f),
                new SoundLayer(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.55f, 1.15f, 0.03f)
        )),

        /**
         * Shockwave (Shockwave enchantment):
         * Ground-rupturing blast wave emitted on combo finishers.
         */
        SHOCKWAVE(ComboStage.of(
                new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.15f, 0.85f, 0.04f),
                new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 1.10f, 0.90f, 0.04f),
                new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.90f, 0.80f, 0.03f)
        )),

        /**
         * Swirling (Swirling enchantment):
         * Rapid 360-degree bladed vortex shearing through adjacent space.
         */
        SWIRLING(ComboStage.of(
                new SoundLayer(SoundEvents.BREEZE_WHIRL, 1.20f, 1.05f, 0.04f),
                new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 0.95f, 1.10f, 0.05f),
                new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 1.05f, 1.00f, 0.04f)
        )),

        /**
         * Soul Leech / Anima Conduit:
         * Mystical resonance and spectral siphon extracting essence.
         */
        SOUL_LEECH(ComboStage.of(
                new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.85f, 1.20f, 0.04f),
                new SoundLayer(SoundEvents.BEACON_ACTIVATE, 0.75f, 1.45f, 0.03f),
                new SoundLayer(SoundEvents.EVOKER_CAST_SPELL, 0.60f, 1.40f, 0.05f)
        )),

        /**
         * Gravity Pull (Gravity enchantment / Hammer of Gravity):
         * Spatial collapse and pulling vortex.
         */
        GRAVITY_PULL(ComboStage.of(
                new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 1.00f, 0.80f, 0.03f),
                new SoundLayer(SoundEvents.BREEZE_WHIRL, 0.85f, 0.90f, 0.04f),
                new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 0.70f, 0.75f, 0.04f)
        )),

        /**
         * Critical Hit Burst:
         * Ultra-sharp edge incision backed by dense kinetic crunch.
         */
        CRITICAL_BURST(ComboStage.of(
                new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 1.15f, 0.95f, 0.04f),
                new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 0.95f, 1.05f, 0.04f),
                new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 0.80f, 1.15f, 0.05f)
        ));

        private final ComboStage stage;

        ProcType(ComboStage stage) {
            this.stage = stage;
        }

        public void play(@NotNull Level level, double x, double y, double z, @Nullable RandomSource random) {
            this.stage.play(level, x, y, z, random);
        }
    }

    /* ========================================================================= */
    /*  SWING AUDIO CHOREOGRAPHY (AIR DISPLACEMENT & CADENCE)                   */
    /* ========================================================================= */

    /**
     * Resolves the air displacement / weapon whoosh audio for a given weapon and combo hit.
     */
    public static @NotNull ComboStage getSwingChoreography(@NotNull MCDWeaponType weapon, int comboStep) {
        return switch (weapon) {
            case SWORD -> switch (comboStep % 3) {
                case 0 -> ComboStage.of(
                        new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.75f, 1.25f, 0.04f)
                );
                case 1 -> ComboStage.of(
                        new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.85f, 1.10f, 0.04f),
                        new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.50f, 1.20f, 0.04f)
                );
                default -> ComboStage.of(
                        new SoundLayer(SoundEvents.MACE_SMASH_AIR, 0.95f, 0.85f, 0.03f),
                        new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.90f, 0.80f, 0.04f)
                );
            };

            case CLAYMORE -> switch (comboStep % 3) {
                case 0 -> ComboStage.of(
                        new SoundLayer(SoundEvents.MACE_SMASH_AIR, 1.00f, 0.85f, 0.03f),
                        new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.90f, 0.80f, 0.04f)
                );
                case 1 -> ComboStage.of(
                        new SoundLayer(SoundEvents.MACE_SMASH_AIR, 1.10f, 0.75f, 0.03f),
                        new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.95f, 0.72f, 0.04f)
                );
                default -> ComboStage.of(
                        new SoundLayer(SoundEvents.MACE_SMASH_AIR, 1.30f, 0.60f, 0.02f),
                        new SoundLayer(SoundEvents.BREEZE_WHIRL, 0.95f, 0.68f, 0.03f)
                );
            };

            case DOUBLE_AXE -> (comboStep % 2 == 0)
                    ? ComboStage.of(
                            new SoundLayer(SoundEvents.BREEZE_WHIRL, 1.05f, 1.10f, 0.04f),
                            new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.90f, 0.95f, 0.04f)
                    )
                    : ComboStage.of(
                            new SoundLayer(SoundEvents.BREEZE_WHIRL, 1.25f, 0.80f, 0.03f),
                            new SoundLayer(SoundEvents.MACE_SMASH_AIR, 1.00f, 0.75f, 0.03f)
                    );

            case GREAT_HAMMER -> (comboStep % 2 == 0)
                    ? ComboStage.of(
                            new SoundLayer(SoundEvents.MACE_SMASH_AIR, 1.15f, 0.75f, 0.03f)
                    )
                    : ComboStage.of(
                            new SoundLayer(SoundEvents.MACE_SMASH_AIR, 1.40f, 0.55f, 0.02f),
                            new SoundLayer(SoundEvents.BREEZE_WHIRL, 0.95f, 0.65f, 0.03f)
                    );

            case MACE -> switch (comboStep % 3) {
                case 0 -> ComboStage.of(
                        new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.80f, 1.18f, 0.04f)
                );
                case 1 -> ComboStage.of(
                        new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.85f, 1.05f, 0.04f),
                        new SoundLayer(SoundEvents.MACE_SMASH_AIR, 0.65f, 1.05f, 0.04f)
                );
                default -> ComboStage.of(
                        new SoundLayer(SoundEvents.MACE_SMASH_AIR, 1.15f, 0.78f, 0.03f),
                        new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.85f, 0.85f, 0.04f)
                );
            };

            case GAUNTLETS -> switch (comboStep % 3) {
                case 0 -> ComboStage.of(
                        new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.70f, 1.35f, 0.04f)
                );
                case 1 -> ComboStage.of(
                        new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.75f, 1.25f, 0.04f),
                        new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.55f, 1.30f, 0.04f)
                );
                default -> ComboStage.of(
                        new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 1.00f, 0.90f, 0.04f),
                        new SoundLayer(SoundEvents.MACE_SMASH_AIR, 0.90f, 0.85f, 0.03f)
                );
            };

            case DAGGERS -> switch (comboStep % 4) {
                case 0 -> ComboStage.of(
                        new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.65f, 1.40f, 0.04f)
                );
                case 1 -> ComboStage.of(
                        new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.70f, 1.45f, 0.04f)
                );
                case 2 -> ComboStage.of(
                        new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.75f, 1.30f, 0.04f),
                        new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.60f, 1.35f, 0.04f)
                );
                default -> ComboStage.of(
                        new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 0.90f, 1.05f, 0.04f),
                        new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.80f, 1.10f, 0.04f)
                );
            };

            case SICKLES -> switch (comboStep % 4) {
                case 0 -> ComboStage.of(
                        new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.80f, 1.30f, 0.04f),
                        new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.55f, 1.35f, 0.04f)
                );
                case 1 -> ComboStage.of(
                        new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.85f, 1.25f, 0.04f)
                );
                case 2 -> ComboStage.of(
                        new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.95f, 1.40f, 0.04f),
                        new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 0.70f, 1.30f, 0.04f)
                );
                default -> ComboStage.of(
                        new SoundLayer(SoundEvents.BREEZE_WHIRL, 1.15f, 0.90f, 0.04f),
                        new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 1.05f, 0.85f, 0.04f)
                );
            };

            case SPEAR -> switch (comboStep % 3) {
                case 0 -> ComboStage.of(
                        new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.80f, 1.30f, 0.04f)
                );
                case 1 -> ComboStage.of(
                        new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.85f, 1.20f, 0.04f),
                        new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.50f, 1.25f, 0.04f)
                );
                default -> ComboStage.of(
                        new SoundLayer(SoundEvents.TRIDENT_RIPTIDE_1, 1.05f, 0.90f, 0.04f),
                        new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 0.90f, 0.85f, 0.04f)
                );
            };

            case GLAIVE -> switch (comboStep % 3) {
                case 0 -> ComboStage.of(
                        new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.90f, 1.18f, 0.04f),
                        new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.60f, 1.22f, 0.04f)
                );
                case 1 -> ComboStage.of(
                        new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 1.00f, 1.05f, 0.04f),
                        new SoundLayer(SoundEvents.MACE_SMASH_AIR, 0.70f, 1.00f, 0.04f)
                );
                default -> ComboStage.of(
                        new SoundLayer(SoundEvents.TRIDENT_RIPTIDE_1, 1.10f, 0.85f, 0.04f),
                        new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 1.05f, 0.80f, 0.04f),
                        new SoundLayer(SoundEvents.MACE_SMASH_AIR, 0.90f, 0.75f, 0.03f)
                );
            };

            case SPEAR_GLAIVE -> getSwingChoreography(MCDWeaponType.SPEAR, comboStep);

            case AXE -> (comboStep % 2 == 0)
                    ? ComboStage.of(
                            new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.85f, 1.00f, 0.04f)
                    )
                    : ComboStage.of(
                            new SoundLayer(SoundEvents.MACE_SMASH_AIR, 1.00f, 0.85f, 0.04f),
                            new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.90f, 0.80f, 0.04f)
                    );

            case SOUL_SCYTHE -> (comboStep % 2 == 0)
                    ? ComboStage.of(
                            new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.95f, 0.90f, 0.04f),
                            new SoundLayer(SoundEvents.EVOKER_CAST_SPELL, 0.50f, 1.30f, 0.04f)
                    )
                    : ComboStage.of(
                            new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 1.10f, 0.75f, 0.04f),
                            new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.65f, 1.05f, 0.03f)
                    );

            case WHIP -> ComboStage.of(
                    new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.85f, 1.50f, 0.05f),
                    new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 0.95f, 1.60f, 0.05f)
            );

            case ANCHOR -> ComboStage.of(
                    new SoundLayer(SoundEvents.MACE_SMASH_AIR, 1.45f, 0.50f, 0.02f),
                    new SoundLayer(SoundEvents.BREEZE_WHIRL, 0.95f, 0.60f, 0.03f)
            );

            case KATANA -> switch (comboStep % 3) {
                case 0 -> ComboStage.of(
                        new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.85f, 1.35f, 0.04f),
                        new SoundLayer(SoundEvents.BREEZE_DEFLECT, 0.50f, 1.50f, 0.05f)
                );
                case 1 -> ComboStage.of(
                        new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.90f, 1.25f, 0.04f),
                        new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.60f, 1.40f, 0.04f)
                );
                default -> ComboStage.of(
                        new SoundLayer(SoundEvents.BREEZE_DEFLECT, 0.95f, 1.20f, 0.04f),
                        new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 1.05f, 1.05f, 0.04f),
                        new SoundLayer(SoundEvents.MACE_SMASH_AIR, 0.80f, 1.15f, 0.04f)
                );
            };

            case RAPIER -> ComboStage.of(
                    new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.70f, 1.50f + (comboStep % 4) * 0.05f, 0.04f)
            );
        };
    }

    /* ========================================================================= */
    /*  HIT IMPACT CHOREOGRAPHY (4-LAYER PSYCHOACOUSTIC STAGES)                 */
    /* ========================================================================= */

    /**
     * Resolves the multi-layered impact choreography for a weapon strike.
     *
     * @param weapon     Weapon archetype.
     * @param comboStep  0-based index in the combo chain.
     * @param isFinisher Whether this strike is the culminating finisher.
     * @param isCrit     Whether this strike landed as a critical hit.
     */
    public static @NotNull ComboStage getHitChoreography(
            @NotNull MCDWeaponType weapon,
            int comboStep,
            boolean isFinisher,
            boolean isCrit
    ) {
        boolean finisher = isFinisher || ((comboStep + 1) >= weapon.getComboLength());

        ComboStage baseStage = switch (weapon) {
            case SWORD -> getSwordHitStage(comboStep, finisher);
            case CLAYMORE -> getClaymoreHitStage(comboStep, finisher);
            case DOUBLE_AXE -> getDoubleAxeHitStage(comboStep, finisher);
            case GREAT_HAMMER -> getGreatHammerHitStage(comboStep, finisher);
            case MACE -> getMaceHitStage(comboStep, finisher);
            case GAUNTLETS -> getGauntletsHitStage(comboStep, finisher);
            case DAGGERS -> getDaggersHitStage(comboStep, finisher);
            case SICKLES -> getSicklesHitStage(comboStep, finisher);
            case SPEAR -> getSpearHitStage(comboStep, finisher);
            case GLAIVE -> getGlaiveHitStage(comboStep, finisher);
            case SPEAR_GLAIVE -> getSpearHitStage(comboStep, finisher);
            case AXE -> getAxeHitStage(comboStep, finisher);
            case SOUL_SCYTHE -> getSoulScytheHitStage(comboStep, finisher);
            case WHIP -> getWhipHitStage();
            case ANCHOR -> getAnchorHitStage();
            case KATANA -> getKatanaHitStage(comboStep, finisher);
            case RAPIER -> getRapierHitStage(comboStep, finisher);
        };

        if (isCrit) {
            return appendCriticalLayers(baseStage);
        }
        return baseStage;
    }

    /* ========================================================================= */
    /*  THE 10 BASE WEAPON IMPACT IMPLEMENTATIONS                                */
    /* ========================================================================= */

    /**
     * 1. SWORD (地下城剑): Balanced 3-hit combo.
     * Slash L (crisp 1.28) -> Slash R (escalating 1.08) -> Heavy Cleave Finisher (thunderous 0.78).
     */
    private static ComboStage getSwordHitStage(int comboStep, boolean finisher) {
        if (finisher) {
            return ComboStage.ofLayers(
                    // 1. Transient: Razor-sharp cutting edge bite & deflection snap
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 1.25f, 0.82f, 0.04f),
                    new SoundLayer(SoundEvents.BREEZE_DEFLECT, 0.95f, 0.85f, 0.04f),
                    // 2. Body: Armor-denting concussive meat crunch
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.35f, 0.78f, 0.03f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.20f, 0.80f, 0.03f),
                    // 3. Sub-bass: Low-end 40-90Hz chest thump & conduit pulse
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.35f, 0.70f, 0.02f),
                    new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.85f, 0.65f, 0.02f),
                    // 4. Kinetic Wake: High-volume blade slipstream & wind burst
                    new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 1.15f, 0.82f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 1.10f, 0.80f, 0.04f)
            );
        }
        int step = comboStep % 3;
        if (step == 0) {
            // Opening Hit: Crisp, agile slash with tactile edge and tight low-end anchor
            return ComboStage.ofLayers(
                    // 1. Transient
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 0.95f, 1.28f, 0.04f),
                    new SoundLayer(SoundEvents.BREEZE_DEFLECT, 0.65f, 1.35f, 0.04f),
                    // 2. Body
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_STRONG, 0.90f, 1.22f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 0.75f, 1.25f, 0.03f),
                    // 3. Sub-bass (40-100Hz tight thump)
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.70f, 1.15f, 0.03f),
                    // 4. Kinetic Wake
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.85f, 1.25f, 0.04f)
            );
        } else {
            // Intermediate Strike: Escalating tension, deepening body presence
            return ComboStage.ofLayers(
                    // 1. Transient
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 1.05f, 1.12f, 0.04f),
                    new SoundLayer(SoundEvents.BREEZE_DEFLECT, 0.75f, 1.18f, 0.04f),
                    // 2. Body
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_STRONG, 1.00f, 1.08f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 0.95f, 1.05f, 0.03f),
                    // 3. Sub-bass
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.85f, 0.95f, 0.02f),
                    new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.55f, 0.85f, 0.02f),
                    // 4. Kinetic Wake
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.95f, 1.08f, 0.04f),
                    new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 0.70f, 1.10f, 0.04f)
            );
        }
    }

    /**
     * 2. CLAYMORE (阔剑): Massive 3-hit cadence.
     * Broad Cleave L (1.15) -> Return Cleave R (0.95) -> Seismic Ground Slam Finisher (0.65).
     */
    private static ComboStage getClaymoreHitStage(int comboStep, boolean finisher) {
        if (finisher) {
            return ComboStage.ofLayers(
                    // 1. Transient: Resonant greatsword deflection ring & crushing incision
                    new SoundLayer(SoundEvents.BREEZE_DEFLECT, 1.10f, 0.72f, 0.03f),
                    // 2. Body: Massive armor-rupturing ground crunch & knockback
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.35f, 0.68f, 0.03f),
                    // 3. Sub-bass: Devastating tectonic rumble & conduit drone
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.45f, 0.65f, 0.02f),
                    // 4. Kinetic Wake: Compressed air shockwave & displaced mass
                    new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 1.15f, 0.70f, 0.03f),
                    // Colossal supplementary layers
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.25f, 0.68f, 0.03f),
                    new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.90f, 0.55f, 0.02f),
                    new SoundLayer(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.55f, 0.65f, 0.02f)
            );
        }
        int step = comboStep % 3;
        if (step == 0) {
            // Hit 1: Heavy broadsword horizontal cleave
            return ComboStage.ofLayers(
                    new SoundLayer(SoundEvents.BREEZE_DEFLECT, 0.95f, 1.15f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_STRONG, 1.05f, 1.10f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.00f, 0.95f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.10f, 0.82f, 0.02f),
                    new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.80f, 0.80f, 0.03f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 1.10f, 1.08f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_AIR, 0.85f, 0.98f, 0.03f)
            );
        } else {
            // Hit 2: Return cleave escalating tension
            return ComboStage.ofLayers(
                    new SoundLayer(SoundEvents.BREEZE_DEFLECT, 1.05f, 0.95f, 0.03f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.25f, 0.85f, 0.03f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.25f, 0.75f, 0.02f),
                    new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.90f, 0.70f, 0.02f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.15f, 0.88f, 0.03f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 1.10f, 0.90f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_AIR, 1.00f, 0.85f, 0.03f)
            );
        }
    }

    /**
     * 3. DOUBLE_AXE (双刃斧): Continuous 360-degree whirlwind cleave.
     * Whirlwind Spin 1 (1.18) -> Vortex Blast Finisher (0.75).
     */
    private static ComboStage getDoubleAxeHitStage(int comboStep, boolean finisher) {
        if (finisher) {
            return ComboStage.ofLayers(
                    // 1. Transient: Razor-sharp twin blade cleaving edge
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 1.25f, 0.80f, 0.04f),
                    // 2. Body: Crushing wedge bite & radial impact
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.35f, 0.75f, 0.03f),
                    // 3. Sub-bass: Rolling vortex rumble & heavy ground wave
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.35f, 0.70f, 0.02f),
                    // 4. Kinetic Wake: Roaring 360 whirlwind vortex & air burst
                    new SoundLayer(SoundEvents.BREEZE_WHIRL, 1.40f, 0.80f, 0.03f),
                    new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 1.25f, 0.85f, 0.03f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.20f, 0.78f, 0.03f),
                    new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.85f, 0.65f, 0.02f)
            );
        }
        // Spin 1: Rapid 360 cleave enveloped in aerodynamic whirlwind and deep low-end inertia
        return ComboStage.ofLayers(
                new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 1.00f, 1.18f, 0.04f),
                new SoundLayer(SoundEvents.PLAYER_ATTACK_STRONG, 1.10f, 1.12f, 0.04f),
                new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.05f, 1.02f, 0.04f),
                new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.05f, 0.90f, 0.02f),
                new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.70f, 0.80f, 0.02f),
                new SoundLayer(SoundEvents.BREEZE_WHIRL, 1.20f, 1.15f, 0.04f),
                new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 1.00f, 1.12f, 0.04f)
        );
    }

    /**
     * 4. GREAT_HAMMER (大锤): Devastating concussive monolith.
     * Heavy Downward Smash (1.05) -> Cataclysmic Earthshaker Ground Smash Finisher (0.60).
     */
    private static ComboStage getGreatHammerHitStage(int comboStep, boolean finisher) {
        if (finisher) {
            return ComboStage.ofLayers(
                    // 1. Transient: Heavy iron face impact click
                    new SoundLayer(SoundEvents.TRIDENT_HIT, 1.15f, 0.68f, 0.03f),
                    // 2. Body: Pulverizing bone & stone rupture
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.45f, 0.65f, 0.03f),
                    // 3. Sub-bass: Seismic tectonic earthshaker rumble
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.55f, 0.60f, 0.02f),
                    // 4. Kinetic Wake: Explosive compressed air rupture
                    new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 1.30f, 0.68f, 0.03f),
                    new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 1.05f, 0.50f, 0.02f),
                    new SoundLayer(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.75f, 0.60f, 0.02f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.35f, 0.65f, 0.03f),
                    new SoundLayer(SoundEvents.MACE_SMASH_AIR, 1.25f, 0.60f, 0.02f)
            );
        }
        // Hit 1: Dense downward blunt crush
        return ComboStage.ofLayers(
                new SoundLayer(SoundEvents.TRIDENT_HIT, 0.90f, 1.10f, 0.04f),
                new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.25f, 0.95f, 0.04f),
                new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.15f, 0.80f, 0.03f),
                new SoundLayer(SoundEvents.MACE_SMASH_AIR, 1.00f, 0.90f, 0.03f),
                new SoundLayer(SoundEvents.PLAYER_ATTACK_STRONG, 1.05f, 0.95f, 0.03f),
                new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.75f, 0.75f, 0.02f)
        );
    }

    /**
     * 5. MACE (钉头锤): 3-hit concussive blunt impact.
     * Downward Bash (1.20) -> Backhand Concussion (1.00) -> Skull-Crusher Finisher (0.75).
     */
    private static ComboStage getMaceHitStage(int comboStep, boolean finisher) {
        if (finisher) {
            return ComboStage.ofLayers(
                    // 1. Transient: Resonant ringing steel flange chime
                    new SoundLayer(SoundEvents.BREEZE_DEFLECT, 1.20f, 0.82f, 0.03f),
                    // 2. Body: Heavy skull-cracking bone fracture
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.35f, 0.78f, 0.03f),
                    // 3. Sub-bass: Dense iron shockwave
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.35f, 0.72f, 0.02f),
                    // 4. Kinetic Wake: Violent air compression pop
                    new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 1.10f, 0.80f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 1.10f, 0.80f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.20f, 0.75f, 0.03f),
                    new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.85f, 0.68f, 0.02f)
            );
        }
        int step = comboStep % 3;
        if (step == 0) {
            // Hit 1: Crisp downward flange strike with dense iron core
            return ComboStage.ofLayers(
                    new SoundLayer(SoundEvents.BREEZE_DEFLECT, 0.90f, 1.25f, 0.04f),
                    new SoundLayer(SoundEvents.TRIDENT_HIT, 0.85f, 1.20f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.10f, 1.15f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.90f, 1.05f, 0.03f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.80f, 1.10f, 0.03f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.80f, 1.20f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_STRONG, 0.95f, 1.18f, 0.04f)
            );
        } else {
            // Hit 2: Escalating backhand concussion
            return ComboStage.ofLayers(
                    new SoundLayer(SoundEvents.BREEZE_DEFLECT, 1.05f, 1.05f, 0.04f),
                    new SoundLayer(SoundEvents.TRIDENT_HIT, 0.95f, 1.00f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.25f, 1.00f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.10f, 0.88f, 0.02f),
                    new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.70f, 0.80f, 0.02f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.00f, 0.95f, 0.03f),
                    new SoundLayer(SoundEvents.MACE_SMASH_AIR, 0.90f, 1.00f, 0.03f)
            );
        }
    }

    /**
     * 6. GAUNTLETS (拳套): Fast 3-hit boxing barrage.
     * Left Jab (1.35) -> Right Cross (1.18) -> Rocket Uppercut Finisher (0.75).
     */
    private static ComboStage getGauntletsHitStage(int comboStep, boolean finisher) {
        if (finisher) {
            return ComboStage.ofLayers(
                    // 1. Transient: Explosive knuckle crack & shock snap
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 1.25f, 0.85f, 0.04f),
                    new SoundLayer(SoundEvents.TRIDENT_HIT, 0.95f, 0.88f, 0.04f),
                    // 2. Body: Concussive sternum smash & bone thud
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.40f, 0.78f, 0.03f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.35f, 0.75f, 0.03f),
                    // 3. Sub-bass: Seismic uppercut lift & launch weight
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.35f, 0.68f, 0.02f),
                    new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.85f, 0.62f, 0.02f),
                    // 4. Kinetic Wake: Rocket air detonation burst
                    new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 1.35f, 0.82f, 0.04f)
            );
        }
        int step = comboStep % 3;
        if (step == 0) {
            // Hit 1: Left Jab - sharp, rapid, high-pitched punch with dense fist mass
            return ComboStage.ofLayers(
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 0.90f, 1.35f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_STRONG, 1.05f, 1.30f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 0.85f, 1.35f, 0.03f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.70f, 1.20f, 0.03f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.70f, 1.22f, 0.03f),
                    new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.85f, 1.38f, 0.04f)
            );
        } else {
            // Hit 2: Right Cross - deeper body impact escalating tension
            return ComboStage.ofLayers(
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 1.05f, 1.18f, 0.04f),
                    new SoundLayer(SoundEvents.TRIDENT_HIT, 0.80f, 1.22f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_STRONG, 1.15f, 1.12f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.05f, 1.15f, 0.03f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.90f, 1.02f, 0.02f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.90f, 1.05f, 0.03f),
                    new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.90f, 1.20f, 0.04f)
            );
        }
    }

    /**
     * 7. DAGGERS (匕首): Ultra-fast 4-hit laceration flurry.
     * Left Stab (1.38) -> Right Stab (1.32) -> Cross Cut (1.18) -> Dual Plunge Finisher (0.80).
     */
    private static ComboStage getDaggersHitStage(int comboStep, boolean finisher) {
        if (finisher) {
            return ComboStage.ofLayers(
                    // 1. Transient: Needle-point puncture snap & razor incision
                    new SoundLayer(SoundEvents.TRIDENT_HIT, 1.35f, 0.82f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 1.25f, 0.80f, 0.04f),
                    // 2. Body: Deep tissue penetration & bone bite
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.30f, 0.78f, 0.03f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.20f, 0.78f, 0.03f),
                    // 3. Sub-bass: Downward plunge compression weight (40-90Hz)
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.25f, 0.70f, 0.02f),
                    new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.80f, 0.65f, 0.02f),
                    // 4. Kinetic Wake: Compressed vacuum pop & dual slice
                    new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 1.25f, 0.85f, 0.04f),
                    new SoundLayer(SoundEvents.BREEZE_SLIDE, 1.10f, 0.90f, 0.04f)
            );
        }
        int step = comboStep % 4;
        return switch (step) {
            case 0 -> ComboStage.ofLayers(
                    // Left Stab: Ultra-fast piercing snap with crisp flesh bite
                    new SoundLayer(SoundEvents.TRIDENT_HIT, 1.00f, 1.38f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 0.85f, 1.40f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_STRONG, 0.90f, 1.32f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 0.75f, 1.40f, 0.03f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.60f, 1.25f, 0.03f),
                    new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.85f, 1.42f, 0.04f)
            );
            case 1 -> ComboStage.ofLayers(
                    // Right Stab: Rapid follow-up with crisp critical edge
                    new SoundLayer(SoundEvents.TRIDENT_HIT, 1.05f, 1.32f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 0.90f, 1.35f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_STRONG, 0.95f, 1.28f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 0.80f, 1.35f, 0.03f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.65f, 1.18f, 0.03f),
                    new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.90f, 1.36f, 0.04f)
            );
            default -> ComboStage.ofLayers(
                    // Cross Cut: Escalating climax tension before the plunge
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 1.15f, 1.18f, 0.04f),
                    new SoundLayer(SoundEvents.BREEZE_DEFLECT, 0.90f, 1.22f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_STRONG, 1.05f, 1.15f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 0.95f, 1.15f, 0.03f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.85f, 1.02f, 0.02f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.85f, 1.05f, 0.03f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 1.00f, 1.20f, 0.04f),
                    new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.95f, 1.25f, 0.04f)
            );
        };
    }

    /**
     * 8. SICKLES (双镰刀): 4-hit reaping combo.
     * Slash L (1.32) -> Slash R (1.24) -> Inward Hook Pull (1.10) -> 360 Spin Finisher (0.78).
     */
    private static ComboStage getSicklesHitStage(int comboStep, boolean finisher) {
        if (finisher) {
            return ComboStage.ofLayers(
                    // 1. Transient: Razor sickle ring & decapitating slice
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 1.30f, 0.82f, 0.04f),
                    new SoundLayer(SoundEvents.BREEZE_DEFLECT, 1.05f, 0.85f, 0.03f),
                    // 2. Body: Curved blade bone shearing & radial crunch
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.35f, 0.78f, 0.03f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.25f, 0.80f, 0.03f),
                    // 3. Sub-bass: Heavy reaping vortex rumble
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.35f, 0.70f, 0.02f),
                    new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.85f, 0.65f, 0.02f),
                    // 4. Kinetic Wake: 360 sickle hurricane air displacement
                    new SoundLayer(SoundEvents.BREEZE_WHIRL, 1.45f, 0.82f, 0.03f),
                    new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 1.25f, 0.82f, 0.04f)
            );
        }
        int step = comboStep % 4;
        if (step == 0) {
            // Hit 1: Fast curved blade slice
            return ComboStage.ofLayers(
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 0.95f, 1.32f, 0.04f),
                    new SoundLayer(SoundEvents.BREEZE_DEFLECT, 0.75f, 1.35f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_STRONG, 0.95f, 1.26f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 0.80f, 1.30f, 0.03f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.65f, 1.15f, 0.03f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.90f, 1.32f, 0.04f),
                    new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.75f, 1.35f, 0.04f)
            );
        } else if (step == 1) {
            // Hit 2: Opposite sweep maintaining agility
            return ComboStage.ofLayers(
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 1.05f, 1.22f, 0.04f),
                    new SoundLayer(SoundEvents.BREEZE_DEFLECT, 0.85f, 1.25f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_STRONG, 1.00f, 1.18f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 0.90f, 1.20f, 0.03f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.75f, 1.08f, 0.03f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.95f, 1.25f, 0.04f)
            );
        } else {
            // Hit 3: Inward Hook Pull - impales and pulls victim into the user
            return ComboStage.ofLayers(
                    new SoundLayer(SoundEvents.TRIDENT_HIT, 1.20f, 1.12f, 0.04f),
                    new SoundLayer(SoundEvents.BREEZE_DEFLECT, 0.95f, 1.18f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.20f, 1.02f, 0.03f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.10f, 0.85f, 0.02f),
                    new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.85f, 0.80f, 0.03f),
                    new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 1.10f, 1.10f, 0.04f),
                    new SoundLayer(SoundEvents.BREEZE_WHIRL, 1.00f, 1.15f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_STRONG, 1.05f, 1.08f, 0.04f)
            );
        }
    }

    /**
     * 9. SPEAR (长矛): 3-hit long-reach linear piercing combo.
     * Penetrating Thrust 1 (1.35) -> Thrust 2 (1.20) -> Power Lunge Finisher (0.78).
     */
    private static ComboStage getSpearHitStage(int comboStep, boolean finisher) {
        if (finisher) {
            return ComboStage.ofLayers(
                    // 1. Transient: Deep piercing steel spearhead snap & armor puncture
                    new SoundLayer(SoundEvents.TRIDENT_HIT, 1.40f, 0.85f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 1.25f, 0.80f, 0.04f),
                    // 2. Body: Full-body drive flesh impalement & bone pierce
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.40f, 0.78f, 0.03f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.30f, 0.75f, 0.03f),
                    // 3. Sub-bass: Heavy kinetic shockwave & deep forward anchor
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.35f, 0.70f, 0.02f),
                    new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.90f, 0.62f, 0.02f),
                    // 4. Kinetic Wake: Roaring high-velocity linear slipstream
                    new SoundLayer(SoundEvents.TRIDENT_RIPTIDE_1, 1.30f, 0.82f, 0.03f),
                    new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 1.20f, 0.80f, 0.04f)
            );
        }
        int step = comboStep % 3;
        if (step == 0) {
            // Hit 1: Crisp high-speed linear thrust
            return ComboStage.ofLayers(
                    new SoundLayer(SoundEvents.TRIDENT_HIT, 1.10f, 1.35f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 0.85f, 1.35f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_STRONG, 1.00f, 1.28f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 0.85f, 1.30f, 0.03f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.75f, 1.15f, 0.03f),
                    new SoundLayer(SoundEvents.BREEZE_SLIDE, 1.00f, 1.38f, 0.04f)
            );
        } else {
            // Hit 2: Deeper thrust with rising tension
            return ComboStage.ofLayers(
                    new SoundLayer(SoundEvents.TRIDENT_HIT, 1.15f, 1.20f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 0.95f, 1.22f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_STRONG, 1.10f, 1.15f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 0.95f, 1.15f, 0.03f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.90f, 1.00f, 0.02f),
                    new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.60f, 0.90f, 0.02f),
                    new SoundLayer(SoundEvents.BREEZE_SLIDE, 1.05f, 1.25f, 0.04f)
            );
        }
    }

    /**
     * 10. GLAIVE (长戟): 3-hit sweeping polearm combo.
     * Wide Sweep L (1.25) -> Wide Sweep R (1.05) -> Sweeping Lunge Finisher (0.72).
     */
    private static ComboStage getGlaiveHitStage(int comboStep, boolean finisher) {
        if (finisher) {
            return ComboStage.ofLayers(
                    // 1. Transient: Ringing polearm blade edge & cutting snap
                    new SoundLayer(SoundEvents.BREEZE_DEFLECT, 1.35f, 0.78f, 0.03f),
                    new SoundLayer(SoundEvents.TRIDENT_HIT, 1.25f, 0.82f, 0.04f),
                    // 2. Body: Heavy cleaving polearm crunch & shaft impact
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.45f, 0.72f, 0.03f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.35f, 0.72f, 0.03f),
                    // 3. Sub-bass: Massive sweeping displacement rumble
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.45f, 0.68f, 0.02f),
                    new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.95f, 0.60f, 0.02f),
                    // 4. Kinetic Wake: Roaring extended arc slipstream & shock burst
                    new SoundLayer(SoundEvents.TRIDENT_RIPTIDE_1, 1.35f, 0.78f, 0.03f),
                    new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 1.25f, 0.78f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 1.25f, 0.75f, 0.03f)
            );
        }
        int step = comboStep % 3;
        if (step == 0) {
            // Hit 1: Wide curved blade polearm sweep
            return ComboStage.ofLayers(
                    new SoundLayer(SoundEvents.BREEZE_DEFLECT, 1.00f, 1.25f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 0.95f, 1.25f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_STRONG, 1.05f, 1.18f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 0.90f, 1.20f, 0.03f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.80f, 1.08f, 0.03f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 1.20f, 1.18f, 0.04f),
                    new SoundLayer(SoundEvents.BREEZE_WHIRL, 0.85f, 1.22f, 0.04f)
            );
        } else {
            // Hit 2: Heavy return arc escalating tension
            return ComboStage.ofLayers(
                    new SoundLayer(SoundEvents.BREEZE_DEFLECT, 1.10f, 1.08f, 0.03f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 1.05f, 1.08f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_STRONG, 1.15f, 1.05f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.10f, 1.02f, 0.03f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.05f, 0.88f, 0.02f),
                    new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.65f, 0.82f, 0.02f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 1.25f, 1.02f, 0.04f),
                    new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 0.75f, 1.00f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_AIR, 0.95f, 1.00f, 0.03f)
            );
        }
    }

    /* ========================================================================= */
    /*  ADDITIONAL WEAPON ARCHETYPES                                            */
    /* ========================================================================= */

    private static ComboStage getAxeHitStage(int comboStep, boolean finisher) {
        if (finisher) {
            return ComboStage.ofLayers(
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 1.15f, 0.80f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.30f, 0.75f, 0.03f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.20f, 0.70f, 0.03f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 1.15f, 0.75f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.10f, 0.78f, 0.03f)
            );
        }
        return ComboStage.ofLayers(
                new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 0.90f, 1.15f, 0.04f),
                new SoundLayer(SoundEvents.PLAYER_ATTACK_STRONG, 1.05f, 1.10f, 0.04f),
                new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.80f, 1.05f, 0.03f),
                new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.90f, 1.10f, 0.04f)
        );
    }

    private static ComboStage getSoulScytheHitStage(int comboStep, boolean finisher) {
        if (finisher) {
            return ComboStage.ofLayers(
                    new SoundLayer(SoundEvents.TOTEM_USE, 0.85f, 1.35f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 1.25f, 0.75f, 0.04f),
                    new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.95f, 0.65f, 0.02f),
                    new SoundLayer(SoundEvents.BREEZE_WHIRL, 1.10f, 0.80f, 0.04f),
                    new SoundLayer(SoundEvents.BEACON_ACTIVATE, 0.80f, 1.25f, 0.03f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.00f, 0.75f, 0.03f)
            );
        }
        return ComboStage.ofLayers(
                new SoundLayer(SoundEvents.EVOKER_CAST_SPELL, 0.75f, 1.25f, 0.04f),
                new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 1.05f, 0.95f, 0.04f),
                new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.65f, 1.05f, 0.03f),
                new SoundLayer(SoundEvents.PLAYER_ATTACK_STRONG, 0.90f, 1.00f, 0.04f)
        );
    }

    private static ComboStage getWhipHitStage() {
        return ComboStage.ofLayers(
                new SoundLayer(SoundEvents.BREEZE_DEFLECT, 0.90f, 1.65f, 0.05f),
                new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.15f, 1.40f, 0.04f),
                new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 0.60f, 0.90f, 0.03f),
                new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 1.30f, 1.50f, 0.05f),
                new SoundLayer(SoundEvents.TRIDENT_HIT, 0.90f, 1.45f, 0.05f)
        );
    }

    private static ComboStage getAnchorHitStage() {
        return ComboStage.ofLayers(
                new SoundLayer(SoundEvents.TRIDENT_HIT, 1.20f, 0.58f, 0.03f),
                new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 1.55f, 0.52f, 0.02f),
                new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.60f, 0.50f, 0.02f),
                new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 1.35f, 0.60f, 0.02f),
                new SoundLayer(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.85f, 0.55f, 0.02f),
                new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.40f, 0.50f, 0.02f),
                new SoundLayer(SoundEvents.CONDUIT_ACTIVATE, 1.10f, 0.45f, 0.02f)
        );
    }

    private static ComboStage getKatanaHitStage(int comboStep, boolean finisher) {
        if (finisher) {
            return ComboStage.ofLayers(
                    new SoundLayer(SoundEvents.BREEZE_DEFLECT, 1.20f, 1.15f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 1.30f, 0.90f, 0.03f),
                    new SoundLayer(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.10f, 0.80f, 0.03f),
                    new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 1.05f, 1.15f, 0.04f),
                    new SoundLayer(SoundEvents.MACE_SMASH_AIR, 0.85f, 1.10f, 0.04f)
            );
        }
        int step = comboStep % 3;
        if (step == 0) {
            return ComboStage.ofLayers(
                    new SoundLayer(SoundEvents.BREEZE_DEFLECT, 0.70f, 1.45f, 0.05f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 0.95f, 1.30f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.60f, 1.20f, 0.04f),
                    new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.75f, 1.40f, 0.04f)
            );
        } else {
            return ComboStage.ofLayers(
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 0.95f, 1.25f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_SWEEP, 1.05f, 1.15f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.75f, 1.10f, 0.04f),
                    new SoundLayer(SoundEvents.BREEZE_SLIDE, 0.80f, 1.30f, 0.04f)
            );
        }
    }

    private static ComboStage getRapierHitStage(int comboStep, boolean finisher) {
        if (finisher) {
            return ComboStage.ofLayers(
                    new SoundLayer(SoundEvents.TRIDENT_HIT, 1.25f, 1.20f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 1.15f, 1.15f, 0.04f),
                    new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.05f, 1.00f, 0.03f),
                    new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 1.10f, 1.25f, 0.04f)
            );
        }
        float pitch = 1.38f + (comboStep % 4) * 0.04f;
        return ComboStage.ofLayers(
                new SoundLayer(SoundEvents.TRIDENT_HIT, 0.90f, pitch, 0.04f),
                new SoundLayer(SoundEvents.PLAYER_ATTACK_STRONG, 0.80f, pitch - 0.05f, 0.04f),
                new SoundLayer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.50f, pitch - 0.10f, 0.03f),
                new SoundLayer(SoundEvents.BREEZE_DEFLECT, 0.60f, pitch + 0.10f, 0.05f)
        );
    }

    private static ComboStage appendCriticalLayers(ComboStage original) {
        List<SoundLayer> expanded = new ArrayList<>(original.layers());
        // Transient cutting edge bite
        expanded.add(new SoundLayer(SoundEvents.PLAYER_ATTACK_CRIT, 1.15f, 0.90f, 0.04f));
        // Deep bone crunch
        expanded.add(new SoundLayer(SoundEvents.MACE_SMASH_GROUND, 0.85f, 1.05f, 0.04f));
        // Compressed shockwave pop
        expanded.add(new SoundLayer(SoundEvents.WIND_CHARGE_BURST, 0.75f, 1.15f, 0.04f));
        return new ComboStage(List.copyOf(expanded));
    }

    /* ========================================================================= */
    /*  HIGH-LEVEL PUBLIC COMBAT API                                            */
    /* ========================================================================= */

    /**
     * Dispatches spatial audio for a weapon hit, positioning the sound source between attacker and target.
     *
     * @param attacker   Entity executing the attack.
     * @param target     Entity being struck.
     * @param weapon     MCD weapon archetype.
     * @param comboStep  Current 0-based combo progression index.
     * @param isFinisher Explicitly mark as a combo finisher.
     * @param isCrit     Whether the strike triggered a critical hit.
     */
    public static void playHit(
            @NotNull Entity attacker,
            @NotNull Entity target,
            @NotNull MCDWeaponType weapon,
            int comboStep,
            boolean isFinisher,
            boolean isCrit
    ) {
        Level level = attacker.level();
        // Target chest center for realistic 3D acoustic localization
        double x = target.getX();
        double y = target.getY() + (target.getEyeHeight() * 0.5);
        double z = target.getZ();

        ComboStage stage = getHitChoreography(weapon, comboStep, isFinisher, isCrit);
        stage.play(level, x, y, z, attacker.getRandom());
    }

    /**
     * Dispatches weapon swing / air displacement audio at the attacker's weapon release position.
     *
     * @param attacker  Entity swinging the weapon.
     * @param weapon    MCD weapon archetype.
     * @param comboStep Current 0-based combo progression index.
     */
    public static void playSwing(
            @NotNull Entity attacker,
            @NotNull MCDWeaponType weapon,
            int comboStep
    ) {
        Level level = attacker.level();
        double x = attacker.getX();
        double y = attacker.getY() + (attacker.getEyeHeight() * 0.8);
        double z = attacker.getZ();

        ComboStage stage = getSwingChoreography(weapon, comboStep);
        stage.play(level, x, y, z, attacker.getRandom());
    }

    /**
     * Dispatches an elemental or enchantment proc sound stage at the specified spatial position.
     *
     * @param level Level to play in.
     * @param pos   Spatial world position.
     * @param proc  The proc archetype.
     */
    public static void playProc(
            @NotNull Level level,
            @NotNull Vec3 pos,
            @NotNull ProcType proc
    ) {
        proc.play(level, pos.x, pos.y, pos.z, level.getRandom());
    }

    /**
     * Dispatches an elemental or enchantment proc sound stage on an entity.
     */
    public static void playProc(@NotNull Entity entity, @NotNull ProcType proc) {
        playProc(entity.level(), entity.position().add(0, entity.getEyeHeight() * 0.5, 0), proc);
    }

    /**
     * Helper to play a single sound with clean client/server level handling.
     */
    public static void playDirect(
            @NotNull Level level,
            double x,
            double y,
            double z,
            @NotNull SoundEvent sound,
            float volume,
            float pitch
    ) {
        float clampedPitch = Math.clamp(pitch, 0.5f, 2.0f);
        if (level.isClientSide()) {
            level.playLocalSound(x, y, z, sound, SoundSource.PLAYERS, volume, clampedPitch, false);
        } else {
            level.playSound(null, x, y, z, sound, SoundSource.PLAYERS, volume, clampedPitch);
        }
    }

    /**
     * Helper to play a single Holder sound with clean client/server level handling.
     */
    public static void playDirect(
            @NotNull Level level,
            double x,
            double y,
            double z,
            @NotNull Holder<SoundEvent> soundHolder,
            float volume,
            float pitch
    ) {
        playDirect(level, x, y, z, soundHolder.value(), volume, pitch);
    }
}
