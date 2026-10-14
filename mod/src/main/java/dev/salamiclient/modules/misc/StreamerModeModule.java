package dev.salamiclient.modules.misc;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.TextSetting;
import dev.salamiclient.util.ComponentText;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Streamer Mode: dein Name wird in Chat, Tab-Liste und über deinem Kopf durch einen eigenen
 * ersetzt, optional verschwinden die Koordinaten aus dem Salami-HUD. Nur auf deinem Bildschirm –
 * gedacht für Streams und Aufnahmen.
 */
public final class StreamerModeModule extends Module {
	private static StreamerModeModule instance;

	private final TextSetting alias = add(new TextSetting("alias", "Salami", 16));
	private final BoolSetting hideCoords = add(new BoolSetting("hide_coords", true));

	public StreamerModeModule() {
		super("streamer_mode", Category.MISC);
		instance = this;
	}

	private static boolean on() {
		return instance != null && instance.isEnabled();
	}

	private static String ownName() {
		Minecraft mc = Minecraft.getInstance();
		return mc.getUser() != null ? mc.getUser().getName() : null;
	}

	/** Für Chat, Tab-Liste und Namensschild: eigenen Namen ersetzen. */
	public static Component hideName(Component text) {
		if (!on() || text == null) {
			return text;
		}
		String alias = instance.alias.get().isBlank() ? "Salami" : instance.alias.get();
		String name = ownName();
		if (name == null || name.equalsIgnoreCase(alias)) {
			return text;
		}
		// UUID mitgeben: Hover-Infos zu deinem Namen (show_entity) enthalten sie
		Minecraft mc = Minecraft.getInstance();
		String uuid = mc.getUser() != null && mc.getUser().getProfileId() != null ? mc.getUser().getProfileId().toString() : null;
		return ComponentText.replace(text, name, alias, uuid);
	}

	/** Für das Info-HUD: Koordinaten ausblenden. */
	public static boolean hidesCoords() {
		return on() && instance.hideCoords.isOn();
	}
}
