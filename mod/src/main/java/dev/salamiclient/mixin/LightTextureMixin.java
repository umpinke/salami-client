package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.salamiclient.modules.visual.FullBrightModule;
//? if >=26.1 {
/*import net.minecraft.client.renderer.LightmapRenderStateExtractor;
*///?} else
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// FullBright: Helligkeit (Gamma) über das Maximum der Optionen hinaus anheben.
// Performance-Mods (BadOptimizations) berechnen die Lightmap nur neu, wenn sich ihre Eingaben
// ändern – FullBright an/aus oder ein neuer Wert erzwingt deshalb selbst eine Neuberechnung.
//? if >=26.1 {
/*@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightTextureMixin {
	@Shadow
	private boolean needsUpdate;
	@Unique
	private float salami$lastOverride = -1;

	@Inject(method = "extract", at = @At("HEAD"))
	private void salami$refreshOnChange(CallbackInfo ci) {
		float override = FullBrightModule.override();
		if (override != salami$lastOverride) {
			salami$lastOverride = override;
			needsUpdate = true;
		}
	}

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
	@Shadow
	private boolean updateLightTexture;
	@Unique
	private float salami$lastOverride = -1;

	@Inject(method = "updateLightTexture", at = @At("HEAD"))
	private void salami$refreshOnChange(CallbackInfo ci) {
		float override = FullBrightModule.override();
		if (override != salami$lastOverride) {
			salami$lastOverride = override;
			updateLightTexture = true;
		}
	}

	// Zweiter floatValue()-Aufruf in updateLightTexture = options.gamma()
	@ModifyExpressionValue(method = "updateLightTexture",
		at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 1))
	private float salami$fullBright(float gamma) {
		return FullBrightModule.gamma(gamma);
	}
}
//?}
