package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.salamiclient.modules.visual.FreeLookModule;
//? if >=26.1 {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.salamiclient.modules.visual.FovChangerModule;
import dev.salamiclient.modules.visual.ZoomModule;
*///?}
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Free Look: Kamera nimmt die freie Blickrichtung statt der der Spielfigur. Ab 26.1 auch Zoom und FOV. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	/** Wo die Kamera die Blickrichtung der Entity übernimmt (ab 26.1 eine eigene Methode). */
	//? if >=26.1 {
	/*private static final String ALIGN = "alignWithEntity";
	*///?} else
	private static final String ALIGN = "setup";

	@WrapOperation(method = ALIGN,
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
	private float salami$freeLookYaw(Entity entity, float partialTick, Operation<Float> original) {
		return FreeLookModule.active() && entity == Minecraft.getInstance().player ? FreeLookModule.yaw() : original.call(entity, partialTick);
	}

	@WrapOperation(method = ALIGN,
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
	private float salami$freeLookPitch(Entity entity, float partialTick, Operation<Float> original) {
		return FreeLookModule.active() && entity == Minecraft.getInstance().player ? FreeLookModule.pitch() : original.call(entity, partialTick);
	}

	//? if >=26.1 {
	/*// Sichtfeld der Welt (die Hand nutzt calculateHudFov und bleibt unverändert); bis 1.21.11 in GameRendererTweaksMixin
	@ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
	private float salami$zoom(float fov, float partialTick) {
		return ZoomModule.applyFov(FovChangerModule.apply(fov, true), partialTick);
	}
	*///?}
}
