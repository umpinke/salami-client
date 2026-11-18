package dev.salamiclient.emotes;

//? if >=1.21.11 {
import net.minecraft.client.model.player.PlayerModel;
//?} else {
/*import net.minecraft.client.model.PlayerModel;
*///?}
import net.minecraft.client.model.geom.ModelPart;

/**
 * Setzt die Haltung eines Emotes aufs Spielermodell: dreht Arme, Beine und Kopf nach der normalen
 * Animation von Minecraft. {@code weight} (0..1) blendet weich zwischen normaler Haltung und Emote
 * über, damit Anfang und Ende nicht springen.
 *
 * <p>Richtungen im Modell: x-Drehung negativ = Arm nach vorn/oben; z-Drehung positiv hebt den
 * hängenden rechten Arm seitlich nach außen, negativ den linken (bei erhobenem Arm andersherum);
 * y-Drehung negativ dreht den nach vorn gestreckten rechten Arm zur Mitte, positiv den linken.
 */
final class EmotePoses {
	private EmotePoses() {}

	/** {@code t} = Sekunden seit Beginn. Nur im Render-Thread (gemeinsamer Zwischenspeicher). */
	static void apply(PlayerModel model, Emote emote, float t, float weight) {
		// Blickrichtung vor allen Änderungen merken: Spuren mit Bezug „Kopf“ hängen daran
		float headX = model.head.xRot;
		float headY = model.head.yRot;
		for (Emote.Track track : emote.tracks()) {
			ModelPart part = switch (track.part()) {
				case HEAD -> model.head;
				case RIGHT_ARM -> model.rightArm;
				case LEFT_ARM -> model.leftArm;
				case RIGHT_LEG -> model.rightLeg;
				case LEFT_LEG -> model.leftLeg;
			};
			track.sample(t, ROTATION);
			float x = ROTATION[0];
			float y = ROTATION[1];
			float z = ROTATION[2];
			switch (track.base()) {
				case CURRENT -> {
					x += part.xRot;
					y += part.yRot;
					z += part.zRot;
				}
				case HEAD -> {
					x += headX;
					y += headY;
				}
				case NONE -> {
				}
			}
			part.xRot += (x - part.xRot) * weight;
			part.yRot += (y - part.yRot) * weight;
			part.zRot += (z - part.zRot) * weight;
		}
	}

	private static final float[] ROTATION = new float[3];
}
