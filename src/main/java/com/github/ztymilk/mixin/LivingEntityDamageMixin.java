package com.github.ztymilk.mixin;

import com.github.ztymilk.combat.DamageFeedbackTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入 {@link LivingEntity#actuallyHurt}，捕获受击伤害与减免计算，触发S2C伤害跳字同步。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {

    @Shadow public abstract float getHealth();
    @Shadow public abstract float getAbsorptionAmount();
    @Shadow protected abstract float getDamageAfterArmorAbsorb(DamageSource damageSource, float damage);
    @Shadow protected abstract float getDamageAfterMagicAbsorb(DamageSource damageSource, float damage);

    @Unique
    private float ourDungeonCraft$healthBeforeHurt;
    @Unique
    private float ourDungeonCraft$absorptionBeforeHurt;

    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void ourDungeonCraft$recordHealthBeforeActuallyHurt(ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
        this.ourDungeonCraft$healthBeforeHurt = this.getHealth();
        this.ourDungeonCraft$absorptionBeforeHurt = this.getAbsorptionAmount();
    }

    @Inject(method = "actuallyHurt", at = @At("TAIL"))
    private void ourDungeonCraft$afterActuallyHurt(ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity.level().isClientSide()) {
            return;
        }

        float totalHealthLost = (this.ourDungeonCraft$healthBeforeHurt + this.ourDungeonCraft$absorptionBeforeHurt)
                - (this.getHealth() + this.getAbsorptionAmount());

        // 计算经过护甲与附魔减免后的伤害值，并兼顾残血超量斩杀显示真实击打威力
        float mitigatedDamage = this.getDamageAfterMagicAbsorb(source, this.getDamageAfterArmorAbsorb(source, amount));
        float effectiveDamage = Math.max(totalHealthLost, mitigatedDamage);

        if (effectiveDamage < 0.1f) {
            return;
        }

        // 解析暴击状态：优先上下文线程变量，其次检查蓄力箭矢，再检查常规下落跳劈暴击
        boolean isCrit = false;
        Boolean trackerCrit = DamageFeedbackTracker.getCurrentCrit();
        if (trackerCrit != null && trackerCrit) {
            isCrit = true;
        } else if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.isCritArrow()) {
            isCrit = true;
        } else if (source.getEntity() instanceof Player player) {
            if (player.fallDistance > 0.0f && !player.onGround() && !player.onClimbable() && !player.isInWater() && !player.hasEffect(MobEffects.BLINDNESS) && !player.isPassenger()) {
                isCrit = true;
            }
        }

        DamageFeedbackTracker.broadcastDamageNumber(entity, source, effectiveDamage, isCrit);
    }
}
