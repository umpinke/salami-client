package dev.salamiclient.mixin;

import dev.salamiclient.modules.hud.TpsModule;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TPS: Zeit-Pakete des Servers mitzählen (nach dem Wechsel auf den Hauptthread). */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handleSetTime",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
			shift = At.Shift.AFTER))
	private void salami$measureTps(ClientboundSetTimePacket packet, CallbackInfo ci) {
		TpsModule.onServerTime(packet.gameTime());
	}
}
