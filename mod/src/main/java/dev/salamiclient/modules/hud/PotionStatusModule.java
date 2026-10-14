package dev.salamiclient.modules.hud;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.theme.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Potion Status: aktive Effekte mit Symbol, Stufe und Restzeit. Läuft ein Effekt aus, blinkt er. */
public final class PotionStatusModule extends HudModule {
	private static final int ROW = 20;

	private final BoolSetting icons = add(new BoolSetting("icons", true));
	private final BoolSetting hideVanilla = add(new BoolSetting("hide_vanilla", false));

	private static PotionStatusModule instance;

	public PotionStatusModule() {
		super("potion_status", Category.HUD, false, 0.005, 0.38);
		instance = this;
	}

	/** Für den Vanilla-Effekt-Block oben rechts (siehe {@code GuiMixin}). */
	public static boolean hidesVanillaEffects() {
		return instance != null && instance.isEnabled() && instance.hideVanilla.isOn();
	}

	private List<MobEffectInstance> effects() {
		var player = mc().player;
		if (player == null) {
			return List.of();
		}
		List<MobEffectInstance> list = new ArrayList<>();
		for (MobEffectInstance effect : player.getActiveEffects()) {
			if (effect.showIcon()) {
				list.add(effect);
			}
		}
		list.sort(Comparator.comparingInt((MobEffectInstance e) -> e.isInfiniteDuration() ? Integer.MAX_VALUE : e.getDuration()).reversed());
		return list;
	}

	private Component name(MobEffectInstance effect) {
		Component name = effect.getEffect().value().getDisplayName();
		if (effect.getAmplifier() <= 0) {
			return name;
		}
		return Component.empty().append(name).append(" ").append(Component.translatable("enchantment.level." + (effect.getAmplifier() + 1)));
	}

	private Component duration(MobEffectInstance effect) {
		float tickRate = mc().level != null ? mc().level.tickRateManager().tickrate() : 20.0F;
		return MobEffectUtil.formatDuration(effect, 1.0F, tickRate);
	}

	@Override
	public boolean hasContent() {
		return !effects().isEmpty();
	}

	@Override
	public int width() {
		int text = 60;
		for (MobEffectInstance effect : effects()) {
			text = Math.max(text, Math.max(mc().font.width(name(effect)), mc().font.width(duration(effect))));
		}
		return (icons.isOn() ? 22 : 2) + text + 4;
	}

	@Override
	public int height() {
		return Math.max(ROW, effects().size() * ROW);
	}

	@Override
	protected void render(GuiGraphics g, DeltaTracker delta) {
		Theme theme = SalamiClient.get().theme();
		int y = 0;
		for (MobEffectInstance effect : effects()) {
			boolean ending = effect.endsWithin(200);
			if (ending && (mc().gui.getGuiTicks() / 5) % 2 == 0) {
				y += ROW;
				continue; // blinken in den letzten 10 Sekunden
			}
			int x = 2;
			if (icons.isOn()) {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(effect.getEffect()), 0, y + 1, 18, 18);
				x = 22;
			}
			g.drawString(mc().font, name(effect), x, y + 1, theme.text(), true);
			g.drawString(mc().font, duration(effect), x, y + 10, ending ? theme.warning() : theme.textDim(), true);
			y += ROW;
		}
	}
}
