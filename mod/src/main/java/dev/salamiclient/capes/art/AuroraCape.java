package dev.salamiclient.capes.art;

/** Polarlicht: wehende Lichtvorhänge über einem Sternenhimmel und einer Bergkette. */
final class AuroraCape extends CapeDesign {
	private static final int[] SKY = {0x03061a, 0x07122f, 0x0b2240, 0x10344a};
	private static final int[] LIGHT = {0x2dff9a, 0x38e8d0, 0x5a8cff, 0xa24dff};
	private static final double[] BASE = {0.50, 0.74, 0.98};

	AuroraCape() {
		super("aurora", 36, 83);
	}

	@Override
	protected void shade(Rgb out, int face, double x, double y, double t, double px, double[] pre) {
		out.ramp(SKY, y / HEIGHT);
		if (y < 1.25) {
			out.add(0xdfe9ff, Art.star(x, y, 13, t, 0.55, 21) * 0.9);
		}
		for (int i = 0; i < BASE.length; i++) {
			double xw = x + 0.05 * Math.sin(Art.TAU * (t + y * 0.6 + i * 0.3));
			double edge = BASE[i]
				+ 0.10 * Math.sin(Art.TAU * (xw * 0.9 + i * 0.37 - t))
				+ 0.04 * Math.sin(Art.TAU * (xw * 2.3 + i * 0.21 + t));
			double dy = y - edge;
			double tail = 0.34 + 0.08 * i;
			double band = dy > 0 ? Math.exp(-(dy * dy) / (0.03 * 0.03)) : Math.exp(dy / tail);
			if (band < 0.004) {
				continue;
			}
			double rays = 0.45 + 0.75 * Art.noise3(xw * 24 + i * 9, y * 1.2, t * 4, 4, 40 + i);
			out.addRamp(LIGHT, -dy / tail * 0.85, band * rays * (0.85 - 0.17 * i));
		}
		// ferne Bergkette, vom Polarlicht angestrahlt
		double far = HEIGHT - 0.20 - 0.24 * Art.fbm(x * 2.4 + 3, 0.5, 4, 11);
		double farCover = Art.cover(far - y, px);
		out.mix(0x0a1c2c, farCover);
		out.add(0x1f6b55, farCover * Math.exp(-(y - far) / 0.035) * 0.55);
		// vorderer Kamm mit Tannenspitzen
		double spikes = Math.abs(Art.fract(x * 17 + 0.3 * Art.noise(x * 6, 2, 5)) - 0.5) * 0.07;
		double near = HEIGHT - 0.07 - 0.12 * Art.fbm(x * 3.1 + 8, 1.5, 3, 12) - spikes;
		out.mix(0x02050b, Art.cover(near - y, px));
		trim(out, face, x, y, px, 0x7fffd0, 0.55);
	}
}
