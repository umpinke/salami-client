package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Shiny Pots: Tränke glänzen wieder wie früher (Verzauberungs-Glanz). Nur Optik. */
public final class ShinyPotsModule extends Module {
	private static ShinyPotsModule instance;

	public ShinyPotsModule() {
		super("shiny_pots", Category.VISUAL);
		instance = this;
	}

	/** Für {@code ItemStackMixin}. */
	public static boolean shines(ItemStack stack) {
		return instance != null && instance.isEnabled()
			&& (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION));
	}
}
