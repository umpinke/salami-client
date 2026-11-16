package dev.salamiclient.capes.art;

/**
 * Malt ein {@link CapeDesign} ins Cape-Layout von Minecraft (64x32 Einheiten: Cape-Box
 * 10x16x1 bei (0,0), Elytra-Box 10x20x2 bei (22,0)).
 *
 * <p>Gemalt wird nur der benutzte Ausschnitt ({@link #UNITS_W} x {@link #UNITS_H} Einheiten
 * oben links), ein Bild je Frame mit 16 Bit pro Pixel (RGB 5-6-5, fein gerastert). Welche
 * Pixel durchsichtig sind, steht fest ({@link #opaque}) und wird nicht mitgespeichert.
 */
public final class CapeRaster {
	/** Breite des ganzen Layouts in Einheiten; die Textur ist {@code 64 * scale} breit. */
	public static final int LAYOUT_W = 64;
	public static final int LAYOUT_H = 32;
	/** Benutzter Ausschnitt in Einheiten. */
	public static final int UNITS_W = 46;
	public static final int UNITS_H = 22;

	private static final double INSIDE_DIM = 0.5;
	private static final double EDGE_DIM = 0.62;
	private static final double WING_SCALE_Y = 0.8;

	/** Form der Elytra wie in der Original-Textur: Zeile → benutzte Spalten (von, bis). */
	private static final boolean[] WING = new boolean[UNITS_W * UNITS_H];
	private static final boolean[] OPAQUE = new boolean[UNITS_W * UNITS_H];
	private static final double[] BAYER = {
		0, 8, 2, 10,
		12, 4, 14, 6,
		3, 11, 1, 9,
		15, 7, 13, 5,
	};

	static {
		wingRow(0, 31, 39);
		wingRow(1, 32, 33);
		wingRow(2, 34, 41);
		wingRow(3, 34, 42);
		for (int y = 4; y <= 6; y++) {
			wingRow(y, 35, 43);
		}
		for (int y = 7; y <= 10; y++) {
			wingRow(y, 35, 44);
		}
		for (int y = 11; y <= 21; y++) {
			wingRow(y, 22, 22);
			wingRow(y, y <= 15 ? 36 : y <= 18 ? 37 : y <= 20 ? 38 : 39, 45);
		}
		for (int y = 0; y < UNITS_H; y++) {
			for (int x = 0; x < UNITS_W; x++) {
				boolean capeBox = x < 22 && y < 17 && (y >= 1 || (x >= 1 && x < 21));
				OPAQUE[y * UNITS_W + x] = capeBox || WING[y * UNITS_W + x];
			}
		}
	}

	private CapeRaster() {}

	private static void wingRow(int y, int from, int to) {
		for (int x = from; x <= to; x++) {
			WING[y * UNITS_W + x] = true;
		}
	}

	private static boolean wing(int x, int y) {
		return x >= 0 && y >= 0 && x < UNITS_W && y < UNITS_H && WING[y * UNITS_W + x];
	}

	/** Ob die Einheit (x, y) des Layouts gemalt wird; alles andere bleibt durchsichtig. */
	public static boolean opaque(int x, int y) {
		return x >= 0 && y >= 0 && x < UNITS_W && y < UNITS_H && OPAQUE[y * UNITS_W + x];
	}

	public static int width(int scale) {
		return UNITS_W * scale;
	}

	public static int height(int scale) {
		return UNITS_H * scale;
	}

	/**
	 * Malt die Zeilen {@code rowFrom..rowTo-1} eines Frames nach {@code out}
	 * ({@code width(scale) * height(scale)} Werte, RGB 5-6-5).
	 */
	public static void render(CapeDesign design, int scale, int frame, short[] out, int rowFrom, int rowTo, Rgb rgb) {
		int w = width(scale);
		double t = frame / (double) design.frames;
		double px = 0.1 / scale;
		double[] pre = design.prepare(t);
		for (int py = rowFrom; py < rowTo; py++) {
			double uy = (py + 0.5) / scale;
			int cy = py / scale;
			for (int qx = 0; qx < w; qx++) {
				int cx = qx / scale;
				if (!OPAQUE[cy * UNITS_W + cx]) {
					continue;
				}
				double ux = (qx + 0.5) / scale;
				if (cx < 22) {
					capeBox(design, rgb, ux, uy, t, px, pre);
				} else {
					wing(design, rgb, cx, cy, ux, uy, t, px, pre);
				}
				out[py * w + qx] = pack(rgb, qx, py);
			}
		}
	}

	private static void capeBox(CapeDesign design, Rgb rgb, double ux, double uy, double t, double px, double[] pre) {
		if (uy >= 1 && ux >= 1 && ux < 11) {
			design.shade(rgb, CapeDesign.OUTSIDE, (ux - 1) / 10, (uy - 1) / 10, t, px, pre);
		} else if (uy >= 1 && ux >= 12) {
			// Innenseite: gespiegelt, damit das Motiv durchs Tuch hindurch an derselben Stelle sitzt
			design.shade(rgb, CapeDesign.INSIDE, 1 - (ux - 12) / 10, (uy - 1) / 10, t, px, pre);
			rgb.mul(INSIDE_DIM);
		} else {
			// Kanten: das Motiv läuft am Rand weiter
			double x;
			double y;
			if (uy < 1) {
				boolean top = ux < 11;
				x = Art.clamp01(((top ? ux - 1 : ux - 11)) / 10);
				y = top ? 0.004 : CapeDesign.HEIGHT - 0.004;
			} else {
				x = ux < 1 ? 0.004 : 0.996;
				y = (uy - 1) / 10;
			}
			design.shade(rgb, CapeDesign.INSIDE, x, y, t, px, pre);
			rgb.mul(EDGE_DIM);
		}
	}

	private static void wing(CapeDesign design, Rgb rgb, int cx, int cy, double ux, double uy, double t, double px, double[] pre) {
		if (cy < 2 || cx < 34) {
			// schmale Kanten der Flügel-Box
			design.shade(rgb, CapeDesign.WING, 0.5, 0.2, t, px, pre);
			rgb.mul(0.4);
			return;
		}
		design.shade(rgb, CapeDesign.WING, (ux - 36) / 10, (uy - 2) / 10 * WING_SCALE_Y, t, px, pre);
		// dunkler Saum entlang der Flügelform
		double fx = ux - cx;
		double fy = uy - cy;
		double rim = 1;
		if (!wing(cx - 1, cy)) {
			rim = Math.min(rim, fx);
		}
		if (!wing(cx + 1, cy)) {
			rim = Math.min(rim, 1 - fx);
		}
		if (!wing(cx, cy - 1)) {
			rim = Math.min(rim, fy);
		}
		if (!wing(cx, cy + 1)) {
			rim = Math.min(rim, 1 - fy);
		}
		rgb.mul(0.45 + 0.55 * Art.smooth(0.05, 0.4, rim));
	}

	private static short pack(Rgb c, int x, int y) {
		double d = (BAYER[(y & 3) << 2 | (x & 3)] + 0.5) / 16;
		int r = (int) (Art.clamp01(c.r) * 31 + d);
		int g = (int) (Art.clamp01(c.g) * 63 + d);
		int b = (int) (Art.clamp01(c.b) * 31 + d);
		return (short) (r << 11 | g << 5 | b);
	}

	/** Rot/Grün/Blau (je 0..255) eines gespeicherten Pixels als {@code 0xRRGGBB}. */
	public static int rgb(short packed) {
		int v = packed & 0xFFFF;
		int r = ((v >> 11) * 527 + 23) >> 6;
		int g = ((v >> 5 & 63) * 259 + 33) >> 6;
		int b = ((v & 31) * 527 + 23) >> 6;
		return r << 16 | g << 8 | b;
	}
}
