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
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Ein Hut als Daten: farbige Würfel, die gemeinsam verschoben und gedreht werden. Die Liste aller
 * Hüte kommt vom Salami-Server ({@link HatCatalog}), deshalb steht hier kein einzelner Hut im Code –
 * ein neuer Hut ist ein Eintrag in {@code server/capes/hats.json} (Format dort in {@code hats.mjs}).
 *
 * <p>Beim Einlesen werden die Würfel einmal in Flächen umgerechnet (in Pixeln des Spielermodells,
 * oben ist negativ). Gezeichnet werden sie ohne eigenes Bild: weiße Textur, Farbe je Fläche.
 */
public final class Hat {
	/** Wo der Hut hängt. */
	public enum Anchor {
		/** Sitzt auf dem Kopf und folgt ihm (Drehen, Nicken, Schleichen). */
		HEAD,
		/** Schwebt über der Schulter und folgt dem Oberkörper. */
		SHOULDER
	}

	/** Wie auf dem Server ({@code hats.mjs}). */
	private static final int MAX_BOXES = 64;
	private static final int MAX_PARTS = 32;
	private static final int MAX_DEPTH = 3;
	private static final float MAX_PIXELS = 32f;
	private static final float MAX_DEGREES = 360f;
	private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
	/** Werte je Fläche in {@link #quads}: vier Ecken (x, y, z) und die Normale. */
	public static final int QUAD = 15;

	private final String id;
	private final Map<String, String> names;
	private final Anchor anchor;
	private final float scale;
	private final boolean glow;
	private final float bob;
	private final float sway;
	private final float[] quads;
	private final int[] colors;

	private Hat(String id, Map<String, String> names, Anchor anchor, float scale, boolean glow, float bob, float sway,
			float[] quads, int[] colors) {
		this.id = id;
		this.names = names;
		this.anchor = anchor;
		this.scale = scale;
		this.glow = glow;
		this.bob = bob;
		this.sway = sway;
		this.quads = quads;
		this.colors = colors;
	}

	/** Name für Server und Auswahl ({@code top_hat}). */
	public String id() {
		return id;
	}

	public Anchor anchor() {
		return anchor;
	}

	/** Größe gegenüber dem Modell in Spieler-Pixeln. */
	public float scale() {
		return scale;
	}

	/** Leuchtet auch im Dunkeln (Heiligenschein). */
	public boolean glow() {
		return glow;
	}

	/** Schwebt so viele Pixel auf und ab. */
	public float bob() {
		return bob;
	}

	/** Pendelt so weit hin und her (Bogenmaß). */
	public float sway() {
		return sway;
	}

	/** Flächen des Modells: je {@link #QUAD} Werte, siehe dort. Nicht verändern. */
	public float[] quads() {
		return quads;
	}

	/** Farbe (ARGB) je Fläche. Nicht verändern. */
	public int[] colors() {
		return colors;
	}

	/** Anzeigename in der Sprache des Spiels, sonst englisch. */
	public String displayName() {
		return CatalogEntry.displayName(names, id);
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

	/** Liest und prüft einen Hut; wirft {@link IllegalArgumentException} mit dem Grund, wenn etwas nicht stimmt. */
	public static Hat parse(JsonObject json) {
		String id = CatalogEntry.id(json);
		if (id.equals("none")) {
			throw new IllegalArgumentException("id \"none\" ist vergeben (kein Hut)");
		}
		Map<String, String> names = CatalogEntry.names(json, id);
		Anchor anchor = Anchor.HEAD;
		String rawAnchor = CatalogEntry.string(json, "anchor");
		if (rawAnchor != null) {
			anchor = switch (rawAnchor) {
				case "head" -> Anchor.HEAD;
				case "shoulder" -> Anchor.SHOULDER;
				default -> throw new IllegalArgumentException(id + ": anchor unbekannt");
			};
		}
		float scale = json.has("scale") ? CatalogEntry.number(json.get("scale"), 0.1f, 3f, id + ": scale") : 1f;
		boolean glow = json.has("glow") && json.get("glow").isJsonPrimitive() && json.get("glow").getAsBoolean();
		float bob = json.has("bob") ? CatalogEntry.number(json.get("bob"), 0f, 4f, id + ": bob") : 0f;
		float sway = json.has("sway") ? (float) Math.toRadians(CatalogEntry.number(json.get("sway"), 0f, 45f, id + ": sway")) : 0f;

		JsonElement rawParts = json.get("parts");
		if (rawParts == null || !rawParts.isJsonArray() || rawParts.getAsJsonArray().isEmpty()) {
			throw new IllegalArgumentException(id + ": parts fehlt");
		}
		Baker baker = new Baker(id);
		for (JsonElement part : rawParts.getAsJsonArray()) {
			baker.part(part, new Matrix4f(), 1);
		}
		if (baker.colors.isEmpty()) {
			throw new IllegalArgumentException(id + ": mindestens ein Würfel nötig");
		}
		float[] quads = new float[baker.quads.size() * QUAD];
		int[] colors = new int[baker.colors.size()];
		for (int i = 0; i < colors.length; i++) {
			System.arraycopy(baker.quads.get(i), 0, quads, i * QUAD, QUAD);
			colors[i] = baker.colors.get(i);
		}
		return new Hat(id, names, anchor, scale, glow, bob, sway, quads, colors);
	}

	/** Rechnet die Teile eines Hutes in Flächen um und zählt dabei die Grenzen mit. */
	private static final class Baker {
		private final String id;
		private final List<float[]> quads = new ArrayList<>();
		private final List<Integer> colors = new ArrayList<>();
		private int parts;
		private int boxes;

		Baker(String id) {
			this.id = id;
		}

		void part(JsonElement raw, Matrix4f parent, int depth) {
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
				float[] at = triple(json.get("at"), MAX_PIXELS, "at");
				matrix.translate(at[0], at[1], at[2]);
			}
			if (json.has("rot")) {
				float[] rot = triple(json.get("rot"), MAX_DEGREES, "rot");
				matrix.rotateZYX((float) Math.toRadians(rot[2]), (float) Math.toRadians(rot[1]), (float) Math.toRadians(rot[0]));
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
					part(child, matrix, depth + 1);
					any = true;
				}
			}
			if (!any) {
				throw new IllegalArgumentException(id + ": Teil ohne Würfel und ohne Unterteile");
			}
		}

		private float[] triple(JsonElement raw, float limit, String what) {
			if (!raw.isJsonArray() || raw.getAsJsonArray().size() != 3) {
				throw new IllegalArgumentException(id + ": " + what + " braucht [x, y, z]");
			}
			JsonArray array = raw.getAsJsonArray();
			float[] values = new float[3];
			for (int i = 0; i < 3; i++) {
				values[i] = CatalogEntry.number(array.get(i), -limit, limit, id + ": " + what);
			}
			return values;
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
