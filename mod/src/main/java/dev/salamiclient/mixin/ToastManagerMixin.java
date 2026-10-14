package dev.salamiclient.mixin;

import dev.salamiclient.modules.misc.NoAdvancementModule;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Advancement: Fortschritts- und Rezept-Popups gar nicht erst anzeigen. */
@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
	@Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
	private void salami$blockToast(Toast toast, CallbackInfo ci) {
		if (NoAdvancementModule.blocks(toast)) {
			ci.cancel();
		}
	}
}
