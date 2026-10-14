package dev.salamiclient.mixin;

import dev.salamiclient.modules.visual.ZoomModule;
import dev.salamiclient.util.ClickTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Klicks zählen (CPS/Keystrokes) und Mausrad beim Zoomen abfangen. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "onButton", at = @At("HEAD"))
	private void salami$countClicks(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		if (action == 1 && minecraft.screen == null && window == minecraft.getWindow().handle()) {
			ClickTracker.press(info.button());
		}
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void salami$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (minecraft.screen == null && window == minecraft.getWindow().handle() && ZoomModule.onScroll(vertical)) {
			ci.cancel();
		}
	}
}
