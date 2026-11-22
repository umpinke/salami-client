package dev.salamiclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Hängt Cosmetics (Hut, Buddy, Mini Me, Emote-Requisiten) an ein Körperteil des Spielermodells:
 * erst die Bewegung des ganzen Modells ({@code root}), dann das Teil – so wie es die Ebenen von
 * Minecraft selbst tun.
 *
 * <p>Ohne Animations-Mods steht {@code root} still und es ändert sich nichts. Punchy! Moves (und
 * ähnliche Mods) bewegen darüber aber den ganzen Körper – lehnen, wippen, ducken. Wer nur das
 * Körperteil nimmt, lässt Hut und Buddy dann an der alten Stelle in der Luft stehen.
 */
final class ModelAnchor {
	private ModelAnchor() {
	}

	static void to(PoseStack poseStack, Model<?> model, ModelPart part) {
		model.root().translateAndRotate(poseStack);
		part.translateAndRotate(poseStack);
	}
}
