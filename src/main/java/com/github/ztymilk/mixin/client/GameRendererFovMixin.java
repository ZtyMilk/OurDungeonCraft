package com.github.ztymilk.mixin.client;

import com.github.ztymilk.roll.RollData;
import com.github.ztymilk.roll.RollDataAccessor;
import com.github.ztymilk.roll.RollState;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into {@link GameRenderer} to apply dynamic FOV boost (+10%) during the dodge roll ACTIVE phase.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererFovMixin {

    @Inject(
            method = "getFov(Lnet/minecraft/client/Camera;FZ)F",
            at = @At("RETURN"),
            cancellable = true
    )
    private void ourDungeonCraft$applyRollFovBoost(
            Camera camera,
            float partialTick,
            boolean useFovSetting,
            CallbackInfoReturnable<Float> cir
    ) {
        if (!useFovSetting) {
            return; // Keep hand FOV steady
        }

        Entity entity = camera.entity();
        if (entity instanceof RollDataAccessor rollAccessor) {
            RollData data = rollAccessor.getRollData();
            if (data.isRolling() && data.getState() == RollState.ACTIVE) {
                float progress = (data.getRollTicks() + partialTick) / (float) RollData.ACTIVE_TICKS;
                progress = Mth.clamp(progress, 0.0f, 1.0f);
                float boost = 1.0f + 0.10f * (float) Math.sin(progress * Math.PI);
                cir.setReturnValue(cir.getReturnValueF() * boost);
            }
        }
    }
}
