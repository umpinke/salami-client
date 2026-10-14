package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.salamiclient.modules.visual.FreeLookModule;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Free Look: Kamera nimmt die freie Blickrichtung statt der der Spielfigur. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@ModifyExpressionValue(method = "setup",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
	private float salami$freeLookYaw(float yaw, @Local(argsOnly = true) Entity entity) {
		return FreeLookModule.active() && entity == Minecraft.getInstance().player ? FreeLookModule.yaw() : yaw;
	}

	@ModifyExpressionValue(method = "setup",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
	private float salami$freeLookPitch(float pitch, @Local(argsOnly = true) Entity entity) {
		return FreeLookModule.active() && entity == Minecraft.getInstance().player ? FreeLookModule.pitch() : pitch;
	}
}
