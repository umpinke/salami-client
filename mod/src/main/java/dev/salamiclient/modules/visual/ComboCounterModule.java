package dev.salamiclient.modules.visual;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.AttackListener;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.theme.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Combo Counter: zählt Treffer in Folge. Ein Treffer zählt, wenn das Ziel gerade
 * verwundbar war (keine Unverwundbarkeits-Ticks). Die Combo endet, wenn du selbst
 * getroffen wirst oder eine Weile nicht triffst.
 */
public final class ComboCounterModule extends HudModule implements AttackListener {
	private final NumberSetting timeout = add(new NumberSetting("timeout", 3, 1, 10, 0.5));

	private int combo;
	private int best;
	private long lastHitMillis;

	public ComboCounterModule() {
		super("combo_counter", Category.VISUAL, false, 0.47, 0.60);
	}

	@Override
	protected void onEnable() {
		combo = 0;
	}

	@Override
	public void beforeAttack(Entity target) {
		if (target instanceof LivingEntity living && living.isAlive() && living.hurtTime == 0) {
			combo++;
			best = Math.max(best, combo);
			lastHitMillis = System.currentTimeMillis();
		}
	}

	@Override
	public void onTick() {
		boolean gotHit = mc().player.hurtTime > 0;
		boolean expired = System.currentTimeMillis() - lastHitMillis > timeout.get() * 1000;
		if (combo > 0 && (gotHit || expired)) {
			combo = 0;
		}
	}

	public int combo() {
		return combo;
	}

	@Override
	public boolean hasContent() {
		return combo > 0;
	}

	private String text() {
		return I18n.get("salami.module.combo_counter.label", Math.max(combo, 0));
	}

	@Override
	public int width() {
		return mc().font.width(text()) + 2;
	}

	@Override
	public int height() {
		return 10;
	}

	@Override
	protected void render(GuiGraphics graphics, DeltaTracker delta) {
		Theme theme = SalamiClient.get().theme();
		int color = combo >= 5 ? theme.warning() : theme.accent();
		graphics.drawString(mc().font, text(), 0, 0, color, true);
	}
}
