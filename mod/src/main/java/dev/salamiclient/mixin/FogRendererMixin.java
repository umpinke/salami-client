package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.salamiclient.modules.visual.NoFogModule;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >=26.1 {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
*///?} else {
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}

/** No Fog: Nebelgrenzen ändern, bevor sie an die Shader gehen. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	//? if >=26.1 {
	/*// Ab 26.1 rechnet setupFog nur noch und gibt die Werte zurück; hochgeladen wird woanders.
	@ModifyReturnValue(method = "setupFog", at = @At("RETURN"))
	private FogData salami$noFog(FogData fog, @Local FogType type) {
		NoFogModule.apply(fog, type);
		return fog;
	}
	*///?} else {
	// Vor dem Hochladen: beim Aufruf von updateBuffer lägen die Werte schon auf dem Stack.
	// Ohne die Methodenparameter – die unterscheiden sich zwischen den Minecraft-Versionen.
	@Inject(method = "setupFog",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;getDevice()Lcom/mojang/blaze3d/systems/GpuDevice;", ordinal = 0))
	private void salami$noFog(CallbackInfoReturnable<Vector4f> cir, @Local FogData fog, @Local FogType type) {
		NoFogModule.apply(fog, type);
	}
	//?}
}
