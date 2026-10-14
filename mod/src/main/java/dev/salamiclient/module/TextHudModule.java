package dev.salamiclient.module;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.theme.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Einzeilige HUD-Anzeige „Bezeichnung: Wert“ im einheitlichen Salami-Look (wie CPS und Reach).
 * Text und Breite werden pro Tick berechnet, nicht pro Frame.
 */
public abstract class TextHudModule extends HudModule {
	private final BoolSetting background = add(new BoolSetting("background", true));
	private String prefix = "";
	private String value;
	private int prefixWidth;
	private int width = 20;

	protected TextHudModule(String id, double defaultX, double defaultY) {
		super(id, Category.HUD, false, defaultX, defaultY);
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
		Theme theme = SalamiClient.get().theme();
		if (background.isOn()) {
			graphics.fill(0, 0, width, 14, 0x80000000);
		}
		graphics.drawString(mc().font, prefix, 4, 3, theme.accent(), true);
		graphics.drawString(mc().font, value == null ? "-" : value, 4 + prefixWidth, 3, theme.text(), true);
	}
}
