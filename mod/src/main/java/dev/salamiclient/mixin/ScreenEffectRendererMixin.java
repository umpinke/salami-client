package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.modules.visual.LowFireModule;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Low Fire: das Feuer im Bild nach unten schieben. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	/** Zeichnet das Feuer vor der Kamera (ab 26.2 eingereicht statt direkt gezeichnet). */
	//? if >=26.2 {
	/*private static final String FIRE = "submitFire";
	*///?} else
	private static final String FIRE = "renderFire";

	@Inject(method = FIRE, at = @At("HEAD"))
	private static void salami$lowerFire(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
		poseStack.pushPose();
		poseStack.translate(0.0F, -LowFireModule.offset(), 0.0F);
	}

	@Inject(method = FIRE, at = @At("RETURN"))
	private static void salami$restoreFire(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
		poseStack.popPose();
	}
}
