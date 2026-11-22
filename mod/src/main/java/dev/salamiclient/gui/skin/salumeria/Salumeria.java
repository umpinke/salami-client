package dev.salamiclient.gui.skin.salumeria;

import dev.salamiclient.SalamiClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Farben und Zeichenhelfer der Salumeria (wie der Launcher-Look „Salumeria“): das Etikett eines
 * italienischen Feinkostladens – fast schwarzes Burgunder, cremefarbene Schrift und feine doppelte
 * Linien, Salami-Rot mit kleinen Rauten, Serifenschrift ({@link Serif}), römische Ziffern und
 * Punktlinien wie auf einer Speisekarte.
 *
 * <p>Linien, Punkte und Rauten werden in Bildschirmpixeln gezeichnet (eine Haarlinie ist bei GUI-Größe 2
 * einen halben GUI-Pixel dünn) – so bleiben sie bei jeder GUI-Größe fein und scharf.
 */
final class Salumeria {
	// Etikett
	static final int BG = 0xFF1A1110;
	static final int BG_DEEP = 0xFF130C0B;
	static final int BG_RAISED = 0xFF241716;
	static final int BG_HOVER = 0xFF2C1C1A;
	// Schrift
	static final int CREAM = 0xFFF3E7D6;
	static final int CREAM_DIM = 0xFFBFAE9B;
	static final int CREAM_SOFT = 0xFFA89685;
	static final int CREAM_FAINT = 0xFF8C7B6C;
	// Linien (Creme mit Deckkraft)
	static final int RULE = 0x9EF3E7D6;
	static final int RULE_SOFT = 0x5CF3E7D6;
	static final int RULE_FAINT = 0x2EF3E7D6;
	// Salami
	static final int RED = 0xFFB23A3A;
	static final int RED_HI = 0xFFC9524A;
	static final int RED_DARK = 0xFF8A2A2A;
	/** Rot für Schrift auf dem dunklen Grund (heller als {@link #RED}, damit es lesbar bleibt). */
	static final int RED_TEXT = 0xFFDA6A5E;
	static final int WARNING = 0xFFE39A62;

	static final Identifier LOGO = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "salumeria/logo");

	private Salumeria() {}

	static void sprite(GuiGraphics g, Identifier id, int x, int y, int w, int h) {
		if (w > 0 && h > 0) {
			g.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, w, h);
		}
	}

	// ------------------------------------------------------------------ Bildschirmpixel

	/** Füllt ein Rechteck in Bildschirmpixeln ({@code dx0..dx1} usw. sind schon mit {@code r} multipliziert). */
	private static void devFill(GuiGraphics g, float r, int dx0, int dy0, int dx1, int dy1, int color) {
		if (dx1 > dx0 && dy1 > dy0) {
			g.fill(dx0, dy0, dx1, dy1, color);
		}
	}

	private static float begin(GuiGraphics g) {
		float r = Serif.drawRatio(g);
		g.pose().pushMatrix();
		g.pose().scale(1 / r, 1 / r);
		return r;
	}

	private static void end(GuiGraphics g) {
		g.pose().popMatrix();
	}

	/** Waagrechte Haarlinie (1 Bildschirmpixel) an der Oberkante der UI-Zeile {@code y}. */
	static void hline(GuiGraphics g, float x0, float x1, float y, int color) {
		float r = begin(g);
		int dy = Math.round(y * r);
		devFill(g, r, Math.round(x0 * r), dy, Math.round(x1 * r), dy + 1, color);
		end(g);
	}

	/** Senkrechte Haarlinie an der linken Kante der UI-Spalte {@code x}. */
	static void vline(GuiGraphics g, float x, float y0, float y1, int color) {
		float r = begin(g);
		int dx = Math.round(x * r);
		devFill(g, r, dx, Math.round(y0 * r), dx + 1, Math.round(y1 * r), color);
		end(g);
	}

	/** Rahmen aus Haarlinien innen an der Kante von (x, y, w, h). */
	static void hairRect(GuiGraphics g, float x, float y, float w, float h, int color) {
		float r = begin(g);
		int x0 = Math.round(x * r);
		int y0 = Math.round(y * r);
		int x1 = Math.round((x + w) * r);
		int y1 = Math.round((y + h) * r);
		devFill(g, r, x0, y0, x1, y0 + 1, color);
		devFill(g, r, x0, y1 - 1, x1, y1, color);
		devFill(g, r, x0, y0 + 1, x0 + 1, y1 - 1, color);
		devFill(g, r, x1 - 1, y0 + 1, x1, y1 - 1, color);
		end(g);
	}

	/** Doppelte Linie wie auf dem Etikett: außen {@code outer}, {@code gap} UI-Pixel weiter innen {@code inner}. */
	static void doubleRule(GuiGraphics g, float x, float y, float w, float h, float gap, int outer, int inner) {
		hairRect(g, x, y, w, h, outer);
		hairRect(g, x + gap, y + gap, w - 2 * gap, h - 2 * gap, inner);
	}

	/** Linie mit Raute in der Mitte (Abschnittstrenner). */
	static void ruleWithDiamond(GuiGraphics g, float x0, float x1, float y, int line, int diamond) {
		float cx = (x0 + x1) / 2f;
		hline(g, x0, cx - 5, y, line);
		hline(g, cx + 5, x1, y, line);
		diamond(g, cx, y + 0.25f, 1.75f, diamond, true);
	}

	/**
	 * Punktlinie (wie die Führungspunkte einer Speisekarte) auf der Zeile {@code y}: feine Punkte in
	 * Bildschirmpixeln, rechtsbündig ausgerichtet, damit sie bei {@code x1} sauber enden.
	 */
	static void leader(GuiGraphics g, float x0, float x1, float y, int color) {
		if (x1 - x0 < 3) {
			return;
		}
		float r = begin(g);
		int dot = Math.max(1, Math.round(r / 2f));
		int step = dot * 3;
		int dy = Math.round(y * r);
		int left = Math.round(x0 * r);
		for (int dx = Math.round(x1 * r) - dot; dx >= left; dx -= step) {
			devFill(g, r, dx, dy, dx + dot, dy + dot, color);
		}
		end(g);
	}

	/**
	 * Raute um (cx, cy) mit halber Diagonale {@code half} (UI-Pixel), gefüllt oder als Umriss, pixelgenau
	 * in Bildschirmpixeln.
	 */
	static void diamond(GuiGraphics g, float cx, float cy, float half, int color, boolean filled) {
		float r = begin(g);
		int x = Math.round(cx * r);
		int y = Math.round(cy * r);
		int h = Math.max(1, Math.round(half * r));
		// Umriss so dick, dass er bei GUI-Größe 2 deutlich, aber fein bleibt
		int t = Math.max(1, Math.round(r * 0.55f));
		int inner = h - Math.max(1, Math.round(t * 1.41f));
		for (int dy = -h; dy <= h; dy++) {
			int w = h - Math.abs(dy);
			int wi = inner - Math.abs(dy);
			if (filled || wi < 0) {
				devFill(g, r, x - w, y + dy, x + w + 1, y + dy + 1, color);
			} else {
				devFill(g, r, x - w, y + dy, x - wi, y + dy + 1, color);
				devFill(g, r, x + wi + 1, y + dy, x + w + 1, y + dy + 1, color);
			}
		}
		end(g);
	}

	/** Weicher Schatten unter einer Fläche. */
	static void shadow(GuiGraphics g, int x, int y, int w, int h) {
		g.fill(x + 1, y + 2, x + w + 2, y + h + 3, 0x30000000);
		g.fill(x + 2, y + 3, x + w + 1, y + h + 2, 0x30000000);
	}

	// ------------------------------------------------------------------ Zahlen

	private static final String[] ROMAN_SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
	private static final int[] ROMAN_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};

	/** Römische Ziffer (1 → I, 4 → IV, 12 → XII). */
	static String roman(int n) {
		if (n <= 0) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < ROMAN_VALUES.length; i++) {
			while (n >= ROMAN_VALUES[i]) {
				out.append(ROMAN_SYMBOLS[i]);
				n -= ROMAN_VALUES[i];
			}
		}
		return out.toString();
	}

	private static final String[] ONES = {"zero", "uno", "due", "tre", "quattro", "cinque", "sei", "sette", "otto", "nove", "dieci",
		"undici", "dodici", "tredici", "quattordici", "quindici", "sedici", "diciassette", "diciotto", "diciannove"};
	private static final String[] TENS = {"", "", "venti", "trenta", "quaranta", "cinquanta", "sessanta", "settanta", "ottanta", "novanta"};

	/** Zahl als italienisches Zahlwort (0–9999), z. B. 69 → „sessantanove“ – für den Untertitel des Etiketts. */
	static String italian(int n) {
		if (n < 0 || n > 9999) {
			return String.valueOf(n);
		}
		if (n < 20) {
			return ONES[n];
		}
		if (n < 100) {
			String tens = TENS[n / 10];
			int one = n % 10;
			if (one == 0) {
				return tens;
			}
			// venti + uno → ventuno, trenta + otto → trentotto, venti + tre → ventitré
			if (one == 1 || one == 8) {
				tens = tens.substring(0, tens.length() - 1);
			}
			return tens + (one == 3 ? "tré" : ONES[one]);
		}
		if (n < 1000) {
			int hundreds = n / 100;
			int rest = n % 100;
			String head = hundreds == 1 ? "cento" : ONES[hundreds] + "cento";
			return rest == 0 ? head : head + italian(rest);
		}
		int thousands = n / 1000;
		int rest = n % 1000;
		String head = thousands == 1 ? "mille" : ONES[thousands] + "mila";
		return rest == 0 ? head : head + italian(rest);
	}
}
