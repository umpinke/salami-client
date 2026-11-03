package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.salamiclient.modules.visual.NameTagsModule;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NameTags: eigenen Namen in der Third-Person-Ansicht zeigen, andere nur bis zur eingestellten Reichweite. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@ModifyReturnValue(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("RETURN"))
	private boolean salami$ownNameTag(boolean show, LivingEntity entity, double distanceSq) {
		return (show && NameTagsModule.inRange(distanceSq)) || NameTagsModule.showOwn(entity);
	}
}
