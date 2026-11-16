package dev.salamiclient.minigames;

import dev.salamiclient.theme.Theme;

/**
 * Zeichenfläche eines Minispiels in eigenen Spielkoordinaten (Gleitkomma, Ursprung links oben,
 * Größe {@link Minigame#width()} × {@link Minigame#height()}). Der Bildschirm bildet sie skaliert und
 * zentriert auf die GUI ab und rundet Kanten auf echte Bildschirmpixel – die Spiele selbst kennen
 * keine Minecraft-Klassen.
 */
public interface Canvas {
	/** Gefülltes Rechteck; leere oder negative Flächen werden ignoriert. */
	void rect(float x0, float y0, float x1, float y1, int argb);

	/** Senkrechter Farbverlauf von {@code top} nach {@code bottom}. */
	void gradient(float x0, float y0, float x1, float y1, int top, int bottom);

	/** Text, waagerecht und senkrecht mittig um ({@code centerX}, {@code centerY}); {@code size} = Zeilenhöhe. */
	void text(String text, float centerX, float centerY, float size, int argb);

	/** Breite eines Texts der Zeilenhöhe {@code size} in Spielkoordinaten. */
	float textWidth(String text, float size);

	/** Übersetzter Text (Sprachdatei). */
	String tr(String key, Object... args);

	/** Aktives Farbschema (Salami-Look oder Standard). */
	Theme theme();

	/** Größe eines Bildschirmpixels in Spielkoordinaten. */
	float pixel();

	/** Zeichnen auf ein Rechteck begrenzen (verschachtelbar) … */
	void clip(float x0, float y0, float x1, float y1);

	/** … und die letzte Begrenzung wieder aufheben. */
	void unclip();

	/** Rahmen der Dicke {@code thickness} innen am Rechteck. */
	default void outline(float x0, float y0, float x1, float y1, float thickness, int argb) {
		rect(x0, y0, x1, y0 + thickness, argb);
		rect(x0, y1 - thickness, x1, y1, argb);
		rect(x0, y0 + thickness, x0 + thickness, y1 - thickness, argb);
		rect(x1 - thickness, y0 + thickness, x1, y1 - thickness, argb);
	}

	/** Achsparallele dicke Linie von (x0, y0) nach (x1, y1) mit halber Breite {@code half}. */
	default void bar(float x0, float y0, float x1, float y1, float half, int argb) {
		rect(Math.min(x0, x1) - half, Math.min(y0, y1) - half, Math.max(x0, x1) + half, Math.max(y0, y1) + half, argb);
	}

	/** Gefüllter Kreis aus waagerechten Streifen (etwa ein Streifen pro Bildschirmpixel). */
	default void circle(float cx, float cy, float r, int argb) {
		if (r <= 0) {
			return;
		}
		int strips = Math.max(2, Math.min(24, Math.round(r / pixel())));
		float h = r / strips;
		for (int i = 0; i < strips; i++) {
			float d = r - (i + 0.5f) * h;
			float half = (float) Math.sqrt(Math.max(0f, r * r - d * d));
			rect(cx - half, cy - r + i * h, cx + half, cy - r + (i + 1) * h, argb);
			rect(cx - half, cy + r - (i + 1) * h, cx + half, cy + r - i * h, argb);
		}
	}

	/** Rechteck mit abgerundeten Ecken (Radius {@code r}). */
	default void roundRect(float x0, float y0, float x1, float y1, float r, int argb) {
		r = Math.min(r, Math.min((x1 - x0) / 2f, (y1 - y0) / 2f));
		if (r <= pixel()) {
			rect(x0, y0, x1, y1, argb);
			return;
		}
		int strips = Math.max(1, Math.min(8, Math.round(r / pixel())));
		float h = r / strips;
		rect(x0, y0 + r, x1, y1 - r, argb);
		for (int i = 0; i < strips; i++) {
			float d = r - (i + 0.5f) * h;
			float inset = r - (float) Math.sqrt(Math.max(0f, r * r - d * d));
			rect(x0 + inset, y0 + i * h, x1 - inset, y0 + (i + 1) * h, argb);
			rect(x0 + inset, y1 - (i + 1) * h, x1 - inset, y1 - i * h, argb);
		}
	}
}
