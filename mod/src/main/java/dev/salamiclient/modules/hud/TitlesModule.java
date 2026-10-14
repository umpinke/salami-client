package dev.salamiclient.modules.hud;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;

/** Titles: große Titel kleiner oder ausblenden, die Actionbar (Text über der Hotbar) verschieben oder ausblenden. */
public final class TitlesModule extends Module {
	private static TitlesModule instance;

	private final BoolSetting hideTitles = add(new BoolSetting("hide_titles", false));
	private final NumberSetting titleSize = add(new NumberSetting("title_size", 0.6, 0.2, 1.5, 0.05));
	private final BoolSetting hideActionBar = add(new BoolSetting("hide_actionbar", false));
	private final NumberSetting actionBarUp = add(new NumberSetting("actionbar_up", 0, -20, 100, 2));

	public TitlesModule() {
		super("titles", Category.HUD);
		instance = this;
	}

	private static boolean on() {
		return instance != null && instance.isEnabled();
	}

	/** Für {@code GuiMixin}. */
	public static boolean hidesTitles() {
		return on() && instance.hideTitles.isOn();
	}

	public static boolean hidesActionBar() {
		return on() && instance.hideActionBar.isOn();
	}

	/** Titel um die Bildmitte skalieren. */
	public static boolean pushTitleScale(GuiGraphics graphics) {
		if (!on() || instance.titleSize.get() == 1.0) {
			return false;
		}
		float s = instance.titleSize.get().floatValue();
		float cx = graphics.guiWidth() / 2f;
		float cy = graphics.guiHeight() / 2f;
		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, cy);
		graphics.pose().scale(s, s);
		graphics.pose().translate(-cx, -cy);
		return true;
	}

	/** Actionbar nach oben schieben (negative Werte: nach unten). */
	public static boolean pushActionBarOffset(GuiGraphics graphics) {
		if (!on() || instance.actionBarUp.get() == 0) {
			return false;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(0, -instance.actionBarUp.get().floatValue());
		return true;
	}
}
