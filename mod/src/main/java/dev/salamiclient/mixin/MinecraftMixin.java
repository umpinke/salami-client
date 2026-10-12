package dev.salamiclient.mixin;

import dev.salamiclient.SalamiClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fenstertitel: "Salami Client · Minecraft 1.21.11 - Einzelspieler". */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
	private void salami$title(CallbackInfoReturnable<String> cir) {
		cir.setReturnValue(SalamiClient.NAME + " · " + cir.getReturnValue());
	}
}
