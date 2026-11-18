package dev.salamiclient.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Ein Modell aus farbigen Würfeln, wie es in den Cosmetics-Katalogen steht ({@code "parts": […]}):
 * Hüte, Buddys und alles, was Emotes mitbringen (Requisiten, Würfe, Verwandlungen). Format und
 * Grenzen wie auf dem Server ({@code server/capes/hats.mjs}).
 *
 * <p>Beim Einlesen werden die Würfel einmal in Flächen umgerechnet (in Pixeln des Spielermodells,
 * oben ist negativ). Gezeichnet werden sie ohne eigenes Bild: weiße Textur, Farbe je Fläche.
 */
public final class BoxModel {
	private static final int MAX_BOXES = 64;
	private static final int MAX_PARTS = 32;
	private static final int MAX_DEPTH = 3;
	private static final float MAX_PIXELS = 32f;
	private static final float MAX_DEGREES = 360f;
	private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
	/** Werte je Fläche in {@link #quads}: vier Ecken (x, y, z) und die Normale. */
	public static final int QUAD = 15;

	private final float[] quads;
	private final int[] colors;
	private final float top;

	private BoxModel(float[] quads, int[] colors) {
		this.quads = quads;
		this.colors = colors;
		float highest = Float.MAX_VALUE;
		for (int i = 0; i < colors.length; i++) {
			for (int corner = 0; corner < 4; corner++) {
				highest = Math.min(highest, quads[i * QUAD + corner * 3 + 1]);
			}
		}
		this.top = highest;
	}

	/** Flächen des Modells: je {@link #QUAD} Werte, siehe dort. Nicht verändern. */
	public float[] quads() {
		return quads;
	}

	/** Farbe (ARGB) je Fläche. Nicht verändern. */
	public int[] colors() {
		return colors;
	}

	/** Höchster Punkt des Modells (y, oben ist negativ). */
	public float top() {
		return top;
	}

	/**
	 * Schreibt alle Flächen in den Puffer (Wesen-Format: Farbe, Textur, Überlagerung, Licht, Normale).
	 * {@code mirror} spiegelt links/rechts – für die andere Schulter.
	 */
	public void emit(PoseStack.Pose pose, VertexConsumer buffer, boolean mirror, int light) {
		float flip = mirror ? -1f : 1f;
		for (int i = 0; i < colors.length; i++) {
			int o = i * QUAD;
			float nx = quads[o + 12] * flip;
			float ny = quads[o + 13];
			float nz = quads[o + 14];
			for (int corner = 0; corner < 4; corner++) {
				int c = o + corner * 3;
				buffer.addVertex(pose, quads[c] * flip, quads[c + 1], quads[c + 2])
					.setColor(colors[i])
					.setUv(0.5f, 0.5f)
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light)
					.setNormal(pose, nx, ny, nz);
			}
		}
	}

	// ------------------------------------------------------------------ Einlesen

	/**
	 * Rechnet eine Liste von Teilen in ein Modell um; wirft {@link IllegalArgumentException} mit dem
	 * Grund, wenn etwas nicht stimmt. {@code what} steht vorn in der Meldung (die id des Eintrags).
	 */
	public static BoxModel parse(JsonElement rawParts, String what) {
		if (rawParts == null || !rawParts.isJsonArray() || rawParts.getAsJsonArray().isEmpty()) {
			throw new IllegalArgumentException(what + ": parts fehlt");
		}
		Baker baker = new Baker(what);
		for (JsonElement part : rawParts.getAsJsonArray()) {
			baker.part(part, new Matrix4f(), 1, false);
		}
		return baker.finish();
	}

	/**
	 * Mehrere Modelle aus einer Liste von Teilen, die sich die Grenzen eines Modells teilen: die Teile
	 * der obersten Ebene, für die {@code own} zutrifft, werden je ein eigenes Modell um ihren
	 * Drehpunkt (ohne ihr {@code at} und {@code rot} – die liest der Aufrufer selbst), alle anderen
	 * zusammen das erste Modell der Liste ({@code null}, wenn es keine gibt). Für Verwandlungen, deren
	 * Teile sich einzeln bewegen.
	 */
	public static List<BoxModel> parseSplit(JsonElement rawParts, String what, java.util.function.Predicate<JsonObject> own) {
		if (rawParts == null || !rawParts.isJsonArray() || rawParts.getAsJsonArray().isEmpty()) {
			throw new IllegalArgumentException(what + ": parts fehlt");
		}
		Baker counter = new Baker(what);
		List<BoxModel> models = new ArrayList<>();
		models.add(null);
		for (JsonElement part : rawParts.getAsJsonArray()) {
			if (part.isJsonObject() && own.test(part.getAsJsonObject())) {
				counter.part(part, new Matrix4f(), 1, true);
				models.add(counter.finish());
				counter.reset();
			}
		}
		boolean any = false;
		for (JsonElement part : rawParts.getAsJsonArray()) {
			if (!(part.isJsonObject() && own.test(part.getAsJsonObject()))) {
				counter.part(part, new Matrix4f(), 1, false);
				any = true;
			}
		}
		if (any) {
			models.set(0, counter.finish());
		}
		return models;
	}

	/** {@code [x, y, z]} mit Werten bis {@code limit}; wirft mit {@code what} im Text. */
	public static float[] triple(JsonElement raw, float limit, String what) {
		if (raw == null || !raw.isJsonArray() || raw.getAsJsonArray().size() != 3) {
			throw new IllegalArgumentException(what + " braucht [x, y, z]");
		}
		JsonArray array = raw.getAsJsonArray();
		float[] values = new float[3];
		for (int i = 0; i < 3; i++) {
			values[i] = CatalogEntry.number(array.get(i), -limit, limit, what);
		}
		return values;
	}

	/** Rechnet Teile in Flächen um und zählt dabei die Grenzen mit. */
	private static final class Baker {
		private final String id;
		private List<float[]> quads = new ArrayList<>();
		private List<Integer> colors = new ArrayList<>();
		private int parts;
		private int boxes;

		Baker(String id) {
			this.id = id;
		}

		/** Bisher gesammelte Flächen als Modell. */
		BoxModel finish() {
			if (colors.isEmpty()) {
				throw new IllegalArgumentException(id + ": mindestens ein Würfel nötig");
			}
			float[] all = new float[quads.size() * QUAD];
			int[] tints = new int[colors.size()];
			for (int i = 0; i < tints.length; i++) {
				System.arraycopy(quads.get(i), 0, all, i * QUAD, QUAD);
				tints[i] = colors.get(i);
			}
			return new BoxModel(all, tints);
		}

		/** Flächen vergessen, die Zähler für die Grenzen bleiben. */
		void reset() {
			quads = new ArrayList<>();
			colors = new ArrayList<>();
		}

		/** {@code bare}: ohne das eigene {@code at} und {@code rot} des Teils (es liegt dann um seinen Drehpunkt). */
		void part(JsonElement raw, Matrix4f parent, int depth, boolean bare) {
			if (!raw.isJsonObject()) {
				throw new IllegalArgumentException(id + ": Teil ist kein Objekt");
			}
			if (depth > MAX_DEPTH) {
				throw new IllegalArgumentException(id + ": Teile höchstens " + MAX_DEPTH + " Ebenen tief");
			}
			if (++parts > MAX_PARTS) {
				throw new IllegalArgumentException(id + ": mehr als " + MAX_PARTS + " Teile");
			}
			JsonObject json = raw.getAsJsonObject();
			// wie ein Modellteil in Minecraft: erst zum Drehpunkt, dann um z, y, x drehen
			Matrix4f matrix = new Matrix4f(parent);
			if (json.has("at")) {
				float[] at = triple(json.get("at"), MAX_PIXELS, id + ": at");
				if (!bare) {
					matrix.translate(at[0], at[1], at[2]);
				}
			}
			if (json.has("rot")) {
				float[] rot = triple(json.get("rot"), MAX_DEGREES, id + ": rot");
				if (!bare) {
					matrix.rotateZYX((float) Math.toRadians(rot[2]), (float) Math.toRadians(rot[1]), (float) Math.toRadians(rot[0]));
				}
			}
			boolean any = false;
			JsonElement rawBoxes = json.get("boxes");
			if (rawBoxes != null) {
				if (!rawBoxes.isJsonArray()) {
					throw new IllegalArgumentException(id + ": boxes ist keine Liste");
				}
				JsonArray list = rawBoxes.getAsJsonArray();
				if (!list.isEmpty()) {
					String rawColor = CatalogEntry.string(json, "color");
					if (rawColor == null || !COLOR.matcher(rawColor).matches()) {
						throw new IllegalArgumentException(id + ": color fehlt (#rrggbb)");
					}
					int color = 0xFF000000 | Integer.parseInt(rawColor.substring(1), 16);
					for (JsonElement box : list) {
						box(box, matrix, color);
					}
					any = true;
				}
			}
			JsonElement children = json.get("parts");
			if (children != null) {
				if (!children.isJsonArray()) {
					throw new IllegalArgumentException(id + ": parts ist keine Liste");
				}
				for (JsonElement child : children.getAsJsonArray()) {
					part(child, matrix, depth + 1, false);
					any = true;
				}
			}
			if (!any) {
				throw new IllegalArgumentException(id + ": Teil ohne Würfel und ohne Unterteile");
			}
		}

		private void box(JsonElement raw, Matrix4f matrix, int color) {
			if (!raw.isJsonArray() || raw.getAsJsonArray().size() != 6) {
				throw new IllegalArgumentException(id + ": Würfel braucht [x, y, z, Breite, Höhe, Tiefe]");
			}
			if (++boxes > MAX_BOXES) {
				throw new IllegalArgumentException(id + ": mehr als " + MAX_BOXES + " Würfel");
			}
			JsonArray array = raw.getAsJsonArray();
			float[] v = new float[6];
			for (int i = 0; i < 6; i++) {
				v[i] = i < 3
					? CatalogEntry.number(array.get(i), -MAX_PIXELS, MAX_PIXELS, id + ": Würfel-Position")
					: CatalogEntry.number(array.get(i), 0.05f, MAX_PIXELS, id + ": Würfel-Größe");
			}
			float x0 = v[0];
			float y0 = v[1];
			float z0 = v[2];
			float x1 = x0 + v[3];
			float y1 = y0 + v[4];
			float z1 = z0 + v[5];
			quad(matrix, color, -1f, 0f, 0f, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
			quad(matrix, color, 1f, 0f, 0f, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
			quad(matrix, color, 0f, -1f, 0f, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
			quad(matrix, color, 0f, 1f, 0f, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
			quad(matrix, color, 0f, 0f, -1f, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
			quad(matrix, color, 0f, 0f, 1f, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		}

		private void quad(Matrix4f matrix, int color, float nx, float ny, float nz, float... corners) {
			float[] quad = new float[QUAD];
			Vector3f point = new Vector3f();
			for (int i = 0; i < 4; i++) {
				matrix.transformPosition(corners[i * 3], corners[i * 3 + 1], corners[i * 3 + 2], point);
				quad[i * 3] = point.x;
				quad[i * 3 + 1] = point.y;
				quad[i * 3 + 2] = point.z;
			}
			matrix.transformDirection(nx, ny, nz, point).normalize();
			quad[12] = point.x;
			quad[13] = point.y;
			quad[14] = point.z;
			quads.add(quad);
			colors.add(color);
		}
	}
}
