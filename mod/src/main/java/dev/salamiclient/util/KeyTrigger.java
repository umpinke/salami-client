package dev.salamiclient.util;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.setting.KeybindSetting;
import net.minecraft.client.Minecraft;

/**
 * Auslöse-Taste eines Makros (Key Pearl, Cobweb Placer …), getrennt von der Taste, die
 * das Modul an- und ausschaltet. Meldet genau den Tick, in dem die Taste gedrückt wird –
 * nur ohne offenes Menü, damit Tippen im Chat nichts auslöst.
 */
public final class KeyTrigger {
	private final KeybindSetting key;
	private boolean wasDown;

	public KeyTrigger(KeybindSetting key) {
		this.key = key;
	}

	/** Einmal pro Tick aufrufen (auch wenn das Ergebnis gerade egal ist – sonst stimmt die Flanke nicht). */
	public boolean pressed() {
		Minecraft mc = Minecraft.getInstance();
		boolean down = key.isBound() && Keys.isDown(key.key());
		boolean pressed = down && !wasDown && mc.screen == null;
		wasDown = down;
		return pressed;
	}
}
