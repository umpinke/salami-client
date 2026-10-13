package dev.salamiclient.mixin;

import dev.salamiclient.modules.visual.ParticlesModule;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Partikel-Modul: ausgeblendete Partikeltypen gar nicht erst erzeugen. */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void salami$filterParticle(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz,
		CallbackInfoReturnable<Particle> cir) {
		if (ParticlesModule.hides(options.getType())) {
			cir.setReturnValue(null);
		}
	}

	/** Krit-Funken und Totem-Wolke laufen über mitlaufende Emitter. */
	@Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V",
		at = @At("HEAD"), cancellable = true)
	private void salami$filterEmitter(Entity entity, ParticleOptions options, CallbackInfo ci) {
		if (ParticlesModule.hides(options.getType())) {
			ci.cancel();
		}
	}

	@Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V",
		at = @At("HEAD"), cancellable = true)
	private void salami$filterEmitterWithLifetime(Entity entity, ParticleOptions options, int lifetime, CallbackInfo ci) {
		if (ParticlesModule.hides(options.getType())) {
			ci.cancel();
		}
	}
}
