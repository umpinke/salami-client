package dev.salamiclient.modules.hud;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.NumberFormat;

/** Scoreboard: rote Zahlen ausblenden, Größe und Hintergrund der Seitenleiste ändern oder sie ganz ausblenden. */
public final class ScoreboardModule extends Module {
	private static ScoreboardModule instance;

	private final BoolSetting hide = add(new BoolSetting("hide", false));
	private final BoolSetting hideNumbers = add(new BoolSetting("hide_numbers", true));
	private final NumberSetting size = add(new NumberSetting("size", 1.0, 0.5, 1.5, 0.05));
	private final NumberSetting background = add(new NumberSetting("background", 1.0, 0.0, 2.0, 0.1));

	public ScoreboardModule() {
		super("scoreboard", Category.HUD);
		instance = this;
	}

	private static boolean on() {
		return instance != null && instance.isEnabled();
	}

	/** Für {@code GuiMixin}. */
	public static boolean hides() {
		return on() && instance.hide.isOn();
	}

	public static NumberFormat numberFormat(NumberFormat vanilla) {
		return on() && instance.hideNumbers.isOn() ? BlankFormat.INSTANCE : vanilla;
	}

	/** Hintergrundfarbe mit angepasster Deckkraft (1.0 = Vanilla). */
	public static int background(int argb) {
		if (!on()) {
			return argb;
		}
		int alpha = (int) Math.min(255, Math.round((argb >>> 24) * instance.background.get()));
		return (alpha << 24) | (argb & 0xFFFFFF);
	}

	/** Größe um den rechten Rand (Mitte) – dort sitzt die Seitenleiste. */
	public static boolean pushScale(GuiGraphics graphics) {
		if (!on() || instance.size.get() == 1.0) {
			return false;
		}
		float s = instance.size.get().floatValue();
		float anchorX = graphics.guiWidth();
		float anchorY = graphics.guiHeight() / 2f;
		graphics.pose().pushMatrix();
		graphics.pose().translate(anchorX, anchorY);
		graphics.pose().scale(s, s);
		graphics.pose().translate(-anchorX, -anchorY);
		return true;
	}
}
