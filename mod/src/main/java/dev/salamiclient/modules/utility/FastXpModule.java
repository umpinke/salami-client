package dev.salamiclient.modules.utility;

import dev.salamiclient.mixin.MinecraftAccessor;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.InteractUtil;
import net.minecraft.world.item.Items;

/**
 * Fast XP: Hältst du mit einer Erfahrungsflasche in der Haupthand die Benutzen-Taste,
 * wird in jedem Tick geworfen statt wie in Vanilla nur alle 4 Ticks.
 */
public final class FastXpModule extends Module {
	/** Hält Vanillas Dauer-Rechtsklick an, solange das Modul wirft. */
	private static final int SUPPRESS_RIGHT_CLICK_TICKS = 4;

	/** Ticks zwischen zwei Würfen (0 = jeder Tick). */
	private final NumberSetting delay = add(new NumberSetting("delay", 0, 0, 4, 1));

	private int cooldown;

	public FastXpModule() {
		super("fast_xp", Category.UTILITY);
	}

	@Override
	public void onTick() {
		if (cooldown > 0) {
			cooldown--;
		}
		if (mc().screen != null || !mc().options.keyUse.isDown() || !mc().player.getMainHandItem().is(Items.EXPERIENCE_BOTTLE)) {
			return;
		}
		((MinecraftAccessor) mc()).salami$setRightClickDelay(SUPPRESS_RIGHT_CLICK_TICKS);
		if (cooldown > 0) {
			return;
		}
		InteractUtil.use();
		cooldown = delay.intValue();
	}
}
