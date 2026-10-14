package dev.salamiclient.modules.hud;

import dev.salamiclient.module.TextHudModule;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.client.resources.language.I18n;

/**
 * Jump Reset: zeigt nach einem Treffer, wie gut dein Sprung-Timing war (Ticks zwischen Treffer
 * und Absprung). Reine Anzeige zum Üben – springen musst du selbst.
 */
public final class JumpResetModule extends TextHudModule {
	private final NumberSetting showSeconds = add(new NumberSetting("show_seconds", 3, 1, 10, 0.5));

	private int lastHurtTime;
	private boolean wasOnGround = true;
	private long hurtTick = -1;
	private int delay = Integer.MIN_VALUE;
	private long shownUntil;
	private long tick;

	public JumpResetModule() {
		super("jump_reset", 0.47, 0.66);
	}

	@Override
	public void onTick() {
		tick++;
		var player = mc().player;
		// neuer Treffer: hurtTime springt hoch
		if (player.hurtTime > lastHurtTime && player.hurtTime == player.hurtDuration) {
			hurtTick = tick;
		}
		lastHurtTime = player.hurtTime;
		boolean jumped = wasOnGround && !player.onGround() && player.getDeltaMovement().y > 0.2;
		wasOnGround = player.onGround();
		if (jumped && hurtTick >= 0 && tick - hurtTick <= 10) {
			delay = (int) (tick - hurtTick);
			shownUntil = System.currentTimeMillis() + (long) (showSeconds.get() * 1000);
			hurtTick = -1;
		}
		super.onTick();
	}

	@Override
	protected String label() {
		return I18n.get("salami.module.jump_reset.label");
	}

	@Override
	protected String value() {
		if (delay == Integer.MIN_VALUE || System.currentTimeMillis() > shownUntil) {
			return null;
		}
		return delay <= 1 ? I18n.get("salami.module.jump_reset.perfect", delay) : I18n.get("salami.module.jump_reset.late", delay);
	}
}
