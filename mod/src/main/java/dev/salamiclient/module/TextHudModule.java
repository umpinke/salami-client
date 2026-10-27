package dev.salamiclient.module;

import dev.salamiclient.render.GuiDraw;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Einzeilige HUD-Anzeige „Bezeichnung: Wert“ im einheitlichen Salami-Look (wie CPS und Reach).
 * Text und Breite werden pro Tick berechnet, nicht pro Frame.
 */
public abstract class TextHudModule extends HudModule {
	private String prefix = "";
	private String value;
	private int prefixWidth;
	private int width = 20;

	protected TextHudModule(String id, double defaultX, double defaultY) {
		super(id, Category.HUD, false, defaultX, defaultY);
	}

	@Override
	protected boolean defaultBackground() {
		return true;
	}

	@Override
	protected int backgroundPadding() {
		return 0; // Rand ist schon in der Breite enthalten
	}

	/** Bezeichnung (bereits übersetzt) oder leer. */
	protected abstract String label();

	/** Wert oder {@code null}, wenn gerade nichts anzuzeigen ist. */
	protected abstract String value();

	@Override
	public void onTick() {
		String label = label();
		prefix = label.isEmpty() ? "" : label + ": ";
		value = value();
		prefixWidth = mc().font.width(prefix);
		width = prefixWidth + mc().font.width(value == null ? "-" : value) + 8;
	}

	@Override
	public boolean hasContent() {
		return value != null;
	}

	@Override
	public int width() {
		return width;
	}

	@Override
	public int height() {
		return 14;
	}

	@Override
	protected void render(GuiGraphics graphics, DeltaTracker delta) {
		GuiDraw.text(graphics, mc().font, prefix, 4, 3, accentColor(), shadow());
		GuiDraw.text(graphics, mc().font, value == null ? "-" : value, 4 + prefixWidth, 3, textColor(), shadow());
	}
}
