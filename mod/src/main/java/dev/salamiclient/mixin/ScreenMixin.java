package dev.salamiclient.mixin;

import dev.salamiclient.modules.visual.ClearBackgroundModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clear Background: Abdunkeln und Unschärfe hinter Menüs in der Welt abschalten. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Inject(method = "renderTransparentBackground", at = @At("HEAD"), cancellable = true)
	private void salami$clearInventoryBackground(GuiGraphics graphics, CallbackInfo ci) {
		if (ClearBackgroundModule.hidesDarkening()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderBlurredBackground", at = @At("HEAD"), cancellable = true)
	private void salami$noMenuBlur(GuiGraphics graphics, CallbackInfo ci) {
		if (ClearBackgroundModule.hidesBlur()) {
			ci.cancel();
		}
	}
}
