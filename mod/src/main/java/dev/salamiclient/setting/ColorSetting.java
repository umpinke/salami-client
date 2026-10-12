package dev.salamiclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * ARGB-Farbe. In der ClickGUI wird per Klick durch eine Palette geschaltet;
 * beliebige Farben lassen sich in der Config als {@code #AARRGGBB} setzen.
 */
public final class ColorSetting extends Setting<Integer> {
	/** Palette für die ClickGUI – Salami-Töne zuerst. */
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

	public ColorSetting(String id, int defaultArgb) {
		super(id, defaultArgb);
	}

	public int argb() {
		return get();
	}

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
	public JsonElement toJson() {
		return new JsonPrimitive(String.format("#%08X", get()));
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json == null || !json.isJsonPrimitive()) {
			return;
		}
		String raw = json.getAsString().trim();
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
	}
}
