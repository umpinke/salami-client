package dev.salamiclient.modules.hud;

import dev.salamiclient.module.TextHudModule;
import net.minecraft.client.resources.language.I18n;

import java.util.Locale;

/** Uptime: wie lange du schon auf diesem Server bzw. in dieser Welt bist. */
public final class UptimeModule extends TextHudModule {
	private Object session;
	private long sessionStart;

	public UptimeModule() {
		super("uptime", 0.20, 0.17);
	}

	@Override
	protected String label() {
		return I18n.get("salami.module.uptime.label");
	}

	@Override
	protected String value() {
		// neue Verbindung = neue Zählung (die Verbindung ist pro Server/Welt ein neues Objekt)
		Object current = mc().getConnection();
		if (current != session) {
			session = current;
			sessionStart = System.currentTimeMillis();
		}
		long seconds = (System.currentTimeMillis() - sessionStart) / 1000;
		return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
	}
}
