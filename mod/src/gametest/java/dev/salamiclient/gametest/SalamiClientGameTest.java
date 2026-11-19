package dev.salamiclient.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.gui.ConfigsScreen;
import dev.salamiclient.gui.CosmeticsScreen;
import dev.salamiclient.gui.FriendsScreen;
import dev.salamiclient.gui.SalamiMenuScreen;
import dev.salamiclient.gui.clickgui.ClickGuiScreen;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.client.SalamiThemeModule;
import dev.salamiclient.modules.misc.AutoReconnectModule;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * End-to-End-Test für salami-core: echte Tasten- und Mausereignisse, echte Welt.
 *
 * <p>Prüft ClickGUI (öffnen, Modul umschalten, Einstellungen aufklappen),
 * Salami-Theme, Freunde- und Cosmetics-Menü und das Configs-Menü. Screenshots landen unter
 * {@code build/run/clientGameTest/screenshots}.
 */
public final class SalamiClientGameTest implements FabricClientGameTest {
	// Layout-Konstanten der ClickGUI (Panel "Client" ist das erste, Standardposition)
	private static final int PANEL_X = 10;
	private static final int PANEL_Y = 24;
	private static final int HEADER_HEIGHT = 15;
	private static final int MODULE_HEIGHT = 14;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!GametestCompat.selected(this)) {
			return;
		}
		ModuleManager modules = SalamiClient.get().modules();
		check(context.computeOnClient(c -> modules.all().stream().noneMatch(m -> m.id().contains("crystal") || m.id().contains("anchor"))),
			"Combat modules (Crystal, Anchor) are still registered");
		check(context.computeOnClient(c -> !modules.byCategory(Category.SOCIAL).isEmpty()), "Social category has no modules");

		// --- Titelbildschirm im Salami-Modus (Logo + Buttons) ---
		context.runOnClient(client -> modules.setEnabled(modules.get(SalamiThemeModule.class), true));
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.getWindow() != null), "No window");
		context.takeScreenshot("salami-00-title");

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			GametestCompat.waitForChunksRender(world);
			context.takeScreenshot("salami-01-hud");

			// --- ClickGUI über die echte Taste öffnen ---
			context.getInput().pressKey(SalamiClient.get().clickGuiKey());
			context.waitTick();
			check(context.computeOnClient(client -> client.screen instanceof SalamiMenuScreen), "Salami menu did not open via keybind");
			context.takeScreenshot("salami-02-menu");

			// --- Panel-Ansicht (über die Kachel „Panels“ erreichbar) ---
			context.setScreen(() -> new ClickGuiScreen(modules, SalamiClient.get().config()));
			context.waitTick();
			context.takeScreenshot("salami-02-clickgui-salami");

			// --- Salami-Modus per Linksklick ausschalten (1. Modul im Client-Panel) ---
			boolean themeBefore = context.computeOnClient(c -> modules.get(SalamiThemeModule.class).isEnabled());
			click(context, PANEL_X + 20, PANEL_Y + HEADER_HEIGHT + 5, InputConstants.MOUSE_BUTTON_LEFT);
			boolean themeAfter = context.computeOnClient(c -> modules.get(SalamiThemeModule.class).isEnabled());
			check(themeBefore != themeAfter, "Left click on module row did not toggle it");
			context.takeScreenshot("salami-03-clickgui-default");

			// --- Einstellungen des 2. Moduls per Rechtsklick aufklappen ---
			click(context, PANEL_X + 20, PANEL_Y + HEADER_HEIGHT + MODULE_HEIGHT + 5, InputConstants.MOUSE_BUTTON_RIGHT);
			context.takeScreenshot("salami-04-module-settings");

			// Theme wiederherstellen und GUI schließen
			click(context, PANEL_X + 20, PANEL_Y + HEADER_HEIGHT + 5, InputConstants.MOUSE_BUTTON_LEFT);
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTick();

			// --- Freunde und Cosmetics (im Test ohne Server: Hinweis statt Liste) ---
			context.setScreen(() -> new FriendsScreen(null));
			context.waitTicks(3);
			check(context.computeOnClient(c -> c.screen instanceof FriendsScreen friends && friends.isTyping()),
				"Friends screen did not open with a focused input");
			check(context.computeOnClient(GametestCompat::acceptsTypedText), "Friends: window does not accept typed text");
			context.takeScreenshot("salami-04b-friends");
			context.setScreen(() -> new CosmeticsScreen(null));
			context.waitTicks(20); // gemalte Capes brauchen ein paar Ticks für die Vorschau
			context.takeScreenshot("salami-04c-cosmetics");
			if (GametestCompat.TEXT_INPUT_GATED) {
				// Ohne Namensfeld im Fokus bleibt die Texteingabe aus
				check(!context.computeOnClient(GametestCompat::acceptsTypedText), "Cosmetics: text input on without a field");
			}
			context.setScreen(() -> null);

			try {
				testConfigs(context, modules);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}

			// --- Pausemenü mit Salami-Buttons ---
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(2);
			context.takeScreenshot("salami-06-pause-menu");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTick();
		}
	}

	/**
	 * Configs-Menü: Export → Import stellt Werte wieder her, Zurücksetzen lädt die
	 * Standardwerte, eine kaputte Datei ändert nichts.
	 * Die Dateidialoge selbst lassen sich im Test nicht bedienen; geprüft wird die Logik dahinter.
	 */
	private static void testConfigs(ClientGameTestContext context, ModuleManager modules) throws IOException {
		context.runOnClient(c -> c.setScreen(new ConfigsScreen(null)));
		context.waitTicks(2);
		context.takeScreenshot("salami-05b-configs");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTick();
		check(context.computeOnClient(c -> c.screen == null), "Configs screen did not close with Esc");

		NumberSetting delay = (NumberSetting) setting(modules.get(AutoReconnectModule.class), "delay");
		Path file = FabricLoader.getInstance().getConfigDir().resolve("salami-gametest-export.json");
		context.runOnClient(c -> {
			delay.set(12.0);
			SalamiClient.get().config().exportTo(file);
			delay.set(20.0);
			SalamiClient.get().importConfig(file);
		});
		check(delay.get() == 12.0, "Configs: import did not restore the exported value (" + delay.get() + ")");

		context.runOnClient(c -> {
			Files.writeString(file, "{ kaputt");
			delay.set(20.0);
		});
		boolean rejected = context.computeOnClient(c -> {
			try {
				SalamiClient.get().importConfig(file);
				return false;
			} catch (IOException e) {
				return true;
			}
		});
		check(rejected, "Configs: broken file was imported");
		check(delay.get() == 20.0, "Configs: broken file changed settings");

		context.runOnClient(c -> SalamiClient.get().resetConfig());
		check(delay.get().equals(delay.defaultValue()), "Configs: reset did not restore the default");
		context.runOnClient(c -> Files.deleteIfExists(file));
	}

	private static Setting<?> setting(Module module, String id) {
		for (Setting<?> setting : module.settings()) {
			if (setting.id().equals(id)) {
				return setting;
			}
		}
		throw new IllegalArgumentException(module.id() + " has no setting " + id);
	}

	/** Klick in GUI-Koordinaten (wird auf Fensterpixel umgerechnet). */
	private static void click(ClientGameTestContext context, int guiX, int guiY, int button) {
		double scale = context.computeOnClient(client -> (double) client.getWindow().getGuiScale());
		context.getInput().setCursorPos(guiX * scale, guiY * scale);
		context.getInput().pressMouse(button);
		context.waitTick();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
