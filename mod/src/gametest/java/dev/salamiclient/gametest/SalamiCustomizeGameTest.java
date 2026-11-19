package dev.salamiclient.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.config.ConfigManager;
import dev.salamiclient.gui.ColorPicker;
import dev.salamiclient.gui.HudEditorScreen;
import dev.salamiclient.gui.ModSettingsScreen;
import dev.salamiclient.gui.clickgui.ClickGuiScreen;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.module.HudStyle;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.modules.hud.ClockModule;
import dev.salamiclient.modules.hud.CpsModule;
import dev.salamiclient.modules.hud.KeystrokesModule;
import dev.salamiclient.modules.hud.PotionStatusModule;
import dev.salamiclient.modules.visual.ArmorHudModule;
import dev.salamiclient.modules.visual.HitboxModule;
import dev.salamiclient.modules.visual.InfoHudModule;
import dev.salamiclient.modules.visual.NameTagsModule;
import dev.salamiclient.modules.visual.WatermarkModule;
import dev.salamiclient.setting.ColorSetting;
import dev.salamiclient.setting.Setting;
import dev.salamiclient.util.Projection;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Anpassen: Farbwähler (Hex, HSV, Regenbogen, Speichern), Darstellung aller HUD-Elemente
 * (eigene Farben, Hintergrund), eigene Oberflächen-Farben und Hitbox-Farben.
 */
public final class SalamiCustomizeGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!GametestCompat.selected(this)) {
			return;
		}
		ModuleManager modules = SalamiClient.get().modules();

		testColorSetting();

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			GametestCompat.waitForChunksRender(world);
			world.getServer().runCommand("tp @p 500.5 -60 0.5 0 10");
			context.waitTicks(10);

			testHudAppearance(context, modules);
			testHudStyles(context, world, modules);
			testHudEditor(context, modules);
			// Rüstung und Effekte der HUD-Screenshots wieder ablegen (der Namensschild-Test erwartet keine Rüstung)
			world.getServer().runCommand("clear @p");
			world.getServer().runCommand("effect clear @p");
			context.waitTicks(2);
			testColorPicker(context, modules);
			testInterfaceColors(context, modules);
			testHitboxColors(context, modules);
			testNameTags(context, modules, world);
			testItemTags(context, modules);
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
		check(setting(cps, "background_color").isHidden() && setting(cps, "background").isHidden(),
			"own background visible while own style is off");
		// Darstellung steht nach den Modul-Einstellungen
		List<Setting<?>> order = cps.settings();
		check(order.indexOf(setting(cps, "show_right")) < order.indexOf(setting(cps, "scale")), "appearance settings not at the end");

		context.runOnClient(c -> {
			for (HudModule module : List.of(cps, info, keys)) {
				set(module, "custom_colors", true);
				set(module, "text_color", 0xFF55FFFF);
				set(module, "accent_color", 0xFFFFAA00);
				set(module, "own_style", true);
				set(module, "background", true);
				set(module, "background_color", 0xA0202060);
			}
		});
		check(!setting(cps, "background_color").isHidden(), "background color hidden while own style and background are on");
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
				for (String id : List.of("custom_colors", "text_color", "accent_color", "background_color", "background", "own_style")) {
					setting(module, id).resetAll();
				}
				modules.setEnabled(module, false);
			}
		});
		check(cps.textColor() == SalamiClient.get().theme().text(), "HUD does not fall back to the theme");
	}

	/** HUD-Elemente, die für Stil- und Editor-Screenshots eingeschaltet werden. */
	private static List<HudModule> showcase(ModuleManager modules) {
		return List.of(modules.get(WatermarkModule.class), modules.get(InfoHudModule.class), modules.get(CpsModule.class),
			modules.get(KeystrokesModule.class), modules.get(ClockModule.class), modules.get(PotionStatusModule.class),
			modules.get(ArmorHudModule.class));
	}

	/** Gemeinsamer HUD-Stil (Clean, Minimal, Klassisch), „Eigener Stil“ pro Element und größere HUD-Größe. */
	private static void testHudStyles(ClientGameTestContext context, TestSingleplayerContext world, ModuleManager modules) {
		GuiScaleModule gui = modules.get(GuiScaleModule.class);
		CpsModule cps = modules.get(CpsModule.class);
		InfoHudModule info = modules.get(InfoHudModule.class);
		world.getServer().runCommand("effect give @p minecraft:speed 120 1");
		world.getServer().runCommand("effect give @p minecraft:night_vision 120 0");
		world.getServer().runCommand("item replace entity @p armor.head with minecraft:diamond_helmet");
		world.getServer().runCommand("item replace entity @p armor.chest with minecraft:iron_chestplate");
		world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_sword");
		context.runOnClient(c -> {
			modules.setEnabled(gui, true);
			for (HudModule module : showcase(modules)) {
				modules.setEnabled(module, true);
			}
		});
		context.waitTicks(5);

		HudStyle clean = context.computeOnClient(c -> cps.style());
		check(clean.hasBackground() && clean.radius() == 3 && !clean.shadow() && !clean.accentLabels(), "Clean style wrong: " + clean);
		check(context.computeOnClient(c -> cps.labelColor() != cps.accentColor()), "Clean style: labels in accent color");
		context.takeScreenshot("salami-64-hud-clean");

		context.runOnClient(c -> set(gui, "hud_style", GuiScaleModule.HudLook.MINIMAL));
		HudStyle minimal = context.computeOnClient(c -> cps.style());
		check(!minimal.hasBackground() && minimal.shadow(), "Minimal style wrong: " + minimal);
		check(setting(gui, "hud_radius").isHidden() && setting(gui, "hud_opacity").isHidden(), "Minimal style shows background settings");
		context.waitTicks(2);
		context.takeScreenshot("salami-65-hud-minimal");

		context.runOnClient(c -> set(gui, "hud_style", GuiScaleModule.HudLook.CLASSIC));
		HudStyle classic = context.computeOnClient(c -> cps.style());
		check(classic.hasBackground() && classic.radius() == 0 && classic.accentLabels(), "Classic style wrong: " + classic);
		check(context.computeOnClient(c -> cps.labelColor() == cps.accentColor()), "Classic style: labels not in accent color");
		context.waitTicks(2);
		context.takeScreenshot("salami-66-hud-classic");
		context.runOnClient(c -> setting(gui, "hud_style").resetAll());

		// Eigener Stil gilt nur für das eine Element
		context.runOnClient(c -> {
			set(cps, "own_style", true);
			set(cps, "background", false);
		});
		check(!context.computeOnClient(c -> cps.style()).hasBackground() && context.computeOnClient(c -> info.style()).hasBackground(),
			"own style leaked into other elements");
		context.runOnClient(c -> {
			setting(cps, "own_style").resetAll();
			setting(cps, "background").resetAll();
		});

		// Größer als früher möglich: HUD bis 300 %, einzelne Elemente bis 400 %
		context.runOnClient(c -> {
			set(gui, "hud_scale", 3.0);
			set(cps, "scale", 4.0);
		});
		check(Math.abs(GuiScaleModule.hudScale() - 3F) < 0.001F && Math.abs(cps.effectiveScale() - 12F) < 0.001F,
			"HUD scale not raised: " + cps.effectiveScale());
		context.runOnClient(c -> {
			setting(gui, "hud_scale").resetAll();
			setting(cps, "scale").resetAll();
		});
	}

	/** HUD-Editor: Mausrad und Griff vergrößern, Einrasten an der Bildschirmmitte, Rechtsklick öffnet Einstellungen. */
	private static void testHudEditor(ClientGameTestContext context, ModuleManager modules) {
		CpsModule cps = modules.get(CpsModule.class);
		InfoHudModule info = modules.get(InfoHudModule.class);
		context.setScreen(() -> new HudEditorScreen(null));
		context.waitTicks(2);
		context.takeScreenshot("salami-67-hud-editor");

		// Mausrad über CPS: +5 %
		int[] box = context.computeOnClient(c -> guiBox(c, cps));
		moveTo(context, box[0] + box[2] / 2, box[1] + box[3] / 2);
		context.getInput().scroll(1);
		context.waitTick();
		check(Math.abs(cps.scale.get() - 1.05) < 0.001, "mouse wheel did not grow the element: " + cps.scale.get());

		// Griff unten rechts ziehen: doppelte Breite = doppelte Größe
		box = context.computeOnClient(c -> guiBox(c, cps));
		double before = cps.scale.get();
		moveTo(context, box[0] + box[2] - 2, box[1] + box[3] - 2);
		context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
		moveTo(context, box[0] + box[2] * 2 - 2, box[1] + box[3] - 2);
		context.waitTick();
		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
		check(cps.scale.get() > before * 1.6, "resize handle did not grow the element: " + before + " -> " + cps.scale.get());

		// Info HUD knapp neben die Bildschirmmitte ziehen: linke Kante rastet auf der Mitte ein
		int[] infoBox = context.computeOnClient(c -> guiBox(c, info));
		int center = context.computeOnClient(c -> c.getWindow().getGuiScaledWidth() / 2);
		moveTo(context, infoBox[0] + 3, infoBox[1] + 3);
		context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
		moveTo(context, center + 2 + 3, infoBox[1] + 3);
		context.waitTick();
		context.takeScreenshot("salami-68-hud-editor-snap");
		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
		int snappedX = context.computeOnClient(c -> guiBox(c, info)[0]);
		check(snappedX == center, "element did not snap to the screen center: x=" + snappedX + ", center=" + center);

		// Elementliste und Rechtsklick → Einstellungen des Elements
		context.runOnClient(c -> HudEditorScreen.showElementList(true));
		context.waitTicks(2);
		context.takeScreenshot("salami-69-hud-editor-elements");
		context.runOnClient(c -> HudEditorScreen.showElementList(false));
		box = context.computeOnClient(c -> guiBox(c, cps));
		moveTo(context, box[0] + 3, box[1] + 3);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		context.waitTick();
		check(context.computeOnClient(c -> c.screen instanceof ModSettingsScreen), "right click did not open the element settings");
		context.waitTicks(2);
		context.takeScreenshot("salami-70-hud-element-settings");

		context.setScreen(() -> null);
		context.runOnClient(c -> {
			for (HudModule module : showcase(modules)) {
				module.resetLayout();
				modules.setEnabled(module, module.enabledByDefault());
			}
		});
	}

	/** Kasten eines HUD-Elements in GUI-Koordinaten {x, y, Breite, Höhe}. */
	private static int[] guiBox(Minecraft client, HudModule hud) {
		int w = client.getWindow().getGuiScaledWidth();
		int h = client.getWindow().getGuiScaledHeight();
		float s = hud.effectiveScale();
		return new int[] {hud.screenX(w), hud.screenY(h), Math.round(hud.boxWidth() * s), Math.round(hud.boxHeight() * s)};
	}

	/** Maus auf GUI-Koordinaten setzen (wird auf Fensterpixel umgerechnet). */
	private static void moveTo(ClientGameTestContext context, double guiX, double guiY) {
		double scale = context.computeOnClient(client -> (double) client.getWindow().getGuiScale());
		context.getInput().setCursorPos(guiX * scale + scale / 2, guiY * scale + scale / 2);
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
			picker.mouseClicked(c.font, b[0] + 30, b[1] + 20, InputConstants.MOUSE_BUTTON_LEFT); // ins Farbfeld
			picker.mouseReleased();
			return text.get();
		});
		check(after != before, "clicking the color field did not change the color");
		// Hex-Eingabe
		context.runOnClient(c -> {
			int[] b = picker.bounds();
			picker.mouseClicked(c.font, b[2] - 20, b[1] + 6 + 72 + 6 + 4, InputConstants.MOUSE_BUTTON_LEFT); // Hex-Feld
		});
		check(picker.isEditingText(), "hex field not editable");
		context.waitTick();
		check(context.computeOnClient(GametestCompat::acceptsTypedText), "hex field: window does not accept typed text");
		context.runOnClient(c -> {
			for (int i = 0; i < 9; i++) {
				picker.keyPressed(InputConstants.KEY_BACKSPACE);
			}
			picker.charTyped("#3366CC");
			picker.keyPressed(InputConstants.KEY_RETURN);
		});
		check(text.get() == 0xFF3366CC, "hex input not applied: " + Integer.toHexString(text.get()));
		check(context.computeOnClient(c -> ((ModSettingsScreen) c.screen).wasCapturingKey()) == false, "still capturing keys after Enter");
		if (GametestCompat.TEXT_INPUT_GATED) {
			context.waitTick();
			check(!context.computeOnClient(GametestCompat::acceptsTypedText), "hex field: text input still on after Enter");
		}

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
		// lädt die Klasse – das Mixin muss alle drei Farben finden (bis 1.21.10: HitboxFeatureRenderer)
		context.runOnClient(c -> {
			try {
				//? if >=1.21.11 {
				Class.forName("net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer");
				//?} else
				/*Class.forName("net.minecraft.client.renderer.feature.HitboxFeatureRenderer");*/
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

	private static void testNameTags(ClientGameTestContext context, ModuleManager modules, TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("summon minecraft:zombie 500.5 -60 6.5 {NoAI:1b,PersistenceRequired:1b,CustomName:\"Testzombie\",CustomNameVisible:1b}");
		server.runCommand("summon minecraft:cow 503.5 -60 6.5 {NoAI:1b,PersistenceRequired:1b}");
		server.runCommand("summon minecraft:item 497.5 -60 5.5 {Item:{id:\"minecraft:diamond\",count:1},PickupDelay:32767}");
		server.runCommand("item replace entity @p armor.chest with minecraft:diamond_chestplate");
		context.waitTicks(20);

		NameTagsModule tags = modules.get(NameTagsModule.class);
		context.runOnClient(c -> {
			modules.setEnabled(tags, true);
			set(tags, "mobs", true);
			c.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
		});
		AABB area = new AABB(490, -64, -5, 510, -50, 15);

		String zombieTag = context.computeOnClient(c -> {
			Zombie zombie = c.level.getEntitiesOfClass(Zombie.class, area).stream().findFirst().orElseThrow();
			return NameTagsModule.decorate(Component.literal("Testzombie"), zombie).getString();
		});
		// Zombies haben von Natur aus 2 Rüstungspunkte; in der Sonne verlieren sie Leben
		check(zombieTag.startsWith("Testzombie") && zombieTag.matches(".*heart.* \\d+\\.\\d.*armor.* 2"),
			"zombie name tag without health/armor: " + zombieTag);
		String ownTag = context.computeOnClient(c -> NameTagsModule.decorate(Component.literal("Player0"), c.player).getString());
		check(ownTag.contains(" 8"), "own name tag without armor: " + ownTag);
		context.runOnClient(c -> {
			set(tags, "scale", 2.0);
			set(tags, "distance_scale", true);
		});
		check(Math.abs(NameTagsModule.scale(20 * 20) - 5F) < 0.01F && Math.abs(NameTagsModule.scale(4) - 2F) < 0.01F,
			"name tag scale wrong: " + NameTagsModule.scale(400));
		context.runOnClient(c -> set(tags, "distance_scale", false));
		context.waitTicks(5);
		context.takeScreenshot("salami-63-nametags");

		context.runOnClient(c -> {
			c.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
			for (String id : List.of("mobs", "scale", "distance_scale")) {
				setting(tags, id).resetAll();
			}
			modules.setEnabled(tags, false);
		});
		check(NameTagsModule.scale(400) == 1F, "name tags still scaled while off");
		server.runCommand("kill @e[type=!player]");
		server.runCommand("item replace entity @p armor.chest with minecraft:air");
	}

	/**
	 * NameTags-Item-Zeile und Bildschirm-Projektion mit Spielern, die nur im Client existieren: einer vorne in
	 * (teils beschädigter) Rüstung ohne Schuhe, einer hinter der Kamera, einer unsichtbar.
	 */
	private static void testItemTags(ClientGameTestContext context, ModuleManager modules) {
		NameTagsModule tags = modules.get(NameTagsModule.class);
		context.waitTicks(25); // Mobs aus dem vorigen Test zu Ende sterben lassen (ihre Namen lägen sonst darüber)
		RemotePlayer[] fakes = context.computeOnClient(c -> {
			RemotePlayer front = fakePlayer(c, 900_001, "Ruestung", 498.5, -60, 5.5);
			ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
			sword.setDamageValue(sword.getMaxDamage() * 3 / 4);
			ItemStack chest = new ItemStack(Items.NETHERITE_CHESTPLATE);
			chest.setDamageValue(chest.getMaxDamage() / 2);
			front.setItemSlot(EquipmentSlot.MAINHAND, sword);
			front.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
			front.setItemSlot(EquipmentSlot.CHEST, chest);
			front.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
			front.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING)); // Füße bleiben leer
			RemotePlayer behind = fakePlayer(c, 900_002, "Hinten", 503.5, -60, -6.5);
			RemotePlayer invisible = fakePlayer(c, 900_003, "Unsichtbar", 497.5, -60, 7.5);
			invisible.setInvisible(true);
			return new RemotePlayer[] {front, behind, invisible};
		});
		context.runOnClient(c -> {
			modules.setEnabled(tags, true);
		});
		check(!setting(tags, "durability").isHidden(), "NameTags: durability hidden while items are on");
		context.waitTicks(5);

		boolean projected = context.computeOnClient(c -> {
			if (!Projection.begin()) {
				return false;
			}
			float w = c.getWindow().getGuiScaledWidth();
			float h = c.getWindow().getGuiScaledHeight();
			float[] out = new float[3];
			boolean front = Projection.toGuiScaled(fakes[0].position().add(0, 2.3, 0), w, h, out)
				&& out[0] > 0 && out[0] < w && out[1] > 0 && out[1] < h && out[2] > 0;
			Projection.toGuiOrEdge(fakes[1].position(), w, h, out);
			boolean behindOutside = out[0] < 0 || out[0] > w || out[1] < 0 || out[1] > h;
			return front && behindOutside;
		});
		check(projected, "Projection: player in front not on screen or player behind not at the screen edge");

		context.runOnClient(c -> set(tags, "range", 8.0));
		check(!NameTagsModule.inRange(10 * 10) && NameTagsModule.inRange(5 * 5), "NameTags: range not applied");
		context.runOnClient(c -> setting(tags, "range").resetAll());
		context.waitTicks(3);
		context.takeScreenshot("salami-64-nametag-items");

		context.runOnClient(c -> {
			for (RemotePlayer fake : fakes) {
				c.level.removeEntity(fake.getId(), Entity.RemovalReason.DISCARDED);
			}
			modules.setEnabled(tags, false);
		});
		check(NameTagsModule.inRange(60 * 60), "NameTags: range still applied while off");
	}

	private static RemotePlayer fakePlayer(Minecraft c, int id, String name, double x, double y, double z) {
		RemotePlayer player = new RemotePlayer(c.level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
		player.setId(id);
		player.snapTo(x, y, z, 180F, 0F); // schaut zur Kamera
		c.level.addEntity(player);
		return player;
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
