package com.github.ztymilk.soul;

import com.github.ztymilk.entity.SoulOrbEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Handles soul drop calculation and entity spawning upon mob defeat.
 * <p>
 * Rule: Mobs drop souls based on their MaxHP rounded to the nearest 10 (1 soul per 10 MaxHP).
 * Low health / finishing blows do not penalize soul drop count (uses MaxHP, not current HP).
 */
public final class SoulDropHandler {

    private SoulDropHandler() {
    }

    /**
     * Calculates soul drop amount based on mob Max HP.
     * Formula: Math.round(maxHealth / 10.0f).
     *
     * @param maxHealth Maximum health of the defeated entity.
     * @return Number of souls to drop.
     */
    public static int calculateSoulDrop(float maxHealth) {
        if (maxHealth <= 0.0f) {
            return 0;
        }
        return Math.round(maxHealth / 10.0f);
    }

    /**
     * Handles death of a LivingEntity. If defeated by a player, drops floating SoulOrbEntity instances.
     *
     * @param victim       The dying living entity.
     * @param damageSource The lethal damage source.
     */
    public static void onLivingEntityDeath(LivingEntity victim, @Nullable DamageSource damageSource) {
        if (victim.level().isClientSide()) {
            return;
        }

        // Players and armor stands do not drop souls
        if (victim instanceof Player || victim instanceof ArmorStand) {
            return;
        }

        Player killer = resolvePlayerKiller(victim, damageSource);
        if (killer == null) {
            return;
        }

        int soulCount = calculateSoulDrop(victim.getMaxHealth());
        if (soulCount <= 0) {
            return;
        }

        if (victim.level() instanceof ServerLevel serverLevel) {
            Vec3 spawnPos = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
            spawnSoulOrbs(serverLevel, spawnPos, soulCount, killer);
        }
    }

    /**
     * Spawns floating soul orbs with random upward velocity.
     *
     * @param level     Server level.
     * @param pos       Spawn position.
     * @param soulCount Total souls to drop.
     * @param killer    Target player to home towards.
     */
    public static void spawnSoulOrbs(ServerLevel level, Vec3 pos, int soulCount, @Nullable Player killer) {
        if (soulCount <= 0) {
            return;
        }

        RandomSource random = level.getRandom();
        // Spawn up to 8 individual visual orbs to avoid performance degradation on giant bosses
        int orbsToSpawn = Math.min(soulCount, 8);
        int baseValue = soulCount / orbsToSpawn;
        int remainder = soulCount % orbsToSpawn;

        for (int i = 0; i < orbsToSpawn; i++) {
            int orbValue = baseValue + (i < remainder ? 1 : 0);
            SoulOrbEntity orb = new SoulOrbEntity(level, pos.x, pos.y, pos.z, orbValue);
            if (killer != null) {
                orb.setTargetPlayer(killer);
            }

            // Random upward velocity: small to large floating ascension
            double angle = random.nextDouble() * Math.PI * 2.0;
            double horizSpeed = 0.03 + random.nextDouble() * 0.05;
            double vx = Math.cos(angle) * horizSpeed;
            double vy = 0.08 + random.nextDouble() * 0.08;
            double vz = Math.sin(angle) * horizSpeed;
            orb.setDeltaMovement(vx, vy, vz);

            level.addFreshEntity(orb);
        }
    }

    /**
     * Resolves the player who is credited with the kill.
     */
    public static @Nullable Player resolvePlayerKiller(LivingEntity victim, @Nullable DamageSource damageSource) {
        if (damageSource != null) {
            if (damageSource.getEntity() instanceof Player player) {
                return player;
            }
            if (damageSource.getDirectEntity() instanceof Player player) {
                return player;
            }
        }

        LivingEntity killCredit = victim.getKillCredit();
        if (killCredit instanceof Player player) {
            return player;
        }

        LivingEntity lastHurtBy = victim.getLastHurtByMob();
        if (lastHurtBy instanceof Player player) {
            // Player contributed damage within the last 100 ticks (5 seconds)
            if (victim.tickCount - victim.getLastHurtByMobTimestamp() <= 100) {
                return player;
            }
        }

        return null;
    }
}
