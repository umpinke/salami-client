package dev.salamiclient.gui.skin;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Pixel-Symbole aus einfachen Rechtecken, für alle Looks gleich (jeder Look färbt sie selbst).
 * Große Symbole sind 12 × 12, kleine 7 × 7 UI-Pixel; (x, y) ist jeweils die linke obere Ecke.
 */
public final class Icons {
	public static final int BIG = 12;
	public static final int SMALL = 7;

	private Icons() {}

	/** Großes Symbol (12 × 12) für einen Eintrag des Startmenüs. */
	public static void draw(GuiGraphics g, MenuView.Kind kind, int x, int y, int color) {
		switch (kind) {
			case MODS -> {
				// Dreieck „abspielen“
				for (int k = 0; k < 6; k++) {
					g.fill(x + 3 + k, y + 1 + k, x + 4 + k, y + 11 - k, color);
				}
			}
			case HUD -> {
				// Bildschirm mit zwei HUD-Elementen
				outline(g, x, y + 1, 12, 9, color);
				g.fill(x + 2, y + 3, x + 6, y + 4, color);
				g.fill(x + 8, y + 7, x + 10, y + 8, color);
				g.fill(x + 4, y + 11, x + 8, y + 12, color);
			}
			case CONFIGS -> {
				// drei Regler
				for (int i = 0; i < 3; i++) {
					int ly = y + 2 + i * 4;
					g.fill(x, ly, x + 12, ly + 1, color);
					int knob = x + (i == 1 ? 7 : i == 0 ? 2 : 4);
					g.fill(knob, ly - 1, knob + 2, ly + 2, color);
				}
			}
			case PANELS -> {
				// zwei Panels nebeneinander
				outline(g, x, y, 5, 12, color);
				outline(g, x + 7, y, 5, 8, color);
				g.fill(x + 1, y + 1, x + 4, y + 3, color);
				g.fill(x + 8, y + 1, x + 11, y + 3, color);
			}
			case FRIENDS -> {
				// zwei Köpfe mit Schultern
				g.fill(x + 1, y + 1, x + 5, y + 5, color);
				g.fill(x, y + 7, x + 6, y + 12, color);
				g.fill(x + 7, y + 2, x + 11, y + 6, color);
				g.fill(x + 6, y + 8, x + 12, y + 12, color);
			}
			case COSMETICS -> {
				// Krone
				g.fill(x, y + 3, x + 2, y + 5, color);
				g.fill(x + 5, y + 1, x + 7, y + 3, color);
				g.fill(x + 10, y + 3, x + 12, y + 5, color);
				g.fill(x + 1, y + 5, x + 11, y + 9, color);
				g.fill(x + 3, y + 4, x + 4, y + 5, color);
				g.fill(x + 5, y + 3, x + 7, y + 5, color);
				g.fill(x + 8, y + 4, x + 9, y + 5, color);
				g.fill(x + 1, y + 10, x + 11, y + 11, color);
			}
			case MINIGAMES -> {
				// Funkeln (vierzackiger Stern)
				g.fill(x + 5, y, x + 7, y + 12, color);
				g.fill(x, y + 5, x + 12, y + 7, color);
				g.fill(x + 4, y + 3, x + 8, y + 9, color);
				g.fill(x + 3, y + 4, x + 9, y + 8, color);
			}
		}
	}

	/** Zahnrad (7 × 7): Ring mit Loch und vier Zähnen. */
	public static void gear(GuiGraphics g, int x, int y, int color) {
		g.fill(x + 3, y, x + 4, y + 1, color);
		g.fill(x + 1, y + 1, x + 6, y + 2, color);
		g.fill(x + 1, y + 2, x + 3, y + 3, color);
		g.fill(x + 4, y + 2, x + 6, y + 3, color);
		g.fill(x, y + 3, x + 2, y + 4, color);
		g.fill(x + 5, y + 3, x + 7, y + 4, color);
		g.fill(x + 1, y + 4, x + 3, y + 5, color);
		g.fill(x + 4, y + 4, x + 6, y + 5, color);
		g.fill(x + 1, y + 5, x + 6, y + 6, color);
		g.fill(x + 3, y + 6, x + 4, y + 7, color);
	}

	/** Bleistift, schräg (7 × 7). */
	public static void pencil(GuiGraphics g, int x, int y, int color) {
		for (int k = 0; k < 5; k++) {
			g.fill(x + 2 + k, y + 4 - k, x + 4 + k, y + 5 - k, color);
		}
		g.fill(x + 1, y + 5, x + 2, y + 6, color);
		g.fill(x, y + 6, x + 1, y + 7, color);
	}

	/** Eingabe-Pfeil „⏎“ (7 × 5). */
	public static void enter(GuiGraphics g, int x, int y, int color) {
		g.fill(x + 6, y, x + 7, y + 3, color);
		g.fill(x + 1, y + 3, x + 7, y + 4, color);
		g.fill(x + 1, y + 2, x + 2, y + 5, color);
		g.fill(x, y + 3, x + 1, y + 4, color);
		g.fill(x + 2, y + 1, x + 3, y + 2, color);
		g.fill(x + 2, y + 5, x + 3, y + 6, color);
	}

	/** Lupe mit − oder + im Glas (10 × 10). */
	public static void magnifier(GuiGraphics g, int x, int y, boolean plus, int color) {
		g.fill(x + 1, y, x + 6, y + 1, color);
		g.fill(x + 1, y + 6, x + 6, y + 7, color);
		g.fill(x, y + 1, x + 1, y + 6, color);
		g.fill(x + 6, y + 1, x + 7, y + 6, color);
		g.fill(x + 6, y + 6, x + 8, y + 8, color);
		g.fill(x + 8, y + 8, x + 10, y + 10, color);
		g.fill(x + 2, y + 3, x + 5, y + 4, color);
		if (plus) {
			g.fill(x + 3, y + 2, x + 4, y + 5, color);
		}
	}

	/** Kleiner Pfeil (5 × 3 bzw. 3 × 5): nach unten (aufgeklappt) oder nach rechts (zugeklappt). */
	public static void chevron(GuiGraphics g, int x, int y, boolean down, int color) {
		if (down) {
			g.fill(x, y, x + 5, y + 1, color);
			g.fill(x + 1, y + 1, x + 4, y + 2, color);
			g.fill(x + 2, y + 2, x + 3, y + 3, color);
		} else {
			g.fill(x + 1, y, x + 2, y + 5, color);
			g.fill(x + 2, y + 1, x + 3, y + 4, color);
			g.fill(x + 3, y + 2, x + 4, y + 3, color);
		}
	}

	/** Haken (7 × 5). */
	public static void check(GuiGraphics g, int x, int y, int color) {
		g.fill(x, y + 2, x + 1, y + 3, color);
		g.fill(x + 1, y + 3, x + 2, y + 4, color);
		g.fill(x + 2, y + 4, x + 3, y + 5, color);
		g.fill(x + 3, y + 3, x + 4, y + 4, color);
		g.fill(x + 4, y + 2, x + 5, y + 3, color);
		g.fill(x + 5, y + 1, x + 6, y + 2, color);
		g.fill(x + 6, y, x + 7, y + 1, color);
	}

	/** Kreuz (5 × 5). */
	public static void cross(GuiGraphics g, int x, int y, int color) {
		for (int k = 0; k < 5; k++) {
			g.fill(x + k, y + k, x + k + 1, y + k + 1, color);
			g.fill(x + 4 - k, y + k, x + 5 - k, y + k + 1, color);
		}
	}

	/** Päckchen (7 × 7) – mitgelieferte Mod ohne An/Aus. */
	public static void parcel(GuiGraphics g, int x, int y, int color) {
		outline(g, x, y + 1, 7, 6, color);
		g.fill(x + 3, y + 1, x + 4, y + 7, color);
		g.fill(x + 1, y, x + 6, y + 1, color);
	}

	/** Lautsprecher (12 × 9); {@code muted}: mit Kreuz in {@code muteColor}. */
	public static void speaker(GuiGraphics g, int x, int y, boolean muted, int color, int muteColor) {
		g.fill(x, y + 3, x + 3, y + 6, color);
		g.fill(x + 3, y + 2, x + 4, y + 7, color);
		g.fill(x + 4, y + 1, x + 5, y + 8, color);
		g.fill(x + 5, y, x + 6, y + 9, color);
		if (muted) {
			cross(g, x + 7, y + 2, muteColor);
		} else {
			g.fill(x + 8, y + 3, x + 9, y + 6, color);
			g.fill(x + 10, y + 1, x + 11, y + 8, color);
		}
	}

	/** Pause (7 × 7). */
	public static void pause(GuiGraphics g, int x, int y, int color) {
		g.fill(x + 1, y, x + 3, y + 7, color);
		g.fill(x + 4, y, x + 6, y + 7, color);
	}

	/** Abspielen (7 × 7). */
	public static void play(GuiGraphics g, int x, int y, int color) {
		for (int k = 0; k < 4; k++) {
			g.fill(x + 1 + k, y + k, x + 2 + k, y + 7 - k, color);
		}
	}

	/** Pokal (12 × 11). */
	public static void trophy(GuiGraphics g, int x, int y, int color) {
		g.fill(x + 2, y, x + 10, y + 1, color);
		g.fill(x + 3, y + 1, x + 9, y + 5, color);
		g.fill(x + 4, y + 5, x + 8, y + 6, color);
		g.fill(x, y + 1, x + 2, y + 2, color);
		g.fill(x, y + 2, x + 1, y + 4, color);
		g.fill(x + 10, y + 1, x + 12, y + 2, color);
		g.fill(x + 11, y + 2, x + 12, y + 4, color);
		g.fill(x + 5, y + 6, x + 7, y + 8, color);
		g.fill(x + 3, y + 8, x + 9, y + 10, color);
	}

	/** Plus (7 × 7). */
	public static void plus(GuiGraphics g, int x, int y, int color) {
		g.fill(x + 3, y, x + 4, y + 7, color);
		g.fill(x, y + 3, x + 7, y + 4, color);
	}

	private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y + 1, x + 1, y + h - 1, color);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}
}
