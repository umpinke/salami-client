package dev.salamiclient.gui.skin;

import dev.salamiclient.render.GuiDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Texte in einen Platz einpassen, ohne sie mitten im Wort abzuschneiden: erst verkleinern, dann
 * umbrechen (auch nach Bindestrichen), erst ganz zuletzt mit „…“ kürzen. Für alle Looks.
 */
public final class TextFit {
	/** So klein wird ein Name höchstens, bevor er gekürzt wird. */
	public static final float MIN_SCALE = 0.75f;

	private TextFit() {}

	/**
	 * Einzeilig, linksbündig: passt der Text nicht, wird er bis {@link #MIN_SCALE} verkleinert (mittig
	 * zur Zeile), danach mit „…“ gekürzt. Liefert die gezeichnete Breite.
	 */
	public static int draw(GuiGraphics g, Font font, String text, int x, int y, int maxWidth, int color) {
		return draw(g, font, text, x, y, maxWidth, color, MIN_SCALE);
	}

	public static int draw(GuiGraphics g, Font font, String text, int x, int y, int maxWidth, int color, float minScale) {
		return draw(g, font, text, x, y, maxWidth, color, minScale, false);
	}

	/** Wie {@link #draw}, wahlweise mit Minecrafts Schriftschatten (Werkbank: Text auf Knöpfen und Slots). */
	public static int draw(GuiGraphics g, Font font, String text, int x, int y, int maxWidth, int color, float minScale, boolean shadow) {
		int width = font.width(text);
		if (width <= maxWidth || maxWidth <= 0) {
			GuiDraw.text(g, font, text, x, y, color, shadow);
			return width;
		}
		float scale = Math.max(minScale, maxWidth / (float) width);
		String shown = text;
		if (width * scale > maxWidth) {
			shown = GuiDraw.ellipsize(font, text, (int) (maxWidth / scale));
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y + (1 - scale) * 4);
		g.pose().scale(scale, scale);
		GuiDraw.text(g, font, shown, 0, 0, color, shadow);
		g.pose().popMatrix();
		return Math.round(font.width(shown) * scale);
	}

	/** Wie {@link #draw}, aber mittig um {@code centerX}. */
	public static void drawCentered(GuiGraphics g, Font font, String text, int centerX, int y, int maxWidth, int color) {
		drawCentered(g, font, text, centerX, y, maxWidth, color, false);
	}

	/** Wie {@link #drawCentered}, wahlweise mit Schriftschatten. */
	public static void drawCentered(GuiGraphics g, Font font, String text, int centerX, int y, int maxWidth, int color, boolean shadow) {
		int width = font.width(text);
		if (width <= maxWidth) {
			GuiDraw.text(g, font, text, centerX - width / 2, y, color, shadow);
			return;
		}
		float scale = Math.max(MIN_SCALE, maxWidth / (float) width);
		String shown = width * scale > maxWidth ? GuiDraw.ellipsize(font, text, (int) (maxWidth / scale)) : text;
		g.pose().pushMatrix();
		g.pose().translate(centerX - font.width(shown) * scale / 2f, y + (1 - scale) * 4);
		g.pose().scale(scale, scale);
		GuiDraw.text(g, font, shown, 0, 0, color, shadow);
		g.pose().popMatrix();
	}

	/** Breite, die {@link #draw} höchstens braucht. */
	public static int width(Font font, String text, int maxWidth) {
		return Math.min(font.width(text), maxWidth);
	}

	/**
	 * Bricht {@code text} in höchstens {@code maxLines} Zeilen um – an Leerzeichen und nach Bindestrichen
	 * („Panel-“ / „Ansicht“). Passt ein einzelnes Wort nicht in eine Zeile, oder bleibt nach der letzten Zeile
	 * Text übrig, endet die letzte Zeile mit „…“.
	 */
	public static List<String> wrap(Font font, String text, int width, int maxLines) {
		// Wörter; glued[i]: Wort i endet mit einem Bindestrich mitten im Wort (ohne Leerzeichen danach)
		List<String> words = new ArrayList<>();
		List<Boolean> glued = new ArrayList<>();
		StringBuilder word = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == ' ') {
				if (!word.isEmpty()) {
					words.add(word.toString());
					glued.add(false);
					word.setLength(0);
				}
				continue;
			}
			word.append(c);
			if (c == '-' && word.length() > 1 && i + 1 < text.length() && text.charAt(i + 1) != ' ') {
				words.add(word.toString());
				glued.add(true);
				word.setLength(0);
			}
		}
		if (!word.isEmpty()) {
			words.add(word.toString());
			glued.add(false);
		}
		List<String> lines = new ArrayList<>();
		int i = 0;
		while (i < words.size() && lines.size() < maxLines) {
			StringBuilder line = new StringBuilder(words.get(i));
			boolean joinTight = glued.get(i);
			i++;
			while (i < words.size()) {
				String candidate = line + (joinTight ? "" : " ") + words.get(i);
				if (font.width(candidate) > width) {
					break;
				}
				line = new StringBuilder(candidate);
				joinTight = glued.get(i);
				i++;
			}
			lines.add(line.toString());
		}
		boolean rest = i < words.size();
		for (int k = 0; k < lines.size(); k++) {
			String l = lines.get(k);
			boolean last = k == lines.size() - 1;
			if (last && rest) {
				lines.set(k, GuiDraw.ellipsize(font, l + "…", width));
			} else if (font.width(l) > width) {
				lines.set(k, GuiDraw.ellipsize(font, l, width));
			}
		}
		return lines;
	}

	/**
	 * Umbruch für längere Texte (Beschreibungen) in {@code width} bei Maßstab 1, sonst kleiner: liefert
	 * {Maßstab, Zeilen}. Bei 1 passen höchstens {@code maxHeight / 10} Zeilen, bei {@link #MIN_SCALE} entsprechend
	 * mehr. Passt der Text auch dann nicht, endet er nach dem letzten ganzen Satz, der noch passt (sonst „…“).
	 */
	public static Wrapped wrapBlock(Font font, FormattedText text, int width, int maxHeight) {
		for (float scale : new float[] {1f, MIN_SCALE}) {
			int lineH = Math.round(10 * scale);
			int maxLines = Math.max(1, maxHeight / lineH);
			List<FormattedCharSequence> lines = font.split(text, (int) (width / scale));
			if (lines.size() <= maxLines) {
				return new Wrapped(scale, lines, false);
			}
		}
		int maxLines = Math.max(1, maxHeight / Math.round(10 * MIN_SCALE));
		String plain = text.getString();
		// rückwärts den letzten Satz suchen, der noch passt
		for (int end = plain.length() - 1; end > 0; end--) {
			char c = plain.charAt(end - 1);
			if ((c == '.' || c == '!' || c == '?') && (end == plain.length() || plain.charAt(end) == ' ')) {
				List<FormattedCharSequence> lines = font.split(Component.literal(plain.substring(0, end)), (int) (width / MIN_SCALE));
				if (lines.size() <= maxLines) {
					return new Wrapped(MIN_SCALE, lines, true);
				}
			}
		}
		List<FormattedCharSequence> lines = font.split(text, (int) (width / MIN_SCALE));
		return new Wrapped(MIN_SCALE, lines.subList(0, maxLines), true);
	}

	/**
	 * Ergebnis von {@link #wrapBlock}.
	 *
	 * @param cut {@code true}, wenn nicht alles hineinpasst – dann den ganzen Text zusätzlich als Tooltip zeigen
	 */
	public record Wrapped(float scale, List<FormattedCharSequence> lines, boolean cut) {
		public int lineHeight() {
			return Math.round(10 * scale);
		}

		public int height() {
			return lines.size() * lineHeight();
		}

		/** Zeichnet die Zeilen ab (x, y). */
		public void draw(GuiGraphics g, Font font, int x, int y, int color) {
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().scale(scale, scale);
			for (int i = 0; i < lines.size(); i++) {
				GuiDraw.text(g, font, lines.get(i), 0, i * 10, color, false);
			}
			g.pose().popMatrix();
		}
	}
}
