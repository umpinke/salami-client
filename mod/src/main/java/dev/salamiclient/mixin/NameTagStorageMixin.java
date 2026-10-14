package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.salamiclient.modules.visual.NameTagsModule;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NameTags: Deckkraft des Hintergrunds hinter Namen. */
@Mixin(NameTagFeatureRenderer.Storage.class)
public abstract class NameTagStorageMixin {
	@ModifyExpressionValue(method = "add",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getBackgroundOpacity(F)F"))
	private float salami$nameTagBackground(float opacity) {
		return NameTagsModule.backgroundOpacity(opacity);
	}
}
