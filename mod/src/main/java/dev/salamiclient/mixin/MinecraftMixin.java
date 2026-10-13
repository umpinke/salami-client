package dev.salamiclient.mixin;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.modules.utility.PlayerEspModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	/** Fenstertitel: "Salami Client · Minecraft 1.21.11 - Einzelspieler". */
	@Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
	private void salami$title(CallbackInfoReturnable<String> cir) {
		cir.setReturnValue(SalamiClient.NAME + " · " + cir.getReturnValue());
	}

	/** Player ESP (Leucht-Modus): Spieler bekommen Minecrafts Glowing-Umriss. */
	@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
	private void salami$espGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && PlayerEspModule.glows(entity)) {
			cir.setReturnValue(true);
		}
	}
}
