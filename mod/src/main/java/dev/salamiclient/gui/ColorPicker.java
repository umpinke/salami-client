package dev.salamiclient.gui;

import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.theme.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import org.lwjgl.glfw.GLFW;

/**
 * Farbwähler für eine {@link ColorSetting} – als kleines Fenster über ClickGUI oder Mods-Menü:
 * Farbfeld (Sättigung/Helligkeit), Farbton, Deckkraft, Hex-Eingabe, Schnellauswahl,
 * Regenbogen und Zurücksetzen. Arbeitet in den (skalierten) Koordinaten des Bildschirms.
 */
public final class ColorPicker {
	private static final int PAD = 6;
	private static final int GAP = 6;
	private static final int INNER_W = 132;
	private static final int FIELD_H = 72;
	private static final int BAR_W = 10;
	private static final int ROW_H = 12;
	private static final int SWATCH = 9;
	public static final int WIDTH = PAD + INNER_W + PAD;
	public static final int HEIGHT = PAD + FIELD_H + GAP + ROW_H + GAP + SWATCH + GAP + ROW_H + PAD;

	private enum Drag { NONE, FIELD, HUE, ALPHA }

	private final ColorSetting setting;
	private final int x0;
	private final int y0;
	private float hue;
	private float saturation;
	private float value;
	private int alpha;
	/** Zuletzt übernommener Wert – ändert er sich von außen (z. B. Zurücksetzen), neu einlesen. */
	private int shown;
	private Drag drag = Drag.NONE;
	private boolean editingHex;
	private String hexBuffer = "";
	private boolean hexInvalid;

	/** Öffnet unter (bzw. über) dem Anker, innerhalb von {@code screenW × screenH}. */
	public ColorPicker(ColorSetting setting, int anchorRight, int anchorBottom, int screenW, int screenH) {
		this.setting = setting;
		int x = anchorRight - WIDTH;
		int y = anchorBottom + 2;
		if (y + HEIGHT > screenH - 2) {
			y = Math.max(2, anchorBottom - 16 - HEIGHT);
		}
		this.x0 = Math.max(2, Math.min(x, screenW - WIDTH - 2));
		this.y0 = Math.max(2, Math.min(y, screenH - HEIGHT - 2));
		load(setting.get());
	}

	public ColorSetting setting() {
		return setting;
	}

	/** Gerade Texteingabe (Hex) – Tasten gehören dann dem Farbwähler. */
	public boolean isEditingText() {
		return editingHex;
	}

	public boolean isDragging() {
		return drag != Drag.NONE;
	}

	private void load(int argb) {
		float[] hsv = ColorSetting.toHsv(argb);
		// Bei Grau/Schwarz den Farbton behalten, sonst springt der Regler auf Rot
		if (hsv[1] > 0 && hsv[2] > 0) {
			hue = hsv[0];
		}
		saturation = hsv[1];
		value = hsv[2];
		alpha = argb >>> 24;
		shown = argb;
	}

	private void apply() {
		int argb = ColorSetting.fromHsv(hue, saturation, value, setting.alphaAllowed() ? alpha : 255);
		shown = argb;
		setting.set(argb);
	}

	// ------------------------------------------------------------------ Layout

	private int fieldW() {
		return INNER_W - GAP - BAR_W - (setting.alphaAllowed() ? GAP + BAR_W : 0);
	}

	private int[] field() {
		return new int[] {x0 + PAD, y0 + PAD, x0 + PAD + fieldW(), y0 + PAD + FIELD_H};
	}

	private int[] hueBar() {
		int x = field()[2] + GAP;
		return new int[] {x, y0 + PAD, x + BAR_W, y0 + PAD + FIELD_H};
	}

	private int[] alphaBar() {
		int x = hueBar()[2] + GAP;
		return new int[] {x, y0 + PAD, x + BAR_W, y0 + PAD + FIELD_H};
	}

	private int hexRowY() {
		return y0 + PAD + FIELD_H + GAP;
	}

	private int[] preview() {
		return new int[] {x0 + PAD, hexRowY(), x0 + PAD + 18, hexRowY() + ROW_H};
	}

	private int[] hexBox() {
		return new int[] {x0 + PAD + 22, hexRowY(), x0 + PAD + INNER_W, hexRowY() + ROW_H};
	}

	private int swatchY() {
		return hexRowY() + ROW_H + GAP;
	}

	private int[] swatch(int index) {
		int step = (INNER_W + 2) / ColorSetting.PALETTE.length;
		int x = x0 + PAD + index * step;
		return new int[] {x, swatchY(), x + step - 2, swatchY() + SWATCH};
	}

	private int buttonY() {
		return swatchY() + SWATCH + GAP;
	}

	private int[] chromaButton(Font font) {
		int w = font.width(I18n.get("salami.colorpicker.chroma")) + 14;
		return new int[] {x0 + PAD, buttonY(), x0 + PAD + w, buttonY() + ROW_H};
	}

	private int[] resetButton(Font font) {
		int w = font.width(I18n.get("salami.colorpicker.reset")) + 8;
		return new int[] {x0 + PAD + INNER_W - w, buttonY(), x0 + PAD + INNER_W, buttonY() + ROW_H};
	}

	private static boolean inside(int[] r, double x, double y) {
		return x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
	}

	/** {x0, y0, x1, y1} des Fensters. */
	public int[] bounds() {
		return new int[] {x0, y0, x0 + WIDTH, y0 + HEIGHT};
	}

	public boolean contains(double x, double y) {
		return x >= x0 && x < x0 + WIDTH && y >= y0 && y < y0 + HEIGHT;
	}

	// ------------------------------------------------------------------ Zeichnen

	public void render(GuiGraphics g, Font font, Theme theme, int mouseX, int mouseY) {
		if (setting.get() != shown && drag == Drag.NONE) {
			load(setting.get());
		}
		g.fill(x0, y0, x0 + WIDTH, y0 + HEIGHT, theme.panel() | 0xFF000000);
		g.renderOutline(x0, y0, WIDTH, HEIGHT, theme.accent());

		// Farbfeld: links weiß → rechts volle Farbe, oben hell → unten schwarz
		int[] f = field();
		int w = f[2] - f[0];
		for (int i = 0; i < w; i++) {
			int top = ColorSetting.fromHsv(hue, i / (float) (w - 1), 1F, 255);
			g.fillGradient(f[0] + i, f[1], f[0] + i + 1, f[3], top, 0xFF000000);
		}
		g.renderOutline(f[0] - 1, f[1] - 1, w + 2, f[3] - f[1] + 2, theme.outline());
		int markX = f[0] + Math.round(saturation * (w - 1));
		int markY = f[1] + Math.round((1 - value) * (FIELD_H - 1));
		g.renderOutline(markX - 2, markY - 2, 5, 5, 0xFF000000);
		g.renderOutline(markX - 1, markY - 1, 3, 3, 0xFFFFFFFF);

		// Farbton
		int[] h = hueBar();
		for (int j = 0; j < FIELD_H; j++) {
			g.fill(h[0], h[1] + j, h[2], h[1] + j + 1, ColorSetting.fromHsv(j / (float) (FIELD_H - 1), 1F, 1F, 255));
		}
		g.renderOutline(h[0] - 1, h[1] - 1, BAR_W + 2, FIELD_H + 2, theme.outline());
		barMarker(g, h, Math.round(hue * (FIELD_H - 1)));

		int opaque = ColorSetting.fromHsv(hue, saturation, value, 255);
		// Deckkraft
		if (setting.alphaAllowed()) {
			int[] a = alphaBar();
			checker(g, a);
			g.fillGradient(a[0], a[1], a[2], a[3], opaque, opaque & 0x00FFFFFF);
			g.renderOutline(a[0] - 1, a[1] - 1, BAR_W + 2, FIELD_H + 2, theme.outline());
			barMarker(g, a, Math.round((1 - alpha / 255F) * (FIELD_H - 1)));
		}

		// Vorschau + Hex
		int[] p = preview();
		checker(g, p);
		g.fill(p[0], p[1], p[2], p[3], setting.argb());
		g.renderOutline(p[0], p[1], p[2] - p[0], p[3] - p[1], theme.outline());
		int[] hb = hexBox();
		g.fill(hb[0], hb[1], hb[2], hb[3], 0xFF0B0B0E);
		g.renderOutline(hb[0], hb[1], hb[2] - hb[0], hb[3] - hb[1],
			editingHex ? (hexInvalid ? theme.warning() : theme.accent()) : theme.outline());
		String hex = editingHex ? hexBuffer : ColorSetting.toHex(setting.get());
		g.drawString(font, hex, hb[0] + 3, hb[1] + 2, theme.text(), false);
		if (editingHex && (System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = hb[0] + 3 + font.width(hex);
			g.fill(cx, hb[1] + 2, cx + 1, hb[3] - 2, theme.text());
		}

		// Schnellauswahl
		for (int i = 0; i < ColorSetting.PALETTE.length; i++) {
			int[] s = swatch(i);
			g.fill(s[0], s[1], s[2], s[3], ColorSetting.PALETTE[i]);
			boolean selected = !setting.isChroma() && (setting.get() | 0xFF000000) == ColorSetting.PALETTE[i];
			g.renderOutline(s[0], s[1], s[2] - s[0], s[3] - s[1],
				selected ? theme.accent() : inside(s, mouseX, mouseY) ? 0xFFFFFFFF : theme.outline());
		}

		// Regenbogen + Zurücksetzen
		if (setting.chromaAllowed()) {
			int[] c = chromaButton(font);
			boolean on = setting.isChroma();
			g.fill(c[0], c[1], c[2], c[3], inside(c, mouseX, mouseY) ? theme.hover() | 0x40000000 : 0x00000000);
			g.fill(c[0] + 1, c[1] + 2, c[0] + 9, c[1] + 10, on ? setting.argb() | 0xFF000000 : 0xFF0B0B0E);
			g.renderOutline(c[0] + 1, c[1] + 2, 8, 8, theme.outline());
			g.drawString(font, I18n.get("salami.colorpicker.chroma"), c[0] + 12, c[1] + 2, on ? theme.accent() : theme.text(), false);
		}
		int[] r = resetButton(font);
		boolean resetHover = inside(r, mouseX, mouseY);
		g.fill(r[0], r[1], r[2], r[3], resetHover ? theme.accent() : theme.header());
		g.drawString(font, I18n.get("salami.colorpicker.reset"), r[0] + 4, r[1] + 2, resetHover ? 0xFF000000 : theme.headerText(), false);
	}

	private static void barMarker(GuiGraphics g, int[] bar, int offset) {
		int y = bar[1] + offset;
		g.fill(bar[0] - 2, y - 1, bar[2] + 2, y + 2, 0xFF000000);
		g.fill(bar[0] - 1, y, bar[2] + 1, y + 1, 0xFFFFFFFF);
	}

	/** Schachbrett hinter durchsichtigen Farben. */
	private static void checker(GuiGraphics g, int[] r) {
		g.fill(r[0], r[1], r[2], r[3], 0xFFCCCCCC);
		for (int y = r[1]; y < r[3]; y += 4) {
			for (int x = r[0] + ((y - r[1]) / 4 % 2) * 4; x < r[2]; x += 8) {
				g.fill(x, y, Math.min(x + 4, r[2]), Math.min(y + 4, r[3]), 0xFF888888);
			}
		}
	}

	// ------------------------------------------------------------------ Eingabe

	/** {@code false}, wenn außerhalb geklickt wurde (dann schließt der Bildschirm den Farbwähler). */
	public boolean mouseClicked(Font font, double x, double y, int button) {
		if (!contains(x, y)) {
			commitHex();
			return false;
		}
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return true;
		}
		if (inside(hexBox(), x, y)) {
			if (!editingHex) {
				editingHex = true;
				hexInvalid = false;
				hexBuffer = ColorSetting.toHex(setting.get());
			}
			return true;
		}
		commitHex();
		if (inside(field(), x, y)) {
			drag = Drag.FIELD;
		} else if (inside(hueBar(), x, y)) {
			drag = Drag.HUE;
		} else if (setting.alphaAllowed() && inside(alphaBar(), x, y)) {
			drag = Drag.ALPHA;
		} else if (setting.chromaAllowed() && inside(chromaButton(font), x, y)) {
			setting.setChroma(!setting.isChroma());
			return true;
		} else if (inside(resetButton(font), x, y)) {
			setting.resetAll();
			load(setting.get());
			return true;
		} else {
			for (int i = 0; i < ColorSetting.PALETTE.length; i++) {
				if (inside(swatch(i), x, y)) {
					setting.setChroma(false);
					load(ColorSetting.PALETTE[i]);
					alpha = setting.alphaAllowed() ? setting.get() >>> 24 : 255; // Deckkraft behalten
					apply();
					return true;
				}
			}
			return true;
		}
		mouseDragged(x, y);
		return true;
	}

	public void mouseDragged(double x, double y) {
		switch (drag) {
			case FIELD -> {
				int[] f = field();
				saturation = clamp01((x - f[0]) / (f[2] - f[0] - 1));
				value = 1 - clamp01((y - f[1]) / (FIELD_H - 1));
				apply();
			}
			case HUE -> {
				hue = clamp01((y - hueBar()[1]) / (FIELD_H - 1));
				apply();
			}
			case ALPHA -> {
				alpha = Math.round((1 - clamp01((y - alphaBar()[1]) / (FIELD_H - 1))) * 255);
				apply();
			}
			case NONE -> {
			}
		}
	}

	public void mouseReleased() {
		drag = Drag.NONE;
	}

	/** {@code true}, wenn die Taste verbraucht wurde; Esc ohne Texteingabe gehört dem Bildschirm. */
	public boolean keyPressed(int key) {
		if (!editingHex) {
			return false;
		}
		switch (key) {
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> commitHex();
			case GLFW.GLFW_KEY_ESCAPE -> editingHex = false;
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (!hexBuffer.isEmpty()) {
					hexBuffer = hexBuffer.substring(0, hexBuffer.length() - 1);
				}
				hexInvalid = false;
			}
			default -> {
			}
		}
		return true;
	}

	public boolean charTyped(String text) {
		if (!editingHex) {
			return false;
		}
		for (char c : text.toCharArray()) {
			boolean hexDigit = Character.digit(c, 16) >= 0;
			if ((hexDigit || (c == '#' && hexBuffer.isEmpty())) && hexBuffer.length() < 9) {
				hexBuffer += Character.toUpperCase(c);
			}
		}
		hexInvalid = false;
		return true;
	}

	private void commitHex() {
		if (!editingHex) {
			return;
		}
		Integer parsed = ColorSetting.parseHex(hexBuffer);
		if (parsed == null) {
			hexInvalid = true;
			editingHex = false;
			return;
		}
		editingHex = false;
		setting.setChroma(false);
		load(parsed);
		if (!setting.alphaAllowed()) {
			alpha = 255;
		}
		apply();
	}

	private static float clamp01(double v) {
		return (float) Math.max(0, Math.min(1, v));
	}
}
