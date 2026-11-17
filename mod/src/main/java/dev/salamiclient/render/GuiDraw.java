package dev.salamiclient.render;

import dev.salamiclient.SalamiClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * GUI-Zeichenaufrufe, deren Namen sich zwischen Minecraft-Versionen unterscheiden
 * (ab 26.1 z. B. {@code text} statt {@code drawString}). Der übrige Code ruft nur diese Klasse
 * auf, damit die Versionsweichen (Stonecutter-Kommentare) an einer Stelle bleiben.
 */
public final class GuiDraw {
	private GuiDraw() {}

	public static void text(GuiGraphics g, Font font, String text, int x, int y, int color) {
		//? if >=26.1 {
		/*g.text(font, text, x, y, color);
		*///?} else
		g.drawString(font, text, x, y, color);
	}

	public static void text(GuiGraphics g, Font font, String text, int x, int y, int color, boolean shadow) {
		//? if >=26.1 {
		/*g.text(font, text, x, y, color, shadow);
		*///?} else
		g.drawString(font, text, x, y, color, shadow);
	}

	public static void text(GuiGraphics g, Font font, Component text, int x, int y, int color) {
		//? if >=26.1 {
		/*g.text(font, text, x, y, color);
		*///?} else
		g.drawString(font, text, x, y, color);
	}

	public static void text(GuiGraphics g, Font font, Component text, int x, int y, int color, boolean shadow) {
		//? if >=26.1 {
		/*g.text(font, text, x, y, color, shadow);
		*///?} else
		g.drawString(font, text, x, y, color, shadow);
	}

	public static void text(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color) {
		//? if >=26.1 {
		/*g.text(font, text, x, y, color);
		*///?} else
		g.drawString(font, text, x, y, color);
	}

	public static void text(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
		//? if >=26.1 {
		/*g.text(font, text, x, y, color, shadow);
		*///?} else
		g.drawString(font, text, x, y, color, shadow);
	}

	public static void centeredText(GuiGraphics g, Font font, String text, int x, int y, int color) {
		//? if >=26.1 {
		/*g.centeredText(font, text, x, y, color);
		*///?} else
		g.drawCenteredString(font, text, x, y, color);
	}

	public static void centeredText(GuiGraphics g, Font font, Component text, int x, int y, int color) {
		//? if >=26.1 {
		/*g.centeredText(font, text, x, y, color);
		*///?} else
		g.drawCenteredString(font, text, x, y, color);
	}

	/** Rahmen (1 Pixel) um ein Rechteck. */
	public static void outline(GuiGraphics g, int x, int y, int width, int height, int color) {
		//? if >=26.1 {
		/*g.outline(x, y, width, height, color);
		*///?} else if >=1.21.11 {
		g.renderOutline(x, y, width, height, color);
		//?} else
		/*g.submitOutline(x, y, width, height, color);*/
	}

	/** Größter Eckenradius, für den es Sprites gibt ({@code textures/gui/sprites/hud/round_N}). */
	public static final int MAX_RADIUS = 6;
	private static final Identifier[] ROUND = new Identifier[MAX_RADIUS + 1];
	private static final Identifier[] RING = new Identifier[MAX_RADIUS + 1];

	static {
		for (int r = 0; r <= MAX_RADIUS; r++) {
			if (r > 0) {
				ROUND[r] = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "hud/round_" + r);
			}
			RING[r] = Identifier.fromNamespaceAndPath(SalamiClient.MOD_ID, "hud/ring_" + r);
		}
	}

	/** Radius, der in die Fläche passt (halbe kürzere Seite, höchstens {@link #MAX_RADIUS}). */
	private static int fitRadius(int radius, int width, int height) {
		return Math.max(0, Math.min(Math.min(radius, MAX_RADIUS), Math.min(width, height) / 2));
	}

	/**
	 * Fläche mit glatt abgerundeten Ecken (kantengeglättetes Nine-Slice-Sprite, eingefärbt mit
	 * {@code color} samt Deckkraft). Radius 0 ist ein normales Rechteck.
	 */
	public static void rounded(GuiGraphics g, int x, int y, int width, int height, int radius, int color) {
		if (width <= 0 || height <= 0 || (color >>> 24) == 0) {
			return;
		}
		int r = fitRadius(radius, width, height);
		if (r == 0) {
			g.fill(x, y, x + width, y + height, color);
		} else {
			g.blitSprite(RenderPipelines.GUI_TEXTURED, ROUND[r], x, y, width, height, color);
		}
	}

	/** Feiner Rand (halber GUI-Pixel) um eine Fläche mit abgerundeten Ecken – passt zu {@link #rounded}. */
	public static void roundedOutline(GuiGraphics g, int x, int y, int width, int height, int radius, int color) {
		if (width <= 1 || height <= 1 || (color >>> 24) == 0) {
			return;
		}
		g.blitSprite(RenderPipelines.GUI_TEXTURED, RING[fitRadius(radius, width, height)], x, y, width, height, color);
	}

	/** Rechteck mit weich abgerundeten Ecken (Radius 3) – für Knöpfe, Karten und Fenster der Menüs. */
	public static void roundRect(GuiGraphics g, int x, int y, int width, int height, int color) {
		rounded(g, x, y, width, height, 3, color);
	}

	/** Farbe mit anderer Deckkraft (0..1 der bisherigen). */
	public static int fade(int argb, float factor) {
		int alpha = Math.round((argb >>> 24) * Math.max(0F, Math.min(1F, factor)));
		return (argb & 0x00FFFFFF) | (alpha << 24);
	}

	/** Gut lesbare Textfarbe (dunkel oder hell) auf einer deckenden Fläche. */
	public static int textOn(int argb) {
		int r = (argb >> 16) & 0xFF;
		int gr = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		return (r * 299 + gr * 587 + b * 114) / 1000 > 150 ? 0xFF1B1415 : 0xFFFFFFFF;
	}

	public static void item(GuiGraphics g, ItemStack stack, int x, int y) {
		//? if >=26.1 {
		/*g.item(stack, x, y);
		*///?} else
		g.renderItem(stack, x, y);
	}

	public static void itemDecorations(GuiGraphics g, Font font, ItemStack stack, int x, int y) {
		//? if >=26.1 {
		/*g.itemDecorations(font, stack, x, y);
		*///?} else
		g.renderItemDecorations(font, stack, x, y);
	}
}
