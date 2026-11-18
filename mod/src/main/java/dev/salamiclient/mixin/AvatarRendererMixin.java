package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.salamiclient.emotes.Emotes;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Emotes mit Verwandlung: an einem verwandelten Spieler hängt nichts mehr – keine Rüstung, kein
 * Cape, nichts in der Hand, kein Hut. An seiner Stelle steht die Figur
 * ({@link dev.salamiclient.emotes.EmoteEffects}).
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@ModifyReturnValue(method = "shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Z", at = @At("RETURN"))
	private boolean salami$noLayersWhenTransformed(boolean render, AvatarRenderState state) {
		return render && !Emotes.transformed(state);
	}
}
