package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.salamiclient.emotes.Emotes;
import dev.salamiclient.modules.visual.NameTagsModule;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NameTags: eigenen Namen in der Third-Person-Ansicht zeigen, andere nur bis zur eingestellten Reichweite.
 * Emotes mit Verwandlung: das Modell eines verwandelten Spielers nicht zeichnen – an seiner Stelle steht
 * die Figur ({@link dev.salamiclient.emotes.EmoteEffects}); was an ihm hängt, lässt {@code AvatarRendererMixin} weg.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@ModifyReturnValue(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("RETURN"))
	private boolean salami$ownNameTag(boolean show, LivingEntity entity, double distanceSq) {
		return (show && NameTagsModule.inRange(distanceSq)) || NameTagsModule.showOwn(entity);
	}

	@ModifyReturnValue(method = "getRenderType", at = @At("RETURN"))
	private RenderType salami$noBodyWhenTransformed(RenderType type, LivingEntityRenderState state, boolean visible, boolean translucent, boolean glowing) {
		return type != null && Emotes.transformed(state) ? null : type;
	}
}
