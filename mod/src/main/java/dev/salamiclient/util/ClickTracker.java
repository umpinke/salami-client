package dev.salamiclient.util;

import java.util.ArrayDeque;

/**
 * Zählt Mausklicks der letzten Sekunde (für CPS und Keystrokes). Gefüttert vom
 * MouseHandlerMixin – nur Klicks im Spiel, nicht in Menüs.
 */
public final class ClickTracker {
	private static final long WINDOW_MILLIS = 1000;
	private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
	private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();

	private ClickTracker() {}

	/** GLFW-Maustaste 0 = links, 1 = rechts. */
	public static void press(int button) {
		ArrayDeque<Long> clicks = clicks(button);
		if (clicks == null) {
			return;
		}
		long now = System.currentTimeMillis();
		clicks.addLast(now);
		prune(clicks, now);
	}

	public static int left() {
		return count(LEFT);
	}

	public static int right() {
		return count(RIGHT);
	}

	private static int count(ArrayDeque<Long> clicks) {
		prune(clicks, System.currentTimeMillis());
		return clicks.size();
	}

	private static ArrayDeque<Long> clicks(int button) {
		return switch (button) {
			case 0 -> LEFT;
			case 1 -> RIGHT;
			default -> null;
		};
	}

	private static void prune(ArrayDeque<Long> clicks, long now) {
		while (!clicks.isEmpty() && now - clicks.peekFirst() > WINDOW_MILLIS) {
			clicks.pollFirst();
		}
	}
}
