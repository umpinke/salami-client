package dev.salamiclient.modules.visual;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Partikel: blendet Partikel nach Quelle aus – z. B. Explosionen beim Crystal-PvP,
 * Krit-Funken oder die Totem-Wolke. Gefiltert wird beim Erzeugen
 * (siehe {@code ParticleEngineMixin}, {@code ClientLevelMixin}).
 */
public final class ParticlesModule extends Module {
	/** Partikel-Quellen; der Name ist zugleich die Setting-ID. */
	public enum Kind {
		EXPLOSIONS(true), HITS(false), TOTEM(false), BLOCK_BREAK(false),
		POTIONS(false), FIRE_SMOKE(false), WEATHER(false), OTHER(false);

		final boolean hiddenByDefault;

		Kind(boolean hiddenByDefault) {
			this.hiddenByDefault = hiddenByDefault;
		}
	}

	private static final Map<ParticleType<?>, Kind> KINDS = new IdentityHashMap<>();
	private static ParticlesModule instance;

	static {
		kind(Kind.EXPLOSIONS, ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, ParticleTypes.GUST,
			ParticleTypes.SMALL_GUST, ParticleTypes.GUST_EMITTER_LARGE, ParticleTypes.GUST_EMITTER_SMALL, ParticleTypes.FLASH);
		kind(Kind.HITS, ParticleTypes.CRIT, ParticleTypes.ENCHANTED_HIT, ParticleTypes.SWEEP_ATTACK, ParticleTypes.DAMAGE_INDICATOR);
		kind(Kind.TOTEM, ParticleTypes.TOTEM_OF_UNDYING);
		kind(Kind.BLOCK_BREAK, ParticleTypes.BLOCK, ParticleTypes.BLOCK_CRUMBLE, ParticleTypes.DUST_PILLAR);
		kind(Kind.POTIONS, ParticleTypes.EFFECT, ParticleTypes.INSTANT_EFFECT, ParticleTypes.ENTITY_EFFECT, ParticleTypes.WITCH);
		kind(Kind.FIRE_SMOKE, ParticleTypes.FLAME, ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.COPPER_FIRE_FLAME,
			ParticleTypes.SMALL_FLAME, ParticleTypes.SMOKE, ParticleTypes.LARGE_SMOKE, ParticleTypes.WHITE_SMOKE,
			ParticleTypes.CAMPFIRE_COSY_SMOKE, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, ParticleTypes.LAVA, ParticleTypes.POOF);
		kind(Kind.WEATHER, ParticleTypes.RAIN, ParticleTypes.SPLASH, ParticleTypes.DRIPPING_WATER, ParticleTypes.FALLING_WATER);
	}

	private static void kind(Kind kind, ParticleType<?>... types) {
		for (ParticleType<?> type : types) {
			KINDS.put(type, kind);
		}
	}

	private final Map<Kind, BoolSetting> hidden = new EnumMap<>(Kind.class);

	public ParticlesModule() {
		super("particles", Category.VISUAL);
		for (Kind kind : Kind.values()) {
			hidden.put(kind, add(new BoolSetting(kind.name().toLowerCase(), kind.hiddenByDefault)));
		}
		instance = this;
	}

	/** Für die Mixins: soll ein Partikel dieses Typs gar nicht erst entstehen? */
	public static boolean hides(ParticleType<?> type) {
		ParticlesModule module = instance;
		if (module == null || !module.isEnabled()) {
			return false; // häufigster Fall, ohne Nachschlagen
		}
		return module.hidden.get(KINDS.getOrDefault(type, Kind.OTHER)).isOn();
	}

	public static boolean hides(Kind kind) {
		ParticlesModule module = instance;
		return module != null && module.isEnabled() && module.hidden.get(kind).isOn();
	}
}
