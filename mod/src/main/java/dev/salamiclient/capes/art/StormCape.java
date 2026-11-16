package dev.salamiclient.capes.art;

import java.util.Random;

/** Gewitter: ziehende Wolken, Regen und zwei Blitze, die den Himmel aufleuchten lassen. */
final class StormCape extends CapeDesign {
	private static final int[] SKY = {0x05061a, 0x0e1436, 0x1d2452, 0x2c3266};
	private static final int[] CLOUD = {0x0b0e22, 0x262c58, 0x5a66a8, 0xb4c2f4};
	/** Wann in der Schleife die Blitze einschlagen. */
	private static final double[] STRIKE = {0.08, 0.58};
	/** Blitze als Strecken (x1, y1, x2, y2, Stärke). */
	private final double[][] bolts = {bolt(5, 0.34), bolt(23, 0.68)};

	StormCape() {
		super("storm", 40, 50);
	}

	private static double[] bolt(long seed, double startX) {
		Random random = new Random(seed);
		double[] segments = new double[40 * 5];
		int n = 0;
		double x = startX;
		double y = 0.30;
		while (y < 1.42) {
			double nx = x + (random.nextDouble() - 0.5) * 0.16;
			double ny = y + 0.05 + random.nextDouble() * 0.09;
			n = segment(segments, n, x, y, nx, ny, 1);
			if (random.nextDouble() < 0.4 && n < segments.length - 5 * 6) {
				// Seitenast
				double bx = nx;
				double by = ny;
				double dir = random.nextBoolean() ? 1 : -1;
				for (int i = 0; i < 3; i++) {
					double cx = bx + dir * (0.04 + random.nextDouble() * 0.07);
					double cy = by + 0.03 + random.nextDouble() * 0.07;
					n = segment(segments, n, bx, by, cx, cy, 0.5);
					bx = cx;
					by = cy;
				}
			}
			x = nx;
			y = ny;
		}
		return java.util.Arrays.copyOf(segments, n);
	}

	private static int segment(double[] out, int n, double x1, double y1, double x2, double y2, double power) {
		if (n + 5 > out.length) {
			return n;
		}
		out[n] = x1;
		out[n + 1] = y1;
		out[n + 2] = x2;
		out[n + 3] = y2;
		out[n + 4] = power;
		return n + 5;
	}

	/** Helligkeit der Blitze zu dieser Zeit: schlagartig hell, flackernd, schnell wieder dunkel. */
	@Override
	protected double[] prepare(double t) {
		double[] flash = new double[STRIKE.length];
		for (int i = 0; i < STRIKE.length; i++) {
			double since = Art.fract(t - STRIKE[i]);
			flash[i] = Math.exp(-since * 6.5) * (0.65 + 0.35 * Math.cos(since * 60));
		}
		return flash;
	}

	@Override
	protected void shade(Rgb out, int face, double x, double y, double t, double px, double[] pre) {
		double flash = pre[0] + pre[1];
		out.ramp(SKY, y / HEIGHT);
		// Wolken ziehen quer durchs Bild; das Rauschen wiederholt sich, deshalb nahtlos
		double drift = x * 3 + t * 3;
		double cloud = Art.fbmWrapX(drift, y * 2.4, 3, 5, 31);
		double thick = Art.smooth(0.38, 0.62, cloud + 0.30 * (1 - y / 0.95));
		double lit = Art.fbmWrapX(drift + 0.12, y * 2.4 - 0.15, 3, 4, 31) - cloud;
		Rgb sky = out;
		double tone = 0.38 + 3.2 * lit + 0.6 * flash * (0.5 + cloud);
		double r = sky.r;
		double g = sky.g;
		double b = sky.b;
		out.ramp(CLOUD, tone);
		out.set(Art.mix(r, out.r, thick), Art.mix(g, out.g, thick), Art.mix(b, out.b, thick));
		out.add(0x9da8ff, flash * 0.16);

		// Regen: schräge Striche, jede Spalte eigenes Tempo
		double rx = (x + y * 0.16) * 46;
		int column = (int) Math.floor(rx);
		if (Art.rnd(column, 0, 17) < 0.6) {
			int laps = 2 + (Art.hash(column, 1, 17) & 1);
			double along = Art.fract(y * 1.3 - t * laps + Art.rnd(column, 2, 17));
			double across = Math.abs(rx - column - 0.5);
			double streak = Art.smooth(0.30, 0.0, along) * Art.smooth(0.16, 0.04, across);
			out.add(0xa9bfe6, streak * (0.20 + 0.25 * flash));
		}

		for (int i = 0; i < bolts.length; i++) {
			double power = pre[i];
			if (power < 0.01) {
				continue;
			}
			double[] bolt = bolts[i];
			double core = 0;
			double halo = 0;
			for (int s = 0; s < bolt.length; s += 5) {
				double d = Art.segment(x, y, bolt[s], bolt[s + 1], bolt[s + 2], bolt[s + 3]);
				if (d > 0.25) {
					continue;
				}
				double strength = bolt[s + 4];
				core = Math.max(core, Art.cover(d - 0.0065 * strength, px) * strength);
				halo = Math.max(halo, (Art.glow(d, 0.02) + 0.45 * Art.glow(d, 0.09)) * strength);
			}
			out.add(0x8f9cff, halo * Math.min(1, power * 1.6));
			out.mix(0xffffff, core * Art.clamp01(power * 3.5));
		}

		// Hügelkette unten, im Blitzlicht mit hellem Rand
		double hill = HEIGHT - 0.10 - 0.09 * Art.fbm(x * 3.3 + 1, 0.5, 4, 19);
		double ground = Art.cover(hill - y, px);
		out.mix(0x03050a, ground);
		out.add(0x7f9be0, ground * Math.exp(-(y - hill) / 0.012) * flash * 0.8);
		trim(out, face, x, y, px, 0x9db8ff, 0.5);
	}
}
