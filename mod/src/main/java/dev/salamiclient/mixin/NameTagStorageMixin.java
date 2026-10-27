package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.modules.visual.NameTagsModule;
//? if >=26.2 {
/*import net.minecraft.client.renderer.SubmitNodeCollection;
import org.joml.Matrix4f;
*///?} else {
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// NameTags: Deckkraft des Hintergrunds hinter Namen und Größe der Namensschilder.
//? if >=26.2 {
/*// Ab 26.2 baut SubmitNodeCollection die Namensschilder; die Entfernung steckt in der Matrix.
@Mixin(SubmitNodeCollection.class)
public abstract class NameTagStorageMixin {
	@ModifyExpressionValue(method = "submitNameTag",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/OptionsRenderState;getBackgroundOpacity(F)F"))
	private float salami$nameTagBackground(float opacity) {
		return NameTagsModule.backgroundOpacity(opacity);
	}

	@WrapOperation(method = "submitNameTag",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
	private void salami$nameTagScale(PoseStack poseStack, float x, float y, float z, Operation<Void> original) {
		Matrix4f pose = poseStack.last().pose();
		double distanceSq = pose.m30() * pose.m30() + pose.m31() * pose.m31() + pose.m32() * pose.m32();
		float factor = NameTagsModule.scale(distanceSq);
		original.call(poseStack, x * factor, y * factor, z * factor);
	}
}
*///?} else {
@Mixin(NameTagFeatureRenderer.Storage.class)
public abstract class NameTagStorageMixin {
	@ModifyExpressionValue(method = "add",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getBackgroundOpacity(F)F"))
	private float salami$nameTagBackground(float opacity) {
		return NameTagsModule.backgroundOpacity(opacity);
	}

	@WrapOperation(method = "add",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
	private void salami$nameTagScale(PoseStack poseStack, float x, float y, float z, Operation<Void> original,
		@Local(argsOnly = true) double distanceSq) {
		float factor = NameTagsModule.scale(distanceSq);
		original.call(poseStack, x * factor, y * factor, z * factor);
	}
}
//?}
