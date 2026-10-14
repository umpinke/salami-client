package dev.salamiclient.modules.hud;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.util.ClickTracker;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/** CPS: Klicks pro Sekunde (links, optional auch rechts). Text wird pro Tick neu gebaut. */
public final class CpsModule extends HudModule {
	private final BoolSetting showRight = add(new BoolSetting("show_right", true));

	private String text = "0 CPS";
	private int width = 40;

	public CpsModule() {
		super("cps", Category.HUD, false, 0.005, 0.30);
	}

	@Override
	protected boolean defaultBackground() {
		return true;
	}

	@Override
	protected int backgroundPadding() {
		return 0; // Rand ist schon in der Breite enthalten
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
		graphics.drawString(mc().font, text, 4, 3, textColor(), shadow());
	}
}
