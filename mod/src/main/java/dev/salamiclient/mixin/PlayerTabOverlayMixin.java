package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.salamiclient.modules.misc.StreamerModeModule;
import dev.salamiclient.modules.misc.TabTweaksModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Streamer Mode (eigener Name) und Tab Tweaks (Ping als Zahl) in der Tab-Liste. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
	@ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"))
	private Component salami$streamerName(Component name, PlayerInfo info) {
		return StreamerModeModule.hideName(name);
	}

	@Inject(method = "renderPingIcon", at = @At("HEAD"), cancellable = true)
	private void salami$numericPing(GuiGraphics graphics, int width, int x, int y, PlayerInfo info, CallbackInfo ci) {
		if (TabTweaksModule.renderPing(graphics, width, x, y, info)) {
			ci.cancel();
		}
	}
}
