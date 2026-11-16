package dev.salamiclient.minigames;

/** Kleine Farbhelfer (ARGB) für die Minispiele. */
public final class Colors {
	/** Salami-Rot */
	public static final int SALAMI = 0xFFB23A3A;
	/** dunkle Pelle */
	public static final int CASING = 0xFF6E2222;
	/** Fett-Weiß */
	public static final int FAT = 0xFFF3E3D3;
	/** Pfeffer-Schwarz */
	public static final int PEPPER = 0xFF2B1D1A;
	public static final int CHEESE = 0xFFE8C14A;
	public static final int CHEESE_DARK = 0xFFB8902A;
	public static final int BREAD = 0xFFD9A86C;
	public static final int CRUST = 0xFF9C6A38;
	public static final int WOOD = 0xFF7A4E32;
	public static final int PICKLE = 0xFF6E9E3A;
	public static final int GOLD = 0xFFFFC857;

	private Colors() {}

	/** Gleiche Farbe mit Deckkraft {@code alpha} (0–1, multipliziert mit der vorhandenen). */
	public static int alpha(int argb, float alpha) {
		int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	/** Mischung von {@code a} nach {@code b} (t = 0 … 1), auch die Deckkraft. */
	public static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int result = 0;
		for (int shift = 0; shift <= 24; shift += 8) {
			int ca = (a >>> shift) & 0xFF;
			int cb = (b >>> shift) & 0xFF;
			result |= Math.round(ca + (cb - ca) * t) << shift;
		}
		return result;
	}

	public static int darker(int argb, float amount) {
		return mix(argb, argb & 0xFF000000, amount);
	}

	public static int lighter(int argb, float amount) {
		return mix(argb, argb | 0x00FFFFFF, amount);
	}

	public static int opaque(int argb) {
		return argb | 0xFF000000;
	}
}
