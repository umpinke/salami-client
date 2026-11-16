package dev.salamiclient.capes.art;

/**
 * Ein gemaltes, animiertes Cape. Das Motiv ist eine Rechenvorschrift statt eines Bildes:
 * {@link #shade} liefert die Farbe für jeden Punkt und Zeitpunkt, deshalb gibt es das Cape
 * in jeder Auflösung und die Jar bleibt klein.
 *
 * <p>Die Animation ist eine Schleife: {@code t} läuft von 0 bis 1 und muss bei 1 genauso
 * aussehen wie bei 0 (nur ganze Umläufe, {@code sin(TAU * t)} usw.).
 */
public abstract class CapeDesign {
	/** Außenseite des Capes – das, was man im Spiel von hinten sieht. */
	public static final int OUTSIDE = 0;
	/** Innenseite (zum Rücken hin); wird nach dem Malen abgedunkelt. */
	public static final int INSIDE = 1;
	/** Elytra-Flügel. */
	public static final int WING = 2;

	/** Höhe der Malfläche: das Cape ist 10 breit und 16 hoch, x läuft 0..1, y 0..1,6. */
	public static final double HEIGHT = 1.6;

	public final String name;
	public final int frames;
	public final int frameMs;

	protected CapeDesign(String name, int frames, int frameMs) {
		this.name = name;
		this.frames = frames;
		this.frameMs = frameMs;
	}

	/**
	 * Farbe am Punkt (x, y) zur Zeit t.
	 *
	 * @param face {@link #OUTSIDE}, {@link #INSIDE} oder {@link #WING}
	 * @param x    0 (links) bis 1 (rechts); auf dem Flügel ab −0,2
	 * @param y    0 (oben) bis {@link #HEIGHT} (unten)
	 * @param t    Stelle in der Schleife, 0 bis 1
	 * @param px   Größe eines Texturpixels in diesen Einheiten (für weiche Kanten)
	 * @param pre  was {@link #prepare} für diesen Frame geliefert hat
	 */
	protected abstract void shade(Rgb out, int face, double x, double y, double t, double px, double[] pre);

	/** Einmal je Frame vorab Gerechnetes (z. B. wo die Teilchen gerade sind); Standard: nichts. */
	protected double[] prepare(double t) {
		return null;
	}

	/** Schmale Zierlinie mit Abstand zum Rand und leicht abgedunkelte Ecken – nur auf der Außenseite. */
	protected static void trim(Rgb out, int face, double x, double y, double px, int color, double strength) {
		if (face != OUTSIDE) {
			return;
		}
		double edge = Math.min(Math.min(x, 1 - x), Math.min(y, HEIGHT - y));
		out.mul(0.72 + 0.28 * Art.smooth(0, 0.16, edge));
		double line = Math.abs(edge - 0.045) - 0.006;
		out.mix(color, Art.cover(line, px) * strength);
	}
}
