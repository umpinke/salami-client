package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * NameTags: Namensschilder lesbarer machen – Größe (optional mit der Entfernung wachsend),
 * Hintergrund, eigener Name in der Third-Person-Ansicht und Leben/Rüstung hinter dem Namen
 * (mit den Herz- und Rüstungs-Symbolen aus dem Spiel). Nur Anzeige, nur bei dir.
 */
public final class NameTagsModule extends Module {
	private static final Component HEART = Component.object(new AtlasSprite(AtlasIds.GUI, Identifier.withDefaultNamespace("hud/heart/full")));
	private static final Component ARMOR = Component.object(new AtlasSprite(AtlasIds.GUI, Identifier.withDefaultNamespace("hud/armor_full")));

	private static NameTagsModule instance;

	private final BoolSetting showOwn = add(new BoolSetting("show_own", true));
	private final BoolSetting health = add(new BoolSetting("show_health", true));
	private final BoolSetting armor = add(new BoolSetting("show_armor", true));
	private final BoolSetting mobs = add(new BoolSetting("mobs", false));
	private final NumberSetting scale = add(new NumberSetting("scale", 1.0, 0.5, 3.0, 0.1));
	private final BoolSetting distanceScale = add(new BoolSetting("distance_scale", false));
	private final NumberSetting background = add(new NumberSetting("background", 0.25, 0.0, 1.0, 0.05));

	/** Entity-ID → zuletzt gebaute Anzeige (wird nur neu gebaut, wenn sich Leben/Rüstung ändern). */
	private record Cached(int tenths, int armorValue, boolean withHealth, boolean withArmor, Component suffix) {}

	private final Map<Integer, Cached> cache = new HashMap<>();

	public NameTagsModule() {
		super("nametags", Category.VISUAL);
		instance = this;
	}

	@Override
	public void onTick() {
		if (cache.size() > 512) {
			cache.clear(); // verschwundene Entities nicht ewig merken
		}
	}

	@Override
	protected void onDisable() {
		cache.clear();
	}

	/** Für {@code LivingEntityRendererMixin}: eigener Name nur, wenn die Kamera nicht in dir steckt. */
	public static boolean showOwn(LivingEntity entity) {
		if (instance == null || !instance.isEnabled() || !instance.showOwn.isOn()) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		return entity == mc.player
			&& Minecraft.renderNames()
			&& !mc.options.getCameraType().isFirstPerson()
			&& !entity.isInvisible();
	}

	/** Für {@code NameTagStorageMixin}. */
	public static float backgroundOpacity(float vanilla) {
		return instance != null && instance.isEnabled() ? instance.background.get().floatValue() : vanilla;
	}

	/**
	 * Für {@code NameTagStorageMixin}: Größenfaktor eines Namensschilds. Mit „Mit Entfernung größer“
	 * bleibt es ab 8 Blöcken etwa gleich groß auf dem Bildschirm (höchstens 4×).
	 */
	public static float scale(double distanceSq) {
		NameTagsModule module = instance;
		if (module == null || !module.isEnabled()) {
			return 1F;
		}
		float factor = module.scale.get().floatValue();
		if (module.distanceScale.isOn()) {
			factor *= (float) Math.max(1.0, Math.min(4.0, Math.sqrt(distanceSq) / 8.0));
		}
		return factor;
	}

	/** Für {@code EntityRendererMixin}: Leben und Rüstung hinter den Namen setzen. */
	public static Component decorate(Component name, Entity entity) {
		NameTagsModule module = instance;
		if (module == null || !module.isEnabled() || name == null || !(entity instanceof LivingEntity living)) {
			return name;
		}
		if (!(living instanceof Player) && !module.mobs.isOn()) {
			return name;
		}
		// Health Indicators zeigt das Leben schon – nicht doppelt
		boolean withHealth = module.health.isOn() && !HealthIndicatorsModule.active();
		boolean withArmor = module.armor.isOn() && living.getArmorValue() > 0;
		if (!withHealth && !withArmor) {
			return name;
		}
		return Component.empty().append(name).append(module.suffix(living, withHealth, withArmor));
	}

	private Component suffix(LivingEntity living, boolean withHealth, boolean withArmor) {
		float hp = living.getHealth() + living.getAbsorptionAmount();
		int tenths = Math.round(hp * 5); // Herzen in Zehnteln (2 Leben = 1 Herz)
		int armorValue = living.getArmorValue();
		Cached cached = cache.get(living.getId());
		if (cached != null && cached.tenths() == tenths && cached.armorValue() == armorValue
			&& cached.withHealth() == withHealth && cached.withArmor() == withArmor) {
			return cached.suffix();
		}
		MutableComponent suffix = Component.empty();
		if (withHealth) {
			float max = Math.max(1F, living.getMaxHealth());
			ChatFormatting color = hp > max * 0.6F ? ChatFormatting.GREEN : hp > max * 0.3F ? ChatFormatting.GOLD : ChatFormatting.RED;
			suffix.append(" ").append(HEART).append(Component.literal(" " + tenths / 10 + "." + tenths % 10).withStyle(color));
		}
		if (withArmor) {
			suffix.append(" ").append(ARMOR).append(Component.literal(" " + armorValue).withStyle(ChatFormatting.AQUA));
		}
		cache.put(living.getId(), new Cached(tenths, armorValue, withHealth, withArmor, suffix));
		return suffix;
	}
}
