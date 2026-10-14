package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.modules.visual.NameTagsModule;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NameTags: Deckkraft des Hintergrunds hinter Namen und Größe der Namensschilder. */
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
