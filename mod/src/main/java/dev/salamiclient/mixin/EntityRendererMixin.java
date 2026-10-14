package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.salamiclient.modules.misc.StreamerModeModule;
import dev.salamiclient.modules.utility.PlayerEspModule;
import dev.salamiclient.modules.visual.HealthIndicatorsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Player ESP: Farbe des Leucht-Umrisses statt der Teamfarbe. Health Indicators: Leben am Namen. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@ModifyExpressionValue(method = "extractRenderState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I"))
	private int salami$espGlowColor(int teamColor, @Local(argsOnly = true) Entity entity) {
		return PlayerEspModule.glowColor(entity, teamColor);
	}

	@ModifyExpressionValue(method = "extractRenderState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;getNameTag(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/network/chat/Component;"))
	private Component salami$healthIndicator(Component name, @Local(argsOnly = true) Entity entity) {
		// Streamer Mode betrifft nur den eigenen Namen – andere Namen nicht jedes Frame durchsuchen
		Component shown = entity == Minecraft.getInstance().player ? StreamerModeModule.hideName(name) : name;
		return HealthIndicatorsModule.decorate(shown, entity);
	}
}
