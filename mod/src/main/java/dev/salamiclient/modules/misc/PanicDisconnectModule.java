package dev.salamiclient.modules.misc;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.KeybindSetting;
import dev.salamiclient.util.KeyTrigger;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Panic Disconnect: Auf Tastendruck sofort vom Server trennen – wie „Verbindung trennen“
 * im Pausenmenü, nur ohne Umweg über das Menü und ohne Rückfrage.
 */
public final class PanicDisconnectModule extends Module {
	private final KeybindSetting triggerKey = add(new KeybindSetting("trigger_key", KeybindSetting.NONE));

	private final KeyTrigger trigger = new KeyTrigger(triggerKey);

	public PanicDisconnectModule() {
		super("panic_disconnect", Category.MISC);
	}

	@Override
	public void onTick() {
		if (trigger.pressed()) {
			// Erst nach dem Tick trennen: die übrigen Module brauchen in diesem Tick noch Spieler und Welt
			mc().schedule(() -> {
				if (mc().level != null) {
					mc().disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
				}
			});
		}
	}
}
