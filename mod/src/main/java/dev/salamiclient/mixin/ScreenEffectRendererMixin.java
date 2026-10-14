package dev.salamiclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.modules.visual.LowFireModule;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Low Fire: das Feuer im Bild nach unten schieben. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	@Inject(method = "renderFire", at = @At("HEAD"))
	private static void salami$lowerFire(PoseStack poseStack, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
		poseStack.pushPose();
		poseStack.translate(0.0F, -LowFireModule.offset(), 0.0F);
	}

	@Inject(method = "renderFire", at = @At("RETURN"))
	private static void salami$restoreFire(PoseStack poseStack, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
		poseStack.popPose();
	}
}
