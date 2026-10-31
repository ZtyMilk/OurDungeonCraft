package com.github.ztymilk.vfx;

import com.github.ztymilk.combat.AttackComboAccessor;
import com.github.ztymilk.combat.CleaveShape;
import com.github.ztymilk.combat.ComboStep;
import com.github.ztymilk.sound.WeaponSoundHelper.MCDWeaponType;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Deluxe Physical Visual Effects and Particle Choreography Engine for Minecraft Dungeons weapons.
 * <p>
 * Implements refined, noise-reduced visual fidelity per WEAPON_DIFFERENTIATION_SPECIFICATION Section 6:
 * <ul>
 *   <li><b>Restrained Single Outer Razor Edge</b>: Completely eradicates inner body fill (midPt, inPt)
 *       and bloat halo. Samples every 9° ~ 12° to guarantee 3 ~ 16 particles per swing (70% ~ 80% reduction).</li>
 *   <li><b>Micro-Scale Particles</b>: Standard dust size scaled down to 0.45f ~ 0.65f.</li>
 *   <li><b>Hitstop Debounce & Pause</b>: Pauses particle generation during Hitstop freeze ticks to eliminate light cores.</li>
 *   <li><b>Acoustic & Kinetic Purity</b>: Supersonic Mach needles, single Mach shock rings, and authentic
 *       contextual block debris with physical parabolic gravity.</li>
 *   <li><b>10-Weapon Dedicated Visual Spectrum</b>: Sword, Claymore, Double Axe, Hammer, Mace, Gauntlets,
 *       Daggers, Sickles, Spear, Glaive.</li>
 * </ul>
 */
public final class WeaponVfx {

    private WeaponVfx() {}

    // =========================================================================
    // COLOR PALETTE SPECIFICATION (SECTION 6.1 & 6.2 - REFINED SCALES: 0.45f ~ 0.65f)
    // =========================================================================

    public static final DustParticleOptions PALETTE_SWORD_WHITE = new DustParticleOptions(0xFFFFFF, 0.55f);
    public static final DustParticleOptions PALETTE_SWORD_SLATE = new DustParticleOptions(0xF8FAFC, 0.50f);
    public static final DustParticleOptions PALETTE_CYAN_FRINGE = new DustParticleOptions(0x93C5FD, 0.45f);
    // Section 3.4 & 6.1 - Sword: Neutral Steel White to Slate Cool Gray (0xFFFFFF -> 0x64748B, 0.46f)
    public static final DustColorTransitionOptions PALETTE_SWORD_TRANSITION =
            new DustColorTransitionOptions(0xFFFFFF, 0x64748B, 0.46f);
    public static final DustColorTransitionOptions PALETTE_SWORD_CYAN_TRANSITION =
            new DustColorTransitionOptions(0xFFFFFF, 0x93C5FD, 0.44f);

    // Section 3.4 & 6.2 - Claymore: Glacial Frost White to Dark Cold Steel (0xF8FAFC -> 0x334155, 0.54f)
    public static final DustColorTransitionOptions PALETTE_CLAYMORE_TRANSITION =
            new DustColorTransitionOptions(0xF8FAFC, 0x334155, 0.54f);

    public static final DustParticleOptions PALETTE_AXE_SLATE = new DustParticleOptions(0xE2E8F0, 0.55f);
    public static final DustParticleOptions PALETTE_AXE_INNER = new DustParticleOptions(0xCBD5E1, 0.50f);
    // Section 3.4 & 6.3 - Double Axe: Cold Slate White to Storm Dark Shadow (0xE2E8F0 -> 0x1E293B, 0.52f)
    public static final DustColorTransitionOptions PALETTE_AXE_TRANSITION =
            new DustColorTransitionOptions(0xE2E8F0, 0x1E293B, 0.52f);

    // Section 3.4 & 6.10 - Glaive: Crescent Silver to Armor Slate Gray (0xF8FAFC -> 0x475569, 0.50f)
    public static final DustColorTransitionOptions PALETTE_GLAIVE_TRANSITION =
            new DustColorTransitionOptions(0xF8FAFC, 0x475569, 0.50f);

    public static final DustParticleOptions PALETTE_HAMMER_AMBER = new DustParticleOptions(0xF59E0B, 0.60f);
    public static final DustParticleOptions PALETTE_HAMMER_GOLD = new DustParticleOptions(0xFCD34D, 0.50f);
    public static final DustColorTransitionOptions PALETTE_HAMMER_TRANSITION = new DustColorTransitionOptions(0xFCD34D, 0x78350F, 0.58f);
    public static final DustColorTransitionOptions PALETTE_HAMMER_FISSURE = new DustColorTransitionOptions(0xFCD34D, 0x451A03, 0.52f);

    public static final DustParticleOptions PALETTE_MACE_STEEL = new DustParticleOptions(0x94A3B8, 0.55f);
    public static final DustParticleOptions PALETTE_MACE_CONCUSSIVE = new DustParticleOptions(0xCBD5E1, 0.50f);
    public static final DustColorTransitionOptions PALETTE_MACE_TRANSITION = new DustColorTransitionOptions(0xCBD5E1, 0x475569, 0.48f);

    // Section 3.4 & 6.6 - Daggers: Dark Crimson Bloodline (0xEF4444 -> 0x450A0A, 0.38f)
    public static final DustColorTransitionOptions PALETTE_DAGGER_TRANSITION =
            new DustColorTransitionOptions(0xEF4444, 0x450A0A, 0.38f);
    public static final DustColorTransitionOptions PALETTE_DAGGER_RUBY_TRANSITION =
            new DustColorTransitionOptions(0xDC2626, 0x7F1D1D, 0.36f);
    public static final DustParticleOptions PALETTE_DAGGER_CRIMSON = new DustParticleOptions(0xEF4444, 0.38f);
    public static final DustParticleOptions PALETTE_DAGGER_RUBY = new DustParticleOptions(0x7F1D1D, 0.36f);

    // Section 3.4 & 6.8 - Sickles: Crescent Silver to Deep Azure (0xF1F5F9 -> 0x0284C7, 0.40f)
    public static final DustColorTransitionOptions PALETTE_SICKLE_TRANSITION =
            new DustColorTransitionOptions(0xF1F5F9, 0x0284C7, 0.40f);
    public static final DustParticleOptions PALETTE_SICKLE_SILVER = new DustParticleOptions(0xF1F5F9, 0.40f);
    public static final DustParticleOptions PALETTE_SICKLE_MOON = new DustParticleOptions(0xCBD5E1, 0.40f);

    // Section 3.4 & 6.7 - Gauntlets: Pure Sonic White to Breeze Gray (0xFFFFFF -> 0x94A3B8, 0.38f)
    public static final DustColorTransitionOptions PALETTE_PUNCH_TRANSITION =
            new DustColorTransitionOptions(0xFFFFFF, 0x94A3B8, 0.38f);
    public static final DustParticleOptions PALETTE_PUNCH_GUST = new DustParticleOptions(0xFFFFFF, 0.38f);

    public static final DustParticleOptions PALETTE_SPEAR_VAPOR = new DustParticleOptions(0xF8FAFC, 0.50f);
    public static final DustParticleOptions PALETTE_SPEAR_RING = new DustParticleOptions(0xFFFFFF, 0.55f);
    public static final DustColorTransitionOptions PALETTE_SPEAR_VAPOR_TRANSITION = new DustColorTransitionOptions(0xFFFFFF, 0x38BDF8, 0.42f);
    public static final DustColorTransitionOptions PALETTE_SPEAR_RING_TRANSITION = new DustColorTransitionOptions(0xFFFFFF, 0x0284C7, 0.46f);

    // =========================================================================
    // 1. 3D ORIENTABLE STREAMLINED BLADE RIBBON TRAJECTORY (REFINED RAZOR EDGE)
    // =========================================================================

    /**
     * Spawns an ultra-refined, noise-reduced 3D blade ribbon arc matching the weapon's exact cutting plane.
     * <p>
     * Per Section 6.1:
     * - Eradicates inner body fill (midPt, inPt) and outer cyan glare halo to eliminate light pollution.
     * - Sparsely samples the razor cutting edge every 9.0 ~ 12.0 degrees (clamped to 5 ~ 12 points).
     * - Spawns exactly 1 clean SWEEP_ATTACK tangential slice at the peak midpoint of the arc.
     * - Total particle count per slash: 6 ~ 14 particles (reduced from 60 ~ 200).
     */
    public static int vector3fToRgb(Vector3f vec) {
        int r = Math.min(255, Math.max(0, (int) (vec.x() * 255.0f)));
        int g = Math.min(255, Math.max(0, (int) (vec.y() * 255.0f)));
        int b = Math.min(255, Math.max(0, (int) (vec.z() * 255.0f)));
        return (r << 16) | (g << 8) | b;
    }

    public static int getRgbFromDust(DustParticleOptions dust) {
        return vector3fToRgb(dust.getColor());
    }

    public static int getFadeRgbForPrimary(int primaryRgb) {
        return switch (primaryRgb) {
            case 0xFFFFFF -> 0x64748B; // Sword steel white -> Slate cool gray
            case 0xF8FAFC -> 0x334155; // Claymore / Glaive frost white -> Dark cold steel
            case 0xE2E8F0 -> 0x1E293B; // Double axe slate white -> Storm dark steel
            case 0xF59E0B, 0xFCD34D -> 0x78350F; // Hammer amber/gold -> Basalt molten brown
            case 0x94A3B8, 0xCBD5E1 -> 0x475569; // Mace steel -> Slate stone gray
            case 0xDC2626, 0xEF4444, 0x991B1B -> 0x450A0A; // Dagger crimson -> Dark blood
            case 0xF1F5F9 -> 0x0369A1; // Sickle silver -> Deep moon azure
            default -> {
                int r = (primaryRgb >> 16) & 0xFF;
                int g = (primaryRgb >> 8) & 0xFF;
                int b = primaryRgb & 0xFF;
                int fr = Math.max(0, (int) (r * 0.35f));
                int fg = Math.max(0, (int) (g * 0.38f));
                int fb = Math.max(0, (int) (b * 0.45f));
                yield (fr << 16) | (fg << 8) | fb;
            }
        };
    }

    /**
     * Master 3D physical blade ribbon trajectory with tangential & centrifugal fluid dynamics.
     * <p>
     * Implements Master VFX Golden Polish Specification v3.0 Sections 2, 3, & 8.2:
     * <ul>
     *   <li><b>Zero Static Particles</b>: Completely eliminates spawnStationary(v=0). All particles carry
     *       tangential velocity v_tangent and centrifugal separation v_centrifugal.</li>
     *   <li><b>Non-Uniform Sampling</b>: Biased sampling s_i = (i / N)^0.8 creates dense, razor-sharp
     *       leading edge at s=1.0 and natural soft fade-out at s=0.0.</li>
     *   <li><b>Head-to-Tail Falloff</b>: Dynamic scale interpolation D(s) = D_head * s + D_tail * (1 - s)
     *       with DustColorTransitionOptions from primary core color to trailing fade color.</li>
     *   <li><b>Tangential Centrifugal Mechanics</b>: v_init = v_tangent(s) + v_centrifugal + v_forward + v_jitter,
     *       creating an authentic slicing airflow ribbon without floating dots.</li>
     *   <li><b>Strict Particle Budget</b>: Exactly 8 ~ 12 particles per swing, perfectly bounded.</li>
     * </ul>
     */
    public static void physicalSlashRibbon(
            Level level,
            LivingEntity attacker,
            double radius,
            float arcDegrees,
            float tiltAngleDegrees,
            boolean reverse,
            int primaryRgb,
            int fadeRgb,
            float headScale,
            float tailScale,
            double kt,
            double kc,
            double kf,
            boolean withSparks,
            boolean spawnSweepParticle
    ) {
        Vec3 chestPos = attacker.position().add(0, attacker.getBbHeight() * 0.55, 0);
        Vec3 look = attacker.getLookAngle().normalize();

        // Construct orthonormal basis in view space
        Vec3 worldUp = (Math.abs(look.y) > 0.95) ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = look.cross(worldUp).normalize();
        Vec3 up = right.cross(look).normalize();

        // Rotate basis around look vector by tilt angle to match exact 3D swing plane
        double tiltRad = Math.toRadians(tiltAngleDegrees);
        double cosTilt = Math.cos(tiltRad);
        double sinTilt = Math.sin(tiltRad);
        Vec3 planeRight = right.scale(cosTilt).add(up.scale(sinTilt)).normalize();

        // Geometry: arc angle in radians
        double totalArcRad = Math.toRadians(arcDegrees);
        double halfArcRad = totalArcRad * 0.5;

        // Bounded sample intervals: 7 ~ 11 intervals (8 ~ 12 particles total)
        int intervals = Math.max(7, Math.min(11, Math.round(arcDegrees / 11.0f)));

        // Kinematics angular velocity: omega = totalArcRad / deltaT (swing duration ~3.5 ticks)
        double omega = totalArcRad / 3.5;
        double dirSign = reverse ? -1.0 : 1.0;
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        for (int i = 0; i <= intervals; i++) {
            // Non-linear sampling s in [0.0, 1.0], biased towards leading edge s -> 1.0 (gamma = 0.80)
            double u = (double) i / intervals;
            double s = Math.pow(u, 0.80);

            // Angle in swing plane: starts at tail (s=0), ends at head (s=1)
            double angle = reverse
                    ? (halfArcRad - s * totalArcRad)
                    : (-halfArcRad + s * totalArcRad);

            // 1. Radial unit vector and point position in 3D swing plane
            Vec3 radialDir = look.scale(Math.cos(angle)).add(planeRight.scale(Math.sin(angle))).normalize();
            Vec3 pos = chestPos.add(radialDir.scale(radius));

            // 2. Tangential unit vector (derivative of radial vector with respect to angle)
            Vec3 tangentDir = look.scale(-Math.sin(angle) * dirSign).add(planeRight.scale(Math.cos(angle) * dirSign)).normalize();

            // 3. Physical velocity synthesis
            // Tangential speed: full at leading edge (s=1), slightly damped at tail (s=0)
            double tangSpeed = kt * (omega * radius) * (0.65 + 0.35 * s);
            Vec3 vTang = tangentDir.scale(tangSpeed);

            // Centrifugal outward separation velocity
            double centSpeed = kc * (omega * omega * radius + 0.25);
            Vec3 vCent = radialDir.scale(centSpeed);

            // Forward airflow draft
            Vec3 vFwd = look.scale(kf);

            // Micro-turbulent jitter
            double jitter = 0.012;
            double jx = (rand.nextDouble() - 0.5) * 2.0 * jitter;
            double jy = (rand.nextDouble() - 0.5) * 2.0 * jitter;
            double jz = (rand.nextDouble() - 0.5) * 2.0 * jitter;

            Vec3 vInit = vTang.add(vCent).add(vFwd).add(jx, jy, jz);

            // 4. Head-to-tail micro-scale evolution D(s) = D_head * s + D_tail * (1 - s)
            float particleScale = (float) (headScale * s + tailScale * (1.0 - s));
            DustColorTransitionOptions particleOption = new DustColorTransitionOptions(primaryRgb, fadeRgb, particleScale);

            // 5. Dynamic emission via exact velocity (count = 0 in sendParticles)
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(particleOption, pos.x, pos.y, pos.z, 0, vInit.x, vInit.y, vInit.z, 1.0);
            } else {
                level.addParticle(particleOption, pos.x, pos.y, pos.z, vInit.x, vInit.y, vInit.z);
            }

            // 6. Exactly ONE clean horizontal/tangential sweep cut at swing apex (midpoint)
            if (spawnSweepParticle && i == intervals / 2) {
                spawnParticle(level, ParticleTypes.SWEEP_ATTACK, pos.x, pos.y, pos.z,
                        tangentDir.x * 0.28, tangentDir.y * 0.28, tangentDir.z * 0.28);
            }

            // 7. Subtle kinetic spark near leading edge if requested
            if (withSparks && i == intervals - 1) {
                spawnParticle(level, ParticleTypes.CRIT, pos.x, pos.y, pos.z,
                        vInit.x * 0.5, vInit.y * 0.5 + 0.12, vInit.z * 0.5);
            }
        }

        playSound(level, chestPos.x, chestPos.y, chestPos.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, reverse ? 1.25f : 1.15f);
    }

    public static void physicalSlashRibbon(
            ServerLevel level,
            LivingEntity attacker,
            double radius,
            float arcDegrees,
            float tiltAngleDegrees,
            boolean reverse,
            DustParticleOptions primaryColor,
            @Nullable DustParticleOptions secondaryColor,
            boolean withColdFringe,
            boolean withSparks,
            boolean spawnSweepParticle
    ) {
        int primaryRgb = getRgbFromDust(primaryColor);
        int fadeRgb = (secondaryColor != null) ? getRgbFromDust(secondaryColor) : getFadeRgbForPrimary(primaryRgb);
        float headScale = Math.max(0.35f, primaryColor.getScale() * 0.85f);
        float tailScale = Math.min(0.65f, primaryColor.getScale() * 1.15f);
        physicalSlashRibbon(level, attacker, radius, arcDegrees, tiltAngleDegrees, reverse,
                primaryRgb, fadeRgb, headScale, tailScale, 0.25, 0.08, 0.06, withSparks, spawnSweepParticle);
    }

    public static void physicalSlashRibbon(
            Level level,
            LivingEntity attacker,
            double radius,
            float arcDegrees,
            float tiltAngleDegrees,
            boolean reverse,
            DustParticleOptions primaryColor,
            @Nullable DustParticleOptions secondaryColor,
            boolean withColdFringe,
            boolean withSparks,
            boolean spawnSweepParticle
    ) {
        if (level instanceof ServerLevel serverLevel) {
            physicalSlashRibbon(serverLevel, attacker, radius, arcDegrees, tiltAngleDegrees, reverse,
                    primaryColor, secondaryColor, withColdFringe, withSparks, spawnSweepParticle);
            return;
        }
        int primaryRgb = getRgbFromDust(primaryColor);
        int fadeRgb = (secondaryColor != null) ? getRgbFromDust(secondaryColor) : getFadeRgbForPrimary(primaryRgb);
        float headScale = Math.max(0.35f, primaryColor.getScale() * 0.85f);
        float tailScale = Math.min(0.65f, primaryColor.getScale() * 1.15f);
        physicalSlashRibbon(level, attacker, radius, arcDegrees, tiltAngleDegrees, reverse,
                primaryRgb, fadeRgb, headScale, tailScale, 0.25, 0.08, 0.06, withSparks, spawnSweepParticle);
    }

    // =========================================================================
    // 2. SUPERSONIC MACH NEEDLE & RING (SPEAR & GLAIVE)
    // =========================================================================

    /**
     * Supersonic Mach Needle & Prandtl-Glauert Singularity Ring for Spear thrusts.
     * <p>
     * Implements Golden Polish Specification Section 5.1 & Section 6.9:
     * <ul>
     *   <li><b>Axial Supersonic Vapor Needle</b>: 3 ~ 4 micro vapor condensation points along the spear shaft
     *       with forward supersonic velocity (v_fwd = 0.55 m/s) using {@link DustColorTransitionOptions}
     *       (0xFFFFFF -> 0x38BDF8), completely eradicating stationary v=0 float points.</li>
     *   <li><b>Prandtl-Glauert Shock Ring</b>: Orthogonal ring located 0.35m behind spear tip,
     *       expanding purely radially outward with centrifugal shock velocity (v_ring = 0.25 * n_radial)
     *       using {@link DustColorTransitionOptions} (0xFFFFFF -> 0x0284C7), reproducing authentic
     *       transonic vapor ring dissipation within 2~3 ticks without smoke blobs.</li>
     *   <li><b>Forward Mach Gust Tip</b>: Exactly 1 SMALL_GUST conical shock puff at tip on power thrust (Step 2).</li>
     *   <li>Total particles: Step 0/1 = 7 ~ 8 particles; Step 2 = 11 ~ 12 particles (strictly within budget).</li>
     * </ul>
     */
    public static void machConeThrust(
            ServerLevel level,
            LivingEntity attacker,
            double length,
            float machHalfAngleDeg,
            int prandtlRings
    ) {
        Vec3 eyePos = attacker.getEyePosition();
        Vec3 look = attacker.getLookAngle().normalize();
        Vec3 tipPos = eyePos.add(look.scale(length));

        Vec3 worldUp = (Math.abs(look.y) > 0.95) ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = look.cross(worldUp).normalize();
        Vec3 up = right.cross(look).normalize();

        boolean isPowerThrust = prandtlRings > 1;

        // 1. Supersonic Axial Vapor Needle (3 ~ 4 discrete points along spear shaft with forward supersonic velocity)
        int shaftDots = isPowerThrust ? 4 : 3;
        double fwdSpeed = 0.55;
        Vec3 fwdVel = look.scale(fwdSpeed);

        for (int j = 1; j <= shaftDots; j++) {
            double d = length * ((double) j / (shaftDots + 1));
            Vec3 jPt = eyePos.add(look.scale(d));
            // Explicit initial velocity: count=0, dx=vx, dy=vy, dz=vz, speed=1.0
            spawnParticle(level, PALETTE_SPEAR_VAPOR_TRANSITION, jPt.x, jPt.y, jPt.z, fwdVel.x, fwdVel.y, fwdVel.z);
        }

        // 2. Prandtl-Glauert Singularity Shock Ring (radius 0.28m ~ 0.32m, 0.35m behind spear tip)
        double ringRadius = isPowerThrust ? 0.32 : 0.28;
        Vec3 ringCenter = tipPos.subtract(look.scale(0.35));
        int ringPoints = isPowerThrust ? 6 : 4;
        double radialBurstSpeed = isPowerThrust ? 0.28 : 0.24;

        for (int r = 0; r < ringPoints; r++) {
            double angle = (2.0 * Math.PI * r) / ringPoints;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Vec3 radialDir = right.scale(cos).add(up.scale(sin)).normalize();
            Vec3 pt = ringCenter.add(radialDir.scale(ringRadius));

            // Radial centrifugal expansion velocity + minor forward slip
            Vec3 ringVel = radialDir.scale(radialBurstSpeed).add(look.scale(0.08));
            spawnParticle(level, PALETTE_SPEAR_RING_TRANSITION, pt.x, pt.y, pt.z, ringVel.x, ringVel.y, ringVel.z);
        }

        // 3. Forward supersonic shock tip puff on power thrust finisher (Step 2)
        if (isPowerThrust) {
            spawnParticle(level, ParticleTypes.SMALL_GUST, tipPos.x, tipPos.y, tipPos.z,
                    look.x * 0.40, look.y * 0.40, look.z * 0.40);
        }

        // Supersonic sonic acoustics
        playSound(level, eyePos.x, eyePos.y, eyePos.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.10f, 1.45f);
        playSound(level, tipPos.x, tipPos.y, tipPos.z, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.95f, 1.35f);
    }

    public static void machConeThrust(
            Level level,
            LivingEntity attacker,
            double length,
            float machHalfAngleDeg,
            int prandtlRings
    ) {
        if (level instanceof ServerLevel serverLevel) {
            machConeThrust(serverLevel, attacker, length, machHalfAngleDeg, prandtlRings);
        } else {
            Vec3 eyePos = attacker.getEyePosition();
            Vec3 look = attacker.getLookAngle().normalize();
            Vec3 tipPos = eyePos.add(look.scale(length));
            spawnParticle(level, ParticleTypes.SMALL_GUST, tipPos.x, tipPos.y, tipPos.z, look.x * 0.35, look.y * 0.35, look.z * 0.35);
            playSound(level, eyePos.x, eyePos.y, eyePos.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.10f, 1.45f);
        }
    }

    // =========================================================================
    // 3. DIRECTIONAL GROUND CRACKS & CONTEXTUAL BLOCK DEBRIS
    // =========================================================================

    /**
     * Directional Ground Fissures and contextual block debris ejecta.
     * <p>
     * Per Section 5.3 & Section 6.4:
     * - Great Hammer Finisher: 14 ~ 16 particles (4 directional ground crack rays + 12 physical contextual block debris + 4 low plumes).
     * - Claymore Finisher: 12 ~ 14 particles (3 fissure rays + 8 physical contextual block debris).
     * - Mace Finisher: 8 ~ 10 particles (3 mini crack rays + 4 debris).
     * - Blinding campfire smoke puffs, massive poof rings, and screen-filling dust plumes are entirely removed.
     */
    public static void directionalGroundSmash(
            ServerLevel level,
            LivingEntity attacker,
            Vec3 impactCenter,
            double radius,
            boolean heavy,
            ParticleOptions fissureColor,
            int fissureCount
    ) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        Vec3 look = attacker.getLookAngle();
        Vec3 horizLook = new Vec3(look.x, 0, look.z).normalize();
        if (horizLook.lengthSqr() < 1e-4) {
            float yawRad = (float) Math.toRadians(-attacker.getYRot() - 90.0f);
            horizLook = new Vec3(Math.cos(yawRad), 0, Math.sin(yawRad));
        }
        Vec3 horizRight = new Vec3(-horizLook.z, 0, horizLook.x);

        // 1. Contextual Block Debris Query
        BlockPos groundPos = BlockPos.containing(impactCenter.x, impactCenter.y - 0.2, impactCenter.z);
        BlockState groundState = level.getBlockState(groundPos);
        if (groundState.isAir()) {
            groundState = level.getBlockState(groundPos.below());
        }
        if (groundState.isAir()) {
            groundState = level.getBlockState(groundPos.below(2));
        }
        if (groundState.isAir()) {
            groundState = Blocks.DIRT.defaultBlockState();
        }
        BlockParticleOption blockDebris = new BlockParticleOption(ParticleTypes.BLOCK, groundState);

        // 2. Directional Ground Crack Points (4 points for heavy Great Hammer, 2-3 for Claymore/Mace)
        double[] angleDeltas = switch (fissureCount) {
            case 1 -> new double[] { 0.0 };
            case 4, 5 -> new double[] { 0.0, -25.0, 25.0, 180.0 }; // 4 radial crack lines
            default -> new double[] { -20.0, 0.0, 20.0 };
        };

        double groundY = impactCenter.y + 0.05;
        for (double deg : angleDeltas) {
            double alphaRad = Math.toRadians(deg);
            Vec3 fDir = horizLook.scale(Math.cos(alphaRad)).add(horizRight.scale(Math.sin(alphaRad))).normalize();

            // Sample stepped points along the ray: 0.45*radius and 0.85*radius for heavy; 0.65*radius otherwise
            double[] rayFractions = (fissureCount >= 4) ? new double[] { 0.45, 0.85 } : new double[] { 0.65 };
            for (double frac : rayFractions) {
                double d = radius * frac;
                double fx = impactCenter.x + fDir.x * d;
                double fz = impactCenter.z + fDir.z * d;

                // Probe terrain surface height
                BlockPos probe = BlockPos.containing(fx, impactCenter.y + 0.5, fz);
                double fy = groundY;
                for (int dy = 2; dy >= -3; dy--) {
                    BlockPos check = probe.above(dy);
                    if (!level.getBlockState(check).isAir() && level.getBlockState(check.above()).isAir()) {
                        fy = check.getY() + 1.02;
                        break;
                    }
                }

                // Subtle outward creeping drift to eliminate static v=0
                Vec3 crawlVel = fDir.scale(0.03);
                spawnParticle(level, fissureColor, fx, fy, fz, crawlVel.x, 0.005, crawlVel.z);
            }
        }

        // 3. Physical Contextual Block Debris Ejecta (exact quotas from Section 6.2)
        // Great Hammer: 12 blocks; Claymore: 8 blocks; Mace / other: 4 debris
        int debrisCount = heavy ? 12 : (fissureCount <= 3 ? 8 : 4);
        for (int i = 0; i < debrisCount; i++) {
            double angle = rand.nextDouble() * 2.0 * Math.PI;
            double r = 0.15 + rand.nextDouble() * (radius * 0.35);
            double ex = impactCenter.x + r * Math.cos(angle);
            double ez = impactCenter.z + r * Math.sin(angle);

            // Ballistic parabolic trajectory
            double evy = 0.22 + rand.nextDouble() * 0.20;
            double forwardBias = 0.10 + rand.nextDouble() * 0.15;
            double radialSpeed = 0.08 + rand.nextDouble() * 0.12;
            double evx = horizLook.x * forwardBias + Math.cos(angle) * radialSpeed;
            double evz = horizLook.z * forwardBias + Math.sin(angle) * radialSpeed;

            spawnParticle(level, blockDebris, ex, groundY + 0.08, ez, evx, evy, evz);
        }

        // 4. Low-Profile Kinetic Shockwave Core (Section 5.3: 4 low-profile ground plumes, 0 blinding clouds)
        if (heavy) {
            for (int k = 0; k < 4; k++) {
                double shockAngle = (k * 0.5 * Math.PI) + (rand.nextDouble() * 0.2);
                double shockDist = 0.35;
                double sx = impactCenter.x + shockDist * Math.cos(shockAngle);
                double sz = impactCenter.z + shockDist * Math.sin(shockAngle);
                double svx = Math.cos(shockAngle) * 0.18;
                double svz = Math.sin(shockAngle) * 0.18;
                spawnParticle(level, ParticleTypes.DUST_PLUME, sx, groundY + 0.04, sz, svx, 0.01, svz);
            }
        }

        // 5. Acoustic Seismic Rupture Feedback
        SoundEvent smashSound = heavy ? SoundEvents.MACE_SMASH_GROUND_HEAVY : SoundEvents.MACE_SMASH_GROUND;
        playSound(level, impactCenter.x, impactCenter.y, impactCenter.z, smashSound, SoundSource.PLAYERS, 1.25f, heavy ? 0.65f : 0.85f);
        if (heavy) {
            playSound(level, impactCenter.x, impactCenter.y, impactCenter.z, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 0.80f, 0.70f);
        }
    }

    public static void directionalGroundSmash(
            Level level,
            LivingEntity attacker,
            Vec3 impactCenter,
            double radius,
            boolean heavy,
            ParticleOptions fissureColor,
            int fissureCount
    ) {
        if (level instanceof ServerLevel serverLevel) {
            directionalGroundSmash(serverLevel, attacker, impactCenter, radius, heavy, fissureColor, fissureCount);
        } else {
            playSound(level, impactCenter.x, impactCenter.y, impactCenter.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.15f, 0.85f);
        }
    }

    // =========================================================================
    // 4. DAGGERS CRIMSON FLURRY (REFINED SLIT & PRECISION PLUNGE)
    // =========================================================================

    /**
     * Fast linear crimson slit thrust for single dagger stabs (Step 0/1).
     * <p>
     * Per Section 2.6, 5.2, 6.6 & 8.3:
     * - Strictly 3 micro-particles (D = 0.38f) along the straight penetration axis.
     * - Emits with dynamic forward velocity (v_fwd = 0.52 m/s) + subtle inward converging drift.
     * - Eradicates spawnStationary(v=0). Zero floating dust points.
     * - Dual-color transition: 0xEF4444 -> 0x450A0A at 0.38f micro-scale.
     * - 1 micro CRIT spark thrown forward at penetration tip.
     * - Budget: exactly 4 particles (3 transition dust + 1 micro crit).
     */
    public static void crimsonStabThrust(Level level, LivingEntity attacker, double length, double lateralOffset) {
        Vec3 chestPos = attacker.position().add(0, attacker.getBbHeight() * 0.55, 0);
        Vec3 look = attacker.getLookAngle().normalize();
        Vec3 worldUp = (Math.abs(look.y) > 0.95) ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = look.cross(worldUp).normalize();

        Vec3 start = chestPos.add(right.scale(lateralOffset));
        // Subtle inward convergence towards player crosshair
        double inwardDrift = (lateralOffset > 0) ? -0.05 : 0.05;
        Vec3 vInit = look.scale(0.52).add(right.scale(inwardDrift));

        // 3 discrete micro-particles along the thrust vector with dynamic forward velocity
        for (int i = 1; i <= 3; i++) {
            double fraction = (double) i / 3.0;
            Vec3 pt = start.add(look.scale(length * fraction));
            spawnParticle(level, PALETTE_DAGGER_TRANSITION, pt.x, pt.y, pt.z, vInit.x, vInit.y, vInit.z);
        }

        // 1 micro crit at tip with forward puncture momentum
        Vec3 tip = start.add(look.scale(length));
        spawnParticle(level, ParticleTypes.CRIT, tip.x, tip.y, tip.z, look.x * 0.35, look.y * 0.35, look.z * 0.35);

        float pitch = (lateralOffset < 0) ? 1.65f : 1.75f;
        playSound(level, chestPos.x, chestPos.y, chestPos.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.75f, pitch);
    }

    /**
     * Daggers Step 2 Scissors Cross-Cut (X-Cut).
     * <p>
     * Per Section 6.6 & 8.3:
     * - Two compact diagonal cutting lines forming a sharp scissor X.
     * - 3 micro-particles per blade (total 6 particles) with inward scissor cutting velocity.
     * - 1 subtle CRIT spark at intersection apex.
     * - Budget: 7 particles.
     */
    public static void crimsonCrossCut(Level level, LivingEntity attacker, double radius) {
        Vec3 chestPos = attacker.position().add(0, attacker.getBbHeight() * 0.55, 0);
        Vec3 look = attacker.getLookAngle().normalize();
        Vec3 worldUp = (Math.abs(look.y) > 0.95) ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = look.cross(worldUp).normalize();
        Vec3 up = right.cross(look).normalize();

        Vec3 center = chestPos.add(look.scale(radius * 0.85));

        // Diagonal scissors: Blade 1 (top-left to bottom-right), Blade 2 (top-right to bottom-left)
        double span = 0.45;
        for (int i = 0; i < 3; i++) {
            double t = (i - 1.0) * (span / 1.0); // -span, 0, +span
            // Blade 1 (left dagger cutting down-right)
            Vec3 p1 = center.add(right.scale(t)).add(up.scale(-t * 0.8));
            Vec3 v1 = look.scale(0.35).add(right.scale(0.18)).add(up.scale(-0.15));
            spawnParticle(level, PALETTE_DAGGER_TRANSITION, p1.x, p1.y, p1.z, v1.x, v1.y, v1.z);

            // Blade 2 (right dagger cutting down-left)
            Vec3 p2 = center.add(right.scale(-t)).add(up.scale(-t * 0.8));
            Vec3 v2 = look.scale(0.35).add(right.scale(-0.18)).add(up.scale(-0.15));
            spawnParticle(level, PALETTE_DAGGER_RUBY_TRANSITION, p2.x, p2.y, p2.z, v2.x, v2.y, v2.z);
        }

        // 1 micro spark at intersection apex
        spawnParticle(level, ParticleTypes.CRIT, center.x, center.y, center.z, 0.0, 0.12, 0.0);
        playSound(level, chestPos.x, chestPos.y, chestPos.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.85f, 1.80f);
    }

    /**
     * Daggers Step 3 Dual Plunge Finisher.
     * <p>
     * Per Section 6.6 & 8.3:
     * - Twin crimson plunge points (2 per dagger, total 4 dust points) with downward forward penetration velocity.
     * - Exactly 3 micro cutting CRIT sparks splashing backward/upward from the impact tip.
     * - Budget: 7 particles.
     */
    public static void crimsonDualPlunge(Level level, LivingEntity attacker, double length) {
        Vec3 chestPos = attacker.position().add(0, attacker.getBbHeight() * 0.65, 0);
        Vec3 look = attacker.getLookAngle().normalize();
        Vec3 worldUp = (Math.abs(look.y) > 0.95) ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = look.cross(worldUp).normalize();

        Vec3 plungeDir = look.scale(length * 0.70).add(0, -0.45, 0);
        Vec3 plungeVel = plungeDir.normalize().scale(0.50);

        // Left and right dagger plunge lines (2 points each)
        double[] offsets = { -0.15, 0.15 };
        for (double off : offsets) {
            Vec3 start = chestPos.add(right.scale(off));
            Vec3 pt1 = start.add(plungeDir.scale(0.50));
            Vec3 pt2 = start.add(plungeDir);
            spawnParticle(level, PALETTE_DAGGER_TRANSITION, pt1.x, pt1.y, pt1.z, plungeVel.x, plungeVel.y, plungeVel.z);
            spawnParticle(level, PALETTE_DAGGER_TRANSITION, pt2.x, pt2.y, pt2.z, plungeVel.x, plungeVel.y, plungeVel.z);
        }

        // Exactly 3 micro cutting CRIT sparks splashing backward/upward from the dual impact tip
        Vec3 dualTip = chestPos.add(plungeDir);
        for (int i = 0; i < 3; i++) {
            double angle = (2.0 * Math.PI * i) / 3.0;
            double vx = -plungeVel.x * 0.25 + Math.cos(angle) * 0.08;
            double vy = 0.12 + Math.random() * 0.08;
            double vz = -plungeVel.z * 0.25 + Math.sin(angle) * 0.08;
            spawnParticle(level, ParticleTypes.CRIT, dualTip.x, dualTip.y, dualTip.z, vx, vy, vz);
        }

        playSound(level, chestPos.x, chestPos.y, chestPos.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.10f, 1.55f);
    }

    // =========================================================================
    // 5. SICKLES SILVER CRESCENT TWINS
    // =========================================================================

    /**
     * Silver Crescent Slash for Sickles (Step 0/1).
     * <p>
     * Per Section 2.6, 6.8 & 8.3:
     * - Moonlit pale silver (0xF1F5F9) fading to deep azure (0x0284C7) at 0.40f.
     * - Non-uniform sampling: 7 points, s_i = (i / 6.0)^0.85.
     * - Negative centrifugal kinematics: kc = -0.15 (pulls inward towards center!).
     * - Tangential speed: kt = 0.28.
     * - 1 subtle SWEEP_ATTACK tangential slice at midpoint.
     * - Budget: 8 particles.
     */
    public static void silverCrescentSlash(
            Level level,
            LivingEntity attacker,
            double radius,
            float arcDegrees,
            float tiltAngleDegrees,
            boolean reverse
    ) {
        Vec3 chestPos = attacker.position().add(0, attacker.getBbHeight() * 0.55, 0);
        Vec3 look = attacker.getLookAngle().normalize();
        Vec3 worldUp = (Math.abs(look.y) > 0.95) ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = look.cross(worldUp).normalize();
        Vec3 up = right.cross(look).normalize();

        double tiltRad = Math.toRadians(tiltAngleDegrees);
        Vec3 planeRight = right.scale(Math.cos(tiltRad)).add(up.scale(Math.sin(tiltRad))).normalize();

        double halfArcRad = Math.toRadians(arcDegrees * 0.5f);
        int points = 7;
        double omega = Math.toRadians(arcDegrees) / 3.0; // rad / tick

        for (int i = 0; i < points; i++) {
            // Non-uniform sampling: denser towards leading head
            double fraction = Math.pow((double) i / (points - 1), 0.85);
            double angle = reverse
                    ? (halfArcRad - fraction * 2.0 * halfArcRad)
                    : (-halfArcRad + fraction * 2.0 * halfArcRad);

            // Radial unit vector in swing plane
            Vec3 rUnit = look.scale(Math.cos(angle)).add(planeRight.scale(Math.sin(angle))).normalize();
            // Tangential unit vector
            double sweepDir = reverse ? -1.0 : 1.0;
            Vec3 tauUnit = look.scale(-Math.sin(angle) * sweepDir).add(planeRight.scale(Math.cos(angle) * sweepDir)).normalize();

            Vec3 pt = chestPos.add(rUnit.scale(radius));

            // Velocity: tangential + negative centrifugal (inward suction!) + forward drag
            double vt = 0.28 * (omega * radius);
            double vc = -0.15 * (omega * omega * radius); // NEGATIVE CENTRIFUGAL INWARD SUCTION!
            double vf = 0.05;
            Vec3 vInit = tauUnit.scale(vt).add(rUnit.scale(vc)).add(look.scale(vf));

            spawnParticle(level, PALETTE_SICKLE_TRANSITION, pt.x, pt.y, pt.z, vInit.x, vInit.y, vInit.z);

            // Midpoint subtle sweep slice
            if (i == points / 2) {
                spawnParticle(level, ParticleTypes.SWEEP_ATTACK, pt.x, pt.y, pt.z,
                        tauUnit.x * 0.25, tauUnit.y * 0.25, tauUnit.z * 0.25);
            }
        }

        playSound(level, chestPos.x, chestPos.y, chestPos.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.05f, reverse ? 1.45f : 1.35f);
    }

    public static void silverCrescentSlash(
            ServerLevel level,
            LivingEntity attacker,
            double radius,
            float arcDegrees,
            float tiltAngleDegrees,
            boolean reverse
    ) {
        silverCrescentSlash((Level) level, attacker, radius, arcDegrees, tiltAngleDegrees, reverse);
    }

    // =========================================================================
    // 6. GAUNTLETS KINETIC BOXING BURSTS & ASCENDING UP-FLOW
    // =========================================================================

    /**
     * High-speed kinetic piston punch compression shock (Step 0/1).
     * <p>
     * Per Section 2.6, 5.2, 6.7 & 8.3:
     * - Strictly no wide sweeping ribbon! Piston gust ahead of punch tip.
     * - 1 SMALL_GUST shot forward (v_fwd = 0.40 m/s).
     * - 3 micro-scale sonic white dust particles (D = 0.38f) orbiting the punch vector with inward spiral velocity.
     * - Budget: exactly 4 particles (1 SMALL_GUST + 3 transition dust).
     */
    public static void kineticPunchBurst(Level level, LivingEntity attacker, double reach) {
        Vec3 eyePos = attacker.getEyePosition();
        Vec3 look = attacker.getLookAngle().normalize();
        Vec3 punchTip = eyePos.add(look.scale(reach));

        Vec3 worldUp = (Math.abs(look.y) > 0.95) ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = look.cross(worldUp).normalize();
        Vec3 up = right.cross(look).normalize();

        // 1 forward SMALL_GUST impulse
        spawnParticle(level, ParticleTypes.SMALL_GUST, punchTip.x, punchTip.y, punchTip.z,
                look.x * 0.40, look.y * 0.40, look.z * 0.40);

        // 3 inward-spiraling sonic white micro-dust particles around the punch line
        double ringRadius = 0.12;
        for (int i = 0; i < 3; i++) {
            double angle = (2.0 * Math.PI * i) / 3.0;
            double d = reach * (0.65 + i * 0.15); // spaced along punch trajectory
            Vec3 radial = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            Vec3 tangent = right.scale(-Math.sin(angle)).add(up.scale(Math.cos(angle)));
            Vec3 pt = eyePos.add(look.scale(d)).add(radial.scale(ringRadius));

            // Velocity: forward stream + inward suction + slight swirl
            Vec3 v = look.scale(0.38).subtract(radial.scale(0.10)).add(tangent.scale(0.12));
            spawnParticle(level, PALETTE_PUNCH_TRANSITION, pt.x, pt.y, pt.z, v.x, v.y, v.z);
        }

        playSound(level, eyePos.x, eyePos.y, eyePos.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.05f, 1.45f);
    }

    // =========================================================================
    // 7. MASTER WEAPON SWING & COMBAT DISPATCHERS (10 WEAPONS SPECTRUM)
    // =========================================================================

    /**
     * Dedicated Air Swing / Aerodynamic Wind & Blade Ribbon Layer.
     * Triggered on weapon swing initiation regardless of whether a target is in crosshairs.
     * Strictly bounded to 5 ~ 12 particles, with ZERO hit sparks.
     * Suspended completely during Hitstop freeze.
     *
     * @param level      World level.
     * @param attacker   The attacking entity.
     * @param weaponType Archetype of the weapon.
     * @param stepIndex  Active combo step index.
     * @param step       Active combo step metadata.
     */
    public static void spawnWeaponAirSwingVfx(
            Level level,
            LivingEntity attacker,
            MCDWeaponType weaponType,
            int stepIndex,
            ComboStep step
    ) {
        // Section 6.1 Axiom 5: Pause particle generation during Hitstop to prevent blinding light cores
        if (attacker instanceof AttackComboAccessor accessor && accessor.ourDungeonCraft$getComboData().isHitstop()) {
            return;
        }

        switch (weaponType) {
            case SWORD -> {
                if (stepIndex == 0) {
                    physicalSlashRibbon(level, attacker, 2.6, 70.0f, -35.0f, false,
                            0xF8FAFC, 0x64748B, 0.45f, 0.55f, 0.22, 0.08, 0.05, false, true);
                } else if (stepIndex == 1) {
                    physicalSlashRibbon(level, attacker, 2.6, 75.0f, 35.0f, true,
                            0xF8FAFC, 0x64748B, 0.45f, 0.55f, 0.22, 0.08, 0.05, false, true);
                } else {
                    physicalSlashRibbon(level, attacker, 2.9, 85.0f, 85.0f, false,
                            0xFFFFFF, 0x93C5FD, 0.48f, 0.58f, 0.26, 0.10, 0.06, false, true);
                }
            }
            case CLAYMORE -> {
                if (stepIndex == 0) {
                    physicalSlashRibbon(level, attacker, 3.2, 105.0f, -25.0f, false,
                            0xF8FAFC, 0x334155, 0.50f, 0.60f, 0.28, 0.10, 0.04, false, true);
                } else if (stepIndex == 1) {
                    physicalSlashRibbon(level, attacker, 3.2, 115.0f, 25.0f, true,
                            0xF8FAFC, 0x334155, 0.50f, 0.60f, 0.28, 0.10, 0.04, false, true);
                } else {
                    physicalSlashRibbon(level, attacker, 3.5, 120.0f, 85.0f, false,
                            0xF8FAFC, 0x1E293B, 0.54f, 0.62f, 0.32, 0.06, 0.08, false, false);
                }
            }
            case DOUBLE_AXE -> {
                if (stepIndex == 0) {
                    whirlwindFinisher(level, attacker, 3.2);
                } else {
                    physicalSlashRibbon(level, attacker, 3.0, 100.0f, 75.0f, false,
                            0xE2E8F0, 0x1E293B, 0.48f, 0.58f, 0.30, 0.12, 0.04, false, true);
                }
            }
            case GREAT_HAMMER -> {
                if (stepIndex == 0) {
                    physicalSlashRibbon(level, attacker, 2.6, 65.0f, -20.0f, false,
                            PALETTE_HAMMER_GOLD, null, false, false, false);
                } else {
                    physicalSlashRibbon(level, attacker, 3.2, 80.0f, 85.0f, false,
                            PALETTE_HAMMER_AMBER, null, false, false, false);
                }
            }
            case MACE -> {
                if (stepIndex == 0) {
                    physicalSlashRibbon(level, attacker, 2.4, 60.0f, 55.0f, false,
                            PALETTE_MACE_CONCUSSIVE, null, false, false, false);
                } else if (stepIndex == 1) {
                    physicalSlashRibbon(level, attacker, 2.4, 65.0f, -40.0f, true,
                            PALETTE_MACE_CONCUSSIVE, null, false, false, false);
                } else {
                    physicalSlashRibbon(level, attacker, 2.6, 80.0f, 85.0f, false,
                            PALETTE_MACE_CONCUSSIVE, null, false, false, false);
                }
            }
            case GAUNTLETS -> {
                if (stepIndex == 0) {
                    kineticPunchBurst(level, attacker, 1.8);
                } else if (stepIndex == 1) {
                    kineticPunchBurst(level, attacker, 2.0);
                } else {
                    Vec3 uppercutPos = attacker.position().add(attacker.getLookAngle().scale(1.2));
                    risingUppercutVfx(level, uppercutPos);
                }
            }
            case DAGGERS -> {
                if (stepIndex == 0) {
                    crimsonStabThrust(level, attacker, 1.8, -0.15);
                } else if (stepIndex == 1) {
                    crimsonStabThrust(level, attacker, 1.8, 0.15);
                } else if (stepIndex == 2) {
                    crimsonCrossCut(level, attacker, 1.8);
                } else {
                    crimsonDualPlunge(level, attacker, 2.0);
                }
            }
            case SICKLES -> {
                if (stepIndex == 0) {
                    silverCrescentSlash(level, attacker, 2.3, 75.0f, -35.0f, false);
                } else if (stepIndex == 1) {
                    silverCrescentSlash(level, attacker, 2.3, 75.0f, 35.0f, true);
                } else if (stepIndex == 2) {
                    inwardVortexPull(level, attacker, 2.5, 90.0f);
                } else {
                    sickleReapFinisher360(level, attacker, 2.6);
                }
            }
            case SPEAR -> {
                if (stepIndex == 0) {
                    machConeThrust(level, attacker, 4.8, 14.0f, 1);
                } else if (stepIndex == 1) {
                    machConeThrust(level, attacker, 4.8, 14.0f, 1);
                } else {
                    machConeThrust(level, attacker, 5.5, 16.0f, 2);
                }
            }
            case GLAIVE -> {
                if (stepIndex == 0) {
                    physicalSlashRibbon(level, attacker, 4.2, 110.0f, -20.0f, false,
                            0xF8FAFC, 0x475569, 0.46f, 0.60f, 0.26, 0.09, 0.05, false, true);
                } else if (stepIndex == 1) {
                    physicalSlashRibbon(level, attacker, 4.2, 120.0f, 20.0f, true,
                            0xF8FAFC, 0x475569, 0.46f, 0.60f, 0.26, 0.09, 0.05, false, true);
                } else {
                    machConeThrust(level, attacker, 4.8, 16.0f, 2);
                }
            }
            default -> {
                whiteSlashArc(level, attacker, 2.6, 70.0f, stepIndex % 2 == 1);
            }
        }
    }

    /**
     * Backward-compatible swing trajectory dispatcher.
     * Delegates exclusively to {@link #spawnWeaponAirSwingVfx}.
     */
    public static void spawnWeaponSwingVfx(
            Level level,
            LivingEntity attacker,
            MCDWeaponType weaponType,
            int stepIndex,
            ComboStep step
    ) {
        spawnWeaponAirSwingVfx(level, attacker, weaponType, stepIndex, step);
    }

    /**
     * Spawns directional impact sparks bouncing off the victim's collision plane along the attack reflection vector.
     * Respects Hitstop freeze lock to ensure 0 duplicate particle emission during freeze ticks.
     */
    public static void spawnDirectionalHitSparks(
            Level level,
            LivingEntity attacker,
            Entity target,
            boolean isCrit
    ) {
        if (attacker instanceof AttackComboAccessor accessor && accessor.ourDungeonCraft$getComboData().isHitstopFreezeLocked(level.getGameTime())) {
            return;
        }

        double tx = target.getX();
        double ty = target.getY() + target.getBbHeight() * 0.55;
        double tz = target.getZ();

        Vec3 attackDir = target.position().subtract(attacker.position());
        if (attackDir.lengthSqr() > 1e-4) {
            attackDir = attackDir.normalize();
        } else {
            attackDir = attacker.getLookAngle().normalize();
        }

        // Directional reflection bounce: slight upward & outward deflection
        Vec3 bounce = attackDir.scale(0.18).add(0, 0.12, 0);
        int count = isCrit ? 5 : 3;
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        for (int i = 0; i < count; i++) {
            double spread = 0.14;
            double vx = bounce.x + (rand.nextDouble() - 0.5) * spread;
            double vy = bounce.y + (rand.nextDouble() - 0.5) * spread;
            double vz = bounce.z + (rand.nextDouble() - 0.5) * spread;
            spawnParticle(level, ParticleTypes.CRIT, tx, ty, tz, vx, vy, vz);
        }
    }

    /**
     * Dedicated On-Hit Impact & Flesh-Tear Layer.
     * Triggered exclusively when physical collision confirms hit on an entity.
     * Spawns directional contact sparks, flesh shearing / weapon impact characteristics,
     * and contextual finisher debris with Hitstop freeze lock protection.
     *
     * @param level         The level.
     * @param attacker      The attacking entity.
     * @param primaryTarget The hit entity (if any).
     * @param weaponType    Weapon archetype.
     * @param stepIndex     Combo step index.
     * @param step          Combo step metadata.
     * @param isCrit        Whether this strike triggered a critical hit.
     */
    public static void spawnWeaponOnHitVfx(
            Level level,
            LivingEntity attacker,
            @Nullable Entity primaryTarget,
            MCDWeaponType weaponType,
            int stepIndex,
            ComboStep step,
            boolean isCrit
    ) {
        // Section 6.1 Axiom 5 & Section 7.3: Complete particle shutdown during Hitstop freeze ticks
        if (attacker instanceof AttackComboAccessor accessor && accessor.ourDungeonCraft$getComboData().isHitstopFreezeLocked(level.getGameTime())) {
            return;
        }

        // 1. Primary target directional contact sparks
        if (primaryTarget != null && primaryTarget.isAlive()) {
            spawnDirectionalHitSparks(level, attacker, primaryTarget, isCrit);
        }

        // 2. Weapon-specific impact characteristics & contextual finisher debris
        if (level instanceof ServerLevel serverLevel) {
            Vec3 forwardOffset = attacker.getLookAngle().scale(1.8);
            Vec3 impactCenter = (primaryTarget != null) ? primaryTarget.position() : attacker.position().add(forwardOffset);

            boolean canTriggerFinisher = (attacker instanceof AttackComboAccessor accessor)
                    ? accessor.ourDungeonCraft$getComboData().tryTriggerFinisherVfx(serverLevel.getGameTime())
                    : true;

            switch (weaponType) {
                case GREAT_HAMMER -> {
                    if (step.isFinisher() && canTriggerFinisher) {
                        // 4 directional ground crack rays + 12 physical block debris + 4 low-profile ground plumes
                        directionalGroundSmash(serverLevel, attacker, impactCenter, 3.2, true, PALETTE_HAMMER_FISSURE, 4);
                    }
                }
                case CLAYMORE -> {
                    if (step.isFinisher() && canTriggerFinisher) {
                        // 8 contextual real block debris
                        directionalGroundSmash(serverLevel, attacker, impactCenter, 3.0, false, PALETTE_SWORD_WHITE, 3);
                    }
                }
                case MACE -> {
                    if (step.isFinisher() && canTriggerFinisher) {
                        maceSkullcrackerImpact(serverLevel, attacker, primaryTarget, impactCenter);
                    }
                }
                case SPEAR -> {
                    if (primaryTarget != null) {
                        spearPiercingImpact(serverLevel, attacker, primaryTarget);
                    }
                }
                case GAUNTLETS -> {
                    // Only dispatch uppercut if cleave physics hasn't already spawned it for GroundSmash shape
                    if (step.isFinisher() && !(step.cleaveShape() instanceof CleaveShape.GroundSmash) && canTriggerFinisher) {
                        risingUppercutVfx(serverLevel, impactCenter);
                    } else if (primaryTarget != null) {
                        spawnParticle(serverLevel, ParticleTypes.SMALL_GUST, primaryTarget.getX(), primaryTarget.getY(0.55), primaryTarget.getZ(), 0, 0.05, 0);
                    }
                }
                case DAGGERS -> {
                    if (primaryTarget != null) {
                        // Micro lacerating blood-crimson droplets
                        ThreadLocalRandom rand = ThreadLocalRandom.current();
                        for (int i = 0; i < (isCrit || step.isFinisher() ? 3 : 2); i++) {
                            double vx = (rand.nextDouble() - 0.5) * 0.12;
                            double vy = 0.05 + rand.nextDouble() * 0.10;
                            double vz = (rand.nextDouble() - 0.5) * 0.12;
                            spawnParticle(serverLevel, PALETTE_DAGGER_RUBY, primaryTarget.getX(), primaryTarget.getY(0.55), primaryTarget.getZ(), vx, vy, vz);
                        }
                    }
                }
                case SICKLES -> {
                    if (stepIndex == 2 && primaryTarget != null && step.knockbackMultiplier() >= 0) {
                        inwardVortexPull(serverLevel, primaryTarget.position(), attacker.position());
                    } else if (primaryTarget != null) {
                        spawnParticle(serverLevel, PALETTE_SICKLE_MOON, primaryTarget.getX(), primaryTarget.getY(0.55), primaryTarget.getZ(), 0, 0.08, 0);
                    }
                }
                default -> {
                    // Standard weapon hits handle directional hit sparks via spawnDirectionalHitSparks
                }
            }
        }
    }

    public static void spawnWeaponOnHitVfx(
            Level level,
            LivingEntity attacker,
            @Nullable Entity primaryTarget,
            MCDWeaponType weaponType,
            int stepIndex,
            ComboStep step
    ) {
        spawnWeaponOnHitVfx(level, attacker, primaryTarget, weaponType, stepIndex, step, false);
    }

    /**
     * Backward-compatible combat VFX entry point.
     * Delegates exclusively to {@link #spawnWeaponOnHitVfx}.
     */
    public static void spawnWeaponCombatVfx(
            Level level,
            LivingEntity attacker,
            @Nullable Entity primaryTarget,
            MCDWeaponType weaponType,
            int stepIndex,
            ComboStep step
    ) {
        spawnWeaponOnHitVfx(level, attacker, primaryTarget, weaponType, stepIndex, step, false);
    }

    /**
     * Mace Step 2 Finisher: Skullcracker concussive armor fracture impact.
     * <p>
     * Implements Section 6.5:
     * - Head-level downward heavy metal sparks: 4 CRIT particles spraying downward (v_y = -0.18 m/s) into the target,
     *   simulating concussive denting of enemy armor.
     * - Target feet: 2 contextual mini block debris pops.
     * - Acoustic: Anvil strike / heavy mace smash.
     */
    public static void maceSkullcrackerImpact(
            ServerLevel level,
            LivingEntity attacker,
            @Nullable Entity primaryTarget,
            Vec3 impactCenter
    ) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        double hitY = (primaryTarget != null)
                ? (primaryTarget.getY() + primaryTarget.getBbHeight() * 0.85)
                : (impactCenter.y + 1.2);

        // 1. Exactly 4 downward high-velocity metal sparks (CRIT) spraying into the crushed helmet
        for (int i = 0; i < 4; i++) {
            double vx = (rand.nextDouble() - 0.5) * 0.25;
            double vy = -(0.15 + rand.nextDouble() * 0.12);
            double vz = (rand.nextDouble() - 0.5) * 0.25;
            spawnParticle(level, ParticleTypes.CRIT, impactCenter.x, hitY, impactCenter.z, vx, vy, vz);
        }

        // 2. Exactly 2 micro contextual block debris popping at the victim's feet
        BlockPos feetPos = BlockPos.containing(impactCenter.x, impactCenter.y - 0.2, impactCenter.z);
        BlockState groundState = level.getBlockState(feetPos);
        if (groundState.isAir()) {
            groundState = level.getBlockState(feetPos.below());
        }
        if (!groundState.isAir()) {
            BlockParticleOption blockDebris = new BlockParticleOption(ParticleTypes.BLOCK, groundState);
            for (int k = 0; k < 2; k++) {
                double vx = (rand.nextDouble() - 0.5) * 0.12;
                double vy = 0.14 + rand.nextDouble() * 0.08;
                double vz = (rand.nextDouble() - 0.5) * 0.12;
                spawnParticle(level, blockDebris, impactCenter.x, impactCenter.y + 0.05, impactCenter.z, vx, vy, vz);
            }
        }

        // Concussive anvil impact resonance
        playSound(level, impactCenter.x, hitY, impactCenter.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.70f, 1.45f);
        playSound(level, impactCenter.x, impactCenter.y, impactCenter.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.10f, 0.90f);
    }

    /**
     * Spear On-Hit Impact: Crisp supersonic piercing needle puncture.
     * <p>
     * Implements Section 6.9 & Section 4.3:
     * - Spawns 3 directional CRIT sparks piercing forward through the victim's collision plane.
     * - Acoustic: Crisp armor puncture click and flesh tear.
     */
    public static void spearPiercingImpact(ServerLevel level, LivingEntity attacker, Entity primaryTarget) {
        Vec3 look = attacker.getLookAngle().normalize();
        Vec3 hitPos = primaryTarget.position().add(0, primaryTarget.getBbHeight() * 0.55, 0);

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        for (int i = 0; i < 3; i++) {
            double spread = 0.08;
            double vx = look.x * 0.28 + (rand.nextDouble() - 0.5) * spread;
            double vy = look.y * 0.28 + (rand.nextDouble() - 0.5) * spread;
            double vz = look.z * 0.28 + (rand.nextDouble() - 0.5) * spread;
            spawnParticle(level, ParticleTypes.CRIT, hitPos.x, hitPos.y, hitPos.z, vx, vy, vz);
        }

        playSound(level, hitPos.x, hitPos.y, hitPos.z, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.85f, 1.40f);
    }

    // =========================================================================
    // 8. BACKWARD COMPATIBLE COMBAT VFX METHODS
    // =========================================================================

    public static void groundSmashWave(ServerLevel level, Vec3 center, double radius) {
        Player nearest = level.getNearestPlayer(center.x, center.y, center.z, radius + 5.0, false);
        if (nearest != null) {
            directionalGroundSmash(level, nearest, center, radius, true, PALETTE_HAMMER_FISSURE, 4);
        } else {
            groundSmash(level, center, radius, true);
        }
    }

    public static void groundSmashWave(Level level, Vec3 center, double radius) {
        if (level instanceof ServerLevel serverLevel) {
            groundSmashWave(serverLevel, center, radius);
        } else {
            playSound(level, center.x, center.y, center.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.20f, 0.85f);
        }
    }

    public static void piercingThrustBeam(ServerLevel level, LivingEntity attacker, double length) {
        machConeThrust(level, attacker, length, 16.0f, 1);
    }

    public static void piercingThrustBeam(Level level, LivingEntity attacker, double length) {
        machConeThrust(level, attacker, length, 16.0f, 1);
    }

    public static void whiteSlashArc(ServerLevel level, LivingEntity attacker, double radius, float arcDegrees, boolean reverse) {
        physicalSlashRibbon(level, attacker, radius, arcDegrees, reverse ? 35.0f : -35.0f, reverse,
                0xF8FAFC, 0x64748B, 0.45f, 0.55f, 0.22, 0.08, 0.05, false, true);
    }

    public static void whiteSlashArc(Level level, LivingEntity attacker, double radius, float arcDegrees, boolean reverse) {
        physicalSlashRibbon(level, attacker, radius, arcDegrees, reverse ? 35.0f : -35.0f, reverse,
                0xF8FAFC, 0x64748B, 0.45f, 0.55f, 0.22, 0.08, 0.05, false, true);
    }

    public static void whirlwindFinisher(ServerLevel level, LivingEntity attacker, double radius) {
        Vec3 waistPos = attacker.position().add(0, attacker.getBbHeight() * 0.55, 0);
        whirlwindFinisher(level, waistPos, radius);
    }

    public static void whirlwindFinisher(ServerLevel level, Vec3 center, double radius) {
        // Exactly 14 points in single thin 360° circle with centrifugal flow (Specification v3.0 Section 6.3 & 8.2)
        int ringPoints = 14;
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        DustColorTransitionOptions axeTransition = new DustColorTransitionOptions(0xE2E8F0, 0x1E293B, 0.52f);

        for (int i = 0; i < ringPoints; i++) {
            double angle = (2.0 * Math.PI * i) / ringPoints;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double x = center.x + radius * cos;
            double z = center.z + radius * sin;

            // Tangential velocity (whirling around the circle: v = 0.38 * tau)
            double tvx = -sin * 0.38;
            double tvz = cos * 0.38;

            // Centrifugal outward separation (kc = 0.14)
            double cvx = cos * 0.08;
            double cvz = sin * 0.08;

            double jx = (rand.nextDouble() - 0.5) * 0.02;
            double jz = (rand.nextDouble() - 0.5) * 0.02;

            double vx = tvx + cvx + jx;
            double vy = (rand.nextDouble() - 0.5) * 0.02;
            double vz = tvz + cvz + jz;

            level.sendParticles(axeTransition, x, center.y, z, 0, vx, vy, vz, 1.0);

            // Exactly 2 tangential sweep flashes at opposite poles (i = 0 and i = 7)
            if (i == 0 || i == 7) {
                spawnParticle(level, ParticleTypes.SWEEP_ATTACK, x, center.y, z, tvx * 0.8, 0.0, tvz * 0.8);
            }
        }

        playSound(level, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.15f, 0.95f);
        playSound(level, center.x, center.y, center.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.95f, 1.30f);
    }

    public static void whirlwindFinisher(Level level, LivingEntity attacker, double radius) {
        if (level instanceof ServerLevel serverLevel) {
            whirlwindFinisher(serverLevel, attacker, radius);
        } else {
            Vec3 waistPos = attacker.position().add(0, attacker.getBbHeight() * 0.55, 0);
            whirlwindFinisher(level, waistPos, radius);
        }
    }

    public static void whirlwindFinisher(Level level, Vec3 center, double radius) {
        if (level instanceof ServerLevel serverLevel) {
            whirlwindFinisher(serverLevel, center, radius);
            return;
        }
        int ringPoints = 14;
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        DustColorTransitionOptions axeTransition = new DustColorTransitionOptions(0xE2E8F0, 0x1E293B, 0.52f);

        for (int i = 0; i < ringPoints; i++) {
            double angle = (2.0 * Math.PI * i) / ringPoints;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double x = center.x + radius * cos;
            double z = center.z + radius * sin;

            double tvx = -sin * 0.38;
            double tvz = cos * 0.38;
            double cvx = cos * 0.08;
            double cvz = sin * 0.08;

            double vx = tvx + cvx + (rand.nextDouble() - 0.5) * 0.02;
            double vy = (rand.nextDouble() - 0.5) * 0.02;
            double vz = tvz + cvz + (rand.nextDouble() - 0.5) * 0.02;

            level.addParticle(axeTransition, x, center.y, z, vx, vy, vz);

            if (i == 0 || i == 7) {
                level.addParticle(ParticleTypes.SWEEP_ATTACK, x, center.y, z, tvx * 0.8, 0.0, tvz * 0.8);
            }
        }
        playSound(level, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.15f, 0.95f);
        playSound(level, center.x, center.y, center.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.95f, 1.30f);
    }

    public static void whirlwindArc(ServerLevel level, LivingEntity attacker, double radius, float arcDegrees) {
        if (arcDegrees >= 360.0f) {
            whirlwindFinisher(level, attacker, radius);
            return;
        }
        physicalSlashRibbon(level, attacker, radius, arcDegrees, 15.0f, false,
                0xE2E8F0, 0x1E293B, 0.45f, 0.58f, 0.30, 0.14, 0.02, false, true);
    }

    public static void whirlwindArc(Level level, LivingEntity attacker, double radius, float arcDegrees) {
        if (level instanceof ServerLevel serverLevel) {
            whirlwindArc(serverLevel, attacker, radius, arcDegrees);
        } else {
            if (arcDegrees >= 360.0f) {
                whirlwindFinisher(level, attacker, radius);
            } else {
                physicalSlashRibbon(level, attacker, radius, arcDegrees, 15.0f, false,
                        0xE2E8F0, 0x1E293B, 0.45f, 0.58f, 0.30, 0.14, 0.02, false, true);
            }
        }
    }

    public static void sickleReapFinisher360(ServerLevel level, LivingEntity attacker, double radius) {
        Vec3 center = attacker.position().add(0, attacker.getBbHeight() * 0.55, 0);
        sickleReapFinisher360(level, center, radius);
    }

    /**
     * Sickles Step 3 360° Reap Finisher.
     * <p>
     * Per Section 6.8 & 8.3:
     * - Exactly 10 points along the full 360° moonlit circle.
     * - Dual-color transition: 0xF1F5F9 -> 0x0284C7 at 0.40f.
     * - Pure tangential cutting velocity (vt = 0.38 m/s) with slight outward clearance (vc = 0.05 m/s).
     * - Exactly 1 SWEEP_ATTACK tangential slice.
     * - Budget: 11 particles.
     */
    public static void sickleReapFinisher360(ServerLevel level, Vec3 center, double radius) {
        int points = 10;
        for (int i = 0; i < points; i++) {
            double angle = (2.0 * Math.PI * i) / points;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double x = center.x + radius * cos;
            double z = center.z + radius * sin;

            // Pure tangential velocity + slight radial clearance
            double tvx = -sin * 0.38;
            double tvz = cos * 0.38;
            double cvx = cos * 0.05;
            double cvz = sin * 0.05;

            spawnParticle(level, PALETTE_SICKLE_TRANSITION, x, center.y, z, tvx + cvx, 0.0, tvz + cvz);

            if (i == 0) {
                spawnParticle(level, ParticleTypes.SWEEP_ATTACK, x, center.y, z, tvx * 0.8, 0.0, tvz * 0.8);
            }
        }

        playSound(level, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.15f, 1.35f);
    }

    public static void sickleReapFinisher360(Level level, LivingEntity attacker, double radius) {
        if (level instanceof ServerLevel serverLevel) {
            sickleReapFinisher360(serverLevel, attacker, radius);
        } else {
            Vec3 center = attacker.position().add(0, attacker.getBbHeight() * 0.55, 0);
            sickleReapFinisher360(level, center, radius);
        }
    }

    public static void sickleReapFinisher360(Level level, Vec3 center, double radius) {
        if (level instanceof ServerLevel serverLevel) {
            sickleReapFinisher360(serverLevel, center, radius);
        } else {
            int points = 10;
            for (int i = 0; i < points; i++) {
                double angle = (2.0 * Math.PI * i) / points;
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                double x = center.x + radius * cos;
                double z = center.z + radius * sin;
                spawnParticle(level, PALETTE_SICKLE_TRANSITION, x, center.y, z, -sin * 0.38, 0.0, cos * 0.38);
            }
            playSound(level, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.15f, 1.35f);
        }
    }

    /**
     * Gauntlets Step 2 Rising Uppercut Ascending Helical Wind Tunnel.
     * <p>
     * Per Section 6.7 & 8.3:
     * - Ascending logarithmic spiral tunnel: H = 2.2m, r = 0.15m.
     * - Exactly 5 spiral points: x(t)=0.15*cos(pi*t), z(t)=0.15*sin(pi*t), y(t)=2.2*t.
     * - Kinematics: vertical vy = 0.28 m/s, rotational vrot = 0.12 m/s.
     * - Apex burst at t = 1.0: 1 SMALL_GUST (vy = 0.25 m/s) + 2 upward CRIT stars (vy = 0.30 m/s).
     * - Dual-color transition: 0xFFFFFF -> 0x94A3B8 at 0.38f micro-scale.
     * - Budget: exactly 8 particles (5 transition dust + 1 SMALL_GUST + 2 CRIT).
     */
    public static void risingUppercutVfx(ServerLevel level, Vec3 pos) {
        double height = 2.2;
        int points = 5;
        for (int i = 0; i < points; i++) {
            double t = (double) i / (points - 1);
            double y = pos.y + t * height;
            double angle = t * Math.PI;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            double px = pos.x + 0.15 * cos;
            double pz = pos.z + 0.15 * sin;

            // Rotational + vertical velocity
            double vx = -sin * 0.12;
            double vy = 0.28;
            double vz = cos * 0.12;

            spawnParticle(level, PALETTE_PUNCH_TRANSITION, px, y, pz, vx, vy, vz);
        }

        // Apex burst: 1 vertical SMALL_GUST + 2 upward CRIT stars
        spawnParticle(level, ParticleTypes.SMALL_GUST, pos.x, pos.y + height, pos.z, 0.0, 0.25, 0.0);
        spawnParticle(level, ParticleTypes.CRIT, pos.x - 0.08, pos.y + height, pos.z, 0.04, 0.30, 0.0);
        spawnParticle(level, ParticleTypes.CRIT, pos.x + 0.08, pos.y + height, pos.z, -0.04, 0.30, 0.0);

        playSound(level, pos.x, pos.y, pos.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.15f, 1.50f);
    }

    public static void risingUppercutVfx(Level level, Vec3 pos) {
        if (level instanceof ServerLevel serverLevel) {
            risingUppercutVfx(serverLevel, pos);
        } else {
            for (int i = 0; i < 5; i++) {
                double t = (double) i / 4.0;
                double y = pos.y + t * 2.2;
                double angle = t * Math.PI;
                spawnParticle(level, PALETTE_PUNCH_TRANSITION, pos.x + 0.15 * Math.cos(angle), y, pos.z + 0.15 * Math.sin(angle),
                        -Math.sin(angle) * 0.12, 0.28, Math.cos(angle) * 0.12);
            }
            spawnParticle(level, ParticleTypes.SMALL_GUST, pos.x, pos.y + 2.2, pos.z, 0.0, 0.25, 0.0);
            playSound(level, pos.x, pos.y, pos.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.15f, 1.45f);
        }
    }

    public static void hookPullVfx(Level level, Vec3 from, Vec3 to) {
        inwardVortexPull(level, from, to);
    }

    public static void inwardVortexLines(Level level, Vec3 from, Vec3 to) {
        inwardVortexPull(level, from, to);
    }

    /**
     * Sickles Inward Vortex Pull - Point-to-Point (Step 2 pull towards player).
     * <p>
     * Per Section 6.8 & 8.3:
     * - 4 streamline particles converging inward towards 'to' at vinward = 0.45 m/s.
     * - Zero stationary points (v != 0).
     * - Dual-color transition: 0xF1F5F9 -> 0x0284C7 at 0.40f.
     * - Budget: 4 ~ 6 particles.
     */
    public static void inwardVortexPull(Level level, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        double dist = dir.length();
        if (dist < 0.01) return;
        Vec3 unit = dir.scale(1.0 / dist);
        Vec3 vInward = unit.scale(0.45);

        int steps = Math.min(6, Math.max(4, (int) (dist * 2)));
        for (int i = 0; i < steps; i++) {
            double t = (double) i / steps;
            Vec3 pt = from.lerp(to, t).add(0, 0.25, 0);
            spawnParticle(level, PALETTE_SICKLE_TRANSITION, pt.x, pt.y, pt.z, vInward.x, vInward.y, vInward.z);
        }

        playSound(level, from.x, from.y, from.z, SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 0.85f, 1.45f);
    }

    /**
     * Sickles Step 2 Inward Vortex Pull - Sector to Chest.
     * <p>
     * Per Section 6.8 & 8.3:
     * - 4 streamline rays (8 total particles) converging toward attacker's chest.
     * - Negative centrifugal / suction kinematics: vinward = 0.45 m/s.
     * - Budget: exactly 8 particles.
     */
    public static void inwardVortexPull(ServerLevel level, LivingEntity attacker, double radius, float arcDegrees) {
        Vec3 chestPos = attacker.position().add(0, attacker.getBbHeight() * 0.55, 0);
        float facingYaw = attacker.getYRot();
        double centerAngle = Math.toRadians(-facingYaw - 90.0f);
        double halfArcRad = Math.toRadians(arcDegrees * 0.5f);

        int rays = 4;
        for (int r = 0; r < rays; r++) {
            double frac = (rays == 1) ? 0.5 : (double) r / (rays - 1);
            double angle = (centerAngle - halfArcRad) + frac * (2.0 * halfArcRad);

            // 2 particles along each suction streamline
            for (int p = 1; p <= 2; p++) {
                double distFrac = 0.45 + p * 0.25; // 0.70, 0.95
                double currentR = radius * distFrac;
                double sx = chestPos.x + currentR * Math.cos(angle);
                double sz = chestPos.z + currentR * Math.sin(angle);
                double sy = chestPos.y;

                Vec3 pt = new Vec3(sx, sy, sz);
                Vec3 toChest = chestPos.subtract(pt).normalize();
                Vec3 vInward = toChest.scale(0.45);

                spawnParticle(level, PALETTE_SICKLE_TRANSITION, sx, sy, sz, vInward.x, vInward.y, vInward.z);
            }
        }

        playSound(level, chestPos.x, chestPos.y, chestPos.z, SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 0.95f, 1.35f);
    }

    public static void inwardVortexPull(Level level, LivingEntity attacker, double radius, float arcDegrees) {
        if (level instanceof ServerLevel serverLevel) {
            inwardVortexPull(serverLevel, attacker, radius, arcDegrees);
        }
    }

    // =========================================================================
    // 9. ELEMENTAL / UNIQUE WEAPON VISUAL EFFECTS (NOISE-REDUCED)
    // =========================================================================

    /**
     * Spawns the Hammer of Gravity vortex: an arcane gravitational singularity with restrained particle suction.
     */
    public static void gravityPull(Level level, Vec3 center, double pullRadius) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        DustParticleOptions voidDust = new DustParticleOptions(0x2A0845, 0.8f);
        spawnSpread(level, voidDust, center.x, center.y + 0.3, center.z, 6, 0.2, 0.2, 0.2, 0.02);
        spawnSpread(level, ParticleTypes.SCULK_CHARGE_POP, center.x, center.y + 0.4, center.z, 3, 0.1, 0.1, 0.1, 0.04);

        int shells = 2;
        int particlesPerShell = 6;
        DustColorTransitionOptions pullDust = new DustColorTransitionOptions(0x9400D3, 0x1A0033, 0.65f);

        for (int s = 1; s <= shells; s++) {
            double r = (pullRadius * s) / shells;
            for (int i = 0; i < particlesPerShell; i++) {
                double angle = (2.0 * Math.PI * i) / particlesPerShell + (s * 0.5);
                double heightOffset = (rand.nextDouble() - 0.5) * 0.6;
                double px = center.x + r * Math.cos(angle);
                double py = center.y + heightOffset;
                double pz = center.z + r * Math.sin(angle);

                Vec3 dir = center.subtract(px, py, pz).normalize();
                double pullSpeed = 0.35 + (1.0 - (r / pullRadius)) * 0.25;

                spawnParticle(level, ParticleTypes.REVERSE_PORTAL, px, py, pz, dir.x * pullSpeed, dir.y * pullSpeed, dir.z * pullSpeed);
                spawnParticle(level, pullDust, px, py, pz, dir.x * pullSpeed * 0.5, dir.y * pullSpeed * 0.5, dir.z * pullSpeed * 0.5);
            }
        }

        playSound(level, center.x, center.y, center.z, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.6f, 1.8f);
        playSound(level, center.x, center.y, center.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.9f, 0.55f);
    }

    /**
     * Spawns the Stormlander lightning blast: clean procedural lightning arc with restrained sparks.
     */
    public static void lightningBlast(Level level, Vec3 strikePos, @Nullable Vec3 secondaryTargetPos) {
        spawnStationary(level, ColorParticleOption.create(ParticleTypes.FLASH, 0.8f, 0.8f, 0.8f), strikePos.x, strikePos.y + 0.5, strikePos.z);
        spawnSpread(level, ParticleTypes.ELECTRIC_SPARK, strikePos.x, strikePos.y + 0.5, strikePos.z, 8, 0.3, 0.3, 0.3, 0.25);

        Vec3 skyOrigin = strikePos.add(0, 3.5, 0);
        spawnProceduralLightningArc(level, skyOrigin, strikePos, 6, 0.30);

        if (secondaryTargetPos != null) {
            spawnProceduralLightningArc(level, strikePos.add(0, 0.5, 0), secondaryTargetPos.add(0, 0.5, 0), 6, 0.25);
            spawnSpread(level, ParticleTypes.ELECTRIC_SPARK, secondaryTargetPos.x, secondaryTargetPos.y + 0.5, secondaryTargetPos.z, 6, 0.2, 0.2, 0.2, 0.15);
        }

        playSound(level, strikePos.x, strikePos.y, strikePos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.1f, 1.4f);
        playSound(level, strikePos.x, strikePos.y, strikePos.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.9f, 1.1f);
    }

    private static void spawnProceduralLightningArc(Level level, Vec3 start, Vec3 end, int segments, double jitterAmount) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        Vec3 current = start;
        DustParticleOptions electricDust = new DustParticleOptions(0x60A5FA, 0.60f);

        for (int i = 1; i <= segments; i++) {
            double fraction = (double) i / segments;
            Vec3 target = start.lerp(end, fraction);

            if (i < segments) {
                target = target.add(
                        (rand.nextDouble() - 0.5) * 1.5 * jitterAmount,
                        (rand.nextDouble() - 0.5) * 1.5 * jitterAmount,
                        (rand.nextDouble() - 0.5) * 1.5 * jitterAmount
                );
            }

            int subSteps = 2;
            for (int s = 0; s < subSteps; s++) {
                Vec3 p = current.lerp(target, (double) s / subSteps);
                spawnStationary(level, ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z);
                spawnStationary(level, electricDust, p.x, p.y, p.z);
            }
            current = target;
        }
    }

    public static void lifeSiphonStream(Level level, Vec3 victimPos, Vec3 playerChest, float healthRestored) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        DustParticleOptions darkBlood = new DustParticleOptions(0x8B0000, 0.65f);
        DustParticleOptions crimsonLifesteal = new DustParticleOptions(0xDC143C, 0.55f);

        spawnSpread(level, ParticleTypes.DAMAGE_INDICATOR, victimPos.x, victimPos.y + 0.8, victimPos.z, 2, 0.15, 0.20, 0.15, 0.05);
        spawnSpread(level, darkBlood, victimPos.x, victimPos.y + 0.8, victimPos.z, 4, 0.2, 0.2, 0.2, 0.04);

        for (int i = 0; i < 2; i++) {
            double ox = (rand.nextDouble() - 0.5) * 0.3;
            double oy = (rand.nextDouble() - 0.5) * 0.3;
            double oz = (rand.nextDouble() - 0.5) * 0.3;
            TrailParticleOption trailOption = new TrailParticleOption(playerChest, 0xDC143C, 10);
            spawnStationary(level, trailOption, victimPos.x + ox, victimPos.y + 0.8 + oy, victimPos.z + oz);
        }

        Vec3 midPoint = victimPos.lerp(playerChest, 0.5).add(0, 0.8, 0);
        int curveSteps = 8;
        for (int i = 0; i <= curveSteps; i++) {
            double t = (double) i / curveSteps;
            double oneMinusT = 1.0 - t;
            double px = oneMinusT * oneMinusT * victimPos.x + 2 * oneMinusT * t * midPoint.x + t * t * playerChest.x;
            double py = oneMinusT * oneMinusT * (victimPos.y + 0.8) + 2 * oneMinusT * t * midPoint.y + t * t * playerChest.y;
            double pz = oneMinusT * oneMinusT * victimPos.z + 2 * oneMinusT * t * midPoint.z + t * t * playerChest.z;

            spawnStationary(level, crimsonLifesteal, px, py, pz);
        }

        int heartCount = Math.max(1, Math.min(3, Math.round(healthRestored / 2.0f)));
        spawnSpread(level, ParticleTypes.HEART, playerChest.x, playerChest.y, playerChest.z, heartCount, 0.25, 0.25, 0.25, 0.02);

        playSound(level, victimPos.x, victimPos.y, victimPos.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.8f, 0.7f);
        playSound(level, playerChest.x, playerChest.y, playerChest.z, SoundEvents.WITCH_DRINK, SoundSource.PLAYERS, 0.6f, 1.3f);
    }

    public static void sunsGraceSanctuary(Level level, Vec3 center, double radius) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        double groundY = center.y + 0.05;

        int ringPoints = 12;
        DustParticleOptions goldDust = new DustParticleOptions(0xFFD700, 0.65f);
        DustParticleOptions brightSunDust = new DustParticleOptions(0xFFF8DC, 0.55f);

        for (int i = 0; i < ringPoints; i++) {
            double angle = (2.0 * Math.PI * i) / ringPoints;
            double rx = radius * Math.cos(angle);
            double rz = radius * Math.sin(angle);
            spawnStationary(level, goldDust, center.x + rx, groundY, center.z + rz);

            if (i % 3 == 0) {
                spawnStationary(level, brightSunDust, center.x + rx * 0.7, groundY, center.z + rz * 0.7);
            }
        }

        int pillars = 3;
        for (int p = 0; p < pillars; p++) {
            double angle = (2.0 * Math.PI * p) / pillars;
            double dist = radius * 0.5;
            double px = center.x + dist * Math.cos(angle);
            double pz = center.z + dist * Math.sin(angle);

            for (double h = 0; h <= 2.0; h += 0.5) {
                spawnStationary(level, goldDust, px, groundY + h, pz);
            }
        }

        spawnStationary(level, ColorParticleOption.create(ParticleTypes.FLASH, 0.8f, 0.8f, 0.7f), center.x, groundY + 1.0, center.z);
        spawnSpread(level, ParticleTypes.GLOW, center.x, groundY + 0.6, center.z, 6, radius * 0.3, 0.3, radius * 0.3, 0.03);

        playSound(level, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.4f);
        playSound(level, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    public static void frostNovaShatter(Level level, Vec3 center, double blastRadius) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        DustParticleOptions iceDust = new DustParticleOptions(0x87CEEB, 0.65f);
        DustColorTransitionOptions frostTransition = new DustColorTransitionOptions(0x00FFFF, 0x1E90FF, 0.60f);

        int helixSteps = 8;
        double helixHeight = 1.8;
        for (int i = 0; i <= helixSteps; i++) {
            double progress = (double) i / helixSteps;
            double angle = progress * (2.0 * Math.PI);
            double r = (1.0 - progress * 0.4) * (blastRadius * 0.35);
            double hx = center.x + r * Math.cos(angle);
            double hy = center.y + progress * helixHeight;
            double hz = center.z + r * Math.sin(angle);

            spawnStationary(level, frostTransition, hx, hy, hz);
            if (i % 2 == 0) {
                spawnStationary(level, ParticleTypes.SNOWFLAKE, hx, hy, hz);
            }
        }

        int blastRays = 6;
        for (int i = 0; i < blastRays; i++) {
            double angle = (2.0 * Math.PI * i) / blastRays;
            double speed = 0.25 + rand.nextDouble() * 0.15;
            double vx = Math.cos(angle) * speed;
            double vz = Math.sin(angle) * speed;

            double sx = center.x + Math.cos(angle) * 0.3;
            double sz = center.z + Math.sin(angle) * 0.3;

            spawnParticle(level, ParticleTypes.SNOWFLAKE, sx, center.y + 0.3, sz, vx, 0.05, vz);
            spawnParticle(level, iceDust, sx, center.y + 0.3, sz, vx * 0.5, 0.03, vz * 0.5);
        }

        playSound(level, center.x, center.y, center.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 1.4f);
        playSound(level, center.x, center.y, center.z, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    public static void cursedExplosion(Level level, Vec3 center, double blastRadius) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        DustParticleOptions emeraldCurse = new DustParticleOptions(0x00FF7F, 0.65f);
        DustColorTransitionOptions cursedWave = new DustColorTransitionOptions(0x00FF7F, 0x002B11, 0.60f);

        int ringPoints = 8;
        for (int i = 0; i < ringPoints; i++) {
            double angle = (2.0 * Math.PI * i) / ringPoints;
            double rx = blastRadius * Math.cos(angle);
            double rz = blastRadius * Math.sin(angle);
            double vx = Math.cos(angle) * 0.25;
            double vz = Math.sin(angle) * 0.25;

            spawnParticle(level, cursedWave, center.x + rx * 0.4, center.y + 0.2, center.z + rz * 0.4, vx, 0.02, vz);
            spawnStationary(level, emeraldCurse, center.x + rx * 0.7, center.y + 0.1, center.z + rz * 0.7);
        }

        spawnSpread(level, ParticleTypes.SCULK_SOUL, center.x, center.y + 0.4, center.z, 4, 0.3, 0.3, 0.3, 0.05);
        spawnSpread(level, ParticleTypes.SCULK_CHARGE_POP, center.x, center.y + 0.4, center.z, 3, 0.2, 0.2, 0.2, 0.04);
        spawnSpread(level, ParticleTypes.WITCH, center.x, center.y + 0.4, center.z, 4, 0.3, 0.3, 0.3, 0.03);

        playSound(level, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9f, 0.85f);
        playSound(level, center.x, center.y, center.z, SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    public static void cursedExplosion(Level level, Entity victim, double blastRadius) {
        cursedExplosion(level, victim.position().add(0, victim.getBbHeight() * 0.5, 0), blastRadius);
    }

    public static void groundSmash(Level level, Vec3 center, double radius, boolean heavy) {
        double groundY = center.y + 0.05;

        int plumePoints = heavy ? 6 : 4;
        for (int i = 0; i < plumePoints; i++) {
            double angle = (2.0 * Math.PI * i) / plumePoints;
            double px = center.x + (radius * 0.65) * Math.cos(angle);
            double pz = center.z + (radius * 0.65) * Math.sin(angle);
            double outwardSpeed = heavy ? 0.20 : 0.15;
            double vx = Math.cos(angle) * outwardSpeed;
            double vz = Math.sin(angle) * outwardSpeed;
            spawnParticle(level, ParticleTypes.DUST_PLUME, px, groundY, pz, vx, 0.04, vz);
        }

        int critCount = heavy ? 6 : 4;
        spawnSpread(level, ParticleTypes.CRIT, center.x, groundY + 0.2, center.z,
                critCount, radius * 0.25, 0.15, radius * 0.25, heavy ? 0.25 : 0.15);

        DustParticleOptions whiteSlateDust = new DustParticleOptions(0xE8ECEF, heavy ? 0.65f : 0.50f);
        int ringPoints = heavy ? 8 : 6;
        for (int i = 0; i < ringPoints; i++) {
            double angle = (2.0 * Math.PI * i) / ringPoints;
            double rx = radius * Math.cos(angle);
            double rz = radius * Math.sin(angle);
            double rvx = Math.cos(angle) * 0.04;
            double rvz = Math.sin(angle) * 0.04;
            spawnParticle(level, whiteSlateDust, center.x + rx, groundY, center.z + rz, rvx, 0.01, rvz);
        }

        SoundEvent smashSound = heavy ? SoundEvents.MACE_SMASH_GROUND_HEAVY : SoundEvents.MACE_SMASH_GROUND;
        playSound(level, center.x, center.y, center.z, smashSound, SoundSource.PLAYERS, 1.15f, heavy ? 0.65f : 0.90f);
    }

    // =========================================================================
    // 10. PRIMITIVE PARTICLE & SOUND DISPATCH HELPERS (SERVER & CLIENT)
    // =========================================================================

    public static void spawnParticle(Level level, ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(particle, x, y, z, 0, vx, vy, vz, 1.0);
        } else {
            level.addParticle(particle, x, y, z, vx, vy, vz);
        }
    }

    public static void spawnStationary(Level level, ParticleOptions particle, double x, double y, double z) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(particle, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        } else {
            level.addParticle(particle, x, y, z, 0.0, 0.0, 0.0);
        }
    }

    public static void spawnSpread(Level level, ParticleOptions particle, double x, double y, double z,
                                   int count, double spreadX, double spreadY, double spreadZ, double speed) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(particle, x, y, z, count, spreadX, spreadY, spreadZ, speed);
        } else {
            ThreadLocalRandom rand = ThreadLocalRandom.current();
            for (int i = 0; i < count; i++) {
                double px = x + (rand.nextDouble() - 0.5) * 2.0 * spreadX;
                double py = y + (rand.nextDouble() - 0.5) * 2.0 * spreadY;
                double pz = z + (rand.nextDouble() - 0.5) * 2.0 * spreadZ;
                double vx = (rand.nextDouble() - 0.5) * speed;
                double vy = (rand.nextDouble() - 0.5) * speed;
                double vz = (rand.nextDouble() - 0.5) * speed;
                level.addParticle(particle, px, py, pz, vx, vy, vz);
            }
        }
    }

    public static void playSound(Level level, double x, double y, double z, SoundEvent sound, SoundSource source, float volume, float pitch) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, x, y, z, sound, source, volume, pitch);
        } else {
            level.playLocalSound(x, y, z, sound, source, volume, pitch, false);
        }
    }
}
