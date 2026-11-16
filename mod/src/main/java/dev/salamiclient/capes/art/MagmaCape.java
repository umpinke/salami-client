package dev.salamiclient.capes.art;

/** Magma: dunkle Basaltschollen, in deren Rissen Lava nach oben pulsiert; Funken steigen auf. */
final class MagmaCape extends CapeDesign {
	private static final int[] ROCK = {0x0f0b0a, 0x1d1614, 0x2c221e};
	private static final int[] LAVA = {0x3a0500, 0xb81c00, 0xff5a00, 0xffb400, 0xfff2b0};
	private static final double CELLS = 5.2;
	private final Drift embers = new Drift(22, 77, 0.006, 0.013, 0.04, true);

	MagmaCape() {
		super("magma", 32, 62);
	}

	@Override
	protected double[] prepare(double t) {
		return embers.at(t);
	}

	@Override
	protected void shade(Rgb out, int face, double x, double y, double t, double px, double[] pre) {
		// Schollen: Abstand zur nächsten und zweitnächsten Mitte, die Differenz ist der Abstand zum Riss
		double gx = x * CELLS + 0.35 * (Art.noise(x * 7, y * 7, 3) - 0.5);
		double gy = y * CELLS + 0.35 * (Art.noise(x * 7 + 40, y * 7, 4) - 0.5);
		int ix = (int) Math.floor(gx);
		int iy = (int) Math.floor(gy);
		double f1 = 9;
		double f2 = 9;
		int cell = 0;
		for (int oy = -1; oy <= 1; oy++) {
			for (int ox = -1; ox <= 1; ox++) {
				double dx = ix + ox + 0.15 + 0.7 * Art.rnd(ix + ox, iy + oy, 1) - gx;
				double dy = iy + oy + 0.15 + 0.7 * Art.rnd(ix + ox, iy + oy, 2) - gy;
				double d = Math.sqrt(dx * dx + dy * dy);
				if (d < f1) {
					f2 = f1;
					f1 = d;
					cell = Art.hash(ix + ox, iy + oy, 9);
				} else if (d < f2) {
					f2 = d;
				}
			}
		}
		double crack = (f2 - f1) / CELLS;
		double grain = Art.fbm(x * 22, y * 22, 3, 6);
		out.ramp(ROCK, 0.25 + 0.5 * grain + 0.25 * ((cell >>> 8 & 255) / 255.0));
		out.mul(0.7 + 0.5 * Art.smooth(0, 0.06, crack));

		// Hitze wandert von unten nach oben durch die Risse
		double wave = 0.5 + 0.5 * Math.sin(Art.TAU * (t + y * 0.7 + 0.3 * Art.noise(x * 3, y * 3, 8)));
		double pulse = 0.35 + 0.65 * wave * wave;
		double flow = 0.6 + 0.4 * Art.fbmLoop(x * 9, y * 9, t, 3, 2, 30);
		double heat = Math.exp(-crack / 0.011) * pulse * flow;
		out.add(0xff3a00, Math.exp(-crack / 0.05) * pulse * 0.45);
		out.mixRamp(LAVA, heat * 1.15, Art.clamp01(heat * 1.8));

		for (int i = 0; i < embers.count; i++) {
			int o = i * Drift.STRIDE;
			double dx = x - pre[o];
			double dy = y - pre[o + 1];
			double size = pre[o + 2];
			double q = (dx * dx + dy * dy) / (size * size);
			if (q < 14) {
				double fade = Art.smooth(-0.1, 0.5, pre[o + 1]);
				out.add(0xffb040, (Math.exp(-q) + 0.3 * Math.exp(-q * 0.3)) * fade);
			}
		}
		trim(out, face, x, y, px, 0xff8a2a, 0.6);
	}
}
