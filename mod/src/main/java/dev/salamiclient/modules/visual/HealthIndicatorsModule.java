package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

/** Health Indicators: Leben hinter dem Namen von Spielern (und optional Mobs mit Namen). */
public final class HealthIndicatorsModule extends Module {
	private static HealthIndicatorsModule instance;

	private final BoolSetting mobs = add(new BoolSetting("mobs", false));
	private final BoolSetting hearts = add(new BoolSetting("hearts", true));

	public HealthIndicatorsModule() {
		super("health_indicators", Category.VISUAL);
		instance = this;
	}

	/** Für {@code EntityRendererMixin}: Name um die Lebensanzeige erweitern. */
	public static Component decorate(Component name, Entity entity) {
		if (instance == null || !instance.isEnabled() || name == null || !(entity instanceof LivingEntity living)) {
			return name;
		}
		if (!(living instanceof Player) && !instance.mobs.isOn()) {
			return name;
		}
		float health = living.getHealth() + living.getAbsorptionAmount();
		float max = Math.max(1.0F, living.getMaxHealth());
		ChatFormatting color = health > max * 0.6F ? ChatFormatting.GREEN : health > max * 0.3F ? ChatFormatting.GOLD : ChatFormatting.RED;
		String value = instance.hearts.isOn()
			? String.format(Locale.ROOT, "%.1f ❤", health / 2.0F)
			: String.format(Locale.ROOT, "%.0f HP", health);
		return Component.empty().append(name).append(Component.literal(" " + value).withStyle(color));
	}
}
