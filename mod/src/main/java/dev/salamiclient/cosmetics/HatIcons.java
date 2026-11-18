package dev.salamiclient.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import dev.salamiclient.SalamiClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Symbole der Hüte fürs Cosmetics-Menü: das Modell des Hutes, schräg von vorn oben gesehen und aus
 * seinen Daten gezeichnet. So braucht ein neuer Hut vom Server kein eigenes Bild.
 *
 * <p>Gezeichnet wird ohne die Grafikkarte, einmal je Hut: die Flächen werden in dreifacher Größe
 * mit Tiefenprüfung gefüllt und dann verkleinert, damit die Kanten glatt sind. Nur im Render-Thread
 * aufrufen.
 */
public final class HatIcons {
	/** Kantenlänge der Symbol-Textur. */
	public static final int SIZE = 64;
	/** So viel größer wird gezeichnet und danach verkleinert (Kantenglättung). */
	private static final int SAMPLES = 3;
	/** Freier Rand um das Modell, in Pixeln des Symbols. */
	private static final float MARGIN = 5f;
	/** Blickwinkel: von vorn, etwas von der Seite und von oben. */
	private static final float YAW = (float) Math.toRadians(32);
	private static final float PITCH = (float) Math.toRadians(24);

	private record Icon(Hat hat, Identifier texture) {}

	private static final Map<String, Icon> ICONS = new HashMap<>();

	private HatIcons() {}

	/** Textur ({@value #SIZE} x {@value #SIZE}) zum Hut; neu gezeichnet, wenn sich seine Daten geändert haben. */
	public static Identifier texture(Hat hat) {
		Icon icon = ICONS.get(hat.id());
		if (icon == null || icon.hat() != hat) {
			Identifier id = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "cosmetics/hat_icon_" + hat.id());
			NativeImage image = draw(hat);
			// register ersetzt und schließt eine ältere Textur mit derselben ID
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "salami hat " + hat.id(), image));
			icon = new Icon(hat, id);
			ICONS.put(hat.id(), icon);
		}
		return icon.texture();
	}

	private static NativeImage draw(Hat hat) {
		float[] quads = hat.quads();
		int[] colors = hat.colors();
		int count = colors.length;
		// Ansicht: im Modell ist oben negativ und vorn bei -z – nach dem Drehen ist x rechts, y unten, z die Tiefe
		Matrix4f view = new Matrix4f().rotateX(PITCH).rotateY(YAW);
		float[] points = new float[count * 12];
		float[] shade = new float[count];
		Vector3f v = new Vector3f();
		Vector3f light = new Vector3f(-0.45f, -0.75f, -0.5f).normalize();
		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		for (int i = 0; i < count; i++) {
			int o = i * Hat.QUAD;
			for (int corner = 0; corner < 4; corner++) {
				view.transformPosition(quads[o + corner * 3], quads[o + corner * 3 + 1], quads[o + corner * 3 + 2], v);
				points[i * 12 + corner * 3] = v.x;
				points[i * 12 + corner * 3 + 1] = v.y;
				points[i * 12 + corner * 3 + 2] = v.z;
				minX = Math.min(minX, v.x);
				maxX = Math.max(maxX, v.x);
				minY = Math.min(minY, v.y);
				maxY = Math.max(maxY, v.y);
			}
			view.transformDirection(quads[o + 12], quads[o + 13], quads[o + 14], v);
			shade[i] = 0.58f + 0.42f * Math.max(0f, v.dot(light));
		}
		// einpassen: das Modell füllt das Symbol bis auf den Rand, mittig
		int n = SIZE * SAMPLES;
		float room = (SIZE - 2 * MARGIN) * SAMPLES;
		float scale = room / Math.max(0.001f, Math.max(maxX - minX, maxY - minY));
		float offsetX = n / 2f - (minX + maxX) / 2f * scale;
		float offsetY = n / 2f - (minY + maxY) / 2f * scale;

		float[] depth = new float[n * n];
		int[] pixels = new int[n * n];
		Arrays.fill(depth, Float.MAX_VALUE);
		for (int i = 0; i < count; i++) {
			int p = i * 12;
			int color = shaded(colors[i], shade[i]);
			float[] x = new float[4];
			float[] y = new float[4];
			float[] z = new float[4];
			for (int corner = 0; corner < 4; corner++) {
				x[corner] = points[p + corner * 3] * scale + offsetX;
				y[corner] = points[p + corner * 3 + 1] * scale + offsetY;
				z[corner] = points[p + corner * 3 + 2];
			}
			triangle(depth, pixels, n, color, x[0], y[0], z[0], x[1], y[1], z[1], x[2], y[2], z[2]);
			triangle(depth, pixels, n, color, x[0], y[0], z[0], x[2], y[2], z[2], x[3], y[3], z[3]);
		}

		NativeImage image = new NativeImage(SIZE, SIZE, true);
		for (int py = 0; py < SIZE; py++) {
			for (int px = 0; px < SIZE; px++) {
				int r = 0;
				int g = 0;
				int b = 0;
				int covered = 0;
				for (int sy = 0; sy < SAMPLES; sy++) {
					for (int sx = 0; sx < SAMPLES; sx++) {
						int sample = pixels[(py * SAMPLES + sy) * n + px * SAMPLES + sx];
						if (sample != 0) {
							r += (sample >> 16) & 0xFF;
							g += (sample >> 8) & 0xFF;
							b += sample & 0xFF;
							covered++;
						}
					}
				}
				if (covered == 0) {
					image.setPixel(px, py, 0);
				} else {
					int alpha = 255 * covered / (SAMPLES * SAMPLES);
					image.setPixel(px, py, (alpha << 24) | ((r / covered) << 16) | ((g / covered) << 8) | (b / covered));
				}
			}
		}
		return image;
	}

	private static int shaded(int argb, float factor) {
		int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * factor));
		int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * factor));
		int b = Math.min(255, Math.round((argb & 0xFF) * factor));
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	/** Füllt ein Dreieck; näher an der Kamera (kleineres z) gewinnt. */
	private static void triangle(float[] depth, int[] pixels, int n, int color,
			float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2) {
		float area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
		if (Math.abs(area) < 1e-4f) {
			return;
		}
		int left = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2))));
		int right = Math.min(n - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
		int top = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2))));
		int bottom = Math.min(n - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
		for (int py = top; py <= bottom; py++) {
			for (int px = left; px <= right; px++) {
				float cx = px + 0.5f;
				float cy = py + 0.5f;
				float w0 = ((x1 - cx) * (y2 - cy) - (x2 - cx) * (y1 - cy)) / area;
				float w1 = ((x2 - cx) * (y0 - cy) - (x0 - cx) * (y2 - cy)) / area;
				float w2 = 1f - w0 - w1;
				if (w0 < 0f || w1 < 0f || w2 < 0f) {
					continue;
				}
				float z = w0 * z0 + w1 * z1 + w2 * z2;
				int index = py * n + px;
				if (z < depth[index]) {
					depth[index] = z;
					pixels[index] = color;
				}
			}
		}
	}
}
