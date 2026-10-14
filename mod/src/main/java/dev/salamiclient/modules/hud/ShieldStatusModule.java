package dev.salamiclient.modules.hud;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.theme.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/**
 * Shield Status: zeigt, ob dein Schild gerade durch eine Axt deaktiviert ist und wie lange noch.
 */
public final class ShieldStatusModule extends HudModule {
	private final BoolSetting onlyWhenHolding = add(new BoolSetting("only_holding", true));

	public ShieldStatusModule() {
		super("shield_status", Category.HUD, false, 0.53, 0.60);
	}

	private ItemStack shield() {
		var player = mc().player;
		if (player == null) {
			return ItemStack.EMPTY;
		}
		if (player.getOffhandItem().is(Items.SHIELD)) {
			return player.getOffhandItem();
		}
		if (player.getMainHandItem().is(Items.SHIELD)) {
			return player.getMainHandItem();
		}
		return onlyWhenHolding.isOn() ? ItemStack.EMPTY : new ItemStack(Items.SHIELD);
	}

	private float cooldown(ItemStack shield, float partialTick) {
		return mc().player.getCooldowns().getCooldownPercent(shield, partialTick);
	}

	@Override
	public boolean hasContent() {
		return !shield().isEmpty();
	}

	@Override
	public int width() {
		return 22 + mc().font.width(I18n.get("salami.module.shield_status.ready")) + 4;
	}

	@Override
	public int height() {
		return 18;
	}

	@Override
	protected void render(GuiGraphics g, DeltaTracker delta) {
		ItemStack shield = shield();
		Theme theme = SalamiClient.get().theme();
		float progress = cooldown(shield, delta.getGameTimeDeltaPartialTick(true));
		g.fill(0, 0, width(), height(), 0x80000000);
		g.renderItem(shield, 1, 1);
		String text;
		int color;
		if (progress > 0) {
			// Axt-Treffer deaktivieren Schilde für 5 Sekunden (100 Ticks)
			text = String.format(Locale.ROOT, "%.1fs", progress * 5.0F);
			color = theme.warning();
			int barWidth = width() - 22;
			g.fill(20, height() - 3, 20 + Math.round(barWidth * progress), height() - 1, theme.warning());
		} else {
			text = I18n.get("salami.module.shield_status.ready");
			color = theme.accent();
		}
		g.drawString(mc().font, text, 20, 5, color, true);
	}
}
