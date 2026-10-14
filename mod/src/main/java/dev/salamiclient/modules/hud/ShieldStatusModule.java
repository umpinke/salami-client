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
	/** Für die Anzeige, wenn „nur mit Schild in der Hand“ aus ist. */
	private static final ItemStack SHIELD = new ItemStack(Items.SHIELD);

	private final BoolSetting onlyWhenHolding = add(new BoolSetting("only_holding", true));

	private ItemStack shield = ItemStack.EMPTY;
	private String ready = "";
	private int width = 60;

	public ShieldStatusModule() {
		super("shield_status", Category.HUD, false, 0.53, 0.60);
	}

	@Override
	public void onTick() {
		var player = mc().player;
		if (player.getOffhandItem().is(Items.SHIELD)) {
			shield = player.getOffhandItem();
		} else if (player.getMainHandItem().is(Items.SHIELD)) {
			shield = player.getMainHandItem();
		} else {
			shield = onlyWhenHolding.isOn() ? ItemStack.EMPTY : SHIELD;
		}
		ready = I18n.get("salami.module.shield_status.ready");
		width = 22 + mc().font.width(ready) + 4;
	}

	@Override
	public boolean hasContent() {
		return !shield.isEmpty();
	}

	@Override
	public int width() {
		return width;
	}

	@Override
	public int height() {
		return 18;
	}

	@Override
	protected void render(GuiGraphics g, DeltaTracker delta) {
		Theme theme = SalamiClient.get().theme();
		float progress = mc().player.getCooldowns().getCooldownPercent(shield, delta.getGameTimeDeltaPartialTick(true));
		g.fill(0, 0, width, 18, 0x80000000);
		g.renderItem(shield, 1, 1);
		if (progress > 0) {
			// Axt-Treffer deaktivieren Schilde für 5 Sekunden (100 Ticks)
			String text = String.format(Locale.ROOT, "%.1fs", progress * 5.0F);
			int barWidth = width - 22;
			g.fill(20, 15, 20 + Math.round(barWidth * progress), 17, theme.warning());
			g.drawString(mc().font, text, 20, 5, theme.warning(), true);
		} else {
			g.drawString(mc().font, ready, 20, 5, theme.accent(), true);
		}
	}
}
