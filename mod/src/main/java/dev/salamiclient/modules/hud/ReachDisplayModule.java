package dev.salamiclient.modules.hud;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.AttackListener;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Reach Display: Abstand deines letzten Treffers (Augen bis Hitbox des Ziels) in Blöcken. */
public final class ReachDisplayModule extends HudModule implements AttackListener {
	private final NumberSetting showSeconds = add(new NumberSetting("show_seconds", 3, 1, 10, 0.5));

	private double lastReach = -1;
	private long lastHitMillis;

	public ReachDisplayModule() {
		super("reach_display", Category.HUD, false, 0.005, 0.25);
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

	private boolean recent() {
		return lastReach >= 0 && System.currentTimeMillis() - lastHitMillis <= showSeconds.get() * 1000;
	}

	private String text() {
		String value = recent() ? String.format(Locale.ROOT, "%.2f", lastReach) : "-";
		return I18n.get("salami.module.reach_display.label", value);
	}

	@Override
	public int width() {
		return mc().font.width(text()) + 8;
	}

	@Override
	public int height() {
		return 14;
	}

	@Override
	protected void render(GuiGraphics graphics, DeltaTracker delta) {
		graphics.fill(0, 0, width(), height(), 0x80000000);
		graphics.drawString(mc().font, text(), 4, 3, SalamiClient.get().theme().text(), true);
	}
}
