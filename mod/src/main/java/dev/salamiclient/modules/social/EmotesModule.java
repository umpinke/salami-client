package dev.salamiclient.modules.social;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.emotes.EmoteEffects;
import dev.salamiclient.emotes.Emotes;
import dev.salamiclient.gui.CosmeticsScreen;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.WorldRenderListener;
import dev.salamiclient.render.WorldContext;
import dev.salamiclient.setting.BoolSetting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Emotes: Winken, Jubeln, Klatschen, Tanzen … – über das Emote-Rad (Taste B, unter Steuerung
 * änderbar) oder Cosmetics → Emotes. Andere Salami-Spieler sehen deine Emotes live über den
 * Cape-Server und du ihre. Manche Emotes bringen etwas mit: einen Schneeball, der wirklich fliegt
 * (nur zum Ansehen, er trifft niemanden), eine Glocke in der Hand oder eine Verwandlung.
 */
public final class EmotesModule extends Module implements WorldRenderListener {
	private static EmotesModule instance;

	private final BoolSetting camera = add(new BoolSetting("camera", true));
	private final BoolSetting share = add(new BoolSetting("share", true));
	private final BoolSetting others = add(new BoolSetting("others", true));
	private final BoolSetting sounds = add(new BoolSetting("sounds", true));

	public EmotesModule() {
		super("emotes", Category.SOCIAL, true);
		instance = this;
	}

	@Override
	public Component description() {
		KeyMapping key = SalamiClient.get().emoteKey();
		return description(key.isUnbound() ? Component.translatable("salami.setting.unbound") : key.getTranslatedKeyMessage());
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

	/** Geräusche von Emotes abspielen (Schneeball, Glocke …). */
	public boolean playsSounds() {
		return sounds.isOn();
	}

	@Override
	public void onTick() {
		Emotes.tick(mc());
	}

	/** Geworfenes und verwandelte Spieler stehen in der Welt, nicht am Spielermodell. */
	@Override
	public void renderWorld(WorldContext context) {
		EmoteEffects.render(context);
	}

	@Override
	protected void onDisable() {
		Emotes.clear();
	}
}
