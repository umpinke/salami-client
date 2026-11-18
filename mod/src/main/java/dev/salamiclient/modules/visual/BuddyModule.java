package dev.salamiclient.modules.visual;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.capes.SharedCapes;
import dev.salamiclient.cosmetics.BuddyCatalog;
import dev.salamiclient.cosmetics.BuddyFollowers;
import dev.salamiclient.cosmetics.Hat;
import dev.salamiclient.gui.CosmeticsScreen;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.WorldRenderListener;
import dev.salamiclient.render.WorldContext;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ModeSetting;
import dev.salamiclient.setting.TextSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Buddys: kleine Begleiter aus Würfeln (Salami-Kumpel, Tannenbaum, Schneemann …). Wie ein Mini Me
 * hängt ein Buddy an deiner Hand, schwebt über der Schulter, steht auf dem Kopf oder läuft dir
 * hinterher. Welche es gibt, steht im Katalog vom Salami-Server ({@link BuddyCatalog}) – ein neuer
 * Buddy braucht kein Update. Andere Salami-Spieler sehen ihn über den Cape-Server
 * ({@link SharedCapes}). Auswahl unter Cosmetics → Buddys.
 */
public final class BuddyModule extends Module implements WorldRenderListener {
	public enum Place { HAND, SHOULDER, HEAD, FOLLOW }

	/** Wer welchen Buddy wo hat. */
	public record Wish(Hat buddy, Place place) {}

	private static BuddyModule instance;

	/** Name des gewählten Buddys ({@code tree_buddy}) oder leer. Gewählt wird im Cosmetics-Menü. */
	private final TextSetting buddy = add(new TextSetting("buddy", "", 24));
	private final ModeSetting<Place> place = add(new ModeSetting<>("place", Place.SHOULDER));
	private final BoolSetting others = add(new BoolSetting("others", true));
	/** Buddys anderer, die der Katalog (noch) nicht kennt: dafür wurde er schon neu angefragt. */
	private @Nullable String askedFor;

	public BuddyModule() {
		super("buddy", Category.VISUAL, true);
		instance = this;
		buddy.hide();
	}

	public static @Nullable BuddyModule instance() {
		return instance;
	}

	@Override
	public boolean hasScreen() {
		return true;
	}

	/** Cosmetics-Menü, Reiter Buddys. */
	@Override
	public void openScreen() {
		mc().setScreen(new CosmeticsScreen(mc().screen, CosmeticsScreen.Tab.BUDDIES));
	}

	/** Der gewählte Buddy oder {@code null} (keiner, oder es gibt ihn nicht mehr). */
	public @Nullable Hat selected() {
		return BuddyCatalog.get(buddy.get());
	}

	/** Buddy wählen; {@code null} oder leer packt ihn weg. */
	public void choose(@Nullable String id) {
		buddy.set(id == null ? "" : id);
	}

	public Place place() {
		return place.get();
	}

	public void setPlace(Place value) {
		place.set(value);
	}

	/**
	 * Wer einen Buddy noch als Schulter-Hut trägt (so war es vor den Buddys): der Hut wird zum Buddy
	 * über der Schulter. Liefert, ob dieser Hut jetzt ein Buddy ist.
	 */
	public static boolean adoptHat(String hatId) {
		BuddyModule module = instance;
		if (module == null || !BuddyCatalog.isBuddy(hatId)) {
			return false;
		}
		if (module.selected() == null) {
			module.choose(hatId);
			module.setPlace(Place.SHOULDER);
			if (!module.isEnabled()) {
				SalamiClient.get().modules().setEnabled(module, true);
			}
		}
		SalamiClient.get().config().markDirty();
		return true;
	}

	/** Eigener Buddy oder {@code null}. */
	public static @Nullable Wish own() {
		BuddyModule module = instance;
		if (module == null || !module.isEnabled()) {
			return null;
		}
		Hat selected = module.selected();
		return selected == null ? null : new Wish(selected, module.place.get());
	}

	/** Buddy eines Spielers: deiner nach deiner Auswahl, andere laut Cape-Server. Nur im Render-/Client-Thread. */
	public static @Nullable Wish of(Player player) {
		BuddyModule module = instance;
		if (module == null || !module.isEnabled()) {
			return null;
		}
		if (player == Minecraft.getInstance().player) {
			return own();
		}
		if (!module.others.isOn()) {
			return null;
		}
		SharedCapes.SharedBuddy shared = SharedCapes.buddyOf(player.getUUID());
		if (shared == null) {
			return null;
		}
		Hat theirs = BuddyCatalog.get(shared.id());
		if (theirs == null) {
			if (!shared.id().equals(module.askedFor)) {
				// jemand hat einen Buddy, den es hier noch nicht gibt: Katalog neu holen
				module.askedFor = shared.id();
				BuddyCatalog.refreshSoon();
			}
			return null;
		}
		try {
			return new Wish(theirs, Place.valueOf(shared.place().toUpperCase(Locale.ROOT)));
		} catch (IllegalArgumentException unknown) {
			return null;
		}
	}

	@Override
	public void onTick() {
		BuddyFollowers.tick(mc());
	}

	@Override
	protected void onDisable() {
		BuddyFollowers.clear();
	}

	@Override
	public void renderWorld(WorldContext context) {
		BuddyFollowers.render(context);
	}
}
