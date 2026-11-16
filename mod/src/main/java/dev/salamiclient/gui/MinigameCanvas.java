package dev.salamiclient.gui;

import dev.salamiclient.minigames.Canvas;
import dev.salamiclient.render.GuiDraw;
import dev.salamiclient.theme.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

/**
 * {@link Canvas} auf Minecrafts GUI: Spielkoordinaten werden skaliert und verschoben, Kanten auf
 * echte Bildschirmpixel gerundet (scharf und ohne Lücken zwischen Nachbarflächen) und Text in ganzen
 * Pixelgrößen gezeichnet.
 */
final class MinigameCanvas implements Canvas {
	private GuiGraphics g;
	private Font font;
	private Theme theme;
	/** Ursprung und Maßstab: Spiel → UI-Koordinaten. */
	private float originX;
	private float originY;
	private float scale = 1f;
	/** Bildschirmpixel pro UI-Einheit (GUI-Skalierung × ClickGUI-Größe). */
	private float pixels = 1f;

	void begin(GuiGraphics g, Font font, Theme theme, float originX, float originY, float scale, float pixels) {
		this.g = g;
		this.font = font;
		this.theme = theme;
		this.originX = originX;
		this.originY = originY;
		this.scale = scale;
		this.pixels = Math.max(0.25f, pixels);
	}

	private float snap(float uiValue) {
		return Math.round(uiValue * pixels) / pixels;
	}

	@Override
	public void rect(float x0, float y0, float x1, float y1, int argb) {
		if ((argb >>> 24) == 0) {
			return;
		}
		float ax = snap(originX + x0 * scale);
		float ay = snap(originY + y0 * scale);
		float bx = snap(originX + x1 * scale);
		float by = snap(originY + y1 * scale);
		if (bx <= ax || by <= ay) {
			return;
		}
		fill(g, ax, ay, bx, by, argb);
	}

	/** Rechteck mit Gleitkomma-Kanten in UI-Koordinaten (GuiGraphics.fill kennt nur ganze Zahlen). */
	static void fill(GuiGraphics g, float x0, float y0, float x1, float y1, int argb) {
		if (x0 == (int) x0 && y0 == (int) y0 && x1 == (int) x1 && y1 == (int) y1) {
			g.fill((int) x0, (int) y0, (int) x1, (int) y1, argb);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x0, y0);
		g.pose().scale(x1 - x0, y1 - y0);
		g.fill(0, 0, 1, 1, argb);
		g.pose().popMatrix();
	}

	@Override
	public void gradient(float x0, float y0, float x1, float y1, int top, int bottom) {
		float ax = snap(originX + x0 * scale);
		float ay = snap(originY + y0 * scale);
		float bx = snap(originX + x1 * scale);
		float by = snap(originY + y1 * scale);
		if (bx <= ax || by <= ay) {
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(ax, ay);
		g.pose().scale(bx - ax, by - ay);
		g.fillGradient(0, 0, 1, 1, top, bottom);
		g.pose().popMatrix();
	}

	/** Schriftgröße (UI-Einheiten pro Schriftpixel), auf ganze Bildschirmpixel gerundet. */
	private float textScale(float size) {
		float k = size * scale / font.lineHeight;
		return Math.max(1, Math.round(k * pixels)) / pixels;
	}

	@Override
	public void text(String text, float centerX, float centerY, float size, int argb) {
		if (text.isEmpty() || (argb >>> 24) < 8) {
			return;
		}
		float k = textScale(size);
		float x = snap(originX + centerX * scale - font.width(text) * k / 2);
		float y = snap(originY + centerY * scale - 3.5f * k);
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(k, k);
		GuiDraw.text(g, font, text, 0, 0, argb, false);
		g.pose().popMatrix();
	}

	@Override
	public float textWidth(String text, float size) {
		return font.width(text) * textScale(size) / scale;
	}

	@Override
	public String tr(String key, Object... args) {
		return I18n.get(key, args);
	}

	@Override
	public Theme theme() {
		return theme;
	}

	@Override
	public float pixel() {
		return 1f / (scale * pixels);
	}

	@Override
	public void clip(float x0, float y0, float x1, float y1) {
		g.enableScissor((int) Math.floor(originX + x0 * scale), (int) Math.floor(originY + y0 * scale),
			(int) Math.ceil(originX + x1 * scale), (int) Math.ceil(originY + y1 * scale));
	}

	@Override
	public void unclip() {
		g.disableScissor();
	}
}
