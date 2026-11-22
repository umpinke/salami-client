package dev.salamiclient.gui.skin.theke;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.render.GuiDraw;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Farben, Texturen und Zeichenhelfer der Feinkost-Theke (wie der Launcher-Look „Feinkost-Theke“):
 * Markise, Kreidetafeln im Holzrahmen an Schienen, Metzgerpapier, runde Stempel, Preisschilder.
 *
 * <p>Die Texturen liegen als GUI-Sprites unter {@code assets/salami/textures/gui/sprites/theke/}
 * (doppelte Auflösung, Kacheln über {@code .mcmeta}).
 */
final class Theke {
	// Papier und Schrift
	static final int PEPPER = 0xFF2B1D1A;
	static final int INK = 0xFF7A5A4A;
	static final int INK_LIGHT = 0xFFA08672;
	static final int PAPER = 0xFFF4EAD9;
	static final int SHEET = 0xFFFFFAF1;
	static final int PAPER_2 = 0xFFEFE2CC;
	static final int PAPER_EDGE = 0xFFD9C7AA;
	static final int PAPER_DEEP = 0xFFC8B292;
	static final int RECEIPT = 0xFFFFFDF8;
	// Salami
	static final int RED = 0xFFB23A3A;
	static final int RED_HI = 0xFFC4483F;
	static final int RED_DARK = 0xFF8A2A2A;
	static final int FAT = 0xFFF3E3D3;
	static final int CREAM = 0xFFFFF3E6;
	// Kreidetafel
	static final int CHALK = 0xFF23302B;
	static final int CHALK_TEXT = 0xFFE9EFE6;
	static final int CHALK_DIM = 0xFF93A69B;
	static final int CHALK_YELLOW = 0xFFF2C46D;
	static final int CHALK_LINE = 0xFF52665D;
	// Holz und Schiene
	static final int WOOD = 0xFF5A3A28;
	static final int WOOD_DARK = 0xFF3D2619;
	static final int WOOD_LIGHT = 0xFF7A5238;
	static final int RAIL = 0xFF9B8571;
	static final int RAIL_DARK = 0xFF6F5C4A;
	static final int WARNING = 0xFFB0521C;

	static final Identifier AWNING = sprite("awning");
	static final Identifier WOOD_TEX = sprite("wood");
	static final Identifier CHALK_TEX = sprite("chalk");
	static final Identifier PAPER_TEX = sprite("paper");
	static final Identifier STAMP_ON = sprite("stamp_on");
	static final Identifier STAMP_ON_HOVER = sprite("stamp_on_hover");
	static final Identifier STAMP_OFF = sprite("stamp_off");
	static final Identifier STAMP_OFF_HOVER = sprite("stamp_off_hover");
	static final Identifier STAMP_PLAY = sprite("stamp_play");
	static final Identifier STAMP_PLAY_HOVER = sprite("stamp_play_hover");
	static final Identifier KNOB = sprite("knob");
	static final Identifier KNOB_HOVER = sprite("knob_hover");

	/** Höhe der Markise (Stange, Stoffbahn und Bögen). */
	static final int AWNING_H = 17;

	private Theke() {}

	private static Identifier sprite(String name) {
		return Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "theke/" + name);
	}

	static void sprite(GuiGraphics g, Identifier id, int x, int y, int w, int h) {
		sprite(g, id, x, y, w, h, 0xFFFFFFFF);
	}

	static void sprite(GuiGraphics g, Identifier id, int x, int y, int w, int h, int color) {
		if (w > 0 && h > 0) {
			g.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, w, h, color);
		}
	}

	// ------------------------------------------------------------------ Grundformen

	/** Rechteck mit abgeschnittenen Ecken (1 Pixel). */
	static void box(GuiGraphics g, int x, int y, int w, int h, int color) {
		if (w <= 2 || h <= 2) {
			g.fill(x, y, x + w, y + h, color);
			return;
		}
		g.fill(x + 1, y, x + w - 1, y + h, color);
		g.fill(x, y + 1, x + 1, y + h - 1, color);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	/** Rahmen (1 Pixel) mit abgeschnittenen Ecken. */
	static void frame(GuiGraphics g, int x, int y, int w, int h, int color) {
		g.fill(x + 1, y, x + w - 1, y + 1, color);
		g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
		g.fill(x, y + 1, x + 1, y + h - 1, color);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	static void shadow(GuiGraphics g, int x, int y, int w, int h, int offset) {
		box(g, x + offset, y + offset + 1, w, h, 0x40000000);
	}

	/** Gepunktete Linie (Preisliste): ein Punkt alle {@code step} Pixel. */
	static void dotted(GuiGraphics g, int x0, int x1, int y, int color, int step) {
		for (int x = x0; x < x1; x += step) {
			g.fill(x, y, x + 1, y + 1, color);
		}
	}

	/** Gestrichelte waagrechte Linie (3 an, 2 aus). */
	static void dashed(GuiGraphics g, int x0, int x1, int y, int color) {
		for (int x = x0; x < x1; x += 5) {
			g.fill(x, y, Math.min(x1, x + 3), y + 1, color);
		}
	}

	/** Gestrichelte senkrechte Linie. */
	static void dashedV(GuiGraphics g, int x, int y0, int y1, int color) {
		for (int y = y0; y < y1; y += 4) {
			g.fill(x, y, x + 1, Math.min(y1, y + 2), color);
		}
	}

	// ------------------------------------------------------------------ Flächen

	/** Metzgerpapier, eingefärbt mit {@code tint}, mit Kante und Schatten. */
	static void paper(GuiGraphics g, int x, int y, int w, int h, int tint, boolean shadow) {
		if (shadow) {
			shadow(g, x, y, w, h, 2);
		}
		sprite(g, PAPER_TEX, x + 1, y + 1, w - 2, h - 2, tint);
		frame(g, x, y, w, h, PAPER_EDGE);
	}

	static void wood(GuiGraphics g, int x, int y, int w, int h) {
		sprite(g, WOOD_TEX, x, y, w, h);
	}

	/** Kreidetafel im Holzrahmen. Rahmen 4 Pixel, bei kleinen Tafeln 2. */
	static void board(GuiGraphics g, int x, int y, int w, int h) {
		int t = h >= 30 && w >= 40 ? 4 : 2;
		shadow(g, x, y, w, h, 2);
		wood(g, x, y, w, h);
		frame(g, x, y, w, h, WOOD_DARK);
		// Licht oben am Rahmen
		g.fill(x + 1, y + 1, x + w - 1, y + 2, WOOD_LIGHT);
		sprite(g, CHALK_TEX, x + t, y + t, w - 2 * t, h - 2 * t);
		// Innenkante: Schatten oben/links auf der Tafel
		g.fill(x + t, y + t, x + w - t, y + t + 1, 0x50000000);
		g.fill(x + t, y + t + 1, x + t + 1, y + h - t, 0x30000000);
	}

	static int boardFrame(int w, int h) {
		return h >= 30 && w >= 40 ? 4 : 2;
	}

	/** Schnur von einer Schiene bzw. der Markise zu einem Schild. */
	static void string(GuiGraphics g, int x, int y0, int y1) {
		g.fill(x, y0, x + 1, y1, RAIL);
		g.fill(x - 1, y1 - 1, x + 2, y1, RAIL_DARK);
	}

	/** Schiene (Messingstange) mit Halterungen an den Enden. */
	static void rail(GuiGraphics g, int x0, int x1, int y) {
		g.fill(x0, y, x1, y + 1, 0xFFBDA991);
		g.fill(x0, y + 1, x1, y + 2, RAIL_DARK);
		g.fill(x0, y + 2, x1, y + 3, 0x30000000);
		g.fill(x0 - 2, y - 2, x0, y + 4, RAIL_DARK);
		g.fill(x1, y - 2, x1 + 2, y + 4, RAIL_DARK);
	}

	/** Markise über die ganze Breite (oben bei y = 0). */
	static void awning(GuiGraphics g, int width) {
		// Schatten der Markise auf dem Hintergrund
		g.fillGradient(0, AWNING_H - 6, width, AWNING_H + 8, 0x60000000, 0x00000000);
		// Streifen mittig ausrichten: eine halbe Periode Versatz je nach Breite
		int offset = ((width % 20) / 2) - 20;
		sprite(g, AWNING, offset, 1, width - offset, 16);
		// Stange
		g.fill(0, 0, width, 1, WOOD_DARK);
		g.fill(0, 1, width, 2, 0x60000000);
	}

	// ------------------------------------------------------------------ Schrift

	/** Fette Überschrift in {@code scale}-facher Größe. */
	static void heading(GuiGraphics g, Font font, String text, float x, float y, int color, float scale) {
		Component bold = Component.literal(text).withStyle(ChatFormatting.BOLD);
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		GuiDraw.text(g, font, bold, 0, 0, color, false);
		g.pose().popMatrix();
	}

	static int headingWidth(Font font, String text, float scale) {
		return Math.round(font.width(Component.literal(text).withStyle(ChatFormatting.BOLD)) * scale);
	}

	// ------------------------------------------------------------------ Preisschild

	static final int TAG_H = 12;
	private static final int TAG_POINT = 5;

	static int tagWidth(Font font, String text) {
		return font.width(text) + TAG_POINT + 10;
	}

	/** Preisschild mit Spitze links und Loch; Text in Pfeffer auf Quittungspapier. */
	static void tag(GuiGraphics g, Font font, int x, int y, String text, int fill, int edge, int textColor) {
		int w = tagWidth(font, text);
		shadow(g, x + TAG_POINT, y, w - TAG_POINT, TAG_H, 1);
		// Spitze: pro Zeile weiter links, je näher an der Mitte
		for (int j = 0; j < TAG_H; j++) {
			int inset = Math.round(Math.abs(j - (TAG_H - 1) / 2f));
			int left = x + inset;
			g.fill(left, y + j, x + TAG_POINT + 1, y + j + 1, fill);
			g.fill(left, y + j, left + 1, y + j + 1, edge);
		}
		g.fill(x + TAG_POINT + 1, y, x + w - 1, y + TAG_H, fill);
		g.fill(x + TAG_POINT, y, x + w - 1, y + 1, edge);
		g.fill(x + TAG_POINT, y + TAG_H - 1, x + w - 1, y + TAG_H, edge);
		g.fill(x + w - 1, y + 1, x + w, y + TAG_H - 1, edge);
		g.fill(x + w - 2, y, x + w - 1, y + 1, edge);
		g.fill(x + w - 2, y + TAG_H - 1, x + w - 1, y + TAG_H, edge);
		// Loch
		g.fill(x + TAG_POINT - 1, y + 5, x + TAG_POINT + 1, y + 7, edge);
		GuiDraw.text(g, font, text, x + TAG_POINT + 5, y + 2, textColor, false);
	}
}
