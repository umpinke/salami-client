package dev.salamiclient.capes.art;

/** Neon: Sonnenuntergang mit gestreifter Sonne über einem Leuchtgitter, das auf einen zuläuft. */
final class NeonCape extends CapeDesign {
	private static final int[] SKY = {0x0a0420, 0x260a50, 0x74146e, 0xf0487f, 0xffb46b};
	private static final int[] SUN = {0xfff27a, 0xffb347, 0xff5f8f, 0xd43cc0};
	private static final double HORIZON = 0.95;
	private static final double SUN_Y = 0.60;
	private static final double SUN_R = 0.31;

	NeonCape() {
		super("neon", 24, 62);
	}

	@Override
	protected void shade(Rgb out, int face, double x, double y, double t, double px, double[] pre) {
		if (y < HORIZON) {
			sky(out, x, y, t, px);
		} else {
			floor(out, x, y, t);
		}
		out.add(0xff9ad8, Math.exp(-Math.abs(y - HORIZON) / 0.012) * 0.8);
		trim(out, face, x, y, px, 0x40f0ff, 0.6);
	}

	private static void sky(Rgb out, double x, double y, double t, double px) {
		out.ramp(SKY, Math.pow(y / HORIZON, 1.35));
		if (y < 0.5) {
			out.add(0xffffff, Art.star(x, y, 12, t, 0.45, 5) * 0.8 * (1 - y / 0.5));
		}
		double dx = x - 0.5;
		double dy = y - SUN_Y;
		double d = Math.sqrt(dx * dx + dy * dy) - SUN_R;
		out.add(0xff4d8a, Math.exp(-Math.max(0, d) / 0.13) * 0.4);
		double disc = Art.cover(d, px);
		if (disc > 0) {
			// Streifen wandern nach unten und werden dabei breiter
			double from = SUN_Y - 0.06;
			double below = y - from;
			double gap = 0;
			if (below > 0) {
				double stripe = Art.fract(below / 0.085 - t);
				double width = 0.12 + 0.5 * Art.clamp01(below / (SUN_R + 0.06));
				double edge = Math.min(stripe, width - stripe) * 0.085;
				gap = stripe < width ? Art.clamp01(0.5 + edge / (px * 1.5)) : Art.clamp01(0.5 - Math.min(stripe - width, 1 - stripe) * 0.085 / (px * 1.5));
				gap *= Art.smooth(0, 0.03, below);
			}
			out.mixRamp(SUN, (dy + SUN_R) / (2 * SUN_R), disc * (1 - gap));
		}
		// Bergkette links und rechts, in der Mitte bleibt die Sonne frei
		double side = Art.smooth(0.10, 0.5, Math.abs(x - 0.5));
		double ridge = HORIZON - side * (0.05 + 0.22 * Art.fbm(x * 5 + 2, 0.5, 3, 15));
		out.mix(0x150530, Art.cover(ridge - y, px));
		out.add(0x40f0ff, Art.glow(y - ridge, 0.005) * 0.75 * Art.smooth(0, 0.08, side));
	}

	private static void floor(Rgb out, double x, double y, double t) {
		double depth = y - HORIZON + 0.012;
		out.set(0x10042a).mix(0x04010c, Art.clamp01(depth / 0.65));
		double scale = 0.11 / depth;
		double near = Art.smooth(0, 0.07, depth);
		// Querlinien kommen auf einen zu: eine Linienbreite Weg je Schleife
		double rows = scale * 3.4 + t;
		double fr = Art.fract(rows);
		double rowDist = Math.min(fr, 1 - fr) / (3.4 * 0.11 / (depth * depth));
		double cols = (x - 0.5) * scale * 9 + 0.5;
		double fc = Art.fract(cols);
		double colDist = Math.min(fc, 1 - fc) / (scale * 9);
		double line = Math.min(rowDist, colDist);
		out.add(0xff3df0, Art.glow(line, 0.0045) * near);
		out.add(0x8a2bff, Art.glow(line, 0.02) * near * 0.35);
		out.add(0xffd0ff, Art.glow(line, 0.0016) * near * 0.6);
	}
}
