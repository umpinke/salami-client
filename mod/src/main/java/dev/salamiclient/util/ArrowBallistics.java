package dev.salamiclient.util;

/**
 * Flugbahn eines Pfeils wie in {@code AbstractArrow#tick}: je Tick bewegen, dann
 * Luftwiderstand ×0.99, dann Schwerkraft −0.05. Startgeschwindigkeit eines Bogens:
 * {@code BowItem.getPowerForTime(ticks) × 3} Blöcke pro Tick.
 */
public final class ArrowBallistics {
	private static final double DRAG = 0.99;
	private static final double GRAVITY = 0.05;
	private static final int MAX_TICKS = 300;
	/** Höchster Schusswinkel, den die Suche nutzt (flache Flugbahn statt Steilschuss). */
	private static final float HIGHEST_PITCH = -45f;
	private static final float LOWEST_PITCH = 89f;

	private ArrowBallistics() {}

	/** Ergebnis: Höhe (relativ zum Start) bei Erreichen der Strecke und Flugzeit in Ticks. */
	public record Flight(double height, double ticks) {}

	/**
	 * Fliegt ein Pfeil mit {@code speed} (Blöcke/Tick) unter {@code pitch} (Grad, Minecraft:
	 * negativ = nach oben) die horizontale Strecke {@code distance}? {@code null}, wenn nicht.
	 */
	public static Flight fly(double speed, float pitch, double distance) {
		double radians = Math.toRadians(-pitch);
		double vx = speed * Math.cos(radians);
		double vy = speed * Math.sin(radians);
		double x = 0;
		double y = 0;
		for (int tick = 0; tick < MAX_TICKS && vx > 1e-4; tick++) {
			if (x + vx >= distance) {
				double fraction = (distance - x) / vx;
				return new Flight(y + vy * fraction, tick + fraction);
			}
			x += vx;
			y += vy;
			vx *= DRAG;
			vy = vy * DRAG - GRAVITY;
		}
		return null;
	}

	/**
	 * Flachster Winkel (Grad), mit dem der Pfeil nach {@code distance} Blöcken auf Höhe
	 * {@code height} ankommt; {@code null}, wenn das Ziel außer Reichweite ist.
	 */
	public static Float solvePitch(double speed, double distance, double height) {
		Flight highest = fly(speed, HIGHEST_PITCH, distance);
		if (highest == null || highest.height() < height) {
			return null;
		}
		// Ankunftshöhe sinkt, je weiter nach unten gezielt wird → Bisektion
		float up = HIGHEST_PITCH;
		float down = LOWEST_PITCH;
		for (int i = 0; i < 40; i++) {
			float mid = (up + down) / 2;
			Flight flight = fly(speed, mid, distance);
			if (flight == null || flight.height() < height) {
				down = mid;
			} else {
				up = mid;
			}
		}
		return up;
	}
}
