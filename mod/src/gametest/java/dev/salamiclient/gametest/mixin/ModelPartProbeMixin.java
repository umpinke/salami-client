package dev.salamiclient.gametest.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.gametest.Probe;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gametest-Messfühler: wie das Spiel ein Körperteil wirklich zeichnet (nach allen anderen Mods, die sich
 * dort einhängen – darum spät). Vergleich mit der Stelle, an der ein Cosmetic hängt (siehe {@link Probe}).
 */
@Mixin(value = ModelPart.class, priority = 5000)
public abstract class ModelPartProbeMixin {
	@Inject(method = "translateAndRotate", at = @At("RETURN"))
	private void salamiGametest$drawn(PoseStack poseStack, CallbackInfo ci) {
		Probe.partDrawn(this, poseStack);
	}
}
