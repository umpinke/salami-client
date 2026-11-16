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
 * Salami-Hüte: kleine Hüte, die auf der Schulter sitzen (vorerst die Weihnachtsmütze). Gezeichnet
 * werden sie von {@link dev.salamiclient.render.SantaHatLayer}; andere Salami-Spieler sehen deinen
 * Hut über den Cape-Server ({@link SharedCapes}), so wie dein Cape.
 */
public final class HatsModule extends Module {
	public enum Hat { NONE, SANTA }

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

	/** Was andere Salami-Spieler von dir sehen sollen ({@code "santa"}) oder {@code null}. */
	public static @Nullable String sharedHat() {
		HatsModule module = instance;
		if (module == null || !module.isEnabled() || module.hat.get() == Hat.NONE) {
			return null;
		}
		return module.hat.get().name().toLowerCase(Locale.ROOT);
	}

	/**
	 * Trägt das Wesen mit dieser Entity-ID gerade eine Weihnachtsmütze? Der eigene Spieler nach
	 * deiner Auswahl, andere Salami-Spieler laut Cape-Server. Nur im Render-Thread aufrufen.
	 */
	public static boolean wearsSanta(int entityId) {
		HatsModule module = instance;
		Minecraft mc = Minecraft.getInstance();
		if (module == null || !module.isEnabled() || mc.level == null) {
			return false;
		}
		Entity entity = mc.level.getEntity(entityId);
		if (!(entity instanceof Player player)) {
			return false;
		}
		if (player == mc.player) {
			return module.hat.get() == Hat.SANTA;
		}
		return module.others.isOn() && "santa".equals(SharedCapes.hatOf(player.getUUID()));
	}
}
