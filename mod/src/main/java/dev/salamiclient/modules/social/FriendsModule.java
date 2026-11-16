package dev.salamiclient.modules.social;

import dev.salamiclient.gui.FriendsScreen;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.ActionSetting;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.KeybindSetting;
import dev.salamiclient.util.Keys;
import org.jetbrains.annotations.Nullable;

/**
 * Freunde: Salami-Spieler als Freunde hinzufügen und mit ihnen schreiben – im eigenen Menü
 * (ClickGUI → Social → Freunde → Öffnen, eigene Taste oder {@code /salami friends}).
 * Solange das Modul an ist, ist man für Freunde online und bekommt ihre Nachrichten im Chat.
 * Die Verbindung selbst hält {@link dev.salamiclient.friends.Friends}.
 */
public final class FriendsModule extends Module {
	private static FriendsModule instance;

	private final ActionSetting open = add(new ActionSetting("open", this::openScreen));
	private final KeybindSetting menuKey = add(new KeybindSetting("menu_key", KeybindSetting.NONE));
	private final BoolSetting messages = add(new BoolSetting("messages", true));
	private final BoolSetting online = add(new BoolSetting("online", true));
	private final BoolSetting sound = add(new BoolSetting("sound", true));

	private boolean menuWasDown;

	public FriendsModule() {
		super("friends", Category.SOCIAL, true);
		instance = this;
	}

	public static @Nullable FriendsModule instance() {
		return instance;
	}

	@Override
	public boolean hasScreen() {
		return true;
	}

	@Override
	public void openScreen() {
		mc().setScreen(new FriendsScreen(mc().screen));
	}

	/** Nachrichten von Freunden im Chat zeigen (wenn das Menü nicht gerade offen ist). */
	public boolean showMessages() {
		return messages.isOn();
	}

	/** „X ist jetzt online/offline“ im Chat. */
	public boolean announceOnline() {
		return online.isOn();
	}

	public boolean playSound() {
		return sound.isOn();
	}

	@Override
	public void onTick() {
		// Tastenzustand immer merken, ausgelöst wird nur ohne offenes Menü
		boolean menuDown = menuKey.isBound() && Keys.isDown(menuKey.key());
		if (menuDown && !menuWasDown && mc().screen == null) {
			openScreen();
		}
		menuWasDown = menuDown;
	}
}
