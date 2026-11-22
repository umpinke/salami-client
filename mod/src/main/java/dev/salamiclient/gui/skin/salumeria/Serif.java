package dev.salamiclient.gui.skin.salumeria;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.render.GuiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Serifenschrift des Salumeria-Looks: Cormorant Garamond (Medium, gerade und kursiv) als TTF-Schrift
 * unter {@code assets/salami/font/} – erzeugt von {@code mod/tools/skin-textures/salumeria.py}.
 *
 * <p>Minecraft zeichnet TTF-Glyphen ohne Glättung (nächster Bildpunkt); scharf sind sie nur, wenn
 * „oversample“ der Zahl der Bildschirmpixel pro gezeichnetem Pixel entspricht. Jeder Schnitt liegt
 * deshalb in mehreren Auflösungen vor ({@code font/salumeria/<schnitt>_<oversample×10>.json}); hier wird
 * beim Zeichnen die passende gewählt (GUI-Größe × Maßstab der Matrix × Textgröße). Beim Messen ohne
 * {@link GuiGraphics} (Treffer, Layout) gilt GUI-Größe × Menügröße × Textgröße – die Breiten der Fassungen
 * unterscheiden sich höchstens um Bruchteile eines Pixels.
 *
 * <p>Die Grundlinie liegt wie bei Minecrafts Schrift 7 Pixel unter der Oberkante; Versalhöhe ≈ 6,9 bei
 * Größe 1 (wie die Pixelschrift), Mittellänge ≈ 4,3, Unterlänge ≈ 3,2. Kapitälchen: Kleinbuchstaben als
 * Versalien aus dem kleineren Schnitt „caps“ (Minecraft kennt keine font-variant).
 */
final class Serif {
	/** Bildschirmpixel pro gezeichnetem Pixel, für die es eine scharfe Fassung gibt (siehe salumeria.py). */
	private static final float[] OVERSAMPLE = {1f, 1.5f, 2f, 2.5f, 3f, 4f, 5f, 6f};

	/** Grundlinie unter der Oberkante (bei Größe 1), wie bei Minecrafts Schrift. */
	static final float BASELINE = 7f;
	static final float CAP = 6.9f;
	static final float X_HEIGHT = 4.3f;
	static final float DESCENT = 3.2f;

	enum Face {
		ROMAN("roman"),
		ITALIC("italic"),
		/** Kleinere Antiqua für Kapitälchen. */
		CAPS("caps");

		private final Style[] styles = new Style[OVERSAMPLE.length];

		Face(String file) {
			for (int i = 0; i < OVERSAMPLE.length; i++) {
				Identifier id = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "salumeria/" + file + "_" + Math.round(OVERSAMPLE[i] * 10));
				styles[i] = Style.EMPTY.withFont(new FontDescription.Resource(id));
			}
		}

		Style style(int variant) {
			return styles[variant];
		}
	}

	/** Antiqua, Textgröße. */
	static final Type ROMAN = new Type(Face.ROMAN, 1f, false);
	/** Kapitälchen, Textgröße. */
	static final Type CAPS = new Type(Face.ROMAN, 1f, true);
	/** Kursiv, Textgröße. */
	static final Type ITALIC = new Type(Face.ITALIC, 1f, false);

	private Serif() {}

	static int guiScale() {
		return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
	}

	/** Bildschirmpixel pro Pixel der aktuellen Matrix. */
	static float drawRatio(GuiGraphics g) {
		return guiScale() * Math.abs(g.pose().m00());
	}

	/** Bildschirmpixel pro UI-Pixel der Salami-Menüs (für Messungen ohne Matrix). */
	static float layoutRatio() {
		return guiScale() * GuiScaleModule.clickGuiScale();
	}

	/** Fassung, deren Auflösung {@code ratio} am nächsten kommt. */
	static int variant(float ratio) {
		int best = 0;
		for (int i = 1; i < OVERSAMPLE.length; i++) {
			if (Math.abs(OVERSAMPLE[i] - ratio) < Math.abs(OVERSAMPLE[best] - ratio)) {
				best = i;
			}
		}
		return best;
	}

	/**
	 * Eine Schriftart: Schnitt, Größe (1 = Versalhöhe der Pixelschrift) und ob Kleinbuchstaben als
	 * Kapitälchen gesetzt werden.
	 */
	record Type(Face face, float scale, boolean caps) {
		Type scaled(float factor) {
			return new Type(face, scale * factor, caps);
		}

		Type size(float size) {
			return new Type(face, size, caps);
		}

		Type smallCaps(boolean on) {
			return new Type(face, scale, on);
		}

		/** Der Text als Component in der Fassung {@code variant} (ohne Maßstab). */
		MutableComponent component(String text, int variant) {
			if (!caps || face != Face.ROMAN) {
				return Component.literal(text).withStyle(face.style(variant));
			}
			MutableComponent out = Component.empty();
			StringBuilder run = new StringBuilder();
			boolean small = false;
			for (int i = 0; i < text.length(); i++) {
				char c = text.charAt(i);
				boolean lower = Character.isLowerCase(c);
				if (lower != small && !run.isEmpty()) {
					out.append(Component.literal(run.toString()).withStyle((small ? Face.CAPS : Face.ROMAN).style(variant)));
					run.setLength(0);
				}
				small = lower;
				run.append(lower ? String.valueOf(c).toUpperCase(java.util.Locale.ROOT) : String.valueOf(c));
			}
			if (!run.isEmpty()) {
				out.append(Component.literal(run.toString()).withStyle((small ? Face.CAPS : Face.ROMAN).style(variant)));
			}
			return out;
		}

		/** Breite in Pixeln der aktuellen Matrix (Layout, Treffer). */
		float width(Font font, String text) {
			if (text == null || text.isEmpty()) {
				return 0;
			}
			int variant = variant(layoutRatio() * scale);
			return font.getSplitter().stringWidth(component(text, variant)) * scale;
		}

		int widthInt(Font font, String text) {
			return (int) Math.ceil(width(font, text));
		}

		/** Zeichnet {@code text} mit der Grundlinie bei {@code baseline}. */
		void draw(GuiGraphics g, Font font, String text, float x, float baseline, int color) {
			if (text == null || text.isEmpty()) {
				return;
			}
			int variant = variant(drawRatio(g) * scale);
			g.pose().pushMatrix();
			g.pose().translate(x, baseline - BASELINE * scale);
			g.pose().scale(scale, scale);
			GuiDraw.text(g, font, component(text, variant), 0, 0, color, false);
			g.pose().popMatrix();
		}

		/** Rechtsbündig an {@code right}. */
		void drawRight(GuiGraphics g, Font font, String text, float right, float baseline, int color) {
			draw(g, font, text, right - width(font, text), baseline, color);
		}

		/** Mittig um {@code center}. */
		void drawCentered(GuiGraphics g, Font font, String text, float center, float baseline, int color) {
			draw(g, font, text, center - width(font, text) / 2f, baseline, color);
		}

		float cap() {
			return CAP * scale;
		}

		/** Höhe der Kapitälchen (bzw. Mittellänge ohne Kapitälchen). */
		float small() {
			return caps ? CAP * 8f / 11f * scale : X_HEIGHT * scale;
		}

		float descent() {
			return DESCENT * scale;
		}
	}

	// ------------------------------------------------------------------ Einpassen

	/** Ergebnis von {@link #fit}: Schriftart (evtl. verkleinert) und Text (zuletzt evtl. mit „…“). */
	record Fitted(Type type, String text, float width) {
		void draw(GuiGraphics g, Font font, float x, float baseline, int color) {
			type.draw(g, font, text, x, baseline, color);
		}

		void drawRight(GuiGraphics g, Font font, float right, float baseline, int color) {
			type.draw(g, font, text, right - width, baseline, color);
		}

		void drawCentered(GuiGraphics g, Font font, float center, float baseline, int color) {
			type.draw(g, font, text, center - width / 2f, baseline, color);
		}
	}

	/**
	 * Passt {@code text} in {@code maxWidth}: erst Kapitälchen ohne Kapitälchen (falls gesetzt), dann
	 * verkleinert bis {@code minFactor}, erst ganz zuletzt mit „…“ gekürzt – nie mitten im Wort, solange es
	 * sich vermeiden lässt.
	 */
	static Fitted fit(Font font, Type type, String text, float maxWidth, float minFactor) {
		float w = type.width(font, text);
		if (w <= maxWidth || maxWidth <= 0) {
			return new Fitted(type, text, w);
		}
		float k = Math.max(minFactor, maxWidth / w);
		Type small = type.scaled(k);
		float ws = small.width(font, text);
		if (ws <= maxWidth) {
			return new Fitted(small, text, ws);
		}
		String shown = ellipsize(font, small, text, maxWidth);
		return new Fitted(small, shown, small.width(font, shown));
	}

	static Fitted fit(Font font, Type type, String text, float maxWidth) {
		return fit(font, type, text, maxWidth, 0.8f);
	}

	/** Längster Anfang von {@code text}, der mit „…“ in {@code maxWidth} passt. */
	static String ellipsize(Font font, Type type, String text, float maxWidth) {
		if (type.width(font, text) <= maxWidth) {
			return text;
		}
		int lo = 0;
		int hi = text.length();
		while (lo < hi) {
			int mid = (lo + hi + 1) / 2;
			if (type.width(font, text.substring(0, mid).stripTrailing() + "…") <= maxWidth) {
				lo = mid;
			} else {
				hi = mid - 1;
			}
		}
		return text.substring(0, lo).stripTrailing() + "…";
	}

	/**
	 * Bricht {@code text} an Leerzeichen in Zeilen von höchstens {@code maxWidth} um (ein einzelnes zu langes
	 * Wort bleibt eine eigene Zeile). Ohne Kürzen – die Aufrufer prüfen die Zeilenzahl.
	 */
	static List<String> wrap(Font font, Type type, String text, float maxWidth) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.trim().split("\\s+")) {
			if (word.isEmpty()) {
				continue;
			}
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && type.width(font, candidate) > maxWidth) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(candidate);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}

	/** Teilt {@code text} in Sätze (Satzzeichen + Leerzeichen); der Rest ohne Satzzeichen ist ein eigener Satz. */
	static List<String> sentences(String text) {
		List<String> out = new ArrayList<>();
		int start = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if ((c == '.' || c == '!' || c == '?') && (i + 1 == text.length() || text.charAt(i + 1) == ' ')) {
				// „z. B.“ und Ähnliches nicht als Satzende nehmen: davor steht ein einzelner Buchstabe
				boolean abbreviation = i >= 1 && Character.isLetter(text.charAt(i - 1)) && (i < 2 || !Character.isLetter(text.charAt(i - 2)));
				if (!abbreviation) {
					out.add(text.substring(start, i + 1).trim());
					start = i + 1;
				}
			}
		}
		if (start < text.length() && !text.substring(start).isBlank()) {
			out.add(text.substring(start).trim());
		}
		return out;
	}
}
