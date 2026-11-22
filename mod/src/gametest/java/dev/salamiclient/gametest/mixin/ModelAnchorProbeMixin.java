package dev.salamiclient.gametest.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.gametest.Probe;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/** Gametest-Messfühler: wo jedes Cosmetic am Spielermodell in jedem Frame hängt (siehe {@link Probe}). */
@Mixin(targets = "dev.salamiclient.render.ModelAnchor")
public abstract class ModelAnchorProbeMixin {
	@Inject(method = "to", at = @At("HEAD"))
	private static void salamiGametest$before(PoseStack poseStack, Model<?> model, ModelPart part, CallbackInfo ci) {
		Probe.anchorStart(poseStack);
	}

	@Inject(method = "to", at = @At("RETURN"))
	private static void salamiGametest$after(PoseStack poseStack, Model<?> model, ModelPart part, CallbackInfo ci) {
		Probe.anchorEnd(poseStack, part, Probe.recording() ? partName(model, part) : "");
	}

	/** Name des Körperteils (head, body, leftArm …) über die Felder des Modells. */
	private static String partName(Model<?> model, ModelPart part) {
		for (Class<?> type = model.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
			for (Field field : type.getDeclaredFields()) {
				if (field.getType() != ModelPart.class) {
					continue;
				}
				try {
					field.setAccessible(true);
					if (field.get(model) == part) {
						return field.getName();
					}
				} catch (ReflectiveOperationException | RuntimeException ignored) {
					// weiter suchen
				}
			}
		}
		return "?";
	}
}
