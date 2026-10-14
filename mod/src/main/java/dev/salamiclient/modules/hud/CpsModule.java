package dev.salamiclient.modules.hud;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.util.ClickTracker;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/** CPS: Klicks pro Sekunde (links, optional auch rechts). Text wird pro Tick neu gebaut. */
public final class CpsModule extends HudModule {
	private final BoolSetting showRight = add(new BoolSetting("show_right", true));
	private final BoolSetting background = add(new BoolSetting("background", true));

	private String text = "0 CPS";
	private int width = 40;

	public CpsModule() {
		super("cps", Category.HUD, false, 0.005, 0.30);
	}

	@Override
	public void onTick() {
		String left = String.valueOf(ClickTracker.left());
		text = showRight.isOn() ? left + " | " + ClickTracker.right() + " CPS" : left + " CPS";
		width = mc().font.width(text) + 8;
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
		if (background.isOn()) {
			graphics.fill(0, 0, width, 14, 0x80000000);
		}
		graphics.drawString(mc().font, text, 4, 3, SalamiClient.get().theme().text(), true);
	}
}
