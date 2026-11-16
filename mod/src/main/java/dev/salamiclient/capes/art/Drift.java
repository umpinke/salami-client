package dev.salamiclient.capes.art;

/**
 * Teilchen, die in einer Schleife durchs Bild ziehen (Funken, Blütenblätter): jedes läuft in
 * einem Durchgang der Schleife ein- oder mehrmals ganz von einer Seite zur anderen, dadurch
 * geht das Ende nahtlos in den Anfang über.
 */
public final class Drift {
	/** Werte je Teilchen in {@link #at}: x, y, Größe, Drehwinkel. */
	public static final int STRIDE = 4;
	private static final double SPAN = CapeDesign.HEIGHT + 0.3;

	public final int count;
	private final int seed;
	private final double minSize;
	private final double maxSize;
	private final double sway;
	private final boolean up;

	/**
	 * @param sway wie weit die Teilchen seitlich pendeln
	 * @param up   steigen statt fallen
	 */
	public Drift(int count, int seed, double minSize, double maxSize, double sway, boolean up) {
		this.count = count;
		this.seed = seed;
		this.minSize = minSize;
		this.maxSize = maxSize;
		this.sway = sway;
		this.up = up;
	}

	/** Lage aller Teilchen zur Zeit t. x von −0,2 bis 1 (so breit wie der Flügel). */
	public double[] at(double t) {
		double[] p = new double[count * STRIDE];
		for (int i = 0; i < count; i++) {
			double depth = Art.rnd(i, 3, seed);
			int laps = 1 + (Art.hash(i, 4, seed) & 1);
			double travel = Art.fract(Art.rnd(i, 1, seed) + t * laps);
			double y = (up ? 1 - travel : travel) * SPAN - 0.15;
			int swings = 1 + (Art.hash(i, 5, seed) >>> 28) % 2;
			double x = -0.2 + 1.2 * Art.rnd(i, 0, seed) + sway * Math.sin(Art.TAU * (t * swings + Art.rnd(i, 6, seed)));
			int spin = (Art.hash(i, 7, seed) & 1) == 0 ? 1 : -1;
			int o = i * STRIDE;
			p[o] = x;
			p[o + 1] = y;
			p[o + 2] = Art.mix(minSize, maxSize, depth);
			p[o + 3] = Art.TAU * (t * spin * (1 + (Art.hash(i, 8, seed) & 1)) + Art.rnd(i, 9, seed));
		}
		return p;
	}
}
