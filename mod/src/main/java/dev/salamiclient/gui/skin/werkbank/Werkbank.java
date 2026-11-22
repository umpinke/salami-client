package dev.salamiclient.gui.skin.werkbank;

import dev.salamiclient.render.GuiDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Farben, Sprites und Zeichenhelfer der Pixel-Werkbank: Minecrafts eigene Inventar-Oberfläche.
 *
 * <p>Wo es passt, zeichnet die Werkbank Minecrafts GUI-Sprites ({@code widget/button*}, Reiter des
 * Kreativinventars, Slot, Rollbalken, Textfeld, Tooltip) – so folgt sie jedem Ressourcenpaket. Flächen
 * ohne passendes Sprite (Inventar-Panel in beliebiger Größe, große Slots) entstehen aus den Farben
 * des Vanilla-Inventars mit derselben Kante wie das Original.
 */
final class Werkbank {
	// Inventar (container/*.png)
	static final int PANEL = 0xFFC6C6C6;
	static final int WHITE = 0xFFFFFFFF;
	static final int SHADOW = 0xFF555555;
	static final int BLACK = 0xFF000000;
	static final int SLOT = 0xFF8B8B8B;
	static final int SLOT_DARK = 0xFF373737;
	/** Titel der Container („Inventar“, „Truhe“). */
	static final int TITLE = 0xFF404040;
	static final int DIM = 0xFF5A5A5A;
	/** Ausgewählte Zeile/Karte: eingedrückt. */
	static final int PRESSED = 0xFFB4B4B4;
	// Stein (widget/button)
	static final int STONE = 0xFF6F6F6F;
	static final int STONE_LIGHT = 0xFFAAAAAA;
	static final int STONE_DARK = 0xFF565656;
	/** Text auf Knöpfen (wie Vanilla) und deaktiviert. */
	static final int BUTTON_TEXT = 0xFFFFFFFF;
	static final int BUTTON_TEXT_OFF = 0xFFE0E0E0;
	static final int BUTTON_TEXT_DISABLED = 0xFFA0A0A0;
	// Akzente (Chat-Farben von Minecraft, abgedunkelt für hellen Grund)
	static final int GREEN = 0xFF2C7A1C;
	static final int RED = 0xFFAA0000;
	/** Grün eingeschalteter Slots in der Hotbar. */
	static final int SLOT_ON = 0xFF5D9A3C;
	/** Färbung des Stein-Knopfs: grün (an, Hauptaktion) bzw. rot (Löschen). */
	static final int TINT_GREEN = 0xFF86F070;
	static final int TINT_RED = 0xFFFF8C80;
	// Eingabefeld (widget/text_field)
	static final int FIELD_TEXT = 0xFFE0E0E0;
	static final int FIELD_HINT = 0xFF808080;
	static final int YELLOW = 0xFFFFFF55;

	static final Identifier BUTTON = vanilla("widget/button");
	static final Identifier BUTTON_HIGHLIGHTED = vanilla("widget/button_highlighted");
	static final Identifier BUTTON_DISABLED = vanilla("widget/button_disabled");
	static final Identifier TEXT_FIELD = vanilla("widget/text_field");
	static final Identifier TEXT_FIELD_HIGHLIGHTED = vanilla("widget/text_field_highlighted");
	static final Identifier SLIDER = vanilla("widget/slider");
	static final Identifier SLIDER_HIGHLIGHTED = vanilla("widget/slider_highlighted");
	static final Identifier SLIDER_HANDLE = vanilla("widget/slider_handle");
	static final Identifier SLIDER_HANDLE_HIGHLIGHTED = vanilla("widget/slider_handle_highlighted");
	static final Identifier CHECKBOX = vanilla("widget/checkbox");
	static final Identifier CHECKBOX_HIGHLIGHTED = vanilla("widget/checkbox_highlighted");
	static final Identifier CHECKBOX_SELECTED = vanilla("widget/checkbox_selected");
	static final Identifier CHECKBOX_SELECTED_HIGHLIGHTED = vanilla("widget/checkbox_selected_highlighted");
	static final Identifier SLOT_SPRITE = vanilla("container/slot");
	static final Identifier SLOT_HIGHLIGHT_BACK = vanilla("container/slot_highlight_back");
	static final Identifier SLOT_HIGHLIGHT_FRONT = vanilla("container/slot_highlight_front");
	static final Identifier SCROLLER = vanilla("container/creative_inventory/scroller");
	static final Identifier SCROLLER_DISABLED = vanilla("container/creative_inventory/scroller_disabled");
	static final Identifier TOOLTIP_BACKGROUND = vanilla("tooltip/background");
	static final Identifier TOOLTIP_FRAME = vanilla("tooltip/frame");

	/** Reiter des Kreativinventars: 26 × 32, ragen 4 Pixel ins Panel. */
	static final int TAB_W = 26;
	static final int TAB_H = 32;
	/** Abstand zweier Reiter (wie im Kreativinventar). */
	static final int TAB_STEP = 27;
	/** So weit ragt ein Reiter über die Oberkante des Panels. */
	static final int TAB_RISE = 28;

	private Werkbank() {}

	private static Identifier vanilla(String path) {
		return Identifier.withDefaultNamespace(path);
	}

	static void sprite(GuiGraphics g, Identifier id, int x, int y, int w, int h) {
		if (w > 0 && h > 0) {
			g.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, w, h);
		}
	}

	static void sprite(GuiGraphics g, Identifier id, int x, int y, int w, int h, int color) {
		if (w > 0 && h > 0) {
			g.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, w, h, color);
		}
	}

	/** Reiter oben am Panel; {@code position} 1 = bündig links, 7 = bündig rechts, sonst 2. */
	static Identifier tabSprite(boolean selected, int position) {
		return vanilla("container/creative_inventory/tab_top_" + (selected ? "selected_" : "unselected_") + position);
	}

	// ------------------------------------------------------------------ Flächen

	/**
	 * Inventar-Panel wie {@code container/inventory.png}: schwarzer Umriss mit abgerundeten Ecken, 2 Pixel
	 * Licht oben/links, 2 Pixel Schatten unten/rechts, Grau #C6C6C6.
	 */
	static void panel(GuiGraphics g, int x, int y, int w, int h) {
		if (w < 8 || h < 8) {
			raised(g, x, y, w, h, PANEL);
			return;
		}
		int r = x + w;
		int b = y + h;
		g.fill(x + 3, y + 3, r - 3, b - 3, PANEL);
		// Umriss
		g.fill(x + 2, y, r - 3, y + 1, BLACK);
		g.fill(x + 1, y + 1, x + 2, y + 2, BLACK);
		g.fill(r - 3, y + 1, r - 2, y + 2, BLACK);
		g.fill(x, y + 2, x + 1, b - 3, BLACK);
		g.fill(r - 2, y + 2, r - 1, y + 3, BLACK);
		g.fill(r - 1, y + 3, r, b - 2, BLACK);
		g.fill(x + 1, b - 3, x + 2, b - 2, BLACK);
		g.fill(x + 2, b - 2, x + 3, b - 1, BLACK);
		g.fill(r - 2, b - 2, r - 1, b - 1, BLACK);
		g.fill(x + 3, b - 1, r - 2, b, BLACK);
		// Licht
		g.fill(x + 2, y + 1, r - 3, y + 3, WHITE);
		g.fill(x + 1, y + 2, x + 3, b - 3, WHITE);
		g.fill(x + 3, y + 3, x + 4, y + 4, WHITE);
		// Schatten
		g.fill(r - 3, y + 3, r - 1, b - 3, SHADOW);
		g.fill(r - 4, b - 4, r - 3, b - 3, SHADOW);
		g.fill(x + 3, b - 3, r - 1, b - 2, SHADOW);
		g.fill(x + 3, b - 2, r - 2, b - 1, SHADOW);
		// Übergänge an den Ecken
		g.fill(r - 3, y + 2, r - 2, y + 3, PANEL);
		g.fill(x + 2, b - 3, x + 3, b - 2, PANEL);
	}

	/** Erhabene Fläche (Licht oben/links, Schatten unten/rechts, 1 Pixel), Ecken frei wie beim Rollbalken. */
	static void raised(GuiGraphics g, int x, int y, int w, int h, int fill) {
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
		g.fill(x, y, x + w - 1, y + 1, WHITE);
		g.fill(x, y + 1, x + 1, y + h - 1, WHITE);
		g.fill(x + 1, y + h - 1, x + w, y + h, SHADOW);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, SHADOW);
	}

	/** Vertiefte Fläche wie ein Slot (Schatten oben/links, Licht unten/rechts, 1 Pixel). */
	static void inset(GuiGraphics g, int x, int y, int w, int h, int fill) {
		g.fill(x, y, x + w, y + h, fill);
		g.fill(x, y, x + w - 1, y + 1, SLOT_DARK);
		g.fill(x, y + 1, x + 1, y + h - 1, SLOT_DARK);
		g.fill(x + 1, y + h - 1, x + w, y + h, WHITE);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, WHITE);
	}

	/** Rille im Panel (wie um Ofen-Anzeigen): Schatten oben/links, Licht unten/rechts, Grau innen. */
	static void groove(GuiGraphics g, int x, int y, int w, int h) {
		g.fill(x, y, x + w - 1, y + 1, SHADOW);
		g.fill(x, y + 1, x + 1, y + h - 1, SHADOW);
		g.fill(x + 1, y + h - 1, x + w, y + h, WHITE);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, WHITE);
	}

	/** Slot; bei 18 × 18 Minecrafts eigenes Sprite, sonst gleich gezeichnet. */
	static void slot(GuiGraphics g, int x, int y, int size) {
		if (size == 18) {
			sprite(g, SLOT_SPRITE, x, y, 18, 18);
		} else {
			inset(g, x, y, size, size, SLOT);
		}
	}

	/** Slot in einer Farbe (Hotbar: grün = an). */
	static void slot(GuiGraphics g, int x, int y, int size, int fill) {
		inset(g, x, y, size, size, fill);
	}

	/** Weißer Schimmer eines Slots unter der Maus, hinter dem Item (wie im Inventar). */
	static void slotHoverBack(GuiGraphics g, int x, int y, int size) {
		sprite(g, SLOT_HIGHLIGHT_BACK, x - 3, y - 3, size + 6, size + 6);
	}

	/** … und vor dem Item. */
	static void slotHoverFront(GuiGraphics g, int x, int y, int size) {
		sprite(g, SLOT_HIGHLIGHT_FRONT, x - 3, y - 3, size + 6, size + 6);
	}

	/**
	 * Stein-Knopf ({@code widget/button}), eingefärbt mit {@code tint} (weiß = Vanilla). Unter der Maus mit
	 * weißem Rand, deaktiviert dunkel.
	 */
	static void stone(GuiGraphics g, int x, int y, int w, int h, int tint, boolean hover, boolean disabled) {
		Identifier id = disabled ? BUTTON_DISABLED : hover ? BUTTON_HIGHLIGHTED : BUTTON;
		sprite(g, id, x, y, w, h, disabled ? 0xFFFFFFFF : tint);
	}

	/** Steinfläche ohne Knopf-Charakter (Kopf eines Panels): wie {@code widget/button}, beliebig groß. */
	static void stoneSurface(GuiGraphics g, int x, int y, int w, int h) {
		if (h <= 24) {
			sprite(g, BUTTON, x, y, w, h);
			return;
		}
		g.fill(x, y, x + w, y + h, BLACK);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, STONE);
		g.fill(x + 1, y + 1, x + w - 2, y + 2, STONE_LIGHT);
		g.fill(x + 1, y + 2, x + 2, y + h - 3, STONE_LIGHT);
		g.fill(x + w - 2, y + 2, x + w - 1, y + h - 3, STONE_DARK);
		g.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, STONE_DARK);
	}

	/**
	 * Minecrafts Tooltip-Kasten (lila Rand auf dunklem Grund, {@code tooltip/background} + {@code tooltip/frame}),
	 * sichtbar genau im Rechteck (x, y, w, h).
	 */
	static void tooltipBox(GuiGraphics g, int x, int y, int w, int h) {
		sprite(g, TOOLTIP_BACKGROUND, x - 8, y - 8, w + 16, h + 16);
		sprite(g, TOOLTIP_FRAME, x - 8, y - 8, w + 16, h + 16);
	}

	/** Schatten unter einem Panel, das frei über der Welt liegt. */
	static void dropShadow(GuiGraphics g, int x, int y, int w, int h) {
		g.fill(x + 2, y + h, x + w, y + h + 2, 0x50000000);
		g.fill(x + w, y + 2, x + w + 2, y + h, 0x50000000);
	}

	// ------------------------------------------------------------------ Schrift

	static void text(GuiGraphics g, Font font, String text, int x, int y, int color, boolean shadow) {
		GuiDraw.text(g, font, text, x, y, color, shadow);
	}

	/** Zahl rechts unten im Slot wie die Stapelgröße eines Items (weiß mit Schatten). */
	static void stackCount(GuiGraphics g, Font font, String text, int itemX, int itemY) {
		g.pose().pushMatrix();
		GuiDraw.text(g, font, text, itemX + 19 - 2 - font.width(text), itemY + 6 + 3, WHITE, true);
		g.pose().popMatrix();
	}
}
