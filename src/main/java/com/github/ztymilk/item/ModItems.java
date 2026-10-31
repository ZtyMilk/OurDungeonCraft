package com.github.ztymilk.item;

import com.github.ztymilk.OurDungeonCraft;
import com.github.ztymilk.combat.BaseWeaponPresets;
import com.github.ztymilk.combat.ComboSequence;
import com.github.ztymilk.sound.WeaponSoundHelper.MCDWeaponType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Registry class for all OurDungeonCraft weapon items.
 * Calibrated with differentiated 0.60 ~ 3.20 attack speed gradient and realistic reach bonuses.
 */
public final class ModItems {

    private ModItems() {}

    public static ResourceKey<Item> keyOf(String path) {
        return ResourceKey.create(Registries.ITEM, OurDungeonCraft.id(path));
    }

    private static DungeonWeaponItem registerWeapon(
            String path,
            MCDWeaponType type,
            ComboSequence sequence,
            double attackDamage,
            double attackSpeed,
            double reachBonus,
            int durability
    ) {
        ResourceKey<Item> key = keyOf(path);
        DungeonWeaponItem item = DungeonWeaponItem.create(key, type, sequence, attackDamage, attackSpeed, reachBonus, durability);
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    // =========================================================================
    // THE 10 BASE WHITE-SLATE WEAPONS OF MINECRAFT DUNGEONS (DIFFERENTIATED)
    // =========================================================================

    /** 1. Sword (地下城剑): 3-hit combo, balanced damage & speed */
    public static final DungeonWeaponItem SWORD = registerWeapon(
            "sword", MCDWeaponType.SWORD, BaseWeaponPresets.SWORD, 7.0, 1.60, 0.0, 1561
    );

    /** 2. Double Axe (双刃斧): 2-hit continuous whirlwind cleave, heavy centrifugal weight */
    public static final DungeonWeaponItem DOUBLE_AXE = registerWeapon(
            "double_axe", MCDWeaponType.DOUBLE_AXE, BaseWeaponPresets.DOUBLE_AXE, 9.5, 0.90, 0.4, 1561
    );

    /** 3. Great Hammer (大锤): 2-hit colossal overhead bludgeon & seismic smash */
    public static final DungeonWeaponItem GREAT_HAMMER = registerWeapon(
            "great_hammer", MCDWeaponType.GREAT_HAMMER, BaseWeaponPresets.GREAT_HAMMER, 13.0, 0.60, 0.6, 1561
    );

    /** 4. Claymore (阔剑): 3-hit sweeping cadence with broad reach & colossal weight */
    public static final DungeonWeaponItem CLAYMORE = registerWeapon(
            "claymore", MCDWeaponType.CLAYMORE, BaseWeaponPresets.CLAYMORE, 11.0, 0.75, 0.8, 1561
    );

    /** 5. Daggers (匕首): 4-hit rapid laceration flurry, lightning fast point-blank */
    public static final DungeonWeaponItem DAGGERS = registerWeapon(
            "daggers", MCDWeaponType.DAGGERS, BaseWeaponPresets.DAGGERS, 4.2, 3.20, -1.0, 1561
    );

    /** 6. Gauntlets (拳套): 3-hit boxing combo with rocket uppercut aerial launcher */
    public static final DungeonWeaponItem GAUNTLETS = registerWeapon(
            "gauntlets", MCDWeaponType.GAUNTLETS, BaseWeaponPresets.GAUNTLETS, 5.0, 2.60, -0.9, 1561
    );

    /** 7. Spear (长矛): 3-hit piercing combo with +2.2m extended piercing reach */
    public static final DungeonWeaponItem SPEAR = registerWeapon(
            "spear", MCDWeaponType.SPEAR, BaseWeaponPresets.SPEAR, 7.5, 1.25, 2.2, 1561
    );

    /** 8. Mace (钉头锤): 3-hit concussive blunt impact with armor fracture */
    public static final DungeonWeaponItem MACE = registerWeapon(
            "mace", MCDWeaponType.MACE, BaseWeaponPresets.MACE, 8.0, 1.40, -0.2, 1561
    );

    /** 9. Sickles (双镰刀): 4-hit reaping combo with vacuum pull & 360 spin */
    public static final DungeonWeaponItem SICKLES = registerWeapon(
            "sickles", MCDWeaponType.SICKLES, BaseWeaponPresets.SICKLES, 5.5, 2.20, -0.5, 1561
    );

    /** 10. Glaive (长戟): 3-hit sweeping polearm with +1.8m extended reach */
    public static final DungeonWeaponItem GLAIVE = registerWeapon(
            "glaive", MCDWeaponType.GLAIVE, BaseWeaponPresets.GLAIVE, 8.5, 1.10, 1.8, 1561
    );

    public static final List<DungeonWeaponItem> ALL_BASE_WEAPONS = List.of(
            SWORD, DOUBLE_AXE, GREAT_HAMMER, CLAYMORE, DAGGERS,
            GAUNTLETS, SPEAR, MACE, SICKLES, GLAIVE
    );

    public static void init() {
        // Classloading triggers static initialization
    }
}
