package dev.salamiclient.modules.hud;

import dev.salamiclient.module.TextHudModule;
import dev.salamiclient.setting.BoolSetting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Speedometer: deine Geschwindigkeit in Blöcken pro Sekunde (optional ohne Höhe). */
public final class SpeedometerModule extends TextHudModule {
	private final BoolSetting horizontal = add(new BoolSetting("horizontal", true));

	private Vec3 last;

	public SpeedometerModule() {
		super("speedometer", 0.20, 0.35);
	}

	@Override
	protected void onEnable() {
		last = null;
	}

	@Override
	protected String label() {
		return I18n.get("salami.module.speedometer.label");
	}

	@Override
	protected String value() {
		Vec3 now = mc().player.position();
		double blocksPerTick = 0;
		if (last != null) {
			double dx = now.x - last.x;
			double dy = horizontal.isOn() ? 0 : now.y - last.y;
			double dz = now.z - last.z;
			blocksPerTick = Math.sqrt(dx * dx + dy * dy + dz * dz);
		}
		last = now;
		return String.format(Locale.ROOT, "%.2f m/s", blocksPerTick * 20);
	}
}
