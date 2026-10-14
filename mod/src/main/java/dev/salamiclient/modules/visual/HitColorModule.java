package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.HurtOverlay;

/**
 * Hit Color: eigene Farbe, mit der getroffene Spieler und Mobs aufblinken.
 * Die Textur wird nur neu geschrieben, wenn sich Farbe oder Deckkraft ändern.
 */
public final class HitColorModule extends Module {
	private final ColorSetting color = add(new ColorSetting("color", 0xFFB23A3A));
	private final NumberSetting opacity = add(new NumberSetting("opacity", 0.7, 0.1, 1.0, 0.05));

	/** Zuletzt geschriebene Farbe; {@code null} = Vanilla. */
	private Integer applied;

	public HitColorModule() {
		super("hit_color", Category.VISUAL);
	}

	@Override
	public void onTick() {
		int wanted = (int) Math.round(opacity.get() * 255) << 24 | (color.argb() & 0xFFFFFF);
		if (applied == null || applied != wanted) {
			write(wanted);
			applied = wanted;
		}
	}

	@Override
	protected void onDisable() {
		if (applied != null) {
			write(HurtOverlay.VANILLA_COLOR);
			applied = null;
		}
	}

	private void write(int argb) {
		if (mc().gameRenderer != null && mc().gameRenderer.overlayTexture() instanceof HurtOverlay overlay) {
			overlay.salami$setHurtColor(argb);
		}
	}
}
