package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import dev.salamiclient.modules.visual.FovChangerModule;
import dev.salamiclient.modules.visual.NoHurtCamModule;
import dev.salamiclient.modules.visual.ZoomModule;
import dev.salamiclient.render.PostEffects;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Hurt Cam, FOV Changer und Zoom (Sichtfeld) sowie eigene Nachbearbeitungs-Effekte. */
@Mixin(GameRenderer.class)
public abstract class GameRendererTweaksMixin {
	@Shadow
	@Final
	private CrossFrameResourcePool resourcePool;

	// Ohne Parameter – die unterscheiden sich zwischen den Minecraft-Versionen
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void salami$noHurtCam(CallbackInfo ci) {
		if (NoHurtCamModule.active()) {
			ci.cancel();
		}
	}

	// ab 26.1 rechnet die Kamera das Sichtfeld aus (CameraMixin)
	//? if <26.1 {
	@ModifyReturnValue(method = "getFov", at = @At("RETURN"))
	private float salami$zoom(float fov, Camera camera, float partialTick, boolean useFovSetting) {
		// Nur das Sichtfeld der Welt – die Hand (useFovSetting = false) bleibt unverändert
		if (!useFovSetting) {
			return fov;
		}
		return ZoomModule.applyFov(FovChangerModule.apply(fov, true), partialTick);
	}
	//?}

	/** Farbsättigung und Motion Blur: direkt nach dem Weltbild, vor HUD und Menüs. */
	@Inject(method = "render",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
	private void salami$postEffects(CallbackInfo ci) {
		PostEffects.process(resourcePool);
	}
}
