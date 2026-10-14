package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Health Indicators: Leben hinter dem Namen von Spielern (und optional Mobs mit Namen).
 * Die Lebensanzeige wird nur neu gebaut, wenn sich das Leben ändert (läuft pro Name und Frame).
 */
public final class HealthIndicatorsModule extends Module {
	private static HealthIndicatorsModule instance;

	private final BoolSetting mobs = add(new BoolSetting("mobs", false));
	private final BoolSetting hearts = add(new BoolSetting("hearts", true));

	/** Entity-ID → zuletzt gebaute Anzeige (Wert in Zehnteln + Einstellung → Text). */
	private record Cached(int tenths, boolean hearts, Component suffix) {}

	private final Map<Integer, Cached> cache = new HashMap<>();

	public HealthIndicatorsModule() {
		super("health_indicators", Category.VISUAL);
		instance = this;
	}

	@Override
	public void onTick() {
		// verschwundene Entities nicht ewig merken
		if (cache.size() > 512) {
			cache.clear();
		}
	}

	@Override
	protected void onDisable() {
		cache.clear();
	}

	/** Für {@code EntityRendererMixin}: Name um die Lebensanzeige erweitern. */
	public static Component decorate(Component name, Entity entity) {
		if (instance == null || !instance.isEnabled() || name == null || !(entity instanceof LivingEntity living)) {
			return name;
		}
		if (!(living instanceof Player) && !instance.mobs.isOn()) {
			return name;
		}
		return Component.empty().append(name).append(instance.suffix(living));
	}

	private Component suffix(LivingEntity living) {
		float health = living.getHealth() + living.getAbsorptionAmount();
		boolean inHearts = hearts.isOn();
		int tenths = Math.round((inHearts ? health / 2.0F : health) * 10);
		Cached cached = cache.get(living.getId());
		if (cached != null && cached.tenths() == tenths && cached.hearts() == inHearts) {
			return cached.suffix();
		}
		float max = Math.max(1.0F, living.getMaxHealth());
		ChatFormatting color = health > max * 0.6F ? ChatFormatting.GREEN : health > max * 0.3F ? ChatFormatting.GOLD : ChatFormatting.RED;
		String value = inHearts
			? " " + tenths / 10 + "." + tenths % 10 + " ❤"
			: " " + Math.round(health) + " HP";
		Component suffix = Component.literal(value).withStyle(color);
		cache.put(living.getId(), new Cached(tenths, inHearts, suffix));
		return suffix;
	}
}
