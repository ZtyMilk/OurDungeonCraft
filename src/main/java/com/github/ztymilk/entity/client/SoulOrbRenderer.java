package com.github.ztymilk.entity.client;

import com.github.ztymilk.OurDungeonCraft;
import com.github.ztymilk.entity.SoulOrbEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

public class SoulOrbRenderer extends EntityRenderer<SoulOrbEntity, SoulOrbRenderState> {

    private static final Identifier TEXTURE = OurDungeonCraft.id("textures/entity/soul_orb.png");
    private static final RenderType RENDER_TYPE = RenderTypes.itemEntityTranslucentCull(TEXTURE);

    public SoulOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.15f;
        this.shadowStrength = 0.5f;
    }

    @Override
    public SoulOrbRenderState createRenderState() {
        return new SoulOrbRenderState();
    }

    @Override
    public void extractRenderState(SoulOrbEntity entity, SoulOrbRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.scale = entity.getScale(partialTick);
        state.age = (float) entity.tickCount + partialTick;
    }

    @Override
    public void submit(
            SoulOrbRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraRenderState
    ) {
        poseStack.pushPose();
        poseStack.translate(0.0f, 0.2f, 0.0f);
        // Face camera (billboard)
        poseStack.mulPose(cameraRenderState.orientation);

        // Gentle breathing pulsation and size scaling
        float pulse = 1.0f + 0.04f * (float) Math.sin(state.age * 0.25f);
        float s = state.scale * pulse * 0.55f;
        poseStack.scale(s, s, s);

        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, consumer) -> {
            float w = 0.5f;
            float h = 0.72f;
            int light = LightTexture.pack(15, 15); // Emissive glowing cyan orb
            int r = 255;
            int g = 255;
            int b = 255;
            int a = 230; // Ethereal translucency

            vertex(consumer, pose, -w * 0.5f, -h * 0.5f, 0.0f, 1.0f, r, g, b, a, light);
            vertex(consumer, pose,  w * 0.5f, -h * 0.5f, 1.0f, 1.0f, r, g, b, a, light);
            vertex(consumer, pose,  w * 0.5f,  h * 0.5f, 1.0f, 0.0f, r, g, b, a, light);
            vertex(consumer, pose, -w * 0.5f,  h * 0.5f, 0.0f, 0.0f, r, g, b, a, light);
        });

        poseStack.popPose();
        super.submit(state, poseStack, collector, cameraRenderState);
    }

    private static void vertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x, float y,
            float u, float v,
            int r, int g, int b, int a,
            int light
    ) {
        consumer.addVertex(pose, x, y, 0.0f)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0f, 1.0f, 0.0f);
    }
}
