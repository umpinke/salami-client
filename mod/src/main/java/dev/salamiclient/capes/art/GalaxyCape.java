package dev.salamiclient.capes.art;

/** Galaxie: eine geneigte Spiralgalaxie, die sich langsam dreht, vor funkelnden Sternen. */
final class GalaxyCape extends CapeDesign {
	private static final int[] ARMS = {0xfff1c8, 0xffb878, 0xd66bff, 0x5a78ff, 0x1d2a80};
	private static final double TILT = Math.toRadians(-24);
	private static final double COS = Math.cos(TILT);
	private static final double SIN = Math.sin(TILT);

	GalaxyCape() {
		super("galaxy", 48, 83);
	}

	@Override
	protected void shade(Rgb out, int face, double x, double y, double t, double px, double[] pre) {
		out.set(0x02030a);
		// Nebelschwaden im Hintergrund
		double haze = Art.fbm(x * 2.2 + 4, y * 2.2, 4, 50);
		out.mix(0x2a1050, Art.smooth(0.45, 0.8, haze) * 0.5);
		out.mix(0x08284a, Art.smooth(0.5, 0.85, Art.fbm(x * 1.8 + 30, y * 1.8 + 7, 4, 51)) * 0.45);
		out.add(0xe6ecff, Art.star(x, y, 15, t, 0.6, 60) * 0.85);
		out.add(0xffd9b0, Art.star(x, y, 6, t, 0.35, 61) * 0.9);

		double dx = (x - 0.5) / 1.75;
		double dy = (y - 0.76) / 1.75;
		double u = dx * COS - dy * SIN;
		double v = (dx * SIN + dy * COS) / 0.58;
		double r = Math.sqrt(u * u + v * v);
		// zwei Arme: nach einer halben Drehung sieht die Galaxie wieder gleich aus – das ist die Schleife
		double angle = Math.atan2(v, u) - Math.PI * t;
		double swirl = angle + 2.7 * Math.log(r + 0.035);
		double arm = Math.pow(0.5 + 0.5 * Math.cos(2 * swirl), 2.3);
		double lr = Math.log(r + 0.035);
		double dust = Art.noise(lr * 3.4 + swirl * 0.4, angle / Math.PI * 5, 0, 5, 70) * 0.6
			+ Art.noise(lr * 9, angle / Math.PI * 12, 0, 12, 71) * 0.4;
		double disk = Math.exp(-r * 4.6);
		out.addRamp(ARMS, r * 2.3, disk * (0.22 + 1.7 * arm * (0.35 + dust)));
		// helle Knoten in den Armen
		double knots = Art.noise(lr * 22, angle / Math.PI * 40, 0, 40, 72);
		out.add(0xcfe0ff, disk * arm * Art.smooth(0.70, 0.95, knots) * 1.3);
		out.add(0xfff4d6, Math.exp(-r * r / 0.0022));
		out.add(0xffc890, Math.exp(-r / 0.06) * 0.55);
		trim(out, face, x, y, px, 0xa58bff, 0.5);
	}
}
