package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.salamiclient.modules.visual.NoFogModule;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No Fog: Nebelgrenzen ändern, bevor sie an die Shader gehen. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	/** Vor dem Hochladen: beim Aufruf von {@code updateBuffer} lägen die Werte schon auf dem Stack. */
	@Inject(method = "setupFog",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;getDevice()Lcom/mojang/blaze3d/systems/GpuDevice;", ordinal = 0))
	private void salami$noFog(Camera camera, int renderDistance, DeltaTracker delta, float darken, ClientLevel level,
		CallbackInfoReturnable<Vector4f> cir, @Local FogData fog, @Local FogType type) {
		NoFogModule.apply(fog, type);
	}
}
