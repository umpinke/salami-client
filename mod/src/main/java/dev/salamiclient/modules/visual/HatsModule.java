package dev.salamiclient.modules.visual;

import dev.salamiclient.capes.SharedCapes;
import dev.salamiclient.gui.CosmeticsScreen;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Salami-Hüte: die kleine Weihnachtsmütze schwebt über der Schulter
 * ({@link dev.salamiclient.render.SantaHatLayer}), alle anderen sitzen auf dem Kopf
 * ({@link dev.salamiclient.render.HeadHatLayer}). Andere Salami-Spieler sehen deinen Hut über den
 * Cape-Server ({@link SharedCapes}), so wie dein Cape – der Server kennt dafür dieselben Namen
 * ({@code server/capes/server.mjs}, {@code HATS}).
 */
public final class HatsModule extends Module {
	public enum Hat {
		NONE,
		SANTA,
		CROWN,
		TOP_HAT,
		CHEF,
		HALO,
		HORNS,
		CAT_EARS,
		PARTY,
		BERET;

		/** Name für Server, Übersetzungen und Symbole ({@code top_hat}). */
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** Hut aus einem Namen ({@code top_hat}) oder {@link #NONE}, wenn er unbekannt ist (neuerer Client). */
		public static Hat of(@Nullable String id) {
			if (id == null) {
				return NONE;
			}
			try {
				return valueOf(id.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException unknown) {
				return NONE;
			}
		}
	}

	private static HatsModule instance;

	private final ModeSetting<Hat> hat = add(new ModeSetting<>("hat", Hat.NONE));
	private final BoolSetting others = add(new BoolSetting("others", true));

	public HatsModule() {
		super("hats", Category.VISUAL, true);
		instance = this;
	}

	public static @Nullable HatsModule instance() {
		return instance;
	}

	@Override
	public boolean hasScreen() {
		return true;
	}

	/** Cosmetics-Menü, Reiter Hüte. */
	@Override
	public void openScreen() {
		mc().setScreen(new CosmeticsScreen(mc().screen, CosmeticsScreen.Tab.HATS));
	}

	public Hat selected() {
		return hat.get();
	}

	public void choose(Hat selected) {
		hat.set(selected);
	}

	/** Was andere Salami-Spieler von dir sehen sollen ({@code "santa"}, {@code "crown"} …) oder {@code null}. */
	public static @Nullable String sharedHat() {
		HatsModule module = instance;
		if (module == null || !module.isEnabled() || module.hat.get() == Hat.NONE) {
			return null;
		}
		return module.hat.get().id();
	}

	/**
	 * Welchen Hut trägt das Wesen mit dieser Entity-ID gerade? Der eigene Spieler nach deiner Auswahl,
	 * andere Salami-Spieler laut Cape-Server. Nur im Render-Thread aufrufen.
	 */
	public static Hat hatOf(int entityId) {
		HatsModule module = instance;
		Minecraft mc = Minecraft.getInstance();
		if (module == null || !module.isEnabled() || mc.level == null) {
			return Hat.NONE;
		}
		Entity entity = mc.level.getEntity(entityId);
		if (!(entity instanceof Player player)) {
			return Hat.NONE;
		}
		if (player == mc.player) {
			return module.hat.get();
		}
		return module.others.isOn() ? Hat.of(SharedCapes.hatOf(player.getUUID())) : Hat.NONE;
	}

	/** Trägt das Wesen die Weihnachtsmütze (über der Schulter)? */
	public static boolean wearsSanta(int entityId) {
		return hatOf(entityId) == Hat.SANTA;
	}
}
