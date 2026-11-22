package dev.salamiclient.modules.misc;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.KeybindSetting;
import dev.salamiclient.setting.TextSetting;
import dev.salamiclient.util.Keys;
import dev.salamiclient.util.UserInput;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * AutoText: bis zu fünf Tasten, die eine eigene Chat-Nachricht oder einen Befehl („/…“) senden –
 * z. B. „gg“. Pro Taste höchstens einmal pro Sekunde, damit kein Server wegen Spam kickt.
 *
 * <p>Weil die Tasten Nachrichten und Befehle im Namen des Spielers senden, übernimmt der Config-Import
 * ({@code ConfigManager#importFrom}) dieses Modul nie – eine fremde Config könnte sonst z. B. W mit „/pay …“ belegen.
 */
public final class AutoTextModule extends Module {
	public static final String ID = "auto_text";
	private static final int SLOTS = 5;
	private static final long COOLDOWN_MILLIS = 1000;

	private final KeybindSetting[] keys = new KeybindSetting[SLOTS];
	private final TextSetting[] texts = new TextSetting[SLOTS];
	private final boolean[] wasDown = new boolean[SLOTS];
	private final long[] lastSent = new long[SLOTS];

	public AutoTextModule() {
		super(ID, Category.MISC);
		String[] defaults = {"gg", "Gutes Spiel!", "", "", ""};
		for (int i = 0; i < SLOTS; i++) {
			keys[i] = add(new KeybindSetting("key_" + (i + 1), KeybindSetting.NONE));
			texts[i] = add(new TextSetting("text_" + (i + 1), defaults[i], 256));
		}
	}

	@Override
	public void onTick() {
		var mc = mc();
		ClientPacketListener connection = mc.getConnection();
		for (int i = 0; i < SLOTS; i++) {
			// Zustand immer merken – sonst zählt eine beim Schließen eines Menüs gehaltene Taste als neuer Druck
			boolean down = keys[i].isBound() && Keys.isDown(keys[i].key());
			if (down && !wasDown[i] && mc.screen == null && connection != null) {
				send(connection, i);
			}
			wasDown[i] = down;
		}
	}

	private void send(ClientPacketListener connection, int slot) {
		String text = texts[slot].get().trim();
		long now = System.currentTimeMillis();
		if (text.isEmpty() || now - lastSent[slot] < COOLDOWN_MILLIS) {
			return;
		}
		lastSent[slot] = now;
		// Eigene Taste mit eigenem Text (importierte Configs bringen keine AutoText-Einträge mit, siehe ConfigManager):
		// zählt wie getippt, z. B. für /salami msg
		UserInput.run(() -> {
			if (text.startsWith("/")) {
				connection.sendCommand(text.substring(1));
			} else {
				connection.sendChat(text);
			}
		});
	}
}
