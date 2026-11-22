package dev.salamiclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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

	/** Fest am Teil: Lage und Drehung wie das Teil (Hüte, Requisiten in der Hand). */
	static void to(PoseStack poseStack, Model<?> model, ModelPart part) {
		model.root().translateAndRotate(poseStack);
		part.translateAndRotate(poseStack);
	}

	/**
	 * Für Figuren, die an einem Teil hängen oder darauf stehen (Buddy, Mini Me): die Stelle
	 * {@code (x, y, z)} (Pixel im Teil) folgt dem Teil ganz, die Figur selbst bleibt aber aufrecht. Sie
	 * schaut wie {@code facing} (nur um die Hochachse) und kippt nur zu {@code follow} mit dem Teil
	 * (0 = gar nicht, 1 = ganz).
	 *
	 * <p>Fest an Hand oder Kopf geklebt schlägt eine Figur jeden Armschwung mit: beim Rennen bis 75°
	 * hin und her, mit Punchy! Moves bis fast 2000° pro Sekunde – sie zappelt, statt zu baumeln.
	 */
	static void upright(PoseStack poseStack, Model<?> model, ModelPart part, float x, float y, float z, ModelPart facing, float follow) {
		Matrix4f base = new Matrix4f(poseStack.last().pose()).invert();
		Matrix4f onPart = local(poseStack, base, model, part);
		Matrix4f looking = facing == part ? onPart : local(poseStack, base, model, facing);
		Vector3f at = onPart.transformPosition(new Vector3f(x / 16f, y / 16f, z / 16f));
		// Blickrichtung im Modell (das Gesicht zeigt nach -z, oben ist -y), nur ihr Winkel um die
		// Hochachse. Nach vorn und oben gemischt, damit er auch beim Blick senkrecht nach unten stimmt.
		Vector3f forward = looking.transformDirection(new Vector3f(0f, 0f, -1f));
		Vector3f up = looking.transformDirection(new Vector3f(0f, -1f, 0f));
		float level = (float) Math.sqrt(forward.x * forward.x + forward.z * forward.z);
		float aheadX = forward.x * level + up.x * forward.y;
		float aheadZ = forward.z * level + up.z * forward.y;
		Quaternionf turn = new Quaternionf().rotationY((float) Math.atan2(-aheadX, -aheadZ));
		if (follow > 0f) {
			turn.slerp(onPart.getUnnormalizedRotation(new Quaternionf()).normalize(), follow);
		}
		poseStack.translate(at.x, at.y, at.z);
		poseStack.mulPose(turn);
	}

	/**
	 * Lage des Teils im Modell. Über die echte Matrix gerechnet (nicht auf einer leeren), damit auch
	 * Mods mitzählen, die beim Drehen eines Teils ihre eigene Matrix dazurechnen.
	 */
	private static Matrix4f local(PoseStack poseStack, Matrix4f inverseBase, Model<?> model, ModelPart part) {
		poseStack.pushPose();
		to(poseStack, model, part);
		Matrix4f local = new Matrix4f(inverseBase).mul(poseStack.last().pose());
		poseStack.popPose();
		return local;
	}
}
