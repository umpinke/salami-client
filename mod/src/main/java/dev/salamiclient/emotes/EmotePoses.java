package dev.salamiclient.emotes;

//? if >=1.21.11 {
import net.minecraft.client.model.player.PlayerModel;
//?} else {
/*import net.minecraft.client.model.PlayerModel;
*///?}
import net.minecraft.client.model.geom.ModelPart;

/**
 * Haltungen der Emotes: dreht Arme, Beine und Kopf des Spielermodells nach der normalen
 * Animation von Minecraft. {@code weight} (0..1) blendet weich zwischen normaler Haltung und
 * Emote über, damit Anfang und Ende nicht springen.
 *
 * <p>Richtungen im Modell: x-Drehung negativ = Arm nach vorn/oben; z-Drehung positiv hebt den
 * rechten Arm seitlich nach außen, negativ den linken; y-Drehung negativ dreht den nach vorn
 * gestreckten rechten Arm zur Mitte, positiv den linken.
 */
final class EmotePoses {
	private static final float PI = (float) Math.PI;

	private EmotePoses() {}

	/** {@code t} = Sekunden seit Beginn. */
	static void apply(PlayerModel model, Emote emote, float t, float weight) {
		float headX = model.head.xRot;
		float headY = model.head.yRot;
		switch (emote) {
			case WAVE -> {
				pose(model.rightArm, weight, 0f, 0f, 2.6f + 0.35f * sin(t * 11f));
				pose(model.head, weight, headX - 0.05f, headY, 0.06f * sin(t * 5.5f));
			}
			case CHEER -> {
				float bounce = 0.22f * sin(t * 13f);
				// Arm erst nach oben (x), dann seitlich kippen: bei erhobenem Arm zeigt z andersherum nach außen
				pose(model.rightArm, weight, -3.0f + bounce, 0f, -0.5f);
				pose(model.leftArm, weight, -3.0f - bounce, 0f, 0.5f);
				pose(model.head, weight, -0.25f, headY, 0f);
			}
			case CLAP -> {
				float clap = (sin(t * 16f) + 1f) / 2f;
				pose(model.rightArm, weight, -1.3f, -0.2f - 0.4f * clap, 0f);
				pose(model.leftArm, weight, -1.3f, 0.2f + 0.4f * clap, 0f);
			}
			case DANCE -> {
				float beat = sin(t * 7f);
				pose(model.rightArm, weight, -0.15f, 0f, 1.4f + 1.0f * beat);
				pose(model.leftArm, weight, -0.15f, 0f, -1.4f + 1.0f * beat);
				pose(model.rightLeg, weight, 0.35f * beat, 0f, 0.05f);
				pose(model.leftLeg, weight, -0.35f * beat, 0f, -0.05f);
				pose(model.head, weight, headX + 0.12f * Math.abs(beat), headY, 0.15f * beat);
			}
			case FACEPALM -> {
				pose(model.rightArm, weight, -2.15f, -0.55f, 0.15f);
				pose(model.head, weight, 0.45f, headY + 0.12f * sin(t * 8f), 0f);
			}
			case POINT -> pose(model.rightArm, weight, headX - PI / 2f, headY - 0.1f, 0f);
			case SHRUG -> {
				// Schultern hoch und wieder runter: einmal über die ganze Dauer
				float lift = sin(Math.min(1f, t / 1.6f) * PI);
				pose(model.rightArm, weight, -0.35f * lift, 0f, 0.15f + 0.6f * lift);
				pose(model.leftArm, weight, -0.35f * lift, 0f, -0.15f - 0.6f * lift);
				pose(model.head, weight, headX - 0.1f * lift, headY, 0.22f * lift);
			}
			case TPOSE -> {
				pose(model.rightArm, weight, 0f, 0f, PI / 2f);
				pose(model.leftArm, weight, 0f, 0f, -PI / 2f);
				pose(model.rightLeg, weight, 0f, 0f, 0f);
				pose(model.leftLeg, weight, 0f, 0f, 0f);
			}
		}
	}

	private static void pose(ModelPart part, float weight, float x, float y, float z) {
		part.xRot = lerp(part.xRot, x, weight);
		part.yRot = lerp(part.yRot, y, weight);
		part.zRot = lerp(part.zRot, z, weight);
	}

	private static float lerp(float from, float to, float weight) {
		return from + (to - from) * weight;
	}

	private static float sin(float value) {
		return (float) Math.sin(value);
	}
}
