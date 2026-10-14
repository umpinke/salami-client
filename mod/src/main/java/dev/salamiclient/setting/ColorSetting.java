package dev.salamiclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.Mth;

/**
 * ARGB-Farbe, frei wählbar im Farbwähler (Farbfeld, Farbton, Deckkraft, Hex) – optional als
 * Regenbogen, der langsam durch alle Farbtöne läuft. Gespeichert als {@code "#AARRGGBB"}, mit
 * Regenbogen als {@code {"color": "#AARRGGBB", "chroma": true}}.
 */
public final class ColorSetting extends Setting<Integer> {
	/** Schnellauswahl im Farbwähler – Salami-Töne zuerst. */
	public static final int[] PALETTE = {
		0xFFB23A3A, // Salami-Rot
		0xFFF3E3D3, // Fett-Weiß
		0xFF7A4A35, // Pelle-Braun
		0xFF2B1D1A, // Pfeffer-Schwarz
		0xFFFFFFFF,
		0xFFFF5555,
		0xFFFFAA00,
		0xFFFFFF55,
		0xFF55FF55,
		0xFF55FFFF,
		0xFF5555FF,
		0xFFFF55FF,
	};

	/** Zeit für einen ganzen Regenbogen-Durchlauf. */
	private static final long CHROMA_MILLIS = 4000;

	private boolean chroma;
	private boolean chromaAllowed = true;
	private boolean alphaAllowed = true;

	public ColorSetting(String id, int defaultArgb) {
		super(id, defaultArgb);
	}

	/** Kein Regenbogen (z. B. wenn jede Farbänderung eine Textur neu berechnet). */
	public ColorSetting noChroma() {
		chromaAllowed = false;
		chroma = false;
		return this;
	}

	/** Deckkraft wird nicht genutzt – Regler im Farbwähler ausblenden. */
	public ColorSetting noAlpha() {
		alphaAllowed = false;
		return this;
	}

	public boolean chromaAllowed() {
		return chromaAllowed;
	}

	public boolean alphaAllowed() {
		return alphaAllowed;
	}

	public boolean isChroma() {
		return chroma;
	}

	public void setChroma(boolean on) {
		boolean next = on && chromaAllowed;
		if (next != chroma) {
			chroma = next;
			notifyChanged();
		}
	}

	/** Aktuelle Farbe – beim Regenbogen der Farbton von jetzt (Deckkraft und Helligkeit bleiben). */
	public int argb() {
		int base = get();
		if (!chroma) {
			return base;
		}
		float hue = (System.currentTimeMillis() % CHROMA_MILLIS) / (float) CHROMA_MILLIS;
		float[] hsv = toHsv(base);
		return fromHsv(hue, Math.max(0.6F, hsv[1]), Math.max(0.6F, hsv[2]), base >>> 24);
	}

	/** Früher: per Klick durch die Palette schalten (bleibt für Tastatur-/Rechtsklick-Bedienung). */
	public void cyclePalette(int direction) {
		int current = 0;
		for (int i = 0; i < PALETTE.length; i++) {
			if (PALETTE[i] == get()) {
				current = i;
				break;
			}
		}
		set(PALETTE[Math.floorMod(current + direction, PALETTE.length)]);
	}

	@Override
	public void resetAll() {
		reset();
		setChroma(false);
	}

	// ------------------------------------------------------------------ HSV

	/** {Farbton, Sättigung, Helligkeit}, jeweils 0..1. */
	public static float[] toHsv(int argb) {
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		int max = Math.max(r, Math.max(g, b));
		int min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		float hue;
		if (delta == 0) {
			hue = 0;
		} else if (max == r) {
			hue = (g - b) / delta;
			if (hue < 0) {
				hue += 6;
			}
		} else if (max == g) {
			hue = (b - r) / delta + 2;
		} else {
			hue = (r - g) / delta + 4;
		}
		return new float[] {hue / 6F, max == 0 ? 0 : delta / max, max / 255F};
	}

	public static int fromHsv(float hue, float saturation, float value, int alpha) {
		float h = hue - (float) Math.floor(hue);
		return Mth.hsvToArgb(h, Mth.clamp(saturation, 0, 1), Mth.clamp(value, 0, 1), Mth.clamp(alpha, 0, 255));
	}

	/** {@code #RRGGBB} bei voller Deckkraft, sonst {@code #AARRGGBB}. */
	public static String toHex(int argb) {
		return (argb >>> 24) == 0xFF ? String.format("#%06X", argb & 0xFFFFFF) : String.format("#%08X", argb);
	}

	/** Liest {@code RRGGBB}/{@code AARRGGBB} (mit oder ohne {@code #}); {@code null} bei ungültiger Eingabe. */
	public static Integer parseHex(String raw) {
		String hex = raw.trim();
		if (hex.startsWith("#")) {
			hex = hex.substring(1);
		}
		if (hex.length() != 6 && hex.length() != 8) {
			return null;
		}
		try {
			long parsed = Long.parseLong(hex, 16);
			return hex.length() == 6 ? (int) (0xFF000000L | parsed) : (int) parsed;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	// ------------------------------------------------------------------ JSON

	@Override
	public JsonElement toJson() {
		JsonPrimitive color = new JsonPrimitive(String.format("#%08X", get()));
		if (!chroma) {
			return color;
		}
		JsonObject json = new JsonObject();
		json.add("color", color);
		json.addProperty("chroma", true);
		return json;
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json == null) {
			return;
		}
		JsonElement color = json;
		boolean rainbow = false;
		if (json.isJsonObject()) {
			JsonObject obj = json.getAsJsonObject();
			color = obj.get("color");
			JsonElement flag = obj.get("chroma");
			rainbow = flag != null && flag.isJsonPrimitive() && flag.getAsJsonPrimitive().isBoolean() && flag.getAsBoolean();
		}
		if (color == null || !color.isJsonPrimitive()) {
			return;
		}
		String raw = color.getAsString().trim();
		if (raw.startsWith("#")) {
			raw = raw.substring(1);
		}
		try {
			long parsed = Long.parseLong(raw, 16);
			// #RRGGBB ohne Alpha -> voll deckend
			set(raw.length() <= 6 ? (int) (0xFF000000L | parsed) : (int) parsed);
		} catch (NumberFormatException ignored) {
			// ungültig -> Standardwert bleibt
		}
		setChroma(rainbow);
	}
}
