package dev.salamiclient.modules.misc;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Confirm Disconnect: „Verbindung trennen“ im Pausenmenü fragt erst nach – kein versehentliches Verlassen mehr. */
public final class ConfirmDisconnectModule extends Module {
	private static ConfirmDisconnectModule instance;

	public ConfirmDisconnectModule() {
		super("confirm_disconnect", Category.MISC, true);
		instance = this;
	}

	/** Für {@code PauseScreenMixin}: Klick auf „Trennen“ öffnet erst eine Rückfrage. */
	public static Button.OnPress wrap(Screen pauseScreen, Component label, Button.OnPress original) {
		boolean isDisconnect = label == CommonComponents.disconnectButtonLabel(true) || label == CommonComponents.disconnectButtonLabel(false);
		if (!isDisconnect) {
			return original;
		}
		return button -> {
			if (instance == null || !instance.isEnabled()) {
				original.onPress(button);
				return;
			}
			Minecraft mc = Minecraft.getInstance();
			mc.setScreen(new ConfirmScreen(
				confirmed -> {
					if (confirmed) {
						original.onPress(button);
					} else {
						mc.setScreen(pauseScreen);
					}
				},
				Component.translatable("salami.module.confirm_disconnect.title"),
				Component.translatable("salami.module.confirm_disconnect.question"),
				label,
				CommonComponents.GUI_CANCEL));
		};
	}
}
