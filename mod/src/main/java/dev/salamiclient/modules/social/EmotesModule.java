package dev.salamiclient.modules.social;

import dev.salamiclient.emotes.Emotes;
import dev.salamiclient.gui.CosmeticsScreen;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import org.jetbrains.annotations.Nullable;

/**
 * Emotes: Winken, Jubeln, Klatschen, Tanzen … – über das Emote-Rad (Taste B, unter Steuerung
 * änderbar) oder Cosmetics → Emotes. Andere Salami-Spieler sehen deine Emotes live über den
 * Cape-Server und du ihre.
 */
public final class EmotesModule extends Module {
	private static EmotesModule instance;

	private final BoolSetting camera = add(new BoolSetting("camera", true));
	private final BoolSetting share = add(new BoolSetting("share", true));
	private final BoolSetting others = add(new BoolSetting("others", true));

	public EmotesModule() {
		super("emotes", Category.SOCIAL, true);
		instance = this;
	}

	public static @Nullable EmotesModule instance() {
		return instance;
	}

	@Override
	public boolean hasScreen() {
		return true;
	}

	/** Cosmetics-Menü, Reiter Emotes. */
	@Override
	public void openScreen() {
		mc().setScreen(new CosmeticsScreen(mc().screen, CosmeticsScreen.Tab.EMOTES));
	}

	/** Aus der Ich-Sicht für das Emote die Kamera nach vorn drehen. */
	public boolean switchesCamera() {
		return camera.isOn();
	}

	/** Eigene Emotes an andere Salami-Spieler schicken. */
	public boolean shares() {
		return share.isOn();
	}

	/** Emotes anderer Salami-Spieler zeigen. */
	public boolean showsOthers() {
		return others.isOn();
	}

	@Override
	public void onTick() {
		Emotes.tick(mc());
	}

	@Override
	protected void onDisable() {
		Emotes.clear();
	}
}
