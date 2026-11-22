package dev.salamiclient.util;

/**
 * Ob ein Chat-Befehl gerade wirklich vom Spieler kommt: im Chat getippt und mit Enter abgeschickt
 * ({@code ChatScreenMixin}) oder über eine eigene AutoText-Taste. Klicks auf Chat-Text, Bücher, Schilder oder Dialoge
 * eines Servers führen Befehle dagegen ohne Tippen aus ({@code sendUnattendedCommand}) – Befehle mit Folgen
 * (z. B. {@code /salami msg}) prüfen deshalb {@link #active()}.
 *
 * <p>Nur auf dem Client-Thread verwenden.
 */
public final class UserInput {
	private static int depth;

	private UserInput() {}

	/** {@code true}, solange ein vom Spieler selbst abgeschickter Befehl läuft. */
	public static boolean active() {
		return depth > 0;
	}

	/** Führt {@code action} als Eingabe des Spielers aus. */
	public static void run(Runnable action) {
		begin();
		try {
			action.run();
		} finally {
			end();
		}
	}

	/** Beginn einer Eingabe des Spielers; immer mit {@link #end()} abschließen. */
	public static void begin() {
		depth++;
	}

	public static void end() {
		if (depth > 0) {
			depth--;
		}
	}

	/**
	 * Jeden Client-Tick: außerhalb einer Eingabe ist nie etwas offen. Setzt den Zähler zurück, falls eine Eingabe
	 * mit einer Ausnahme abgebrochen ist und {@link #end()} nie lief – sonst gälten spätere Klicks als getippt.
	 */
	public static void reset() {
		depth = 0;
	}
}
