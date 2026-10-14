package dev.salamiclient.mixin;

import com.mojang.realmsclient.dto.RealmsServer;
import dev.salamiclient.modules.misc.AutoReconnectModule;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.realms.RealmsConnect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Auto Reconnect: Realms verbinden anders – dann keinen alten Server anbieten. */
@Mixin(RealmsConnect.class)
public abstract class RealmsConnectMixin {
	@Inject(method = "connect", at = @At("HEAD"))
	private void salami$forgetServer(RealmsServer server, ServerAddress address, CallbackInfo ci) {
		AutoReconnectModule.forget();
	}
}
