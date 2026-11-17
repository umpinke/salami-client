package dev.salamiclient.modules.visual;

import dev.salamiclient.capes.SharedCapes;
import dev.salamiclient.gui.CosmeticsScreen;
import dev.salamiclient.minime.MiniMeFollowers;
import dev.salamiclient.minime.MiniMeSkins;
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
 * Mini Me: ein kleines Abbild eines Spielers (Skin zum eingegebenen Namen), das an deiner Hand
 * hängt, auf deiner Schulter oder deinem Kopf sitzt oder dir hinterherläuft. Andere
 * Salami-Spieler sehen es über den Cape-Server ({@link SharedCapes}). Auswahl unter
 * Cosmetics → Mini Me.
 */
public final class MiniMeModule extends Module implements WorldRenderListener {
	public enum Place { NONE, HAND, SHOULDER, HEAD, FOLLOW }

	/** Wer welches Mini Me wo trägt. */
	public record Wish(String name, Place place) {}

	private static MiniMeModule instance;

	private final TextSetting name = add(new TextSetting("name", "", 16));
	private final ModeSetting<Place> place = add(new ModeSetting<>("place", Place.NONE));
	private final BoolSetting others = add(new BoolSetting("others", true));

	public MiniMeModule() {
		super("minime", Category.VISUAL, true);
		instance = this;
	}

	public static @Nullable MiniMeModule instance() {
		return instance;
	}

	@Override
	public boolean hasScreen() {
		return true;
	}

	/** Cosmetics-Menü, Reiter Mini Me. */
	@Override
	public void openScreen() {
		mc().setScreen(new CosmeticsScreen(mc().screen, CosmeticsScreen.Tab.MINIME));
	}

	public String name() {
		return name.get();
	}

	public void setName(String value) {
		name.set(value);
	}

	public Place place() {
		return place.get();
	}

	public void setPlace(Place value) {
		place.set(value);
	}

	/** Eigenes Mini Me (gültiger Name und ein Platz) oder {@code null}. */
	public static @Nullable Wish own() {
		MiniMeModule module = instance;
		if (module == null || !module.isEnabled() || module.place.get() == Place.NONE
			|| !MiniMeSkins.NAME.matcher(module.name.get()).matches()) {
			return null;
		}
		return new Wish(module.name.get(), module.place.get());
	}

	/** Mini Me eines Spielers: deins nach deiner Auswahl, andere laut Cape-Server. Nur im Render-/Client-Thread. */
	public static @Nullable Wish of(Player player) {
		MiniMeModule module = instance;
		if (module == null || !module.isEnabled()) {
			return null;
		}
		if (player == Minecraft.getInstance().player) {
			return own();
		}
		if (!module.others.isOn()) {
			return null;
		}
		SharedCapes.SharedMiniMe shared = SharedCapes.miniMeOf(player.getUUID());
		if (shared == null || !MiniMeSkins.NAME.matcher(shared.name()).matches()) {
			return null;
		}
		try {
			Place where = Place.valueOf(shared.place().toUpperCase(Locale.ROOT));
			return where == Place.NONE ? null : new Wish(shared.name(), where);
		} catch (IllegalArgumentException unknown) {
			return null;
		}
	}

	@Override
	public void onTick() {
		MiniMeFollowers.tick(mc());
	}

	@Override
	protected void onDisable() {
		MiniMeFollowers.clear();
	}

	@Override
	public void renderWorld(WorldContext context) {
		MiniMeFollowers.render(context);
	}
}
