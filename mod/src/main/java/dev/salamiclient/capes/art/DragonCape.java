package dev.salamiclient.capes.art;

/** Drache: smaragdgrüne Schuppen, über die ein goldener Schimmer läuft, und ein Auge, das einen ansieht. */
final class DragonCape extends CapeDesign {
	private static final int[] SCALE = {0x04201a, 0x0a4a36, 0x149068, 0x52dca4};
	private static final int[] IRIS = {0xfff2a0, 0xffb520, 0xe2560a, 0x5a1602};
	private static final double STEP_X = 1 / 7.0;
	private static final double STEP_Y = 0.074;
	private static final double RADIUS = 0.086;
	private static final double EYE_X = 0.5;
	private static final double EYE_Y = 0.62;
	private static final double EYE_W = 0.34;
	private static final double EYE_H = 0.15;

	DragonCape() {
		super("dragon", 32, 62);
	}

	@Override
	protected void shade(Rgb out, int face, double x, double y, double t, double px, double[] pre) {
		scales(out, x, y, t, px);
		if (face != WING) {
			eye(out, x, y, t, px);
		}
		trim(out, face, x, y, px, 0xffd36b, 0.6);
	}

	/** Schuppen wie Dachziegel: die obere Reihe liegt über der unteren. */
	private static void scales(Rgb out, double x, double y, double t, double px) {
		int row = (int) Math.floor(y / STEP_Y) - 1;
		for (int j = row; j <= row + 2; j++) {
			double cy = j * STEP_Y;
			double shift = (j & 1) == 0 ? 0 : 0.5;
			int i = (int) Math.round(x / STEP_X - shift);
			double cx = (i + shift) * STEP_X;
			double dx = (x - cx) / RADIUS;
			double dy = (y - cy) / (RADIUS * 1.25);
			double d = Math.sqrt(dx * dx + dy * dy);
			if (d >= 1 && j < row + 2) {
				continue;
			}
			double tint = Art.rnd(i, j, 5);
			out.ramp(SCALE, 0.12 + 0.62 * Art.clamp01(dy * 0.5 + 0.5) + 0.2 * tint - 0.25 * Math.abs(dx));
			// Kiel in der Mitte und heller Rand unten
			out.add(0x9cffd8, Math.exp(-(dx * dx) / 0.012) * 0.16 * Art.smooth(-0.6, 0.6, dy));
			out.add(0xbfffe6, Art.smooth(0.74, 0.93, d) * Art.smooth(0.1, 0.8, dy) * 0.42);
			out.mul(1 - 0.75 * Art.smooth(0.90, 1.0, d));
			// goldener Schimmer wandert schräg nach unten – je Schuppe einheitlich
			double shimmer = 0.5 + 0.5 * Math.sin(Art.TAU * (t - cx * 0.55 - cy * 0.75 + tint * 0.08));
			shimmer = shimmer * shimmer * shimmer;
			out.mix(0xf2b632, shimmer * 0.42 * Art.smooth(-0.9, 0.9, dy));
			out.add(0xfff6d0, shimmer * Art.smooth(0.74, 0.93, d) * Art.smooth(0.1, 0.8, dy) * 0.5);
			return;
		}
	}

	private static void eye(Rgb out, double x, double y, double t, double px) {
		double dx = (x - EYE_X) / EYE_W;
		double dy = (y - EYE_Y) / EYE_H;
		// Mandelform: zwei Bögen
		double lid = Math.abs(dy) - (1 - dx * dx) * (1 - 0.25 * dx * dx);
		if (lid > 0.5 || Math.abs(dx) > 1.15) {
			return;
		}
		double shape = lid * EYE_H;
		out.mul(1 - 0.85 * Art.smooth(0.07, -0.01, shape));
		double open = Art.cover(shape + 0.012, px);
		if (open <= 0) {
			return;
		}
		double ex = x - EYE_X;
		double ey = y - EYE_Y;
		double r = Math.sqrt(ex * ex + ey * ey) / 0.17;
		double angle = Math.atan2(ey, ex);
		double fibers = Art.noise(angle * 9, r * 3, 44) * 0.5 + Art.noise(angle * 23, r * 5, 45) * 0.5;
		double glow = 0.85 + 0.15 * Math.sin(Art.TAU * t);
		Rgb iris = out;
		double keepR = out.r;
		double keepG = out.g;
		double keepB = out.b;
		iris.ramp(IRIS, r * 0.75 + 0.3 * (fibers - 0.5));
		iris.mul(glow * (1 - 0.5 * Art.smooth(0.9, 1.7, r)));
		// Pupille: senkrechter Schlitz, der sich langsam weitet und verengt
		double slit = 0.022 + 0.012 * Math.sin(Art.TAU * t);
		double pupil = Math.abs(ex) - slit * (1 - (ey / (EYE_H * 0.95)) * (ey / (EYE_H * 0.95)));
		iris.mix(0x050302, Art.cover(pupil, px));
		// Glanzlicht
		double hx = ex + 0.075;
		double hy = ey + 0.055;
		iris.mix(0xffffff, Art.cover(Math.sqrt(hx * hx + hy * hy * 2.2) - 0.022, px) * 0.9);
		// Schatten unterm Oberlid
		iris.mul(0.55 + 0.45 * Art.smooth(-EYE_H * 0.95, -EYE_H * 0.2, ey));
		out.set(Art.mix(keepR, iris.r, open), Art.mix(keepG, iris.g, open), Art.mix(keepB, iris.b, open));
	}
}
