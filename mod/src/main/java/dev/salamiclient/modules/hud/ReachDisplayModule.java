package dev.salamiclient.modules.hud;

import dev.salamiclient.module.AttackListener;
import dev.salamiclient.module.TextHudModule;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Reach Display: Abstand deines letzten Treffers (Augen bis Hitbox des Ziels) in Blöcken. */
public final class ReachDisplayModule extends TextHudModule implements AttackListener {
	private final NumberSetting showSeconds = add(new NumberSetting("show_seconds", 3, 1, 10, 0.5));

	private double lastReach = -1;
	private long lastHitMillis;

	public ReachDisplayModule() {
		super("reach_display", 0.005, 0.25);
	}

	@Override
	public void beforeAttack(Entity target) {
		var player = mc().player;
		if (player == null) {
			return;
		}
		Vec3 eyes = player.getEyePosition();
		lastReach = Math.sqrt(target.getBoundingBox().distanceToSqr(eyes));
		lastHitMillis = System.currentTimeMillis();
	}

	/** Letzter gemessener Abstand ({@code -1} = noch kein Treffer). */
	public double lastReach() {
		return lastReach;
	}

	@Override
	protected String label() {
		return I18n.get("salami.module.reach_display.short");
	}

	@Override
	protected String value() {
		boolean recent = lastReach >= 0 && System.currentTimeMillis() - lastHitMillis <= showSeconds.get() * 1000;
		return recent ? String.format(Locale.ROOT, "%.2f", lastReach) : "-";
	}
}
