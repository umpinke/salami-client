package dev.salamiclient.mixin;

import dev.salamiclient.modules.visual.HitboxModule;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Hitbox-Modul: eigene Farben für Rahmen, Augenhöhe und Blickrichtung (nur wenn das Modul an ist). */
@Mixin(EntityHitboxDebugRenderer.class)
public abstract class EntityHitboxDebugRendererMixin {
	@ModifyConstant(method = "showHitboxes", constant = @Constant(intValue = -1))
	private int salami$boxColor(int vanilla) {
		return HitboxModule.boxColor(vanilla);
	}

	@ModifyConstant(method = "showHitboxes", constant = @Constant(intValue = -65536))
	private int salami$eyeColor(int vanilla) {
		return HitboxModule.eyeColor(vanilla);
	}

	@ModifyConstant(method = "showHitboxes", constant = @Constant(intValue = -16776961))
	private int salami$lookColor(int vanilla) {
		return HitboxModule.lookColor(vanilla);
	}
}
