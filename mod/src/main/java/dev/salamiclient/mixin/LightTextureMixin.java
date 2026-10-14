package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.salamiclient.modules.visual.FullBrightModule;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** FullBright: Helligkeit (Gamma) über das Maximum der Optionen hinaus anheben. */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
	/** Zweiter {@code floatValue()}-Aufruf in {@code updateLightTexture} = {@code options.gamma()}. */
	@ModifyExpressionValue(method = "updateLightTexture",
		at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 1))
	private float salami$fullBright(float gamma) {
		return FullBrightModule.gamma(gamma);
	}
}
