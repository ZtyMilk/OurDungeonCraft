package com.github.ztymilk.item;

import com.github.ztymilk.combat.AttackComboAccessor;
import com.github.ztymilk.combat.AttackComboData;
import com.github.ztymilk.combat.ComboSequence;
import com.github.ztymilk.combat.ComboStep;
import com.github.ztymilk.sound.WeaponSoundHelper.MCDWeaponType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Base weapon item class for all Minecraft Dungeons weapons in OurDungeonCraft.
 * Encapsulates weapon archetype, combo sequences, dynamic damage scaling, and reach modifiers.
 */
public class DungeonWeaponItem extends Item {

    public static final Identifier REACH_MODIFIER_ID = Identifier.fromNamespaceAndPath("ourdungeoncraft", "weapon_reach");

    private final MCDWeaponType weaponType;
    private final ComboSequence comboSequence;
    private final double reachBonus;

    public DungeonWeaponItem(
            @NotNull Properties properties,
            @NotNull MCDWeaponType weaponType,
            @NotNull ComboSequence comboSequence,
            double reachBonus
    ) {
        super(properties);
        this.weaponType = weaponType;
        this.comboSequence = comboSequence;
        this.reachBonus = reachBonus;
    }

    public @NotNull MCDWeaponType getWeaponType() {
        return this.weaponType;
    }

    public @NotNull ComboSequence getComboSequence() {
        return this.comboSequence;
    }

    public double getReachBonus() {
        return this.reachBonus;
    }

    /**
     * Builds attributes and properties for a Minecraft Dungeons base weapon.
     */
    public static DungeonWeaponItem create(
            @NotNull ResourceKey<Item> key,
            @NotNull MCDWeaponType type,
            @NotNull ComboSequence sequence,
            double attackDamage,
            double attackSpeed,
            double reachBonus,
            int durability
    ) {
        ItemAttributeModifiers.Builder modifiers = ItemAttributeModifiers.builder()
                .add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(
                                Item.BASE_ATTACK_DAMAGE_ID,
                                Math.max(0.0, attackDamage - 1.0), // Base player hand is 1.0
                                AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.MAINHAND
                )
                .add(
                        Attributes.ATTACK_SPEED,
                        new AttributeModifier(
                                Item.BASE_ATTACK_SPEED_ID,
                                attackSpeed - 4.0, // Base player attack speed is 4.0
                                AttributeModifier.Operation.ADD_VALUE
                        ),
                        EquipmentSlotGroup.MAINHAND
                );

        if (reachBonus != 0.0) {
            modifiers.add(
                    Attributes.ENTITY_INTERACTION_RANGE,
                    new AttributeModifier(
                            REACH_MODIFIER_ID,
                            reachBonus,
                            AttributeModifier.Operation.ADD_VALUE
                    ),
                    EquipmentSlotGroup.MAINHAND
            );
        }

        Properties properties = new Properties()
                .setId(key) // Mandatory in 1.21.11!
                .durability(durability)
                .rarity(Rarity.COMMON)
                .attributes(modifiers.build());

        return new DungeonWeaponItem(properties, type, sequence, reachBonus);
    }

    /**
     * Minecraft 1.21.11 Hook: scales attack damage dynamically based on the current combo step!
     */
    @Override
    public float getAttackDamageBonus(Entity target, float damage, DamageSource damageSource) {
        if (damageSource != null && damageSource.getEntity() instanceof Player player) {
            if (player instanceof AttackComboAccessor accessor) {
                AttackComboData data = accessor.ourDungeonCraft$getComboData();
                ComboStep step = this.comboSequence.getStep(data.getComboStep());
                float mult = step.damageMultiplier();
                if (mult != 1.0f) {
                    return damage * (mult - 1.0f);
                }
            }
        }
        return 0.0f;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay tooltipDisplay,
            Consumer<Component> tooltipAdder,
            TooltipFlag tooltipFlag
    ) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltipAdder, tooltipFlag);

        // Combo length display
        tooltipAdder.accept(Component.translatable(
                "tooltip.ourdungeoncraft.combo",
                this.comboSequence.getLength()
        ).withStyle(ChatFormatting.GRAY));

        // Reach display
        if (this.reachBonus != 0.0) {
            tooltipAdder.accept(Component.translatable(
                    "tooltip.ourdungeoncraft.reach",
                    String.format("%+.1fm", this.reachBonus)
            ).withStyle(this.reachBonus > 0.0 ? ChatFormatting.BLUE : ChatFormatting.RED));
        }
    }
}
