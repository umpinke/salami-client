package dev.salamiclient.gui.skin.theke;

import dev.salamiclient.gui.skin.Icons;
import dev.salamiclient.gui.skin.MenuView;
import dev.salamiclient.gui.skin.ModsView;
import dev.salamiclient.gui.skin.Skin;
import dev.salamiclient.gui.skin.TextFit;
import dev.salamiclient.render.GuiDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import static dev.salamiclient.gui.skin.theke.Theke.*;

/**
 * Feinkost-Theke: rot-weiße Markise oben, Navigation auf Kreidetafeln im Holzrahmen, Inhalt auf
 * Metzgerpapier mit Pfeffer-Schrift, runde rote Stempel als Schalter, Preisschilder für Hinweise.
 * Vorbild für die anderen Looks (siehe {@code gui/skin/SKINS.md}).
 */
public final class ThekeSkin implements Skin {
	private static final Ink PAPER_INK = new Ink(PEPPER, INK, RED, CREAM, PAPER_EDGE, WARNING);
	private static final Ink CHALK_INK = new Ink(CHALK_TEXT, CHALK_DIM, CHALK_YELLOW, CHALK, CHALK_LINE, 0xFFF0A070);
	private static final Ink INPUT_INK = new Ink(PEPPER, INK_LIGHT, RED, CREAM, PAPER_EDGE, WARNING);

	private final ThekeMenuView menuView = new ThekeMenuView(this);
	private final ThekeModsView modsView = new ThekeModsView(this);

	public ThekeSkin() {}

	@Override
	public String id() {
		return "theke";
	}

	// ------------------------------------------------------------------ Flächen

	@Override
	public void backdrop(GuiGraphics g, int width, int height) {
		// warmes Dunkel, unten dichter (dort liegen Hotbar und Chat)
		g.fillGradient(0, 0, width, height, 0xB8221813, 0xE8150E0B);
		awning(g, width);
	}

	@Override
	public int backdropTop() {
		return AWNING_H;
	}

	@Override
	public void surface(GuiGraphics g, Surface kind, int x, int y, int width, int height) {
		switch (kind) {
			case WINDOW -> paper(g, x, y, width, height, SHEET, true);
			case SIDE -> board(g, x, y, width, height);
			case CARD -> paper(g, x, y, width, height, PAPER, false);
			case POPUP -> {
				box(g, x + 3, y + 4, width, height, 0x55000000);
				sprite(g, PAPER_TEX, x + 1, y + 1, width - 2, height - 2, RECEIPT);
				frame(g, x, y, width, height, PEPPER);
			}
			case INPUT -> {
				g.fill(x, y, x + width, y + height, RECEIPT);
				frame(g, x, y, width, height, PAPER_EDGE);
			}
		}
	}

	@Override
	public Ink ink(Surface kind) {
		return switch (kind) {
			case SIDE -> CHALK_INK;
			case INPUT -> INPUT_INK;
			default -> PAPER_INK;
		};
	}

	// ------------------------------------------------------------------ Bedienelemente

	@Override
	public void button(GuiGraphics g, Font font, int x, int y, int width, int height, String label, ButtonKind kind, UiState state) {
		iconButton(g, x, y, width, height, kind, state);
		if (label == null || label.isEmpty()) {
			return;
		}
		int lip = lip(kind, state);
		int textY = y + (height - lip - 8) / 2 + (state == UiState.PRESSED ? 1 : 0);
		TextFit.drawCentered(g, font, label, x + width / 2, textY, width - 6, buttonInk(kind, state));
	}

	@Override
	public void iconButton(GuiGraphics g, int x, int y, int size, ButtonKind kind, UiState state) {
		iconButton(g, x, y, size, size, kind, state);
	}

	/** Unterkante („Lippe“) der vollen Knöpfe: 2 Pixel, gedrückt keine. */
	private static int lip(ButtonKind kind, UiState state) {
		return (kind == ButtonKind.PRIMARY || kind == ButtonKind.SECONDARY) && state != UiState.PRESSED && state != UiState.DISABLED ? 2 : 0;
	}

	private void iconButton(GuiGraphics g, int x, int y, int w, int h, ButtonKind kind, UiState state) {
		boolean hover = state == UiState.HOVER;
		if (state == UiState.DISABLED) {
			if (kind != ButtonKind.GHOST) {
				box(g, x, y, w, h, PAPER_2);
				frame(g, x, y, w, h, PAPER_EDGE);
			}
			return;
		}
		int top = state == UiState.PRESSED ? y + 1 : y;
		switch (kind) {
			case PRIMARY -> {
				box(g, x, y, w, h, RED_DARK);
				box(g, x, top, w, h - lip(kind, state) - (top - y), hover ? RED_HI : RED);
			}
			case SECONDARY -> {
				box(g, x, y, w, h, PAPER_DEEP);
				box(g, x, top, w, h - lip(kind, state) - (top - y), hover ? CREAM : FAT);
				frame(g, x, y, w, h, hover ? RED_HI : PAPER_DEEP);
			}
			case DANGER -> {
				if (hover || state == UiState.PRESSED) {
					box(g, x, y, w, h, RED_DARK);
				} else {
					frame(g, x, y, w, h, RED_DARK);
				}
			}
			case GHOST -> {
				if (hover || state == UiState.PRESSED) {
					box(g, x, y, w, h, 0x22B23A3A);
				}
			}
		}
	}

	@Override
	public int buttonInk(ButtonKind kind, UiState state) {
		if (state == UiState.DISABLED) {
			return INK_LIGHT;
		}
		boolean hover = state == UiState.HOVER || state == UiState.PRESSED;
		return switch (kind) {
			case PRIMARY -> CREAM;
			case SECONDARY -> hover ? RED : PEPPER;
			case DANGER -> hover ? CREAM : RED_DARK;
			case GHOST -> hover ? RED : INK;
		};
	}

	@Override
	public int[] toggleSize() {
		return new int[] {11, 11};
	}

	@Override
	public void toggle(GuiGraphics g, int x, int y, boolean on, UiState state) {
		boolean hover = state == UiState.HOVER || state == UiState.PRESSED;
		int color = state == UiState.DISABLED ? 0x80FFFFFF : 0xFFFFFFFF;
		sprite(g, on ? (hover ? STAMP_ON_HOVER : STAMP_ON) : (hover ? STAMP_OFF_HOVER : STAMP_OFF), x, y, 11, 11, color);
	}

	@Override
	public void checkbox(GuiGraphics g, int x, int y, int size, boolean checked, UiState state) {
		boolean hover = state == UiState.HOVER;
		if (checked) {
			box(g, x, y, size, size, hover ? RED_HI : RED);
			if (size >= 7) {
				Icons.check(g, x + (size - 7) / 2, y + (size - 5) / 2, CREAM);
			} else {
				g.fill(x + 2, y + 2, x + size - 2, y + size - 2, CREAM);
			}
		} else {
			box(g, x, y, size, size, RECEIPT);
			frame(g, x, y, size, size, hover ? RED : INK_LIGHT);
		}
		if (state == UiState.DISABLED) {
			box(g, x, y, size, size, 0x80FFFAF1);
		}
	}

	@Override
	public void tab(GuiGraphics g, Font font, int x, int y, int width, int height, String label, String count, boolean active,
		boolean vertical, UiState state) {
		boolean hover = state == UiState.HOVER;
		int textY = y + (height - 8) / 2;
		if (vertical) {
			// Kreidetafel: gewählte Sorte als roter Balken über die ganze Breite
			if (active) {
				g.fill(x, y, x + width, y + height, RED);
				g.fill(x, y + height - 1, x + width, y + height, RED_DARK);
			} else if (hover) {
				g.fill(x, y, x + width, y + height, 0x1CE9EFE6);
			}
			int countW = count == null ? 0 : font.width(count) + 4;
			if (count != null) {
				GuiDraw.text(g, font, count, x + width - 5 - font.width(count), textY, active ? CREAM : CHALK_DIM, false);
			}
			TextFit.draw(g, font, label, x + 6, textY, width - 11 - countW, active ? CREAM : hover ? CHALK_YELLOW : CHALK_TEXT);
			return;
		}
		// Papier: gewählter Reiter als roter Aufkleber, die anderen als Papierstreifen
		if (active) {
			box(g, x, y, width, height, RED_DARK);
			box(g, x, y, width, height - 2, RED);
		} else {
			box(g, x, y, width, height, hover ? CREAM : PAPER_2);
			frame(g, x, y, width, height, hover ? RED_HI : PAPER_EDGE);
		}
		String text = count == null ? label : label + " " + count;
		int color = active ? CREAM : hover ? RED : INK;
		TextFit.drawCentered(g, font, text, x + width / 2, active ? textY - 1 : textY, width - 6, color);
	}

	@Override
	public void row(GuiGraphics g, int x, int y, int width, int height, boolean selected, UiState state) {
		boolean hover = state == UiState.HOVER || state == UiState.PRESSED;
		if (height > 24) {
			// Karte: Papierstück; gewählt mit doppeltem roten Rand
			shadow(g, x, y, width, height, 1);
			sprite(g, PAPER_TEX, x + 1, y + 1, width - 2, height - 2, hover ? SHEET : PAPER);
			if (selected) {
				frame(g, x, y, width, height, RED);
				frame(g, x + 1, y + 1, width - 2, height - 2, RED);
			} else {
				frame(g, x, y, width, height, hover ? RED_HI : PAPER_EDGE);
			}
			if (state == UiState.DISABLED) {
				box(g, x, y, width, height, 0x60FFFAF1);
			}
			return;
		}
		// Zeile: zartes Rot mit Strich links (wie der Finger auf der Preisliste)
		if (selected || hover) {
			g.fill(x, y, x + width, y + height, selected ? 0x2AB23A3A : 0x1AB23A3A);
			g.fill(x, y, x + 2, y + height, RED);
		}
	}

	@Override
	public void input(GuiGraphics g, int x, int y, int width, int height, boolean focused) {
		g.fill(x, y, x + width, y + height, RECEIPT);
		frame(g, x, y, width, height, focused ? 0xFFE0B0A8 : PAPER_EDGE);
		if (focused) {
			g.fill(x + 1, y + height - 1, x + width - 1, y + height, RED);
		} else {
			dashed(g, x + 1, x + width - 1, y + height - 1, INK_LIGHT);
		}
	}

	@Override
	public void slider(GuiGraphics g, int x, int y, int width, int height, float value, UiState state) {
		float v = Math.max(0, Math.min(1, value));
		int knob = Math.min(7, height);
		int trackY = y + (height - 3) / 2;
		int left = x + knob / 2;
		int right = x + width - knob / 2;
		box(g, left, trackY, right - left, 3, PAPER_2);
		frame(g, left, trackY, right - left, 3, PAPER_EDGE);
		int kx = left + Math.round((right - left) * v);
		if (kx > left + 1) {
			g.fill(left + 1, trackY + 1, kx, trackY + 2, RED);
		}
		boolean hover = state == UiState.HOVER || state == UiState.PRESSED;
		sprite(g, hover ? KNOB_HOVER : KNOB, kx - knob / 2, y + (height - knob) / 2, knob, knob,
			state == UiState.DISABLED ? 0x80FFFFFF : 0xFFFFFFFF);
	}

	@Override
	public void scrollbar(GuiGraphics g, int x, int y, int width, int height, int thumbY, int thumbHeight, UiState state) {
		int cx = x + width / 2;
		for (int yy = y; yy < y + height; yy += 3) {
			g.fill(cx, yy, cx + 1, yy + 1, PAPER_DEEP);
		}
		box(g, x, y + thumbY, width, thumbHeight, state == UiState.HOVER || state == UiState.PRESSED ? RED : RED_DARK);
	}

	@Override
	public void badge(GuiGraphics g, Font font, int x, int y, String text) {
		int w = Math.max(10, font.width(text) + 6);
		box(g, x - w, y, w, 11, RED_DARK);
		box(g, x - w, y, w, 10, RED);
		GuiDraw.text(g, font, text, x - w + (w - font.width(text) + 1) / 2, y + 1, CREAM, false);
	}

	@Override
	public void tag(GuiGraphics g, Font font, int x, int y, String text) {
		Theke.tag(g, font, x, y, text, RECEIPT, PEPPER, PEPPER);
	}

	@Override
	public int tagWidth(Font font, String text) {
		return Theke.tagWidth(font, text);
	}

	@Override
	public int tagHeight() {
		return TAG_H;
	}

	@Override
	public void divider(GuiGraphics g, int x, int y, int width, Surface on) {
		if (on == Surface.SIDE) {
			dashed(g, x, x + width, y, CHALK_LINE);
		} else {
			dotted(g, x, x + width, y, INK_LIGHT, 2);
		}
	}

	// ------------------------------------------------------------------ Schrift

	@Override
	public void heading(GuiGraphics g, Font font, String text, int x, int y, int color, float scale) {
		Theke.heading(g, font, text, x, y, color, scale);
	}

	@Override
	public int headingWidth(Font font, String text, float scale) {
		return Theke.headingWidth(font, text, scale);
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
