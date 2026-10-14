package dev.salamiclient.modules.hud;

import dev.salamiclient.module.TextHudModule;
import net.minecraft.client.resources.language.I18n;

import java.util.Locale;

/**
 * TPS: geschätzte Server-Ticks pro Sekunde. Der Server schickt etwa jede Sekunde seine Spielzeit;
 * aus Spielzeit-Zuwachs geteilt durch echte Zeit ergibt sich die TPS (geglättet, max. 20).
 */
public final class TpsModule extends TextHudModule {
	private static long lastGameTime = -1;
	private static long lastPacketNanos;
	private static double tps = -1;

	public TpsModule() {
		super("tps", 0.20, 0.29);
	}

	/** Für {@code ClientPacketListenerMixin}: bei jedem Zeit-Paket des Servers (Hauptthread). */
	public static void onServerTime(long gameTime) {
		long now = System.nanoTime();
		if (lastGameTime >= 0 && gameTime > lastGameTime) {
			double seconds = (now - lastPacketNanos) / 1_000_000_000.0;
			if (seconds > 0.2) {
				double sample = Math.min(20.0, (gameTime - lastGameTime) / seconds);
				tps = tps < 0 ? sample : tps * 0.6 + sample * 0.4;
			}
		}
		lastGameTime = gameTime;
		lastPacketNanos = now;
	}

	/** Neue Verbindung: alte Messung verwerfen. */
	public static void reset() {
		lastGameTime = -1;
		tps = -1;
	}

	@Override
	protected String label() {
		return "TPS";
	}

	@Override
	protected String value() {
		return tps < 0 ? I18n.get("salami.module.tps.measuring") : String.format(Locale.ROOT, "%.1f", tps);
	}
}
