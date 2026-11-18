package dev.salamiclient.modules.visual;

import dev.salamiclient.capes.SharedCapes;
import dev.salamiclient.cosmetics.BuddyCatalog;
import dev.salamiclient.cosmetics.Hat;
import dev.salamiclient.cosmetics.HatCatalog;
import dev.salamiclient.gui.CosmeticsScreen;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.TextSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Salami-Hüte (Cosmetics → Hüte). Welche Hüte es gibt, steht im Katalog vom Salami-Server
 * ({@link HatCatalog}) – ein neuer Hut braucht kein Update. Gezeichnet werden sie von
 * {@link dev.salamiclient.render.HatLayer}. Andere Salami-Spieler sehen deinen Hut über den
 * Cape-Server ({@link SharedCapes}), so wie dein Cape.
 */
public final class HatsModule extends Module {
	private static HatsModule instance;

	/** Name des gewählten Hutes ({@code top_hat}) oder leer. Gewählt wird im Cosmetics-Menü. */
	private final TextSetting hat = add(new TextSetting("hat", "", 24));
	private final BoolSetting others = add(new BoolSetting("others", true));
	/** Hüte anderer, die der Katalog (noch) nicht kennt: dafür wurde er schon neu angefragt. */
	private @Nullable String askedFor;

	public HatsModule() {
		super("hats", Category.VISUAL, true);
		instance = this;
		hat.hide();
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

	/**
	 * Name des gewählten Hutes oder leer (kein Hut). Ältere Einstellungen haben den Namen groß
	 * geschrieben ({@code SANTA}, {@code NONE}) – das gilt weiter.
	 */
	public String selectedId() {
		String id = hat.get().toLowerCase(Locale.ROOT);
		return id.equals("none") ? "" : id;
	}

	/**
	 * Der gewählte Hut oder {@code null} (keiner, oder es gibt ihn nicht mehr). Wer noch einen Buddy
	 * als Schulter-Hut gewählt hat (so war es vor Cosmetics → Buddys), bekommt ihn hier als Buddy.
	 */
	public @Nullable Hat selected() {
		String id = selectedId();
		if (!id.isEmpty() && BuddyModule.adoptHat(id)) {
			hat.set("");
			return null;
		}
		return HatCatalog.get(id);
	}

	/** Hut aufsetzen; {@code null} oder leer nimmt ihn ab. */
	public void choose(@Nullable String id) {
		hat.set(id == null ? "" : id);
	}

	/**
	 * Was andere Salami-Spieler von dir sehen sollen ({@code "santa"}, {@code "crown"} …) oder {@code null}.
	 * Ohne Hut wird ein Buddy über der Schulter auch als Hut gemeldet: so sehen ihn Spieler mit einer
	 * älteren Version weiter, die Buddys nur als Schulter-Hut kennt.
	 */
	public static @Nullable String sharedHat() {
		HatsModule module = instance;
		Hat selected = module == null || !module.isEnabled() ? null : module.selected();
		if (selected != null) {
			return selected.id();
		}
		BuddyModule.Wish buddy = BuddyModule.own();
		return buddy != null && buddy.place() == BuddyModule.Place.SHOULDER && HatCatalog.get(buddy.buddy().id()) != null
			? buddy.buddy().id() : null;
	}

	/**
	 * Welchen Hut trägt das Wesen mit dieser Entity-ID gerade? Der eigene Spieler nach deiner Auswahl,
	 * andere Salami-Spieler laut Cape-Server. Nur im Render-Thread aufrufen.
	 */
	public static @Nullable Hat hatOf(int entityId) {
		HatsModule module = instance;
		Minecraft mc = Minecraft.getInstance();
		if (module == null || !module.isEnabled() || mc.level == null) {
			return null;
		}
		Entity entity = mc.level.getEntity(entityId);
		if (!(entity instanceof Player player)) {
			return null;
		}
		if (player == mc.player) {
			return module.selected();
		}
		if (!module.others.isOn()) {
			return null;
		}
		String id = SharedCapes.hatOf(player.getUUID());
		if (id != null && BuddyCatalog.isBuddy(id) && SharedCapes.buddyOf(player.getUUID()) != null) {
			return null; // ihr Buddy, für ältere Versionen zusätzlich als Hut gemeldet: zeichnet BuddyLayer
		}
		Hat theirs = HatCatalog.get(id);
		if (theirs == null && id != null && !id.equals(module.askedFor)) {
			// jemand trägt einen Hut, den es hier noch nicht gibt: Katalog neu holen
			module.askedFor = id;
			HatCatalog.refreshSoon();
		}
		return theirs;
	}
}
