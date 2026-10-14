package dev.salamiclient.modules.client;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import net.minecraft.client.Minecraft;

/**
 * Borderless Fullscreen: F11 macht ein randloses Fenster in Bildschirmgröße statt echtem Vollbild –
 * Alt+Tab und ein zweiter Bildschirm gehen ohne Flackern und ohne Auflösungswechsel.
 */
public final class BorderlessFullscreenModule extends Module {
	private static BorderlessFullscreenModule instance;

	public BorderlessFullscreenModule() {
		super("borderless_fullscreen", Category.CLIENT);
		instance = this;
	}

	/** Für {@code WindowMixin}. */
	public static boolean active() {
		return instance != null && instance.isEnabled();
	}

	@Override
	protected void onEnable() {
		reapply();
	}

	@Override
	protected void onDisable() {
		reapply();
	}

	/** Läuft das Spiel schon im Vollbild, sofort in den neuen Modus wechseln. */
	private static void reapply() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getWindow() != null && mc.getWindow().isFullscreen()) {
			mc.getWindow().toggleFullScreen();
			mc.getWindow().toggleFullScreen();
		}
	}
}
