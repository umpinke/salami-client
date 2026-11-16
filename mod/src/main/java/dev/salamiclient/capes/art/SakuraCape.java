package dev.salamiclient.capes.art;

import java.util.Random;

/** Kirschblüte: blühender Ast vor dem Abendmond, Blütenblätter trudeln herunter. */
final class SakuraCape extends CapeDesign {
	private static final int[] SKY = {0x191040, 0x45206c, 0xa8437c, 0xf08a8c, 0xffd0a6};
	private static final int[] PETAL = {0xff7fb0, 0xffa9cc, 0xffd6e6};
	/** Ast als Strecken: x1, y1, x2, y2, Dicke am Anfang, Dicke am Ende. */
	private static final double[] BRANCH = {
		-0.25, 0.06, 0.12, 0.17, 0.030, 0.024,
		0.12, 0.17, 0.38, 0.26, 0.024, 0.018,
		0.38, 0.26, 0.62, 0.22, 0.018, 0.012,
		0.62, 0.22, 0.86, 0.30, 0.012, 0.005,
		0.38, 0.26, 0.47, 0.44, 0.014, 0.009,
		0.47, 0.44, 0.44, 0.60, 0.009, 0.004,
		0.62, 0.22, 0.72, 0.08, 0.010, 0.004,
		0.12, 0.17, 0.20, 0.36, 0.012, 0.004,
	};
	/** Blüten: x, y, Radius, Drehung. */
	private final double[] blossoms;
	private final Drift petals = new Drift(30, 314, 0.016, 0.034, 0.07, false);

	SakuraCape() {
		super("sakura", 36, 83);
		Random random = new Random(42);
		int perSegment = 4;
		blossoms = new double[BRANCH.length / 6 * perSegment * 4];
		int o = 0;
		for (int s = 0; s < BRANCH.length; s += 6) {
			for (int i = 0; i < perSegment; i++) {
				double f = (i + 0.3 + 0.6 * random.nextDouble()) / perSegment;
				blossoms[o++] = Art.mix(BRANCH[s], BRANCH[s + 2], f) + (random.nextDouble() - 0.5) * 0.09;
				blossoms[o++] = Art.mix(BRANCH[s + 1], BRANCH[s + 3], f) + (random.nextDouble() - 0.5) * 0.09;
				blossoms[o++] = 0.028 + 0.022 * random.nextDouble();
				blossoms[o++] = random.nextDouble() * Art.TAU;
			}
		}
	}

	@Override
	protected double[] prepare(double t) {
		return petals.at(t);
	}

	@Override
	protected void shade(Rgb out, int face, double x, double y, double t, double px, double[] pre) {
		out.ramp(SKY, y / HEIGHT + 0.05 * (Art.fbm(x * 2, y * 2, 2, 7) - 0.5));
		// Mond mit Hof
		double mx = x - 0.68;
		double my = y - 0.50;
		double moon = Math.sqrt(mx * mx + my * my) - 0.19;
		out.add(0xffd9c0, Math.exp(-Math.max(0, moon) / 0.10) * 0.35);
		out.mix(0xfff4e4, Art.cover(moon, px) * (0.92 - 0.10 * Art.fbm(x * 9, y * 9, 3, 8)));
		// Hügel
		double far = HEIGHT - 0.22 - 0.10 * Math.sin(x * 4.2 + 1) - 0.05 * Art.fbm(x * 4, 3, 3, 9);
		out.mix(0x5a2360, Art.cover(far - y, px) * 0.9);
		double near = HEIGHT - 0.10 - 0.07 * Math.sin(x * 3.1 + 4) - 0.04 * Art.fbm(x * 6, 5, 3, 10);
		out.mix(0x2a1238, Art.cover(near - y, px));

		// Ast: wiegt sich ganz leicht
		double bx = x - 0.006 * Math.sin(Art.TAU * t) * (0.4 + x);
		double by = y - 0.004 * Math.sin(Art.TAU * t + 1) * x;
		for (int s = 0; s < BRANCH.length; s += 6) {
			double ax = BRANCH[s];
			double ay = BRANCH[s + 1];
			double dx = BRANCH[s + 2] - ax;
			double dy = BRANCH[s + 3] - ay;
			double h = Art.clamp01(((bx - ax) * dx + (by - ay) * dy) / (dx * dx + dy * dy));
			double ex = bx - ax - dx * h;
			double ey = by - ay - dy * h;
			double d = Math.sqrt(ex * ex + ey * ey) - Art.mix(BRANCH[s + 4], BRANCH[s + 5], h);
			out.mix(0x1c0f1f, Art.cover(d, px));
		}
		for (int o = 0; o < blossoms.length; o += 4) {
			double dx = bx - blossoms[o];
			double dy = by - blossoms[o + 1];
			double radius = blossoms[o + 2];
			double dist = Math.sqrt(dx * dx + dy * dy);
			if (dist > radius * 1.1) {
				continue;
			}
			// fünf Blütenblätter
			double lobes = Math.abs(Math.cos(2.5 * (Math.atan2(dy, dx) + blossoms[o + 3])));
			double shape = dist - radius * (0.62 + 0.38 * lobes);
			out.mixRamp(PETAL, 1 - dist / radius, Art.cover(shape, px));
			out.mix(0xffe58a, Art.cover(dist - radius * 0.16, px));
		}

		// fallende Blütenblätter: gedrehte Ellipsen
		for (int i = 0; i < petals.count; i++) {
			int o = i * Drift.STRIDE;
			double dx = x - pre[o];
			double dy = y - pre[o + 1];
			double size = pre[o + 2];
			if (Math.abs(dx) > size || Math.abs(dy) > size) {
				continue;
			}
			double cos = Math.cos(pre[o + 3]);
			double sin = Math.sin(pre[o + 3]);
			double lx = (dx * cos + dy * sin) / size;
			double ly = (-dx * sin + dy * cos) / (size * 0.52);
			double shape = (Math.sqrt(lx * lx + ly * ly) - 1) * size * 0.52;
			out.mixRamp(PETAL, 0.5 + 0.5 * lx, Art.cover(shape, px) * 0.95);
		}
		trim(out, face, x, y, px, 0xffd6e6, 0.5);
	}
}
