package dev.salamiclient.minigames;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Gemeinsame Basis aller Minispiele: feste Spielschritte ({@link #STEPS_PER_SECOND} pro Sekunde,
 * unabhängig von den FPS), Punkte, Spielende, gehaltene Tasten und Geräusche.
 *
 * <p>Ein Spiel kennt keine Minecraft-Klassen: Der Bildschirm ruft {@link #step()} im festen Takt auf,
 * reicht Tasten ({@link #press}/{@link #release}) und Klicks in Spielkoordinaten weiter und lässt das
 * Spiel auf einer {@link Canvas} zeichnen – mit {@code partial} (0–1) als Anteil bis zum nächsten
 * Schritt, damit Bewegungen auch bei hohen FPS flüssig aussehen.
 */
public abstract class Minigame {
	public static final int STEPS_PER_SECOND = 60;

	/** Ein Geräusch mit Tonhöhe (1 = normal). */
	public record Sound(Sfx sfx, float pitch) {}

	/** Eine Zeile in der Infospalte neben dem Spielfeld: Übersetzungsschlüssel + Wert. */
	public record Stat(String labelKey, String value) {}

	protected Random random = new Random();
	private int score;
	private boolean over;
	private int steps;
	private int overSteps;
	private final List<Sound> sounds = new ArrayList<>();
	private final Set<GameKey> held = EnumSet.noneOf(GameKey.class);

	/** Feste ID (Highscore-Datei, Übersetzungen), z. B. {@code "cold_cuts"}. */
	public abstract String id();

	/** Breite der Spielfläche in Spielkoordinaten. */
	public abstract float width();

	/** Höhe der Spielfläche in Spielkoordinaten. */
	public abstract float height();

	/** Übersetzungsschlüssel der Steuerungshinweise unter dem Spielfeld. */
	public abstract List<String> controls();

	/** Spielzustand auf Anfang (nach {@link #restart}). */
	protected abstract void reset();

	/** Ein fester Spielschritt. Läuft auch nach dem Spielende weiter (Animationen), siehe {@link #isOver()}. */
	protected abstract void update();

	public abstract void render(Canvas canvas, float partial);

	/** Übersetzungsschlüssel des Spielnamens. */
	public String nameKey() {
		return "salami.minigames.game." + id();
	}

	/** Zusätzliche Werte für die Infospalte (Level, Zeit …). */
	public List<Stat> stats() {
		return List.of();
	}

	/** Überschrift am Spielende. */
	public String overTitleKey() {
		return "salami.minigames.over";
	}

	/** Schritte nach dem Spielende, bevor das Ergebnis erscheint (Zeit für die Abschluss-Animation). */
	public int overlayDelay() {
		return 30;
	}

	/** Neues Spiel mit zufälligem Startwert. */
	public final void restart() {
		restart(System.nanoTime());
	}

	/** Neues Spiel mit festem Startwert (gleiche Würfe, z. B. für Tests). */
	public final void restart(long seed) {
		random = new Random(seed);
		score = 0;
		over = false;
		steps = 0;
		overSteps = 0;
		sounds.clear();
		held.clear();
		reset();
	}

	/** Ein anschaulicher Spielstand für die Vorschau im Menü (und Screenshots). */
	public void demo() {
		restart(42);
	}

	public final void step() {
		if (over) {
			overSteps++;
		} else {
			steps++;
		}
		update();
	}

	/** Taste gedrückt; Wiederholungen des Betriebssystems (Taste schon gehalten) werden ignoriert. */
	public final void press(GameKey key) {
		if (held.add(key)) {
			onPress(key);
		}
	}

	public final void release(GameKey key) {
		if (held.remove(key)) {
			onRelease(key);
		}
	}

	/** Alle Tasten loslassen (Pause, Fenster verliert den Fokus). */
	public final void releaseAll() {
		for (GameKey key : List.copyOf(held)) {
			release(key);
		}
	}

	protected void onPress(GameKey key) {}

	protected void onRelease(GameKey key) {}

	protected final boolean isHeld(GameKey key) {
		return held.contains(key);
	}

	/** Mausklick in Spielkoordinaten (0 = links, 1 = rechts, 2 = Mitte). */
	public void click(float x, float y, int button) {}

	/** Mausposition in Spielkoordinaten. */
	public void hover(float x, float y) {}

	public final int score() {
		return score;
	}

	protected final void addScore(int points) {
		score = Math.max(0, score + points);
	}

	protected final void setScore(int points) {
		score = Math.max(0, points);
	}

	public final boolean isOver() {
		return over;
	}

	/** Spielende erreicht und Abschluss-Animation vorbei: Ergebnis anzeigen. */
	public final boolean overlayReady() {
		return over && overSteps >= overlayDelay();
	}

	protected final void gameOver() {
		over = true;
	}

	/** Gespielte Schritte (ohne die nach dem Spielende). */
	protected final int steps() {
		return steps;
	}

	/** Schritte seit dem Spielende. */
	protected final int overSteps() {
		return overSteps;
	}

	protected final void sound(Sfx sfx) {
		sound(sfx, 1f);
	}

	protected final void sound(Sfx sfx, float pitch) {
		if (sounds.size() < 16) {
			sounds.add(new Sound(sfx, pitch));
		}
	}

	/** Seit dem letzten Aufruf ausgelöste Geräusche (und leert die Liste). */
	public final List<Sound> drainSounds() {
		if (sounds.isEmpty()) {
			return List.of();
		}
		List<Sound> copy = List.copyOf(sounds);
		sounds.clear();
		return copy;
	}

	/** Zeit als m:ss. */
	protected static String clock(int steps) {
		int seconds = steps / STEPS_PER_SECOND;
		return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
	}

	protected static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}
}
