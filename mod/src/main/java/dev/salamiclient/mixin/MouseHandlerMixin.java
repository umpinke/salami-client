package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.salamiclient.modules.visual.FreeLookModule;
import dev.salamiclient.modules.visual.ZoomModule;
import dev.salamiclient.util.ClickTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Klicks zählen (CPS/Keystrokes), Mausrad beim Zoomen abfangen, Free Look. */
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

	/** Free Look: Mausbewegung dreht nur die Kamera, nicht die Spielfigur; beim Zoomen langsamer. */
	@WrapOperation(method = "turnPlayer",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void salami$freeLook(LocalPlayer player, double dx, double dy, Operation<Void> original) {
		double scale = ZoomModule.sensitivityScale();
		if (!FreeLookModule.turn(dx * scale, dy * scale)) {
			original.call(player, dx * scale, dy * scale);
		}
	}
}
