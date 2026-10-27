package dev.salamiclient.modules.hud;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.render.GuiDraw;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.theme.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Potion Status: aktive Effekte mit Symbol, Stufe und Restzeit. Läuft ein Effekt aus, blinkt er.
 * Zeilen werden einmal pro Tick gebaut; das Zeichnen pro Frame liest nur noch den Zwischenspeicher.
 */
public final class PotionStatusModule extends HudModule {
	private static final int ROW = 20;

	private final BoolSetting icons = add(new BoolSetting("icons", true));
	private final BoolSetting hideVanilla = add(new BoolSetting("hide_vanilla", false));

	private static PotionStatusModule instance;

	private record Row(Identifier sprite, Component name, Component duration, boolean ending) {}

	private List<Row> rows = List.of();
	private int textWidth = 60;

	public PotionStatusModule() {
		super("potion_status", Category.HUD, false, 0.005, 0.38);
		instance = this;
	}

	/** Für den Vanilla-Effekt-Block oben rechts (siehe {@code GuiMixin}). */
	public static boolean hidesVanillaEffects() {
		return instance != null && instance.isEnabled() && instance.hideVanilla.isOn();
	}

	@Override
	public void onTick() {
		var player = mc().player;
		List<MobEffectInstance> effects = new ArrayList<>();
		for (MobEffectInstance effect : player.getActiveEffects()) {
			if (effect.showIcon()) {
				effects.add(effect);
			}
		}
		effects.sort(Comparator.comparingInt((MobEffectInstance e) -> e.isInfiniteDuration() ? Integer.MAX_VALUE : e.getDuration()).reversed());
		float tickRate = mc().level.tickRateManager().tickrate();
		List<Row> built = new ArrayList<>(effects.size());
		int width = 60;
		for (MobEffectInstance effect : effects) {
			Component name = name(effect);
			Component duration = MobEffectUtil.formatDuration(effect, 1.0F, tickRate);
			width = Math.max(width, Math.max(mc().font.width(name), mc().font.width(duration)));
			built.add(new Row(Gui.getMobEffectSprite(effect.getEffect()), name, duration, effect.endsWithin(200)));
		}
		rows = built;
		textWidth = width;
	}

	private static Component name(MobEffectInstance effect) {
		Component name = effect.getEffect().value().getDisplayName();
		if (effect.getAmplifier() <= 0) {
			return name;
		}
		return Component.empty().append(name).append(" ").append(Component.translatable("enchantment.level." + (effect.getAmplifier() + 1)));
	}

	@Override
	public boolean hasContent() {
		return !rows.isEmpty();
	}

	@Override
	public int width() {
		return (icons.isOn() ? 22 : 2) + textWidth + 4;
	}

	@Override
	public int height() {
		return Math.max(ROW, rows.size() * ROW);
	}

	@Override
	protected void render(GuiGraphics g, DeltaTracker delta) {
		Theme theme = SalamiClient.get().theme();
		boolean blinkOff = (mc().gui.getGuiTicks() / 5) % 2 == 0;
		int y = 0;
		for (Row row : rows) {
			if (row.ending() && blinkOff) {
				y += ROW;
				continue; // blinken in den letzten 10 Sekunden
			}
			int x = 2;
			if (icons.isOn()) {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, row.sprite(), 0, y + 1, 18, 18);
				x = 22;
			}
			GuiDraw.text(g, mc().font, row.name(), x, y + 1, textColor(), shadow());
			GuiDraw.text(g, mc().font, row.duration(), x, y + 10, row.ending() ? theme.warning() : dimColor(), shadow());
			y += ROW;
		}
	}
}
