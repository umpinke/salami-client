package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.salamiclient.modules.visual.FullBrightModule;
//? if >=26.1 {
/*import net.minecraft.client.renderer.LightmapRenderStateExtractor;
*///?} else
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// FullBright: Helligkeit (Gamma) über das Maximum der Optionen hinaus anheben.
//? if >=26.1 {
/*@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightTextureMixin {
	// Erster floatValue()-Aufruf in extract = options.gamma()
	@ModifyExpressionValue(method = "extract",
		at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 0))
	private float salami$fullBright(float gamma) {
		return FullBrightModule.gamma(gamma);
	}
}
*///?} else {
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
	// Zweiter floatValue()-Aufruf in updateLightTexture = options.gamma()
	@ModifyExpressionValue(method = "updateLightTexture",
		at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 1))
	private float salami$fullBright(float gamma) {
		return FullBrightModule.gamma(gamma);
	}
}
//?}
