package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.salamiclient.modules.utility.PlayerEspModule;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Player ESP: Farbe des Leucht-Umrisses statt der Teamfarbe. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@ModifyExpressionValue(method = "extractRenderState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I"))
	private int salami$espGlowColor(int teamColor, @Local(argsOnly = true) Entity entity) {
		return PlayerEspModule.glowColor(entity, teamColor);
	}
}
