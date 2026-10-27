package dev.salamiclient.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
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
