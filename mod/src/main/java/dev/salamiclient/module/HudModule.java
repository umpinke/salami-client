package dev.salamiclient.module;

import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Basis für verschiebbare HUD-Elemente (Overlay-System).
 *
 * <p>Position als Anteil der Bildschirmgröße (0..1, linke obere Ecke), damit sie
 * bei jeder Auflösung und GUI-Skalierung stimmt. Verschoben wird im HUD-Editor;
 * die Positions-Settings sind in der ClickGUI ausgeblendet.
 */
public abstract class HudModule extends Module {
	public final NumberSetting x;
	public final NumberSetting y;
	public final NumberSetting scale;

	protected HudModule(String id, Category category, boolean enabledByDefault, double defaultX, double defaultY) {
		super(id, category, enabledByDefault);
		this.x = add(new NumberSetting("x", defaultX, 0, 1, 0.001));
		this.y = add(new NumberSetting("y", defaultY, 0, 1, 0.001));
		x.hide();
		y.hide();
		this.scale = add(new NumberSetting("scale", 1.0, 0.5, 2.0, 0.1));
	}

	/** Breite/Höhe in unskalierten GUI-Pixeln (für Editor-Rahmen und Begrenzung am Rand). */
	public abstract int width();

	public abstract int height();

	/** Zeichnet bei (0,0) – Verschiebung und Skalierung übernimmt {@link #onHud}. */
	protected abstract void render(GuiGraphics graphics, DeltaTracker delta);

	/** Ob das Element gerade etwas anzeigt (Editor zeigt trotzdem einen Rahmen). */
	public boolean hasContent() {
		return true;
	}

	/** Eigene Größe × globale HUD-Größe (Modul „Oberfläche“). */
	public float effectiveScale() {
		return scale.get().floatValue() * GuiScaleModule.hudScale();
	}

	public int screenX(int guiWidth) {
		int w = Math.round(width() * effectiveScale());
		return Math.max(0, Math.min(guiWidth - w, (int) Math.round(x.get() * guiWidth)));
	}

	public int screenY(int guiHeight) {
		int h = Math.round(height() * effectiveScale());
		return Math.max(0, Math.min(guiHeight - h, (int) Math.round(y.get() * guiHeight)));
	}

	public void moveTo(int guiX, int guiY, int guiWidth, int guiHeight) {
		x.set((double) guiX / guiWidth);
		y.set((double) guiY / guiHeight);
	}

	@Override
	public final void onHud(GuiGraphics graphics, DeltaTracker delta) {
		if (!hasContent()) {
			return;
		}
		float s = effectiveScale();
		graphics.pose().pushMatrix();
		graphics.pose().translate(screenX(graphics.guiWidth()), screenY(graphics.guiHeight()));
		graphics.pose().scale(s, s);
		render(graphics, delta);
		graphics.pose().popMatrix();
	}
}
