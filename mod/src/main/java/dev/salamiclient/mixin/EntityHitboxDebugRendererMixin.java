package dev.salamiclient.mixin;

import dev.salamiclient.modules.visual.HitboxModule;
//? if >=1.21.11 {
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
//?} else {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.feature.HitboxFeatureRenderer;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.injection.At;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// Hitbox-Modul: eigene Farben für Rahmen, Augenhöhe und Blickrichtung (nur wenn das Modul an ist).
//? if >=1.21.11 {
@Mixin(EntityHitboxDebugRenderer.class)
public abstract class EntityHitboxDebugRendererMixin {
	@ModifyConstant(method = "showHitboxes", constant = @Constant(intValue = -1))
	private int salami$boxColor(int vanilla) {
		return HitboxModule.boxColor(vanilla);
	}

	@ModifyConstant(method = "showHitboxes", constant = @Constant(intValue = -65536))
	private int salami$eyeColor(int vanilla) {
		return HitboxModule.eyeColor(vanilla);
	}

	@ModifyConstant(method = "showHitboxes", constant = @Constant(intValue = -16776961))
	private int salami$lookColor(int vanilla) {
		return HitboxModule.lookColor(vanilla);
	}
}
//?} else {
/*// Bis 1.21.10 zeichnet der HitboxFeatureRenderer; Rahmen und Augenhöhe kommen als Farbe aus dem Render-State.
@Mixin(HitboxFeatureRenderer.class)
public abstract class EntityHitboxDebugRendererMixin {
	@WrapOperation(method = "renderHitbox", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/ShapeRenderer;renderLineBox(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;DDDDDDFFFF)V"))
	private static void salami$boxColors(PoseStack.Pose pose, VertexConsumer lines, double x0, double y0, double z0,
		double x1, double y1, double z1, float red, float green, float blue, float alpha, Operation<Void> original) {
		int vanilla = ARGB.colorFromFloat(alpha, red, green, blue);
		int argb = switch (vanilla) {
			case -1 -> HitboxModule.boxColor(vanilla);
			case -65536 -> HitboxModule.eyeColor(vanilla);
			default -> vanilla;
		};
		original.call(pose, lines, x0, y0, z0, x1, y1, z1,
			ARGB.redFloat(argb), ARGB.greenFloat(argb), ARGB.blueFloat(argb), ARGB.alphaFloat(argb));
	}

	@ModifyConstant(method = "renderHitboxesAndViewVector", constant = @Constant(intValue = -16776961))
	private static int salami$lookColor(int vanilla) {
		return HitboxModule.lookColor(vanilla);
	}
}
*///?}
