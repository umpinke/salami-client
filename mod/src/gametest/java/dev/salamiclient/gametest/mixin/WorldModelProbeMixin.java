package dev.salamiclient.gametest.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.salamiclient.gametest.Probe;
import dev.salamiclient.render.WorldContext;
import dev.salamiclient.render.WorldModels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gametest-Messfühler: in der Welt gezeichnete Modelle (hinterherlaufende Mini Mes, Verwandlungen). */
@Mixin(WorldModels.class)
public abstract class WorldModelProbeMixin {
	@Inject(method = "model", at = @At("HEAD"))
	private static void salamiGametest$model(CallbackInfo ci, @Local(argsOnly = true) WorldContext context) {
		Probe.world(context.matrices());
	}
}
