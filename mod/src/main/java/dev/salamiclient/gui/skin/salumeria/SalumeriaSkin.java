package dev.salamiclient.gui.skin.salumeria;

import dev.salamiclient.gui.skin.Icons;
import dev.salamiclient.gui.skin.MenuView;
import dev.salamiclient.gui.skin.ModsView;
import dev.salamiclient.gui.skin.Skin;
import dev.salamiclient.gui.skin.TextFit;
import dev.salamiclient.render.GuiDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import static dev.salamiclient.gui.skin.salumeria.Salumeria.*;

/**
 * Salumeria: das Etikett eines italienischen Feinkostladens. Fast schwarzes Burgunder mit feinen
 * doppelten Linien in Creme, Serifenschrift (Cormorant Garamond) in Kapitälchen und kursiv, Salami-Rot
 * als Akzent mit kleinen Rauten (Schalter: gefüllte rote Raute = an, Umriss = aus), römische Ziffern
 * und Punktlinien wie auf einer Speisekarte. Siehe {@code gui/skin/SKINS.md}.
 */
public final class SalumeriaSkin implements Skin {
	private static final Ink LABEL_INK = new Ink(CREAM, CREAM_DIM, RED_TEXT, CREAM, RULE_SOFT, WARNING);
	private static final Ink INPUT_INK = new Ink(CREAM, CREAM_FAINT, RED_TEXT, CREAM, RULE_SOFT, WARNING);

	/** Überschriften sind etwas größer als die Pixelschrift gleicher Höhe – die Mittellänge der Antiqua ist kleiner. */
	static final float HEADING = 1.15f;
	static final int TAG_H = 13;

	private final SalumeriaMenuView menuView = new SalumeriaMenuView(this);
	private final SalumeriaModsView modsView = new SalumeriaModsView(this);

	public SalumeriaSkin() {}

	@Override
	public String id() {
		return "salumeria";
	}

	// ------------------------------------------------------------------ Flächen

	@Override
	public void backdrop(GuiGraphics g, int width, int height) {
		// warmes Dunkel, unten dichter (Hotbar, Chat), an den Rändern etwas dunkler
		g.fillGradient(0, 0, width, height, 0xC4160D0C, 0xEC0E0807);
		g.fillGradient(0, 0, width, Math.min(40, height), 0x50000000, 0x00000000);
	}

	@Override
	public int backdropTop() {
		return 0;
	}

	@Override
	public void surface(GuiGraphics g, Surface kind, int x, int y, int width, int height) {
		switch (kind) {
			case WINDOW -> {
				shadow(g, x, y, width, height);
				g.fill(x, y, x + width, y + height, BG);
				doubleRule(g, x, y, width, height, 2.5f, RULE, RULE_SOFT);
			}
			case SIDE -> {
				g.fill(x, y, x + width, y + height, BG_DEEP);
				hairRect(g, x, y, width, height, RULE_SOFT);
			}
			case CARD -> {
				g.fill(x, y, x + width, y + height, BG_RAISED);
				hairRect(g, x, y, width, height, RULE_FAINT);
			}
			case POPUP -> {
				shadow(g, x, y, width, height);
				g.fill(x, y, x + width, y + height, BG);
				doubleRule(g, x, y, width, height, 2f, RULE, RULE_SOFT);
			}
			case INPUT -> {
				g.fill(x, y, x + width, y + height, BG_DEEP);
				hline(g, x, x + width, y + height - 0.5f, RULE);
			}
		}
	}

	@Override
	public Ink ink(Surface kind) {
		return kind == Surface.INPUT ? INPUT_INK : LABEL_INK;
	}

	// ------------------------------------------------------------------ Bedienelemente

	/** Typografische Pfeile statt „<“/„>“ in Knöpfen („< Zurück“ → „‹ Zurück“). */
	static String typographic(String label) {
		String out = label;
		if (out.startsWith("< ")) {
			out = "‹ " + out.substring(2);
		}
		if (out.endsWith(" >")) {
			out = out.substring(0, out.length() - 2) + " ›";
		}
		return out;
	}

	@Override
	public void button(GuiGraphics g, Font font, int x, int y, int width, int height, String label, ButtonKind kind, UiState state) {
		iconButton(g, x, y, width, height, kind, state);
		if (label == null || label.isEmpty()) {
			return;
		}
		String text = typographic(label);
		int color = buttonInk(kind, state);
		if (text.chars().noneMatch(Character::isLetter)) {
			// „+“, „-“, „100%“: in der Pixelschrift (die Antiqua hat Mediävalziffern und feine Zeichen)
			TextFit.drawCentered(g, font, text, x + width / 2, y + (height - 8) / 2 + (state == UiState.PRESSED ? 1 : 0), width - 4, color);
			return;
		}
		Serif.Type type = kind == ButtonKind.GHOST ? Serif.ITALIC.size(1.15f) : Serif.CAPS;
		Serif.Fitted fitted = Serif.fit(font, type, text, width - 6);
		if (fitted.type() != type && kind != ButtonKind.GHOST) {
			// zu breit in Kapitälchen: erst normal gesetzt versuchen, bevor verkleinert wird
			Serif.Fitted plain = Serif.fit(font, Serif.ROMAN, text, width - 6);
			if (plain.type().scale() > fitted.type().scale()) {
				fitted = plain;
			}
		}
		float baseline = y + (height + fitted.type().cap()) / 2f - 0.5f + (state == UiState.PRESSED ? 1 : 0);
		fitted.drawCentered(g, font, x + width / 2f, baseline, color);
		if (kind == ButtonKind.GHOST && (state == UiState.HOVER || state == UiState.PRESSED)) {
			float w = fitted.width();
			hline(g, x + (width - w) / 2f, x + (width + w) / 2f, baseline + 2f, RED_HI);
		}
	}

	@Override
	public void iconButton(GuiGraphics g, int x, int y, int size, ButtonKind kind, UiState state) {
		iconButton(g, x, y, size, size, kind, state);
	}

	private void iconButton(GuiGraphics g, int x, int y, int w, int h, ButtonKind kind, UiState state) {
		boolean hover = state == UiState.HOVER || state == UiState.PRESSED;
		if (state == UiState.DISABLED) {
			if (kind != ButtonKind.GHOST) {
				hairRect(g, x, y, w, h, RULE_FAINT);
			}
			return;
		}
		switch (kind) {
			case PRIMARY -> {
				g.fill(x, y, x + w, y + h, state == UiState.PRESSED ? RED_DARK : hover ? RED_HI : RED);
				hairRect(g, x + 1.5f, y + 1.5f, w - 3, h - 3, 0x66F3E7D6);
			}
			case SECONDARY -> {
				g.fill(x, y, x + w, y + h, hover ? BG_HOVER : BG_RAISED);
				hairRect(g, x, y, w, h, hover ? RULE : RULE_SOFT);
			}
			case DANGER -> {
				if (hover) {
					g.fill(x, y, x + w, y + h, RED_DARK);
				}
				hairRect(g, x, y, w, h, hover ? RED_HI : RED);
			}
			case GHOST -> {
				if (hover) {
					g.fill(x, y, x + w, y + h, 0x12F3E7D6);
				}
			}
		}
	}

	@Override
	public int buttonInk(ButtonKind kind, UiState state) {
		if (state == UiState.DISABLED) {
			return CREAM_FAINT;
		}
		boolean hover = state == UiState.HOVER || state == UiState.PRESSED;
		return switch (kind) {
			case PRIMARY -> CREAM;
			case SECONDARY -> hover ? CREAM : CREAM_DIM;
			case DANGER -> hover ? CREAM : RED_TEXT;
			case GHOST -> hover ? CREAM : CREAM_DIM;
		};
	}

	@Override
	public int[] toggleSize() {
		return new int[] {9, 9};
	}

	@Override
	public void toggle(GuiGraphics g, int x, int y, boolean on, UiState state) {
		boolean hover = state == UiState.HOVER || state == UiState.PRESSED;
		boolean disabled = state == UiState.DISABLED;
		toggleDiamond(g, x + 4.5f, y + 4.5f, 4f, on, hover, disabled);
	}

	/** Raute als Schalter: gefüllt rot = an, Umriss = aus. */
	static void toggleDiamond(GuiGraphics g, float cx, float cy, float half, boolean on, boolean hover, boolean disabled) {
		if (on) {
			int color = disabled ? 0x80B23A3A : hover ? RED_HI : RED;
			diamond(g, cx, cy, half, color, true);
		} else {
			int color = disabled ? 0x408C7B6C : hover ? CREAM : CREAM_FAINT;
			diamond(g, cx, cy, half, color, false);
		}
	}

	@Override
	public void checkbox(GuiGraphics g, int x, int y, int size, boolean checked, UiState state) {
		boolean hover = state == UiState.HOVER;
		if (checked) {
			g.fill(x, y, x + size, y + size, hover ? RED_HI : RED);
			if (size >= 7) {
				Icons.check(g, x + (size - 7) / 2, y + (size - 5) / 2, CREAM);
			} else {
				g.fill(x + 2, y + 2, x + size - 2, y + size - 2, CREAM);
			}
		} else {
			g.fill(x, y, x + size, y + size, BG_DEEP);
			hairRect(g, x, y, size, size, hover ? CREAM : RULE);
		}
		if (state == UiState.DISABLED) {
			g.fill(x, y, x + size, y + size, 0x90130C0B);
		}
	}

	@Override
	public void tab(GuiGraphics g, Font font, int x, int y, int width, int height, String label, String count, boolean active,
		boolean vertical, UiState state) {
		boolean hover = state == UiState.HOVER;
		int color = active || hover ? CREAM : vertical ? 0xFFD9CAB6 : CREAM_DIM;
		Serif.Type type = Serif.CAPS;
		float baseline = y + (height + type.cap()) / 2f - 0.5f;
		if (vertical) {
			// Register: Raute vor dem gewählten Eintrag, Zahl kursiv rechts, gewählt rot unterstrichen
			float countW = count == null ? 0 : Serif.ITALIC.width(font, count);
			if (count != null) {
				Serif.ITALIC.drawRight(g, font, count, x + width - 5, baseline, active ? CREAM_DIM : CREAM_FAINT);
			}
			if (active) {
				diamond(g, x + 5, baseline - type.small() / 2f, 1.75f, RED_HI, true);
			}
			Serif.Fitted fitted = Serif.fit(font, type, label, width - 15 - (count == null ? 0 : countW + 4));
			fitted.draw(g, font, x + 10, baseline, color);
			if (active) {
				g.fill(x + 3, Math.round(baseline + 3), x + width - 3, Math.round(baseline + 4), RED);
			} else if (hover) {
				hline(g, x + 3, x + width - 3, baseline + 3, RULE_SOFT);
			}
			return;
		}
		String text = count == null ? label : label + " " + count;
		Serif.Fitted fitted = Serif.fit(font, type, text, width - 6);
		fitted.drawCentered(g, font, x + width / 2f, baseline, color);
		float w = fitted.width();
		float lineY = Math.min(y + height - 1, baseline + 3);
		if (active) {
			g.fill(Math.round(x + (width - w) / 2f), Math.round(lineY), Math.round(x + (width + w) / 2f), Math.round(lineY) + 1, RED);
		} else if (hover) {
			hline(g, x + (width - w) / 2f, x + (width + w) / 2f, lineY, RULE_SOFT);
		}
	}

	@Override
	public void row(GuiGraphics g, int x, int y, int width, int height, boolean selected, UiState state) {
		boolean hover = state == UiState.HOVER || state == UiState.PRESSED;
		if (height > 24) {
			// Karte: Etikett im Etikett; gewählt mit doppelter roter Linie
			g.fill(x, y, x + width, y + height, hover ? BG_HOVER : BG_RAISED);
			if (selected) {
				doubleRule(g, x, y, width, height, 2f, RED_HI, RED);
			} else {
				hairRect(g, x, y, width, height, hover ? RULE : RULE_FAINT);
			}
			if (state == UiState.DISABLED) {
				g.fill(x, y, x + width, y + height, 0x80130C0B);
			}
			return;
		}
		if (selected || hover) {
			g.fill(x, y, x + width, y + height, selected ? 0x2EB23A3A : 0x12F3E7D6);
			if (selected) {
				vline(g, x, y, y + height, RED_HI);
			}
		}
	}

	@Override
	public void input(GuiGraphics g, int x, int y, int width, int height, boolean focused) {
		g.fill(x, y, x + width, y + height, BG_DEEP);
		if (focused) {
			hairRect(g, x, y, width, height, RULE_SOFT);
			g.fill(x, y + height - 1, x + width, y + height, RED);
		} else {
			hline(g, x, x + width, y + height - 0.5f, RULE);
		}
	}

	@Override
	public void slider(GuiGraphics g, int x, int y, int width, int height, float value, UiState state) {
		float v = Math.max(0, Math.min(1, value));
		float left = x + 4;
		float right = x + width - 4;
		float cy = y + height / 2f;
		hline(g, left, right, cy, RULE);
		float kx = left + (right - left) * v;
		if (kx > left + 1) {
			g.fill(Math.round(left), Math.round(cy - 0.5f), Math.round(kx), Math.round(cy - 0.5f) + 1, RED);
		}
		boolean hover = state == UiState.HOVER || state == UiState.PRESSED;
		boolean disabled = state == UiState.DISABLED;
		diamond(g, kx, cy, 3.5f, disabled ? CREAM_FAINT : CREAM, true);
		diamond(g, kx, cy, 2.25f, disabled ? 0xFF5A3A36 : hover ? RED_HI : RED, true);
	}

	@Override
	public void scrollbar(GuiGraphics g, int x, int y, int width, int height, int thumbY, int thumbHeight, UiState state) {
		float cx = x + width / 2f;
		vline(g, cx, y, y + height, RULE_FAINT);
		boolean hover = state == UiState.HOVER || state == UiState.PRESSED;
		int bx = Math.round(cx) - 1;
		g.fill(bx, y + thumbY, bx + 2, y + thumbY + thumbHeight, hover ? CREAM_DIM : RULE_SOFT);
	}

	@Override
	public void badge(GuiGraphics g, Font font, int x, int y, String text) {
		int w = Math.max(9, font.width(text) + 5);
		g.fill(x - w, y, x, y + 10, RED);
		hairRect(g, x - w, y, w, 10, 0x55F3E7D6);
		GuiDraw.text(g, font, text, x - w + (w - font.width(text) + 1) / 2, y + 1, CREAM, false);
	}

	@Override
	public void tag(GuiGraphics g, Font font, int x, int y, String text) {
		int w = tagWidth(font, text);
		g.fill(x, y, x + w, y + TAG_H, 0xEE130C0B);
		hairRect(g, x, y, w, TAG_H, RULE_SOFT);
		Serif.ITALIC.draw(g, font, text, x + 6, y + 9, CREAM_DIM);
	}

	@Override
	public int tagWidth(Font font, String text) {
		return Serif.ITALIC.widthInt(font, text) + 12;
	}

	@Override
	public int tagHeight() {
		return TAG_H;
	}

	@Override
	public void divider(GuiGraphics g, int x, int y, int width, Surface on) {
		if (width >= 40) {
			ruleWithDiamond(g, x, x + width, y, RULE_SOFT, RED_HI);
		} else {
			hline(g, x, x + width, y, RULE_SOFT);
		}
	}

	// ------------------------------------------------------------------ Schrift

	@Override
	public void heading(GuiGraphics g, Font font, String text, int x, int y, int color, float scale) {
		Serif.Type type = Serif.ROMAN.size(scale * HEADING);
		// Oberkante der Versalien bei y, wie bei der Pixelschrift
		type.draw(g, font, text, x, y + type.cap(), color);
	}

	@Override
	public int headingWidth(Font font, String text, float scale) {
		return Serif.ROMAN.size(scale * HEADING).widthInt(font, text);
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
