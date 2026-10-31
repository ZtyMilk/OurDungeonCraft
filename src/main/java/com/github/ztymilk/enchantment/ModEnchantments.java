package com.github.ztymilk.enchantment;

import com.github.ztymilk.OurDungeonCraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Handles the "Roll Master" (翻滚大师) armor-exclusive enchantment:
 * - Levels: I, II, III
 * - Bonus roll charges: +1 / +2 / +3
 * - Armor-exclusive (Helmet, Chestplate, Leggings, Boots)
 * - Stacks across multiple armor pieces up to a hard ceiling of 4 total charges.
 */
public final class ModEnchantments {
    private ModEnchantments() {}

    public static final Identifier ROLL_MASTER_ID = OurDungeonCraft.id("roll_master");
    public static final ResourceKey<Enchantment> ROLL_MASTER = ResourceKey.create(Registries.ENCHANTMENT, ROLL_MASTER_ID);

    public static void init() {
        OurDungeonCraft.LOGGER.info("Registered Roll Master enchantment key: {}", ROLL_MASTER_ID);
    }

    /**
     * Retrieves the Roll Master enchantment level on the specified ItemStack.
     *
     * @param stack The item to check.
     * @return Enchantment level (0, 1, 2, 3...).
     */
    public static int getRollMasterLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        ItemEnchantments enchantments = stack.getEnchantments();
        if (enchantments == null || enchantments.isEmpty()) {
            return 0;
        }
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (holder.is(ROLL_MASTER_ID) || holder.is(ROLL_MASTER)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    /**
     * Calculates the total bonus roll charges contributed by all armor pieces worn by the entity.
     * Stacks across all armor slots (HEAD, CHEST, LEGS, FEET).
     *
     * @param entity LivingEntity to check.
     * @return Total stacked bonus roll charges.
     */
    public static int calculateRollMasterBonus(LivingEntity entity) {
        if (entity == null) {
            return 0;
        }
        int totalBonus = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.isArmor()) {
                ItemStack stack = entity.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    totalBonus += getRollMasterLevel(stack);
                }
            }
        }
        return totalBonus;
    }
}
