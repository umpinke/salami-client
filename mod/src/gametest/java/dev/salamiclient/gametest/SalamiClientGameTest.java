package dev.salamiclient.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.gui.ConfigsScreen;
import dev.salamiclient.gui.CosmeticsScreen;
import dev.salamiclient.gui.FriendsScreen;
import dev.salamiclient.gui.HudEditorScreen;
import dev.salamiclient.gui.ModsScreen;
import dev.salamiclient.gui.SalamiMenuScreen;
import dev.salamiclient.gui.clickgui.ClickGuiScreen;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.client.SalamiThemeModule;
import dev.salamiclient.modules.misc.AutoReconnectModule;
import dev.salamiclient.modules.visual.CrosshairModule;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

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

		// --- Salami-Knopf im Titelbildschirm öffnet das Salami-Menü, Esc führt zurück ---
		check(context.computeOnClient(c -> hasSalamiButton(c.screen)), "Title screen has no Salami button");
		context.clickScreenButton("salami.menu.button");
		context.waitTicks(2);
		check(context.computeOnClient(c -> c.screen instanceof SalamiMenuScreen), "Salami button on the title screen did not open the menu");
		context.takeScreenshot("salami-00b-title-salami-menu");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTick();
		check(context.computeOnClient(c -> c.screen instanceof TitleScreen), "Esc in the Salami menu did not return to the title screen");

		// --- Mod Menu (nur mit -PpackMods im Testlauf): „Konfigurieren“ liefert das Salami-Menü ---
		if (FabricLoader.getInstance().isModLoaded("modmenu")) {
			check(context.computeOnClient(SalamiClientGameTest::modMenuOpensSalamiMenu), "Mod Menu config entry does not open the Salami menu");
		}

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			GametestCompat.waitForChunksRender(world);
			context.takeScreenshot("salami-01-hud");

			// --- Eigenes Fadenkreuz behält Minecrafts Angriffsanzeige (Option „Fadenkreuz“) ---
			CrosshairModule crosshair = modules.get(CrosshairModule.class);
			AttackIndicatorStatus indicatorBefore = context.computeOnClient(c -> c.options.attackIndicator().get());
			context.runOnClient(c -> {
				modules.setEnabled(crosshair, true);
				c.options.attackIndicator().set(AttackIndicatorStatus.CROSSHAIR);
				c.player.resetAttackStrengthTicker();
			});
			context.waitTick();
			context.takeScreenshot("salami-01b-crosshair-attack-indicator");
			context.runOnClient(c -> {
				modules.setEnabled(crosshair, false);
				c.options.attackIndicator().set(indicatorBefore);
			});

			// --- ClickGUI über die echte Taste öffnen ---
			context.getInput().pressKey(SalamiClient.get().clickGuiKey());
			context.waitTick();
			check(context.computeOnClient(client -> client.screen instanceof SalamiMenuScreen), "Salami menu did not open via keybind");
			context.takeScreenshot("salami-02-menu");
			testMenuFirst(context, modules);

			// --- Panel-Ansicht (über die Kachel „Panel-Ansicht“ erreichbar) ---
			context.setScreen(() -> new ClickGuiScreen(modules, SalamiClient.get().config()));
			context.waitTick();
			context.takeScreenshot("salami-02-clickgui-salami");
			testPanelScrolling(context);

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

			// --- Pausemenü mit Salami-Knopf: öffnet das Salami-Menü, Esc führt ins Pausemenü zurück ---
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(2);
			context.takeScreenshot("salami-06-pause-menu");
			check(context.computeOnClient(c -> hasSalamiButton(c.screen)), "Pause menu has no Salami button");
			context.clickScreenButton("salami.menu.button");
			context.waitTicks(2);
			check(context.computeOnClient(c -> c.screen instanceof SalamiMenuScreen), "Salami button in the pause menu did not open the menu");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTick();
			check(context.computeOnClient(c -> c.screen instanceof PauseScreen), "Esc in the Salami menu did not return to the pause menu");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTick();

			testGerman(context, modules);
		}
	}

	/** Mausrad über dem langen Panel „Optik“ scrollt dessen Inhalt; Strg + Mausrad zoomt weiter. */
	private static void testPanelScrolling(ClientGameTestContext context) {
		// Panel „Optik“ steht im Standard-Layout in der zweiten Spalte
		double scale = context.computeOnClient(c -> (double) c.getWindow().getGuiScale());
		context.getInput().setCursorPos((10 + 140 + 60) * scale, 100 * scale);
		context.getInput().scroll(-3);
		context.waitTick();
		int scrolled = context.computeOnClient(c -> c.screen instanceof ClickGuiScreen screen ? screen.scrollPanel(Category.VISUAL, 0) : -1);
		check(scrolled > 0, "mouse wheel did not scroll the Visual panel: " + scrolled);
		context.takeScreenshot("salami-02b-clickgui-scrolled");
		context.getInput().scroll(3);
		context.waitTick();
		int back = context.computeOnClient(c -> c.screen instanceof ClickGuiScreen screen ? screen.scrollPanel(Category.VISUAL, 0) : -1);
		check(back == 0, "mouse wheel did not scroll the Visual panel back: " + back);
	}

	/**
	 * Auf Deutsch: Mods-Menü, Panel-Ansicht und HUD-Editor (Screenshots zum Prüfen der Übersetzung und
	 * der umbrechenden HUD-Editor-Leiste), danach zurück auf Englisch.
	 */
	private static void testGerman(ClientGameTestContext context, ModuleManager modules) {
		setLanguage(context, "de_de");
		check("Fadenkreuz".equals(context.computeOnClient(c -> I18n.get("salami.module.crosshair"))), "German module names missing");
		context.setScreen(() -> new ModsScreen(modules, null));
		context.waitTicks(3);
		context.takeScreenshot("salami-07-mods-german");
		context.setScreen(() -> new ClickGuiScreen(modules, SalamiClient.get().config()));
		context.waitTicks(2);
		context.takeScreenshot("salami-07b-panels-german");
		context.setScreen(() -> new HudEditorScreen(null));
		context.waitTicks(2);
		context.takeScreenshot("salami-07c-hud-editor-german");
		context.setScreen(() -> null);
		context.waitTick();
		// Die Menü-Taste öffnet immer das Salami-Menü mit den Kacheln
		context.getInput().pressKey(SalamiClient.get().clickGuiKey());
		context.waitTicks(2);
		check(context.computeOnClient(c -> c.screen instanceof SalamiMenuScreen), "menu key did not open the Salami menu (German)");
		context.takeScreenshot("salami-07d-menu-german");
		context.setScreen(() -> null);
		setLanguage(context, "en_us");
	}

	/**
	 * „Salami-Menü zuerst“: die Menü-Taste öffnet immer das Salami-Menü (nie die Panel-Ansicht). Die
	 * Panel-Ansicht ist eine Unteransicht – Esc führt ins Salami-Menü zurück, die Menü-Taste schließt aus
	 * jedem Salami-Bildschirm alles; danach öffnet sie wieder das Salami-Menü.
	 */
	private static void testMenuFirst(ClientGameTestContext context, ModuleManager modules) {
		// Salami-Menü → Panel-Ansicht → Esc → Salami-Menü
		context.runOnClient(c -> c.setScreen(new ClickGuiScreen(modules, SalamiClient.get().config(), c.screen)));
		context.waitTick();
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTick();
		check(context.computeOnClient(c -> c.screen instanceof SalamiMenuScreen), "Esc in the panel view did not return to the Salami menu");

		// Menü-Taste in Panel-Ansicht, Mods-Menü und HUD-Editor: schließt alles
		for (String hub : new String[] {"panels", "mods", "hud"}) {
			context.runOnClient(c -> c.setScreen(switch (hub) {
				case "panels" -> new ClickGuiScreen(modules, SalamiClient.get().config(), c.screen);
				case "mods" -> new ModsScreen(modules, c.screen);
				default -> new HudEditorScreen(c.screen);
			}));
			context.waitTick();
			context.getInput().pressKey(SalamiClient.get().clickGuiKey());
			context.waitTick();
			check(context.computeOnClient(c -> c.screen == null), "menu key did not close everything from the " + hub + " screen");
			// … und öffnet danach wieder das Salami-Menü, nie die Panel-Ansicht
			context.getInput().pressKey(SalamiClient.get().clickGuiKey());
			context.waitTicks(2);
			check(context.computeOnClient(c -> c.screen instanceof SalamiMenuScreen), "menu key did not reopen the Salami menu after " + hub);
		}
	}

	private static void setLanguage(ClientGameTestContext context, String code) {
		CompletableFuture<Void> reload = context.computeOnClient(c -> {
			c.getLanguageManager().setSelected(code);
			return c.reloadResourcePacks();
		});
		// warten, bis geladen ist und der Ladebildschirm ausgeblendet hat
		context.waitFor(c -> reload.isDone() && !GametestCompat.hasOverlay(c), 1200);
		context.waitTicks(2);
	}

	/** Ruft den Mod-Menu-Eintrag von salami-core auf wie Mod Menu selbst (über Reflection, Mod Menu ist hier optional). */
	private static boolean modMenuOpensSalamiMenu(Minecraft client) {
		try {
			Class<?> factoryType = Class.forName("com.terraformersmc.modmenu.api.ConfigScreenFactory");
			for (var container : FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class)) {
				if (container.getProvider().getMetadata().getId().equals(SalamiClient.MOD_ID)) {
					Object api = container.getEntrypoint();
					Object factory = Class.forName("com.terraformersmc.modmenu.api.ModMenuApi").getMethod("getModConfigScreenFactory").invoke(api);
					Object screen = factoryType.getMethod("create", Screen.class).invoke(factory, client.screen);
					return screen instanceof SalamiMenuScreen;
				}
			}
			return false;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Salami-Knopf (Symbol neben „Einzelspieler“ bzw. „Zurück zum Spiel“) im Bildschirm? */
	private static boolean hasSalamiButton(Screen screen) {
		if (screen == null) {
			return false;
		}
		for (var child : screen.children()) {
			if (child instanceof SpriteIconButton button && button.getMessage().getContents() instanceof TranslatableContents translatable
				&& translatable.getKey().equals("salami.menu.button")) {
				return true;
			}
		}
		return false;
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
