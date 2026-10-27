package dev.salamiclient.mixin;

import dev.salamiclient.modules.visual.ParticlesModule;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Partikel-Modul: Blockabbau-Partikel umgehen die ParticleEngine-Fabrik und werden hier gefiltert.
 * Ohne die Methodenparameter – die unterscheiden sich zwischen den Minecraft-Versionen.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
	private void salami$filterDestroyEffect(CallbackInfo ci) {
		if (ParticlesModule.hides(ParticlesModule.Kind.BLOCK_BREAK)) {
			ci.cancel();
		}
	}

	@Inject(method = "addBreakingBlockEffect", at = @At("HEAD"), cancellable = true)
	private void salami$filterBreakingEffect(CallbackInfo ci) {
		if (ParticlesModule.hides(ParticlesModule.Kind.BLOCK_BREAK)) {
			ci.cancel();
		}
	}
}
