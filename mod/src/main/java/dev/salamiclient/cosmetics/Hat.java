package dev.salamiclient.cosmetics;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.Map;

/**
 * Ein Hut als Daten: farbige Würfel ({@link BoxModel}), die gemeinsam verschoben und gedreht werden.
 * Die Liste aller Hüte kommt vom Salami-Server ({@link HatCatalog}), deshalb steht hier kein
 * einzelner Hut im Code – ein neuer Hut ist ein Eintrag in {@code server/capes/hats.json} (Format
 * dort in {@code hats.mjs}).
 *
 * <p>Buddys ({@link BuddyCatalog}) sind dieselbe Art Eintrag ohne festen Platz: ihr Nullpunkt liegt
 * unten in der Mitte, wo sie stehen, wählt der Spieler.
 */
public final class Hat {
	/** Wo der Hut hängt. */
	public enum Anchor {
		/** Sitzt auf dem Kopf und folgt ihm (Drehen, Nicken, Schleichen). */
		HEAD,
		/** Schwebt über der Schulter und folgt dem Oberkörper. */
		SHOULDER
	}

	private final String id;
	private final Map<String, String> names;
	private final Anchor anchor;
	private final float scale;
	private final boolean glow;
	private final float bob;
	private final float sway;
	private final BoxModel model;

	private Hat(String id, Map<String, String> names, Anchor anchor, float scale, boolean glow, float bob, float sway,
			BoxModel model) {
		this.id = id;
		this.names = names;
		this.anchor = anchor;
		this.scale = scale;
		this.glow = glow;
		this.bob = bob;
		this.sway = sway;
		this.model = model;
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

	/** Die Würfel. */
	public BoxModel model() {
		return model;
	}

	/** Flächen des Modells: je {@link BoxModel#QUAD} Werte, siehe dort. Nicht verändern. */
	public float[] quads() {
		return model.quads();
	}

	/** Farbe (ARGB) je Fläche. Nicht verändern. */
	public int[] colors() {
		return model.colors();
	}

	/** Anzeigename in der Sprache des Spiels, sonst englisch. */
	public String displayName() {
		return CatalogEntry.displayName(names, id);
	}

	/** Siehe {@link BoxModel#emit}. */
	public void emit(PoseStack.Pose pose, VertexConsumer buffer, boolean mirror, int light) {
		model.emit(pose, buffer, mirror, light);
	}

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
		return new Hat(id, names, anchor, scale, glow, bob, sway, BoxModel.parse(json.get("parts"), id));
	}
}
