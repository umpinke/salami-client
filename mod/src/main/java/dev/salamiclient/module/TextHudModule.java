package dev.salamiclient.module;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.theme.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Einzeilige HUD-Anzeige „Bezeichnung: Wert“ im einheitlichen Salami-Look (wie CPS und Reach).
 * Der Text wird pro Tick berechnet, nicht pro Frame.
 */
public abstract class TextHudModule extends HudModule {
	private final BoolSetting background = add(new BoolSetting("background", true));
	private String label = "";
	private String value = "";

	protected TextHudModule(String id, double defaultX, double defaultY) {
		super(id, Category.HUD, false, defaultX, defaultY);
	}

	/** Bezeichnung (bereits übersetzt) oder leer. */
	protected abstract String label();

	/** Wert oder {@code null}, wenn gerade nichts anzuzeigen ist. */
	protected abstract String value();

	@Override
	public void onTick() {
		label = label();
		value = value();
	}

	@Override
	public boolean hasContent() {
		return value != null;
	}

	private String prefix() {
		return label.isEmpty() ? "" : label + ": ";
	}

	@Override
	public int width() {
		return mc().font.width(prefix() + (value == null ? "-" : value)) + 8;
	}

	@Override
	public int height() {
		return 14;
	}

	@Override
	protected void render(GuiGraphics graphics, DeltaTracker delta) {
		Theme theme = SalamiClient.get().theme();
		if (background.isOn()) {
			graphics.fill(0, 0, width(), height(), 0x80000000);
		}
		String prefix = prefix();
		graphics.drawString(mc().font, prefix, 4, 3, theme.accent(), true);
		graphics.drawString(mc().font, value == null ? "-" : value, 4 + mc().font.width(prefix), 3, theme.text(), true);
	}
}
