package dev.salamiclient.capes.art;

/**
 * Rechenhelfer für die gemalten Capes: Rauschen, Übergänge, Abstände. Alles reine Mathematik
 * ohne Minecraft – dieselben Klassen laufen im Spiel und im Export für den Launcher
 * ({@code branding/CapeExport.java}).
 */
public final class Art {
	public static final double TAU = Math.PI * 2;

	private Art() {}

	public static double clamp01(double v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}

	public static double mix(double a, double b, double t) {
		return a + (b - a) * t;
	}

	/** Weicher Übergang 0→1 zwischen {@code a} und {@code b} (auch mit a &gt; b). */
	public static double smooth(double a, double b, double x) {
		double t = clamp01((x - a) / (b - a));
		return t * t * (3 - 2 * t);
	}

	public static double fract(double v) {
		return v - Math.floor(v);
	}

	/** Deckung einer Form mit Abstand {@code d} zur Kante (negativ = innen), eine Pixelbreite weich. */
	public static double cover(double d, double px) {
		return clamp01(0.5 - d / (px * 1.5));
	}

	/** Leuchten um eine Linie/Kante: 1 auf der Linie, fällt mit {@code width} ab. */
	public static double glow(double d, double width) {
		double q = d / width;
		return 1 / (1 + q * q);
	}

	public static int hash(int x, int y, int seed) {
		int h = x * 0x27d4eb2d ^ y * 0x165667b1 ^ seed * 0x9e3779b9;
		h ^= h >>> 15;
		h *= 0x2c1b3c6d;
		h ^= h >>> 12;
		h *= 0x297a2d39;
		h ^= h >>> 15;
		return h;
	}

	/** Zufallswert 0..1 für eine Gitterzelle. */
	public static double rnd(int x, int y, int seed) {
		return (hash(x, y, seed) >>> 8) * (1.0 / (1 << 24));
	}

	private static double fade(double t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}

	private static int wrap(int v, int period) {
		if (period <= 0) {
			return v;
		}
		int m = v % period;
		return m < 0 ? m + period : m;
	}

	/** Weiches Rauschen 0..1. */
	public static double noise(double x, double y, int seed) {
		return noise(x, y, 0, 0, seed);
	}

	/** Weiches Rauschen 0..1, das sich nach {@code perX}/{@code perY} wiederholt (0 = nie). */
	public static double noise(double x, double y, int perX, int perY, int seed) {
		int xi = (int) Math.floor(x);
		int yi = (int) Math.floor(y);
		double fx = fade(x - xi);
		double fy = fade(y - yi);
		int x0 = wrap(xi, perX);
		int x1 = wrap(xi + 1, perX);
		int y0 = wrap(yi, perY);
		int y1 = wrap(yi + 1, perY);
		double a = mix(rnd(x0, y0, seed), rnd(x1, y0, seed), fx);
		double b = mix(rnd(x0, y1, seed), rnd(x1, y1, seed), fx);
		return mix(a, b, fy);
	}

	/** Rauschen in drei Richtungen; {@code z} wiederholt sich nach {@code perZ} – für nahtlose Schleifen. */
	public static double noise3(double x, double y, double z, int perZ, int seed) {
		int zi = (int) Math.floor(z);
		double fz = fade(z - zi);
		int z0 = wrap(zi, perZ);
		int z1 = wrap(zi + 1, perZ);
		return mix(noise(x, y, seed + z0 * 7919), noise(x, y, seed + z1 * 7919), fz);
	}

	/** Mehrere Oktaven Rauschen, 0..1. */
	public static double fbm(double x, double y, int octaves, int seed) {
		double sum = 0;
		double amp = 0.5;
		double total = 0;
		for (int i = 0; i < octaves; i++) {
			sum += noise(x, y, seed + i * 131) * amp;
			total += amp;
			x = x * 2.03 + 17.1;
			y = y * 2.03 + 9.7;
			amp *= 0.5;
		}
		return sum / total;
	}

	/** Wie {@link #fbm}, aber in x nach {@code perX} wiederholt (zum nahtlosen Durchschieben). */
	public static double fbmWrapX(double x, double y, int perX, int octaves, int seed) {
		double sum = 0;
		double amp = 0.5;
		double total = 0;
		for (int i = 0; i < octaves; i++) {
			sum += noise(x, y, perX, 0, seed + i * 131) * amp;
			total += amp;
			x *= 2;
			y = y * 2 + 9.7;
			perX *= 2;
			amp *= 0.5;
		}
		return sum / total;
	}

	/** Wie {@link #fbm}, mit der Zeit {@code t} (0..1) als dritter Richtung; läuft nach t=1 nahtlos weiter. */
	public static double fbmLoop(double x, double y, double t, int steps, int octaves, int seed) {
		double sum = 0;
		double amp = 0.5;
		double total = 0;
		for (int i = 0; i < octaves; i++) {
			sum += noise3(x, y, t * steps, steps, seed + i * 131) * amp;
			total += amp;
			x = x * 2.03 + 17.1;
			y = y * 2.03 + 9.7;
			amp *= 0.5;
		}
		return sum / total;
	}

	/** Abstand des Punkts zur Strecke a–b. */
	public static double segment(double x, double y, double ax, double ay, double bx, double by) {
		double dx = bx - ax;
		double dy = by - ay;
		double h = clamp01(((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy));
		double ex = x - ax - dx * h;
		double ey = y - ay - dy * h;
		return Math.sqrt(ex * ex + ey * ey);
	}

	/**
	 * Sterne auf einem Raster: Helligkeit 0..1 des Sterns in der Zelle von (x, y). {@code cells}
	 * = Zellen je Einheit, {@code chance} = Anteil der Zellen mit Stern; die Sterne funkeln.
	 */
	public static double star(double x, double y, double cells, double t, double chance, int seed) {
		double gx = x * cells;
		double gy = y * cells;
		int ix = (int) Math.floor(gx);
		int iy = (int) Math.floor(gy);
		double pick = rnd(ix, iy, seed);
		if (pick > chance) {
			return 0;
		}
		double dx = gx - ix - (0.2 + 0.6 * rnd(ix, iy, seed + 1));
		double dy = gy - iy - (0.2 + 0.6 * rnd(ix, iy, seed + 2));
		double size = 0.035 + 0.075 * rnd(ix, iy, seed + 3);
		double q = (dx * dx + dy * dy) / (size * size);
		if (q > 12) {
			return 0;
		}
		int speed = 1 + (hash(ix, iy, seed + 4) & 1);
		double twinkle = 0.6 + 0.4 * Math.sin(TAU * (t * speed + pick / chance));
		return twinkle * (Math.exp(-q) + 0.25 * Math.exp(-q * 0.25));
	}
}
