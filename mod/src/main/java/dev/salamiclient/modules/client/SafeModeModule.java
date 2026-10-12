package dev.salamiclient.modules.client;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.StringListSetting;
import dev.salamiclient.util.Chat;
import net.minecraft.client.multiplayer.ServerData;

import java.util.List;
import java.util.Locale;

/**
 * Safe Mode: Solange aktiv, sind alle Utility-(Kampf-)Module aus und lassen sich
 * nicht einschalten.
 *
 * <p>Aktivierung:
 * <ul>
 *   <li>manuell in der ClickGUI,</li>
 *   <li>per <b>Easy Leave</b>-Taste (Standard: Ende) – ein Druck, alles Kampf-Zeug ist aus,</li>
 *   <li>automatisch beim Betreten eines Servers aus der Liste {@code servers}.</li>
 * </ul>
 * Ausschalten geht nur bewusst in der ClickGUI (Client → Safe Mode).
 */
public final class SafeModeModule extends Module {
	public enum Reason { MANUAL, EASY_LEAVE, SERVER }

	public final BoolSetting autoOnServers = add(new BoolSetting("auto_servers", true));
	/** Server, auf denen Utility-Module verboten sind (Suffix-Match, ohne Port). */
	public final StringListSetting servers = add(new StringListSetting("servers", List.of(
		"hypixel.net",
		"gommehd.net",
		"cubecraft.net",
		"minemen.club"
	)));

	public SafeModeModule() {
		super("safe_mode", Category.CLIENT);
	}

	@Override
	protected void onEnable() {
		SalamiClient.get().modules().disableCategory(Category.UTILITY);
	}

	/** Aktiviert Safe Mode und meldet, wie viele Kampfmodule ausgeschaltet wurden. */
	public void engage(Reason reason) {
		int running = 0;
		for (Module module : SalamiClient.get().modules().byCategory(Category.UTILITY)) {
			if (module.isEnabled()) {
				running++;
			}
		}
		SalamiClient.get().modules().setEnabled(this, true);
		switch (reason) {
			case EASY_LEAVE -> Chat.warn("salami.easy_leave.done", running);
			case SERVER -> Chat.warn("salami.safe_mode.server", running);
			case MANUAL -> Chat.info("salami.safe_mode.on");
		}
	}

	/** Beim Server-Beitritt: Safe Mode automatisch an, wenn der Server gelistet ist. */
	public void onJoin(ServerData server) {
		if (server == null || !autoOnServers.isOn() || isEnabled()) {
			return;
		}
		String host = hostOf(server.ip);
		for (String pattern : servers.get()) {
			if (host.equals(pattern) || host.endsWith("." + pattern)) {
				engage(Reason.SERVER);
				return;
			}
		}
	}

	/** "Play.Example.net:25565" -> "play.example.net" (IPv6 in Klammern bleibt unverändert). */
	static String hostOf(String address) {
		String host = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
		if (!host.startsWith("[")) {
			int colon = host.lastIndexOf(':');
			if (colon > 0 && host.indexOf(':') == colon) {
				host = host.substring(0, colon);
			}
		}
		return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
	}
}
