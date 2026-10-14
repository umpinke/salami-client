package dev.salamiclient.modules.misc;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** No Advancement: keine Fortschritts-Popups oben rechts, optional auch keine Rezept-Popups und Chat-Meldungen. */
public final class NoAdvancementModule extends Module {
	private static NoAdvancementModule instance;

	private final BoolSetting recipes = add(new BoolSetting("recipes", true));
	private final BoolSetting chat = add(new BoolSetting("chat", false));

	public NoAdvancementModule() {
		super("no_advancement", Category.MISC);
		instance = this;
	}

	private static boolean on() {
		return instance != null && instance.isEnabled();
	}

	/** Für {@code ToastManagerMixin}. */
	public static boolean blocks(Toast toast) {
		if (!on()) {
			return false;
		}
		return toast instanceof AdvancementToast || instance.recipes.isOn() && toast instanceof RecipeToast;
	}

	/** Für {@code ChatComponentMixin}: „X hat den Fortschritt … erzielt“ ausblenden. */
	public static boolean blocksChat(Component message) {
		return on() && instance.chat.isOn()
			&& message.getContents() instanceof TranslatableContents translatable
			&& translatable.getKey().startsWith("chat.type.advancement.");
	}
}
