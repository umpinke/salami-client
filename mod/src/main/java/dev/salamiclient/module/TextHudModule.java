package dev.salamiclient.module;

import dev.salamiclient.render.GuiDraw;
import dev.salamiclient.setting.BoolSetting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Einzeilige HUD-Anzeige „Bezeichnung Wert“ im einheitlichen Salami-Look: ruhige Bezeichnung,
 * heller Wert, Innenabstand schon in der Breite. Text und Breite werden pro Tick berechnet,
 * nicht pro Frame.
 */
public abstract class TextHudModule extends HudModule {
	/** Innenabstand links/rechts und oben. */
	protected static final int PAD_X = 5;
	protected static final int PAD_Y = 3;

	private final BoolSetting showLabel = add(new BoolSetting("show_label", true));

	private String prefix = "";
	private String value;
	private int prefixWidth;
	private int width = 20;

	protected TextHudModule(String id, double defaultX, double defaultY) {
		super(id, Category.HUD, false, defaultX, defaultY);
	}

	@Override
	protected boolean padded() {
		return false; // Rand ist schon in der Breite enthalten
	}

	/** Bezeichnung (bereits übersetzt) oder leer. */
	protected abstract String label();

	/** Wert oder {@code null}, wenn gerade nichts anzuzeigen ist. */
	protected abstract String value();

	@Override
	public void onTick() {
		String label = showLabel.isOn() ? label() : "";
		prefix = label.isEmpty() ? "" : label + "  ";
		value = value();
		prefixWidth = mc().font.width(prefix);
		width = prefixWidth + mc().font.width(value == null ? "-" : value) + 2 * PAD_X - 1;
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
		GuiDraw.text(graphics, mc().font, prefix, PAD_X, PAD_Y, labelColor(), shadow());
		GuiDraw.text(graphics, mc().font, value == null ? "-" : value, PAD_X + prefixWidth, PAD_Y, textColor(), shadow());
	}
}
