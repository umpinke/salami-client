package dev.salamiclient.gui;

import dev.salamiclient.gui.skin.Skin;
import dev.salamiclient.gui.skin.Skins;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;

/**
 * Gemeinsamer Rahmen der Salami-Fenster (Einstellungen eines Mods, Configs, Cosmetics, Freunde,
 * Minispiele): Hintergrund des Looks, Fenster mittig unter dessen Deko, oben „Zurück“, Titel und eine
 * Linie. Alles kommt aus dem {@link Skin}; die Bildschirme rechnen mit denselben Rechtecken.
 */
public final class SalamiWindow {
	/** Höhe der Kopfzeile (Zurück, Titel) bis zur Linie. */
	public static final int HEADER = 24;
	/** Abstand des Inhalts vom Fensterrand. */
	public static final int PAD = 10;

	private SalamiWindow() {}

	public static Skin skin() {
		return Skins.current();
	}

	/**
	 * Hintergrund hinter einem Salami-Bildschirm (nach Minecrafts eigenem Hintergrund aufrufen): der
	 * {@link Skin#backdrop} in UI-Koordinaten.
	 */
	public static void backdrop(GuiGraphics g, int width, int height, float uiScale) {
		g.pose().pushMatrix();
		g.pose().scale(uiScale, uiScale);
		skin().backdrop(g, Math.round(width / uiScale), Math.round(height / uiScale));
		g.pose().popMatrix();
	}

	/** Fenster {x0, y0, x1, y1}: höchstens {@code maxW × maxH}, mittig unter der Deko des Hintergrunds. */
	public static int[] frame(int uiW, int uiH, int maxW, int maxH) {
		int top = skin().backdropTop() + 4;
		int w = Math.min(uiW - 16, maxW);
		int h = Math.min(uiH - top - 6, maxH);
		int x0 = (uiW - w) / 2;
		int y0 = top + Math.max(0, (uiH - top - 6 - h) / 2);
		return new int[] {x0, y0, x0 + w, y0 + h};
	}

	/** „Zurück“ oben links im Fenster. */
	public static int[] backButton(Font font, int[] frame) {
		int w = font.width(I18n.get("salami.mods.back")) + 12;
		return new int[] {frame[0] + 5, frame[1] + 5, frame[0] + 5 + w, frame[1] + 19};
	}

	/** Oberkante des Inhalts unter der Kopfzeile. */
	public static int contentTop(int[] frame) {
		return frame[1] + HEADER + 5;
	}

	/**
	 * Fenster, „Zurück“, Titel (mittig, so groß wie es zwischen „Zurück“ und {@code rightReserve} Pixeln am
	 * rechten Rand passt) und die Linie unter der Kopfzeile.
	 */
	public static void draw(GuiGraphics g, Font font, int[] frame, String title, int mx, int my, int rightReserve) {
		Skin skin = skin();
		skin.surface(g, Skin.Surface.WINDOW, frame[0], frame[1], frame[2] - frame[0], frame[3] - frame[1]);
		Skin.Ink ink = skin.ink(Skin.Surface.WINDOW);
		int[] back = backButton(font, frame);
		skin.button(g, font, back[0], back[1], back[2] - back[0], back[3] - back[1], I18n.get("salami.mods.back"),
			Skin.ButtonKind.GHOST, Skin.UiState.of(inside(back, mx, my)));
		int center = (frame[0] + frame[2]) / 2;
		int half = Math.min(center - back[2] - 6, frame[2] - rightReserve - 6 - center);
		float scale = skin.headingWidth(font, title, 1.5f) <= 2 * half ? 1.5f : 1f;
		int w = skin.headingWidth(font, title, scale);
		skin.heading(g, font, title, center - w / 2, frame[1] + (scale > 1 ? 7 : 8), ink.text(), scale);
		g.fill(frame[0] + PAD - 2, frame[1] + HEADER, frame[2] - PAD + 2, frame[1] + HEADER + 1, ink.text());
	}

	/**
	 * Tasten-Hinweis unten mittig als {@link Skin#tag Schild}; passt er nicht in die Breite, wird er an „·“
	 * auf mehrere Schilder übereinander verteilt.
	 */
	public static void hint(GuiGraphics g, Font font, String text, int uiW, int uiH) {
		Skin skin = skin();
		List<String> lines = hintLines(font, text, uiW);
		int h = skin.tagHeight();
		int y = uiH - 4 - lines.size() * (h + 2) + 2;
		for (String l : lines) {
			skin.tag(g, font, (uiW - skin.tagWidth(font, l)) / 2, y, l);
			y += h + 2;
		}
	}

	/** Platz, den {@link #hint} unten braucht (samt Abstand). */
	public static int hintHeight(Font font, String text, int uiW) {
		return hintLines(font, text, uiW).size() * (skin().tagHeight() + 2) + 6;
	}

	private static List<String> hintLines(Font font, String text, int uiW) {
		Skin skin = skin();
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String part : text.split("\\s*·\\s*")) {
			String candidate = line.isEmpty() ? part : line + "  ·  " + part;
			if (!line.isEmpty() && skin.tagWidth(font, candidate) > uiW - 8) {
				lines.add(line.toString());
				line = new StringBuilder(part);
			} else {
				line = new StringBuilder(candidate);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}

	public static boolean inside(int[] r, double x, double y) {
		return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
	}

	/** Zustand eines Bedienelements unter der Maus. */
	public static Skin.UiState state(int[] r, double x, double y) {
		return Skin.UiState.of(inside(r, x, y));
	}
}
