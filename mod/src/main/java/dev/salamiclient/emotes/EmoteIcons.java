package dev.salamiclient.emotes;

import com.mojang.blaze3d.platform.NativeImage;
import dev.salamiclient.SalamiClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Symbole der Emotes für Rad und Menü: eine Strichfigur in der Haltung des Emotes, aus seinen Daten
 * gezeichnet. So braucht ein neues Emote vom Server kein eigenes Bild.
 *
 * <p>Die Figur wird leicht schräg von vorn gezeigt, damit auch nach vorn gestreckte Arme zu sehen
 * sind. Nur im Render-Thread aufrufen.
 */
public final class EmoteIcons {
	/** Kantenlänge der Symbol-Textur. */
	public static final int SIZE = 64;
	private static final int COLOR = 0xFFECE8E2;
	private static final float ARM = 16f;
	private static final float LEG = 20f;
	/** Halbe Schulterbreite: breit genug, dass ein gerade erhobener Arm am Kopf vorbeigeht. */
	private static final float SHOULDER = 10f;
	/** Blick schräg von vorn: so weit wandert „nach vorn“ im Bild zur Seite und nach unten. */
	private static final float FORWARD_X = 0.5f;
	private static final float FORWARD_Y = 0.2f;

	private record Icon(Emote emote, Identifier texture) {}

	private static final Map<String, Icon> ICONS = new HashMap<>();

	private EmoteIcons() {}

	/** Textur ({@value #SIZE} x {@value #SIZE}) zum Emote; neu gezeichnet, wenn sich seine Daten geändert haben. */
	public static Identifier texture(Emote emote) {
		Icon icon = ICONS.get(emote.id());
		if (icon == null || icon.emote() != emote) {
			Identifier id = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "emotes/icon_" + emote.id());
			NativeImage image = draw(emote);
			// register ersetzt und schließt eine ältere Textur mit derselben ID
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "salami emote " + emote.id(), image));
			icon = new Icon(emote, id);
			ICONS.put(emote.id(), icon);
		}
		return icon.texture();
	}

	private static NativeImage draw(Emote emote) {
		NativeImage image = new NativeImage(SIZE, SIZE, true);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				image.setPixel(x, y, 0);
			}
		}
		// Grundhaltung: alles hängt gerade herunter; die Beine stehen für die Lesbarkeit leicht auseinander
		float[][] rotation = new float[Emote.Part.values().length][3];
		float[] sample = new float[3];
		for (Emote.Track track : emote.tracks()) {
			track.sample(emote.iconTime(), sample);
			System.arraycopy(sample, 0, rotation[track.part().ordinal()], 0, 3);
		}
		float[] head = rotation[Emote.Part.HEAD.ordinal()];
		// Kopf: neigt sich um den Hals (z), nickt (x) und dreht sich (y) – als kleine Verschiebung angedeutet
		float headX = 32f + (float) Math.sin(head[2]) * 7f - (float) Math.sin(head[1]) * 4f;
		float headY = 11f + (float) Math.sin(head[0]) * 4f;
		ring(image, headX, headY, 6.5f);
		line(image, 32f, 18f, 32f, 40f);
		line(image, 32f - SHOULDER, 23f, 32f + SHOULDER, 23f);
		limb(image, 32f - SHOULDER, 23f, rotation[Emote.Part.RIGHT_ARM.ordinal()], 0f, ARM);
		limb(image, 32f + SHOULDER, 23f, rotation[Emote.Part.LEFT_ARM.ordinal()], 0f, ARM);
		limb(image, 30f, 40f, rotation[Emote.Part.RIGHT_LEG.ordinal()], 0.22f, LEG);
		limb(image, 34f, 40f, rotation[Emote.Part.LEFT_LEG.ordinal()], -0.22f, LEG);
		return image;
	}

	/**
	 * Arm oder Bein: hängt in der Grundhaltung nach unten (y), gedreht wie im Modell erst um x, dann
	 * y, dann z; {@code spread} spreizt zusätzlich seitlich (nur fürs Symbol).
	 */
	private static void limb(NativeImage image, float originX, float originY, float[] rot, float spread, float length) {
		double sx = Math.sin(rot[0]);
		double cx = Math.cos(rot[0]);
		// nach x-Drehung: (0, cos x, sin x); nach y-Drehung:
		double ax = sx * Math.sin(rot[1]);
		double ay = cx;
		double az = sx * Math.cos(rot[1]);
		double z = rot[2] + spread;
		double bx = ax * Math.cos(z) - ay * Math.sin(z);
		double by = ax * Math.sin(z) + ay * Math.cos(z);
		double forward = -az; // das Modell schaut nach -z
		float endX = originX + (float) ((bx * 0.87 + forward * FORWARD_X) * length);
		float endY = originY + (float) ((by * 0.97 + forward * FORWARD_Y) * length);
		line(image, originX, originY, endX, endY);
	}

	private static void line(NativeImage image, float x0, float y0, float x1, float y1) {
		int steps = Math.max(1, Math.round(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * 2f));
		for (int i = 0; i <= steps; i++) {
			float f = i / (float) steps;
			dot(image, x0 + (x1 - x0) * f, y0 + (y1 - y0) * f);
		}
	}

	private static void ring(NativeImage image, float centerX, float centerY, float radius) {
		int steps = 72;
		for (int i = 0; i < steps; i++) {
			double angle = i * 2 * Math.PI / steps;
			dot(image, centerX + (float) Math.cos(angle) * radius, centerY + (float) Math.sin(angle) * radius);
		}
	}

	/** Runder Punkt mit 4 Pixeln Durchmesser – die Strichstärke. */
	private static void dot(NativeImage image, float x, float y) {
		int left = Math.round(x) - 2;
		int top = Math.round(y) - 2;
		for (int dy = 0; dy < 4; dy++) {
			for (int dx = 0; dx < 4; dx++) {
				if ((dx == 0 || dx == 3) && (dy == 0 || dy == 3)) {
					continue;
				}
				int px = left + dx;
				int py = top + dy;
				if (px >= 0 && py >= 0 && px < SIZE && py < SIZE) {
					image.setPixel(px, py, COLOR);
				}
			}
		}
	}
}
