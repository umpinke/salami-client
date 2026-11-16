package dev.salamiclient.capes.art;

/** Prisma: Mosaik aus Kristall-Dreiecken, durch das die Regenbogenfarben wandern; ein Glanz zieht drüber. */
final class PrismCape extends CapeDesign {
	private static final int[] HOLO = {0x29f0ff, 0x4d7cff, 0xa24dff, 0xff4dd2, 0xffb86b, 0x5dffb0, 0x29f0ff};
	private static final double CELLS = 6.5;
	private static final double ROW = Math.sqrt(3) / 2;

	PrismCape() {
		super("prism", 36, 83);
	}

	@Override
	protected void shade(Rgb out, int face, double x, double y, double t, double px, double[] pre) {
		// Dreiecksgitter: schiefes Raster, jede Raute in zwei Dreiecke geteilt
		double v = y * CELLS / ROW;
		double u = x * CELLS - v * 0.5;
		int iu = (int) Math.floor(u);
		int iv = (int) Math.floor(v);
		double fu = u - iu;
		double fv = v - iv;
		boolean upper = fu + fv > 1;
		int id = Art.hash(iu, iv, upper ? 91 : 92);
		double tone = (id >>> 8 & 0xFFFF) / 65535.0;
		double seam = Math.min(Math.min(upper ? 1 - fu : fu, upper ? 1 - fv : fv), Math.abs(fu + fv - 1) * 0.7071) / CELLS;

		// Farbton: fließt weich übers Tuch, jedes Dreieck leicht versetzt, einmal rundum je Schleife
		double flow = Art.fbm(x * 1.4 + 2, y * 1.4, 3, 93) * 1.6 + (x * 0.35 + y * 0.3);
		double hue = flow + tone * 0.22 + t;
		double light = 0.62 + 0.38 * Math.sin(Art.TAU * (t + tone * 3));
		out.ramp(HOLO, Art.fract(hue));
		// die meisten Facetten bleiben dunkel wie geschliffenes Glas, einzelne blitzen auf
		double shine = light * light * (0.35 + 0.65 * Art.rnd(iu, iv, upper ? 95 : 96));
		out.mul(0.16 + 0.95 * shine).mix(0xffffff, 0.22 * shine * shine).add(0x0a0c24, 1);
		// Facette: zur Kante hin dunkler, feine helle Fuge
		out.mul(0.72 + 0.28 * Art.smooth(0, 0.035, seam));
		out.mix(0xdfe8ff, Art.cover(seam - 0.0018, px) * 0.55);

		// Glanzstreifen: läuft einmal diagonal durchs Bild, Anfang und Ende liegen außerhalb
		double band = x + y * 0.6 - (-0.4 + t * 2.8);
		double gleam = Math.exp(-(band * band) / (0.07 * 0.07));
		out.add(0xffffff, gleam * (0.12 + 0.55 * tone));
		trim(out, face, x, y, px, 0xffffff, 0.7);
	}

}
