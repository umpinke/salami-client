package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.modules.visual.NoHurtCamModule;
import dev.salamiclient.modules.visual.ZoomModule;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Hurt Cam (Kamera wackelt nicht bei Schaden) und Zoom (Sichtfeld). */
@Mixin(GameRenderer.class)
public abstract class GameRendererTweaksMixin {
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void salami$noHurtCam(PoseStack poseStack, float partialTick, CallbackInfo ci) {
		if (NoHurtCamModule.active()) {
			ci.cancel();
		}
	}

	@ModifyReturnValue(method = "getFov", at = @At("RETURN"))
	private float salami$zoom(float fov, Camera camera, float partialTick, boolean useFovSetting) {
		return ZoomModule.applyFov(fov, partialTick);
	}
}
