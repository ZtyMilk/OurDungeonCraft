package com.github.ztymilk.combat;

import com.github.ztymilk.sound.WeaponSoundHelper;
import com.github.ztymilk.sound.WeaponSoundHelper.MCDWeaponType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks player weapon attack combo progression, cadence, and timing.
 * Implements weapon type resolution, combo window resets, and hit indexing.
 */
public class AttackComboData {

    /**
     * Maximum ticks allowed between attack swings before combo sequence resets to step 0.
     * Deprecated in favor of {@link #getComboResetTicks(MCDWeaponType)}.
     */
    public static final int COMBO_RESET_TICKS = 20;

    /**
     * Dynamic combo reset window based on weapon cadence and recovery duration.
     * Light fast weapons reset snappily (12-15 ticks), heavy colossal weapons allow deliberate timing (30-40 ticks).
     */
    public static int getComboResetTicks(@NotNull MCDWeaponType type) {
        return switch (type) {
            case DAGGERS -> 12;
            case GAUNTLETS -> 14;
            case SICKLES -> 15;
            case SWORD -> 20;
            case MACE -> 22;
            case SPEAR, SPEAR_GLAIVE -> 24;
            case GLAIVE -> 26;
            case DOUBLE_AXE, AXE -> 30;
            case CLAYMORE -> 35;
            case GREAT_HAMMER -> 40;
            default -> 20;
        };
    }

    private int comboStep = 0;
    private long lastAttackGameTime = -100L;
    private @NotNull MCDWeaponType currentWeaponType = MCDWeaponType.SWORD;
    private boolean attacking = false;

    // Hitstop / Impact freeze state
    private int hitstopTicks = 0;
    private int totalHitstopTicks = 0;
    private float hitstopShake = 0.0f;

    // VFX debouncing state
    private long lastVfxGameTime = -100L;
    private long lastHitVfxGameTime = -100L;
    private long lastFinisherVfxGameTime = -100L;

    public boolean tryTriggerSwingVfx(long currentGameTime) {
        if (this.isHitstop() || this.lastVfxGameTime == currentGameTime) {
            return false;
        }
        this.lastVfxGameTime = currentGameTime;
        return true;
    }

    public void markVfxTriggered(long currentGameTime) {
        this.lastVfxGameTime = currentGameTime;
    }

    /**
     * Checks whether Hitstop freeze lock is actively preventing new particle emissions.
     * True during subsequent freeze ticks after the impact onset tick.
     */
    public boolean isHitstopFreezeLocked(long currentGameTime) {
        return this.isHitstop() && currentGameTime > this.lastAttackGameTime;
    }

    public boolean tryTriggerHitVfx(long currentGameTime) {
        if (this.isHitstopFreezeLocked(currentGameTime)) {
            return false;
        }
        this.lastHitVfxGameTime = currentGameTime;
        return true;
    }

    public boolean tryTriggerFinisherVfx(long currentGameTime) {
        if (this.isHitstopFreezeLocked(currentGameTime) || this.lastFinisherVfxGameTime == currentGameTime) {
            return false;
        }
        this.lastFinisherVfxGameTime = currentGameTime;
        return true;
    }

    public int getComboStep() {
        return this.comboStep;
    }

    public void setComboStep(int step) {
        this.comboStep = step;
    }

    public @NotNull MCDWeaponType getWeaponType() {
        return this.currentWeaponType;
    }

    public void setWeaponType(@NotNull MCDWeaponType type) {
        this.currentWeaponType = type;
    }

    public boolean isAttacking() {
        return this.attacking;
    }

    public void setAttacking(boolean attacking) {
        this.attacking = attacking;
    }

    public boolean isHitstop() {
        return this.hitstopTicks > 0;
    }

    public int getHitstopTicks() {
        return this.hitstopTicks;
    }

    public int getTotalHitstopTicks() {
        return this.totalHitstopTicks;
    }

    public float getHitstopShake() {
        return this.hitstopShake;
    }

    public float getHitstopProgress() {
        return this.totalHitstopTicks > 0 ? (float) this.hitstopTicks / (float) this.totalHitstopTicks : 0.0f;
    }

    public void triggerHitstop(int ticks, float shakeIntensity) {
        this.hitstopTicks = Math.max(this.hitstopTicks, ticks);
        this.totalHitstopTicks = Math.max(this.totalHitstopTicks, ticks);
        this.hitstopShake = Math.max(this.hitstopShake, shakeIntensity);
    }

    public void clearHitstop() {
        this.hitstopTicks = 0;
        this.totalHitstopTicks = 0;
        this.hitstopShake = 0.0f;
    }

    public long getLastAttackGameTime() {
        return this.lastAttackGameTime;
    }

    /**
     * Resolves the {@link MCDWeaponType} for a given {@link ItemStack}.
     */
    public static @NotNull MCDWeaponType resolveWeaponType(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return MCDWeaponType.GAUNTLETS;
        }

        Item item = stack.getItem();
        if (item instanceof com.github.ztymilk.item.DungeonWeaponItem weapon) {
            return weapon.getWeaponType();
        }

        String name = item.toString().toLowerCase();

        // 1. Maces & Flails
        if (item == Items.MACE || name.contains("mace") || name.contains("flail")) {
            return MCDWeaponType.MACE;
        }

        // 2. Great Hammer / Heavy Bludgeons
        if (name.contains("hammer") || name.contains("cudgel")) {
            return MCDWeaponType.GREAT_HAMMER;
        }

        // 2. Double Axe / Whirlwind
        if (name.contains("double_axe") || name.contains("whirlwind") || name.contains("cursed_axe")) {
            return MCDWeaponType.DOUBLE_AXE;
        }

        // 3. Spear / Glaive / Tridents
        if (name.contains("glaive")) {
            return MCDWeaponType.GLAIVE;
        }
        SwingAnimation swingAnim = stack.get(DataComponents.SWING_ANIMATION);
        if (swingAnim != null && swingAnim.type() == SwingAnimationType.STAB) {
            return MCDWeaponType.SPEAR;
        }
        if (item == Items.TRIDENT || name.contains("spear") || name.contains("pike")) {
            return MCDWeaponType.SPEAR;
        }

        // 4. Gauntlets / Fists
        if (name.contains("gauntlet") || name.contains("binding") || name.contains("fist") || name.contains("claw")) {
            return MCDWeaponType.GAUNTLETS;
        }

        // 5. Claymore / Broadsword / Heavy 2H blades
        if (name.contains("claymore") || name.contains("broadsword") || name.contains("heartstealer") || name.contains("greatsword")) {
            return MCDWeaponType.CLAYMORE;
        }

        // 6. Daggers
        if (name.contains("dagger") || name.contains("fang")) {
            return MCDWeaponType.DAGGERS;
        }

        // 6b. Sickles
        if (name.contains("sickle")) {
            return MCDWeaponType.SICKLES;
        }

        // 7. Regular Axes
        if (name.contains("axe")) {
            return MCDWeaponType.AXE;
        }

        // 8. Default to Sword combo (Swords, Katanas, Cutlasses, or general blades)
        return MCDWeaponType.SWORD;
    }

    /**
     * Invoked when the player initiates an attack swing.
     * Progresses combo index or resets if the attack interval timed out.
     */
    public void onAttack(@NotNull ItemStack mainHandStack, long currentGameTime) {
        if (currentGameTime == this.lastAttackGameTime && this.attacking) {
            return; // Prevent duplicate step increment in the same game tick
        }

        MCDWeaponType resolvedType = resolveWeaponType(mainHandStack);
        int resetTicks = getComboResetTicks(resolvedType);

        // If weapon type changed or cadence timed out, reset combo sequence
        if (resolvedType != this.currentWeaponType || (currentGameTime - this.lastAttackGameTime) > resetTicks) {
            this.comboStep = 0;
        } else {
            // Advance combo step modulo combo length
            int maxSteps;
            if (mainHandStack.getItem() instanceof com.github.ztymilk.item.DungeonWeaponItem weapon) {
                maxSteps = weapon.getComboSequence().getLength();
            } else {
                maxSteps = switch (resolvedType) {
                    case SWORD -> 3;
                    case DOUBLE_AXE -> 2;
                    case GREAT_HAMMER -> 2;
                    case GAUNTLETS -> 3;
                    case SPEAR, SPEAR_GLAIVE, GLAIVE -> 3;
                    case CLAYMORE -> 3;
                    case DAGGERS, SICKLES -> 4;
                    default -> 3;
                };
            }
            this.comboStep = (this.comboStep + 1) % maxSteps;
        }

        this.currentWeaponType = resolvedType;
        this.lastAttackGameTime = currentGameTime;
        this.attacking = true;
    }

    /**
     * Ticked every frame/tick to update Hitstop countdown and clear active attacking state when swing finishes.
     */
    public void tick(float attackAnim) {
        if (this.hitstopTicks > 0) {
            this.hitstopTicks--;
            if (this.hitstopTicks == 0) {
                this.totalHitstopTicks = 0;
                this.hitstopShake = 0.0f;
            }
        }
        if (this.attacking && attackAnim <= 0.001f && this.hitstopTicks == 0) {
            this.attacking = false;
        }
    }

    /**
     * Calculates authentic Hitstop tick duration based on weapon archetype, combo cadence,
     * finisher status, and critical hits.
     * - Light attacks (combo openers/flurries): 1 tick (50ms) for snappy pacing.
     * - Medium attacks (Sword, Spear): 2 ticks (100ms).
     * - Heavy attacks / finishers: 3 to 6 ticks (150ms ~ 300ms, up to 6 ticks for Great Hammer & Claymore).
     * - Critical hits: +1 to +2 ticks duration.
     */
    public static int calculateHitstopTicks(@NotNull MCDWeaponType type, int stepIndex, boolean isFinisher, boolean isCrit) {
        if (isFinisher) {
            if (type == MCDWeaponType.GREAT_HAMMER) {
                return isCrit ? 8 : 6;
            }
            if (type == MCDWeaponType.CLAYMORE) {
                return isCrit ? 7 : 6;
            }
            if (type == MCDWeaponType.DOUBLE_AXE || type == MCDWeaponType.MACE || type == MCDWeaponType.GLAIVE) {
                return isCrit ? 6 : 5;
            }
            if (type == MCDWeaponType.GAUNTLETS || type == MCDWeaponType.SPEAR || type == MCDWeaponType.SWORD || type == MCDWeaponType.SICKLES) {
                return isCrit ? 5 : 4;
            }
            return isCrit ? 4 : 3; // Daggers finisher
        }
        if (isCrit) {
            return 3;
        }
        // Heavy weapons basic hits
        if (type == MCDWeaponType.GREAT_HAMMER) {
            return 4;
        }
        if (type == MCDWeaponType.CLAYMORE || type == MCDWeaponType.DOUBLE_AXE || type == MCDWeaponType.MACE || type == MCDWeaponType.GLAIVE) {
            return 3;
        }
        if (type == MCDWeaponType.SWORD || type == MCDWeaponType.SPEAR) {
            return 2;
        }
        // Rapid / agile strikes (Daggers, Sickles, Gauntlets)
        return 1;
    }

    /**
     * Calculates authentic camera shake intensity for Hitstop impact.
     */
    public static float calculateShakeIntensity(@NotNull MCDWeaponType type, int stepIndex, boolean isFinisher, boolean isCrit) {
        float base;
        if (isFinisher) {
            base = switch (type) {
                case GREAT_HAMMER -> 0.55f;
                case CLAYMORE -> 0.45f;
                case MACE -> 0.40f;
                case DOUBLE_AXE, GLAIVE, GAUNTLETS -> 0.35f;
                case SICKLES -> 0.30f;
                case SWORD, SPEAR -> 0.25f;
                case DAGGERS -> 0.18f;
                default -> 0.25f;
            };
        } else if (type == MCDWeaponType.GREAT_HAMMER) {
            base = 0.30f;
        } else if (type == MCDWeaponType.CLAYMORE) {
            base = 0.20f;
        } else if (type == MCDWeaponType.MACE || type == MCDWeaponType.DOUBLE_AXE) {
            base = 0.18f;
        } else if (type == MCDWeaponType.GLAIVE) {
            base = 0.16f;
        } else if (type == MCDWeaponType.SWORD) {
            base = 0.08f;
        } else if (type == MCDWeaponType.SPEAR) {
            base = 0.06f;
        } else if (type == MCDWeaponType.GAUNTLETS || type == MCDWeaponType.SICKLES) {
            base = 0.05f;
        } else {
            base = 0.04f;
        }
        if (isCrit) {
            base += 0.12f;
        }
        return base;
    }

    /**
     * Applies tactile micro-jitter and impact notch to camera rotation during Hitstop.
     */
    public void applyHitstopCameraShake(@NotNull org.joml.Quaternionf rotation, float partialTick) {
        if (this.hitstopTicks <= 0 || this.totalHitstopTicks <= 0) {
            return;
        }
        float progress = ((float) this.hitstopTicks - partialTick) / (float) this.totalHitstopTicks;
        progress = Math.clamp(progress, 0.0f, 1.0f);

        float intensity = this.hitstopShake * progress;
        // Pitch tuck: sharp downward impact notch (-X)
        rotation.rotateX((float) Math.toRadians(-3.5f * intensity));

        // High-frequency roll micro-jitter
        float jitter = (float) Math.sin(System.currentTimeMillis() * 0.06) * intensity * 1.8f;
        rotation.rotateZ((float) Math.toRadians(jitter));
    }
}
