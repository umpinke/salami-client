package dev.salamiclient.mixin;

import dev.salamiclient.modules.misc.AutoReconnectModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Auto Reconnect: Ziel beim Verbindungsaufbau merken (auch wenn der Beitritt scheitert). */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
	@Inject(method = "startConnecting", at = @At("HEAD"))
	private static void salami$rememberTarget(Screen parent, Minecraft minecraft, ServerAddress address, ServerData server,
		boolean quickPlay, TransferState transfer, CallbackInfo ci) {
		AutoReconnectModule.onConnectStart(server);
	}
}
