package dev.salamiclient.gametest;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.gui.clickgui.ClickGuiScreen;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.client.SafeModeModule;
import dev.salamiclient.modules.client.SalamiThemeModule;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.lwjgl.glfw.GLFW;

/**
 * End-to-End-Test für salami-core: echte Tasten- und Mausereignisse, echte Welt.
 *
 * <p>Prüft ClickGUI (öffnen, Modul umschalten, Einstellungen aufklappen),
 * Salami-Theme und Easy Leave / Safe Mode. Screenshots landen unter
 * {@code build/run/clientGameTest/screenshots}.
 */
public final class SalamiClientGameTest implements FabricClientGameTest {
	// Layout-Konstanten der ClickGUI (Panel "Client" ist das erste, Standardposition)
	private static final int PANEL_X = 10;
	private static final int PANEL_Y = 24;
	private static final int HEADER_HEIGHT = 15;
	private static final int MODULE_HEIGHT = 14;

	/** Test-Utility-Modul, um Easy Leave ohne echte Kampfmodule zu prüfen. */
	private static final class DummyUtility extends Module {
		DummyUtility() {
			super("gametest_dummy", Category.UTILITY);
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		ModuleManager modules = SalamiClient.get().modules();
		Module dummy = context.computeOnClient(client -> modules.register(new DummyUtility()));

		// --- Titelbildschirm im Salami-Modus (Logo + Buttons) ---
		context.runOnClient(client -> modules.setEnabled(modules.get(SalamiThemeModule.class), true));
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.getWindow() != null), "No window");
		context.takeScreenshot("salami-00-title");

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientWorld().waitForChunksRender();
			context.takeScreenshot("salami-01-hud");

			// --- ClickGUI über die echte Taste öffnen ---
			context.getInput().pressKey(SalamiClient.get().clickGuiKey());
			context.waitTick();
			check(context.computeOnClient(client -> client.screen instanceof ClickGuiScreen), "ClickGUI did not open via keybind");
			context.takeScreenshot("salami-02-clickgui-salami");

			// --- Salami-Modus per Linksklick ausschalten (1. Modul im Client-Panel) ---
			boolean themeBefore = context.computeOnClient(c -> modules.get(SalamiThemeModule.class).isEnabled());
			click(context, PANEL_X + 20, PANEL_Y + HEADER_HEIGHT + 5, GLFW.GLFW_MOUSE_BUTTON_LEFT);
			boolean themeAfter = context.computeOnClient(c -> modules.get(SalamiThemeModule.class).isEnabled());
			check(themeBefore != themeAfter, "Left click on module row did not toggle it");
			context.takeScreenshot("salami-03-clickgui-default");

			// --- Safe-Mode-Einstellungen per Rechtsklick aufklappen (2. Modul) ---
			click(context, PANEL_X + 20, PANEL_Y + HEADER_HEIGHT + MODULE_HEIGHT + 5, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			context.takeScreenshot("salami-04-safemode-settings");

			// Theme wiederherstellen und GUI schließen
			click(context, PANEL_X + 20, PANEL_Y + HEADER_HEIGHT + 5, GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTick();

			// --- Easy Leave ---
			check(context.computeOnClient(c -> modules.setEnabled(dummy, true)), "Utility module could not be enabled");
			context.getInput().pressKey(SalamiClient.get().easyLeaveKey());
			context.waitTicks(2);
			check(context.computeOnClient(c -> !dummy.isEnabled()), "Easy Leave did not disable utility module");
			check(context.computeOnClient(c -> modules.get(SafeModeModule.class).isEnabled()), "Easy Leave did not enable Safe Mode");
			check(context.computeOnClient(c -> !modules.setEnabled(dummy, true)), "Safe Mode did not block enabling a utility module");
			context.takeScreenshot("salami-05-easy-leave-chat");

			// --- Pausemenü mit Salami-Buttons ---
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTicks(2);
			context.takeScreenshot("salami-06-pause-menu");
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTick();

			// Aufräumen: Safe Mode wieder aus, damit die Test-Config neutral bleibt
			context.runOnClient(c -> modules.setEnabled(modules.get(SafeModeModule.class), false));
		}
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
