package dev.salamiclient.mixin;

import dev.salamiclient.modules.visual.CapesModule;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Salami Capes: Cape-Textur des eigenen Spielers ersetzen (Cape- und Elytra-Layer lesen sie aus dem Skin). */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void salami$cape(CallbackInfoReturnable<PlayerSkin> cir) {
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		PlayerSkin skin = CapesModule.apply(self.getUUID(), cir.getReturnValue());
		if (skin != cir.getReturnValue()) {
			cir.setReturnValue(skin);
		}
	}
}
