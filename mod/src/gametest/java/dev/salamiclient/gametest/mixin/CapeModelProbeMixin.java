package dev.salamiclient.gametest.mixin;

import dev.salamiclient.gametest.Probe;
import net.minecraft.client.model.HumanoidModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gametest-Messfühler: Minecrafts Cape-Modell (ein eigenes Spielermodell nur mit dem Cape) – wo sein
 * Körper beim Zeichnen sitzt, im Vergleich zum Körper des Spielermodells (siehe {@link Probe}).
 */
//? if >=1.21.11 {
@Mixin(targets = "net.minecraft.client.model.player.PlayerCapeModel", priority = 5000)
//?} else
/*@Mixin(targets = "net.minecraft.client.model.PlayerCapeModel", priority = 5000)*/
public abstract class CapeModelProbeMixin {
	@Inject(method = "setupAnim", at = @At("TAIL"))
	private void salamiGametest$track(CallbackInfo ci) {
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		Probe.track(model.body, "capeBody");
		if (model.body.hasChild("cape")) {
			Probe.track(model.body.getChild("cape"), "cape");
		}
	}
}
