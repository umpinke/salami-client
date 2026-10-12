package dev.salamiclient.mixin;

import dev.salamiclient.SalamiClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hook vor jedem Angriff – am Anfang von {@code attack}, also bevor
 * {@code ensureHasSentCarriedItem()} den gehaltenen Slot an den Server meldet.
 * Ein hier gewechselter Slot gilt dadurch schon für diesen Schlag.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void salami$beforeAttack(Player player, Entity target, CallbackInfo ci) {
		SalamiClient client = SalamiClient.get();
		if (client != null && client.modules() != null) {
			client.modules().beforeAttack(target);
		}
	}
}
