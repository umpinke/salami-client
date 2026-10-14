package dev.salamiclient.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.config.ConfigManager;
import dev.salamiclient.gui.ColorPicker;
import dev.salamiclient.gui.ModSettingsScreen;
import dev.salamiclient.gui.clickgui.ClickGuiScreen;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.modules.client.SafeModeModule;
import dev.salamiclient.modules.hud.CpsModule;
import dev.salamiclient.modules.hud.KeystrokesModule;
import dev.salamiclient.modules.visual.HitboxModule;
import dev.salamiclient.modules.visual.InfoHudModule;
import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.List;

/**
 * Anpassen: Farbwähler (Hex, HSV, Regenbogen, Speichern), Darstellung aller HUD-Elemente
 * (eigene Farben, Hintergrund), eigene Oberflächen-Farben und Hitbox-Farben.
 */
public final class SalamiCustomizeGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		ModuleManager modules = SalamiClient.get().modules();
		context.runOnClient(c -> modules.setEnabled(modules.get(SafeModeModule.class), false));

		testColorSetting();

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientWorld().waitForChunksRender();
			world.getServer().runCommand("tp @p 500.5 -60 0.5 0 10");
			context.waitTicks(10);

			testHudAppearance(context, modules);
			testColorPicker(context, modules);
			testInterfaceColors(context, modules);
			testHitboxColors(context, modules);
		}
	}

	private static void testColorSetting() {
		check(ColorSetting.parseHex("#12AB34") == 0xFF12AB34, "hex #RRGGBB");
		check(ColorSetting.parseHex("8012ab34") == 0x8012AB34, "hex AARRGGBB");
		check(ColorSetting.parseHex("xyz") == null && ColorSetting.parseHex("#12345") == null, "invalid hex accepted");
		float[] red = ColorSetting.toHsv(0xFFFF0000);
		check(red[0] == 0 && red[1] == 1 && red[2] == 1, "HSV of red");
		check(ColorSetting.fromHsv(1 / 3F, 1, 1, 255) == 0xFF00FF00, "HSV green");
		check(ColorSetting.toHex(0xFF12AB34).equals("#12AB34") && ColorSetting.toHex(0x8012AB34).equals("#8012AB34"), "toHex");

		ColorSetting probe = new ColorSetting("probe", 0xFF112233);
		probe.setChroma(true);
		JsonElement saved = probe.toJson();
		ColorSetting loaded = new ColorSetting("probe", 0);
		loaded.fromJson(saved);
		check(loaded.isChroma() && loaded.get() == 0xFF112233, "rainbow color did not survive saving: " + saved);
		loaded.fromJson(new JsonPrimitive("#FF445566"));
		check(!loaded.isChroma() && loaded.get() == 0xFF445566, "old color format no longer readable");
		ColorSetting noRainbow = new ColorSetting("probe", 0xFF000000).noChroma();
		noRainbow.setChroma(true);
		check(!noRainbow.isChroma(), "rainbow on a color that does not allow it");
	}

	private static void testHudAppearance(ClientGameTestContext context, ModuleManager modules) {
		CpsModule cps = modules.get(CpsModule.class);
		InfoHudModule info = modules.get(InfoHudModule.class);
		KeystrokesModule keys = modules.get(KeystrokesModule.class);
		context.runOnClient(c -> {
			for (Module module : List.of(cps, info, keys)) {
				modules.setEnabled(module, true);
			}
		});
		check(setting(cps, "accent_color").isHidden(), "custom colors visible while turned off");
		check(!setting(cps, "background_color").isHidden(), "background color hidden while background is on");
		// Darstellung steht nach den Modul-Einstellungen
		List<Setting<?>> order = cps.settings();
		check(order.indexOf(setting(cps, "show_right")) < order.indexOf(setting(cps, "scale")), "appearance settings not at the end");

		context.runOnClient(c -> {
			for (HudModule module : List.of(cps, info, keys)) {
				set(module, "custom_colors", true);
				set(module, "text_color", 0xFF55FFFF);
				set(module, "accent_color", 0xFFFFAA00);
				set(module, "background_color", 0xA0202060);
			}
			set(info, "background", true);
		});
		check(!setting(cps, "accent_color").isHidden(), "custom colors hidden while turned on");
		check(cps.textColor() == 0xFF55FFFF && cps.accentColor() == 0xFFFFAA00, "custom HUD colors not used");
		check(cps.backgroundColor() == 0xA0202060, "background color not used");
		context.runOnClient(c -> set(cps, "background", false));
		check(cps.backgroundColor() == 0 && setting(cps, "background_color").isHidden(), "background still drawn while off");
		context.runOnClient(c -> set(cps, "background", true));
		context.waitTicks(5);
		context.takeScreenshot("salami-60-hud-custom-colors");

		context.runOnClient(c -> {
			for (HudModule module : List.of(cps, info, keys)) {
				for (String id : List.of("custom_colors", "text_color", "accent_color", "background_color", "background")) {
					setting(module, id).resetAll();
				}
				modules.setEnabled(module, false);
			}
		});
		check(cps.textColor() == SalamiClient.get().theme().text(), "HUD does not fall back to the theme");
	}

	private static void testColorPicker(ClientGameTestContext context, ModuleManager modules) {
		CpsModule cps = modules.get(CpsModule.class);
		context.runOnClient(c -> {
			modules.setEnabled(cps, true);
			set(cps, "custom_colors", true);
		});
		ColorSetting text = (ColorSetting) setting(cps, "text_color");
		context.setScreen(() -> new ModSettingsScreen(modules, cps, null));
		context.waitTicks(2);
		ColorPicker picker = context.computeOnClient(c -> ((ModSettingsScreen) c.screen).openColorPicker(text));
		check(picker != null, "color picker did not open");
		context.waitTicks(2);
		context.takeScreenshot("salami-61-color-picker");

		int before = text.get();
		int after = context.computeOnClient(c -> {
			int[] b = picker.bounds();
			picker.mouseClicked(c.font, b[0] + 30, b[1] + 20, 0); // ins Farbfeld
			picker.mouseReleased();
			return text.get();
		});
		check(after != before, "clicking the color field did not change the color");
		// Hex-Eingabe
		context.runOnClient(c -> {
			int[] b = picker.bounds();
			picker.mouseClicked(c.font, b[2] - 20, b[1] + 6 + 72 + 6 + 4, 0); // Hex-Feld
		});
		check(picker.isEditingText(), "hex field not editable");
		context.runOnClient(c -> {
			for (int i = 0; i < 9; i++) {
				picker.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE);
			}
			picker.charTyped("#3366CC");
			picker.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
		});
		check(text.get() == 0xFF3366CC, "hex input not applied: " + Integer.toHexString(text.get()));
		check(context.computeOnClient(c -> ((ModSettingsScreen) c.screen).wasCapturingKey()) == false, "still capturing keys after Enter");

		// Regenbogen läuft durch die Farbtöne
		context.runOnClient(c -> text.setChroma(true));
		int first = text.argb();
		context.waitTicks(10);
		check(text.argb() != first, "rainbow color does not change");
		context.setScreen(() -> null);
		context.runOnClient(c -> {
			text.resetAll();
			setting(cps, "custom_colors").resetAll();
			modules.setEnabled(cps, false);
		});
		check(!text.isChroma() && text.get() == 0xFFFFFFFF, "reset did not clear the rainbow");
	}

	private static void testInterfaceColors(ClientGameTestContext context, ModuleManager modules) {
		GuiScaleModule gui = modules.get(GuiScaleModule.class);
		boolean salami = SalamiClient.get().theme().isSalami();
		context.runOnClient(c -> {
			modules.setEnabled(gui, true);
			set(gui, "custom_colors", true);
			set(gui, "accent_color", 0xFF33CC66);
			set(gui, "header_color", 0xFF203040);
		});
		check(SalamiClient.get().theme().accent() == 0xFF33CC66 && SalamiClient.get().theme().header() == 0xFF203040,
			"interface colors not applied");
		check(SalamiClient.get().theme().isSalami() == salami, "custom colors changed the theme kind");
		ConfigManager config = SalamiClient.get().config();
		context.setScreen(() -> new ClickGuiScreen(modules, config));
		context.waitTicks(2);
		context.takeScreenshot("salami-62-clickgui-custom-colors");
		context.setScreen(() -> null);
		context.runOnClient(c -> {
			for (String id : List.of("custom_colors", "accent_color", "header_color")) {
				setting(gui, id).resetAll();
			}
		});
		check(SalamiClient.get().theme().accent() != 0xFF33CC66, "interface colors stayed after reset");
	}

	private static void testHitboxColors(ClientGameTestContext context, ModuleManager modules) {
		// lädt die Klasse – das Mixin muss alle drei Farben finden
		context.runOnClient(c -> {
			try {
				Class.forName("net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer");
			} catch (ClassNotFoundException e) {
				throw new AssertionError(e);
			}
		});
		HitboxModule hitbox = modules.get(HitboxModule.class);
		check(HitboxModule.boxColor(-1) == -1, "hitbox color changed while the module is off");
		context.runOnClient(c -> {
			modules.setEnabled(hitbox, true);
			set(hitbox, "color", 0xFF00FFAA);
			set(hitbox, "look_line", false);
		});
		check(HitboxModule.boxColor(-1) == 0xFF00FFAA, "hitbox color not used");
		check(HitboxModule.lookColor(-16776961) == 0 && HitboxModule.eyeColor(-65536) == 0xFFFF0000, "hitbox line settings not used");
		context.runOnClient(c -> {
			setting(hitbox, "color").resetAll();
			setting(hitbox, "look_line").resetAll();
			modules.setEnabled(hitbox, false);
		});
	}

	// ------------------------------------------------------------------ Helfer

	private static Setting<?> setting(Module module, String id) {
		for (Setting<?> setting : module.settings()) {
			if (setting.id().equals(id)) {
				return setting;
			}
		}
		throw new AssertionError(module.id() + " has no setting " + id);
	}

	@SuppressWarnings("unchecked")
	private static void set(Module module, String id, Object value) {
		((Setting<Object>) setting(module, id)).set(value);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
