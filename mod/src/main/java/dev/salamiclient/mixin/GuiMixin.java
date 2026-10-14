package dev.salamiclient.mixin;

import dev.salamiclient.modules.hud.PotionStatusModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Potion Status: Vanilla-Effektsymbole oben rechts ausblenden, wenn gewünscht. */
@Mixin(Gui.class)
public abstract class GuiMixin {
	@Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
	private void salami$hideVanillaEffects(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
		if (PotionStatusModule.hidesVanillaEffects()) {
			ci.cancel();
		}
	}
}
