package dev.salamiclient.gametest.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.gametest.Probe;
import dev.salamiclient.render.BoxRender;
import dev.salamiclient.render.WorldContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gametest-Messfühler: wo Würfel-Modelle (Hüte, Buddys, Requisiten, Würfe) gezeichnet werden. */
@Mixin(BoxRender.class)
public abstract class WorldDrawProbeMixin {
	@Inject(method = "world", at = @At("HEAD"))
	private static void salamiGametest$world(CallbackInfo ci, @Local(argsOnly = true) WorldContext context) {
		Probe.world(context.matrices());
	}

	@Inject(method = "submit", at = @At("HEAD"))
	private static void salamiGametest$submit(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
		Probe.box(poseStack);
	}
}
