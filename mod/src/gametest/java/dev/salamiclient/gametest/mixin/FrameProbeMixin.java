package dev.salamiclient.gametest.mixin;

import dev.salamiclient.gametest.Probe;
import dev.salamiclient.module.ModuleManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gametest-Messfühler: ein Aufruf pro gezeichnetem Frame (Salamis Weltrendering). */
@Mixin(ModuleManager.class)
public abstract class FrameProbeMixin {
	@Inject(method = "renderWorld", at = @At("HEAD"))
	private void salamiGametest$frame(CallbackInfo ci) {
		Probe.frame(Minecraft.getInstance());
	}
}
