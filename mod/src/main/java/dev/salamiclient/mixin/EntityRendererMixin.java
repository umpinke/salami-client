package dev.salamiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.salamiclient.modules.misc.StreamerModeModule;
import dev.salamiclient.modules.utility.PlayerEspModule;
import dev.salamiclient.modules.visual.HealthIndicatorsModule;
import dev.salamiclient.modules.visual.NameTagsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** ESP: Farbe des Leucht-Umrisses statt der Teamfarbe. Health Indicators / NameTags: Leben und Rüstung am Namen. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@ModifyExpressionValue(method = "extractRenderState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I"))
	private int salami$espGlowColor(int teamColor, @Local(argsOnly = true) Entity entity) {
		return PlayerEspModule.glowColor(entity, teamColor);
	}

	/** Wo der Name fürs Namensschild geholt wird (ab 26.2 eine eigene Methode). */
	//? if >=26.2 {
	/*private static final String NAME_TAG = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V";
	*///?} else
	private static final String NAME_TAG = "extractRenderState";

	@ModifyExpressionValue(method = NAME_TAG,
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;getNameTag(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/network/chat/Component;"))
	private Component salami$healthIndicator(Component name, @Local(argsOnly = true) Entity entity) {
		// Streamer Mode betrifft nur den eigenen Namen – andere Namen nicht jedes Frame durchsuchen
		Component shown = entity == Minecraft.getInstance().player ? StreamerModeModule.hideName(name) : name;
		return NameTagsModule.decorate(HealthIndicatorsModule.decorate(shown, entity), entity);
	}
}
