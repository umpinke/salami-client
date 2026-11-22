package dev.salamiclient.gui.skin.werkbank;

import dev.salamiclient.gui.skin.Icons;
import dev.salamiclient.gui.skin.MenuView;
import dev.salamiclient.gui.skin.ModsView;
import dev.salamiclient.gui.skin.Skin;
import dev.salamiclient.gui.skin.TextFit;
import dev.salamiclient.render.GuiDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import static dev.salamiclient.gui.skin.werkbank.Werkbank.*;

/**
 * Pixel-Werkbank: die Salami-Menüs als Minecraft-Inventar, als hätte Mojang sie gebaut. Hellgraue
 * Inventar-Panels mit Vanilla-Kante, eingelassene Slots, dunkelgraue Containertitel, Stein-Knöpfe,
 * Reiter des Kreativinventars, der lila Item-Tooltip und echte Item-Symbole.
 *
 * <p>Flächen: WINDOW und POPUP = Inventar-Panel, SIDE = Steinfläche (Kopf der Panels), CARD = Rille im
 * Panel, INPUT = schwarzes Textfeld. Schalter = Steinschieber auf einer Slot-Schiene (an: grün).
 */
public final class WerkbankSkin implements Skin {
	private static final Ink PANEL_INK = new Ink(TITLE, DIM, GREEN, WHITE, SLOT, RED);
	private static final Ink STONE_INK = new Ink(WHITE, 0xFFD0D0D0, YELLOW, TITLE, 0xFF9A9A9A, 0xFFFF6B6B);
	private static final Ink INPUT_INK = new Ink(FIELD_TEXT, FIELD_HINT, YELLOW, BLACK, 0xFFA0A0A0, 0xFFFF5555);

	private static final int TOGGLE_W = 20;
	private static final int TOGGLE_H = 11;
	private static final int TAG_H = 14;

	private final WerkbankMenuView menuView = new WerkbankMenuView(this);
	private final WerkbankModsView modsView = new WerkbankModsView(this);

	public WerkbankSkin() {}

	@Override
	public String id() {
		return "werkbank";
	}

	// ------------------------------------------------------------------ Flächen

	@Override
	public void backdrop(GuiGraphics g, int width, int height) {
		// wie Minecrafts Inventar über der Welt, unten etwas dichter (Hotbar, Chat)
		g.fillGradient(0, 0, width, height, 0xB0101010, 0xE0101010);
	}

	@Override
	public int backdropTop() {
		return 0;
	}

	@Override
	public void surface(GuiGraphics g, Surface kind, int x, int y, int width, int height) {
		switch (kind) {
			case WINDOW -> panel(g, x, y, width, height);
			case POPUP -> {
				dropShadow(g, x, y, width, height);
				panel(g, x, y, width, height);
			}
			case SIDE -> stoneSurface(g, x, y, width, height);
			case CARD -> groove(g, x, y, width, height);
			case INPUT -> sprite(g, TEXT_FIELD, x, y, width, height);
		}
	}

	@Override
	public Ink ink(Surface kind) {
		return switch (kind) {
			case SIDE -> STONE_INK;
			case INPUT -> INPUT_INK;
			default -> PANEL_INK;
		};
	}

	// ------------------------------------------------------------------ Bedienelemente

	@Override
	public void button(GuiGraphics g, Font font, int x, int y, int width, int height, String label, ButtonKind kind, UiState state) {
		iconButton(g, x, y, width, height, kind, state);
		if (label == null || label.isEmpty()) {
			return;
		}
		int textY = y + (height - 7) / 2;
		boolean shadow = kind != ButtonKind.GHOST && state != UiState.DISABLED;
		TextFit.drawCentered(g, font, label, x + width / 2, textY, width - 6, buttonInk(kind, state), shadow);
	}

	@Override
	public void iconButton(GuiGraphics g, int x, int y, int size, ButtonKind kind, UiState state) {
		iconButton(g, x, y, size, size, kind, state);
	}

	private static void iconButton(GuiGraphics g, int x, int y, int w, int h, ButtonKind kind, UiState state) {
		boolean hover = state == UiState.HOVER || state == UiState.PRESSED;
		boolean disabled = state == UiState.DISABLED;
		switch (kind) {
			case PRIMARY -> stone(g, x, y, w, h, TINT_GREEN, hover, disabled);
			case SECONDARY -> stone(g, x, y, w, h, 0xFFFFFFFF, hover, disabled);
			case DANGER -> stone(g, x, y, w, h, TINT_RED, hover, disabled);
			case GHOST -> {
				// flach auf dem Panel; unter der Maus der weiße Schimmer eines Slots
				if (hover && !disabled) {
					g.fill(x, y, x + w, y + h, 0x60FFFFFF);
				}
			}
		}
	}

	@Override
	public int buttonInk(ButtonKind kind, UiState state) {
		if (state == UiState.DISABLED) {
			return kind == ButtonKind.GHOST ? 0xFF8B8B8B : BUTTON_TEXT_DISABLED;
		}
		return kind == ButtonKind.GHOST ? TITLE : BUTTON_TEXT;
	}

	@Override
	public int[] toggleSize() {
		return new int[] {TOGGLE_W, TOGGLE_H};
	}

	/** Schieber: Slot-Schiene (an: grün) mit einem Stein-Knopf links (aus) bzw. rechts (an). */
	@Override
	public void toggle(GuiGraphics g, int x, int y, boolean on, UiState state) {
		boolean hover = state == UiState.HOVER || state == UiState.PRESSED;
		boolean disabled = state == UiState.DISABLED;
		inset(g, x, y + 1, TOGGLE_W, TOGGLE_H - 2, disabled ? 0xFF9A9A9A : on ? SLOT_ON : SLOT);
		int knobW = 10;
		stone(g, on ? x + TOGGLE_W - knobW : x, y, knobW, TOGGLE_H, 0xFFFFFFFF, hover, disabled);
	}

	@Override
	public void checkbox(GuiGraphics g, int x, int y, int size, boolean checked, UiState state) {
		boolean hover = state == UiState.HOVER || state == UiState.PRESSED;
		if (size >= 17) {
			sprite(g, checked ? (hover ? CHECKBOX_SELECTED_HIGHLIGHTED : CHECKBOX_SELECTED) : (hover ? CHECKBOX_HIGHLIGHTED : CHECKBOX), x, y, size, size);
		} else {
			// wie widget/checkbox, nur klein: schwarzer Rand (weiß unter der Maus), dunkles Feld, weißer Haken
			g.fill(x, y, x + size, y + size, hover ? WHITE : BLACK);
			g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF2B2B2B);
			if (checked) {
				if (size >= 9) {
					Icons.check(g, x + (size - 7) / 2, y + (size - 5) / 2, WHITE);
				} else {
					g.fill(x + 2, y + 2, x + size - 2, y + size - 2, WHITE);
				}
			}
		}
		if (state == UiState.DISABLED) {
			g.fill(x, y, x + size, y + size, 0x80C6C6C6);
		}
	}

	@Override
	public void tab(GuiGraphics g, Font font, int x, int y, int width, int height, String label, String count, boolean active,
		boolean vertical, UiState state) {
		boolean hover = state == UiState.HOVER;
		int textY = y + (height - 7) / 2;
		if (vertical) {
			// Liste auf Stein: gewählt eingedrückt hell wie ein Slot, sonst Text auf Stein
			if (active) {
				inset(g, x, y, width, height, PANEL);
			} else if (hover) {
				g.fill(x + 1, y, x + width - 1, y + height, 0x30FFFFFF);
			}
			int countW = count == null ? 0 : font.width(count) + 4;
			int color = active ? TITLE : hover ? YELLOW : WHITE;
			if (count != null) {
				text(g, font, count, x + width - 4 - font.width(count), textY, active ? DIM : 0xFFD0D0D0, !active);
			}
			TextFit.draw(g, font, label, x + 5, textY, width - 9 - countW, color, TextFit.MIN_SCALE, !active);
			return;
		}
		// Reiter wie im Kreativinventar: gewählt in Panelgrau, die anderen dunkler
		int fill = active ? PANEL : hover ? 0xFFA0A0A0 : SLOT;
		int top = active ? y : y + 1;
		g.fill(x + 1, top, x + width - 1, top + 1, BLACK);
		g.fill(x, top + 1, x + 1, y + height, BLACK);
		g.fill(x + width - 1, top + 1, x + width, y + height, BLACK);
		g.fill(x + 1, top + 1, x + width - 1, y + height, fill);
		g.fill(x + 1, top + 1, x + width - 2, top + 2, WHITE);
		g.fill(x + 1, top + 2, x + 2, y + height, WHITE);
		g.fill(x + width - 2, top + 2, x + width - 1, y + height, SHADOW);
		if (!active) {
			g.fill(x, y + height - 1, x + width, y + height, BLACK);
		}
		String text = count == null ? label : label + " " + count;
		int color = active ? TITLE : WHITE;
		TextFit.drawCentered(g, font, text, x + width / 2, active ? textY : textY + 1, width - 6, color, !active);
	}

	@Override
	public void row(GuiGraphics g, int x, int y, int width, int height, boolean selected, UiState state) {
		boolean hover = state == UiState.HOVER || state == UiState.PRESSED;
		if (height > 24) {
			// Karte: erhabene Kachel; angelegt/gewählt eingedrückt
			if (selected) {
				inset(g, x, y, width, height, PRESSED);
			} else {
				raised(g, x, y, width, height, PANEL);
			}
			if (hover) {
				g.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x40FFFFFF);
			}
			if (state == UiState.DISABLED) {
				g.fill(x, y, x + width, y + height, 0x80C6C6C6);
			}
			return;
		}
		// Zeile: gewählt eingedrückt, unter der Maus der weiße Schimmer eines Slots
		if (selected) {
			inset(g, x, y, width, height, PRESSED);
		}
		if (hover) {
			g.fill(x + (selected ? 1 : 0), y + (selected ? 1 : 0), x + width - (selected ? 1 : 0), y + height - (selected ? 1 : 0),
				0x60FFFFFF);
		}
	}

	@Override
	public void input(GuiGraphics g, int x, int y, int width, int height, boolean focused) {
		sprite(g, focused ? TEXT_FIELD_HIGHLIGHTED : TEXT_FIELD, x, y, width, height);
	}

	@Override
	public void slider(GuiGraphics g, int x, int y, int width, int height, float value, UiState state) {
		float v = Math.max(0, Math.min(1, value));
		boolean hover = state == UiState.HOVER || state == UiState.PRESSED;
		int knobW = height >= 12 ? 8 : Math.max(4, Math.min(6, height));
		// schmale Regler: dünne Schiene, Griff über die ganze Höhe
		int trackH = height >= 12 ? height : Math.max(3, Math.min(4, height - 2));
		int trackY = y + (height - trackH) / 2;
		sprite(g, hover ? SLIDER_HIGHLIGHTED : SLIDER, x, trackY, width, trackH);
		int kx = x + Math.round((width - knobW) * v);
		sprite(g, hover ? SLIDER_HANDLE_HIGHLIGHTED : SLIDER_HANDLE, kx, y, knobW, height,
			state == UiState.DISABLED ? 0xFF808080 : 0xFFFFFFFF);
	}

	@Override
	public void scrollbar(GuiGraphics g, int x, int y, int width, int height, int thumbY, int thumbHeight, UiState state) {
		boolean hover = state == UiState.HOVER || state == UiState.PRESSED;
		// Schiene vertieft wie im Kreativinventar, Griff erhaben
		inset(g, x, y, width, height, SLOT);
		int ty = y + thumbY;
		if (width >= 12) {
			sprite(g, SCROLLER, x + (width - 12) / 2, ty, 12, Math.min(15, thumbHeight));
			return;
		}
		int fill = hover ? 0xFFDADADA : PANEL;
		if (width <= 3) {
			g.fill(x, ty, x + width, ty + thumbHeight, fill);
			g.fill(x, ty, x + 1, ty + thumbHeight, WHITE);
			g.fill(x + width - 1, ty, x + width, ty + thumbHeight, SHADOW);
			g.fill(x, ty + thumbHeight - 1, x + width, ty + thumbHeight, SHADOW);
		} else {
			raised(g, x, ty, width, thumbHeight, fill);
		}
	}

	@Override
	public void badge(GuiGraphics g, Font font, int x, int y, String text) {
		int w = Math.max(10, font.width(text) + 5);
		g.fill(x - w, y, x, y + 11, BLACK);
		g.fill(x - w + 1, y + 1, x - 1, y + 10, 0xFFC02020);
		g.fill(x - w + 1, y + 1, x - 1, y + 2, 0xFFE05050);
		text(g, font, text, x - w + (w - font.width(text) + 1) / 2, y + 2, WHITE, true);
	}

	/** Schild = Minecrafts Tooltip-Kasten mit weißer Schrift – lesbar auf jeder Fläche und über der Welt. */
	@Override
	public void tag(GuiGraphics g, Font font, int x, int y, String text) {
		int w = tagWidth(font, text);
		tooltipBox(g, x, y, w, TAG_H);
		text(g, font, text, x + 4, y + 3, WHITE, true);
	}

	@Override
	public int tagWidth(Font font, String text) {
		return font.width(text) + 8;
	}

	@Override
	public int tagHeight() {
		return TAG_H;
	}

	/** Rille wie zwischen den Bereichen eines Inventars: dunkle Linie mit Licht darunter. */
	@Override
	public void divider(GuiGraphics g, int x, int y, int width, Surface on) {
		if (on == Surface.SIDE) {
			g.fill(x, y, x + width, y + 1, STONE_DARK);
			g.fill(x, y + 1, x + width, y + 2, STONE_LIGHT);
			return;
		}
		g.fill(x, y, x + width, y + 1, SLOT);
		g.fill(x, y + 1, x + width, y + 2, WHITE);
	}

	// ------------------------------------------------------------------ Schrift

	/**
	 * Überschrift in Minecrafts Schrift wie ein Containertitel – ohne Fettdruck und nur in ganzen Größen
	 * (1 oder 2), damit die Pixel scharf bleiben.
	 */
	@Override
	public void heading(GuiGraphics g, Font font, String text, int x, int y, int color, float scale) {
		int s = headingScale(scale);
		if (s == 1) {
			GuiDraw.text(g, font, text, x, y, color, false);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(s, s);
		GuiDraw.text(g, font, text, 0, 0, color, false);
		g.pose().popMatrix();
	}

	@Override
	public int headingWidth(Font font, String text, float scale) {
		return font.width(text) * headingScale(scale);
	}

	private static int headingScale(float scale) {
		return scale >= 2f ? 2 : 1;
	}

	// ------------------------------------------------------------------ Bildschirme mit eigenem Aufbau

	@Override
	public MenuView menuView() {
		return menuView;
	}

	@Override
	public ModsView modsView() {
		return modsView;
	}
}
