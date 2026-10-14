package dev.salamiclient.gametest;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.gui.ModSettingsScreen;
import dev.salamiclient.gui.ModsScreen;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.client.SafeModeModule;
import dev.salamiclient.modules.hud.CpsModule;
import dev.salamiclient.modules.hud.KeystrokesModule;
import dev.salamiclient.modules.hud.PotionStatusModule;
import dev.salamiclient.modules.hud.ReachDisplayModule;
import dev.salamiclient.modules.hud.ShieldStatusModule;
import dev.salamiclient.modules.hud.ToggleSprintModule;
import dev.salamiclient.modules.visual.FullBrightModule;
import dev.salamiclient.modules.visual.HideArmorModule;
import dev.salamiclient.modules.visual.HitColorModule;
import dev.salamiclient.modules.visual.HitboxModule;
import dev.salamiclient.modules.visual.LowFireModule;
import dev.salamiclient.modules.visual.NameTagsModule;
import dev.salamiclient.modules.visual.NoHurtCamModule;
import dev.salamiclient.modules.visual.SideShieldModule;
import dev.salamiclient.modules.visual.ZoomModule;
import dev.salamiclient.setting.Setting;
import dev.salamiclient.util.ClickTracker;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.EntityHitResult;
import org.apache.commons.lang3.function.FailableFunction;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Tests der Mods aus Batch A (Mods-Menü, Optik, HUD) in einer echten Welt. */
public final class SalamiModsGameTest implements FabricClientGameTest {
	private static final int FEET_Y = -60;

	@Override
	public void runTest(ClientGameTestContext context) {
		ModuleManager modules = SalamiClient.get().modules();
		context.runOnClient(c -> modules.setEnabled(modules.get(SafeModeModule.class), false));

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientWorld().waitForChunksRender();
			TestServerContext server = world.getServer();
			server.runCommand("time set day");
			server.runCommand("gamerule doDaylightCycle false");

			testModsScreen(context, modules);
			testVisuals(context, server, modules);
			testZoom(context, modules);
			testClicksAndReach(context, server, modules);
			testToggleSprint(context, server, modules);
			testHud(context, server, modules);
		}
	}

	// ------------------------------------------------------------------ Mods-Menü

	private static void testModsScreen(ClientGameTestContext context, ModuleManager modules) {
		context.setScreen(() -> new ModsScreen(modules, null));
		context.waitTicks(3);
		context.takeScreenshot("salami-20-mods-menu");

		context.getInput().typeChars("zoom");
		context.waitTick();
		context.takeScreenshot("salami-21-mods-search");

		Module zoom = modules.get(ZoomModule.class);
		context.setScreen(() -> new ModSettingsScreen(modules, zoom, null));
		context.waitTicks(2);
		context.takeScreenshot("salami-22-mod-settings");
		context.setScreen(() -> null);
	}

	// ------------------------------------------------------------------ Optik

	private static void testVisuals(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		server.runCommand("clear @a");
		server.runCommand("item replace entity @p weapon.offhand with minecraft:shield");
		server.runCommand("item replace entity @p armor.chest with minecraft:diamond_chestplate");
		server.runCommand("item replace entity @p armor.head with minecraft:diamond_helmet");
		// Kein Feuerschutz: der würde das Feuer im Bild ausblenden. Resistenz 255 = kein Schaden.
		server.runCommand("effect give @p minecraft:resistance 60 255 true");
		server.runCommand("gamemode survival @p");
		server.runCommand("tp @p 140.5 " + FEET_Y + " 0.5 0 0");
		context.waitTicks(10); // Chunk bei x=140 erst laden lassen, sonst greift setblock nicht
		server.runCommand("setblock 140 " + FEET_Y + " 0 minecraft:lava");
		// hinter dem Spieler: in der Third-Person-Front-Ansicht sichtbar, ohne die Kamera zu verdecken
		server.runCommand("summon minecraft:zombie 141.5 " + FEET_Y + " -2.5 {NoAI:1b,PersistenceRequired:1b,Tags:[\"salami_visual\"]}");

		List<Class<? extends Module>> visuals = List.of(SideShieldModule.class, HideArmorModule.class, LowFireModule.class,
			NoHurtCamModule.class, HitColorModule.class, HitboxModule.class, FullBrightModule.class, NameTagsModule.class);
		for (Class<? extends Module> type : visuals) {
			context.runOnClient(c -> modules.setEnabled(modules.get(type), true));
		}
		check(waitFor(context, 40, c -> c.player.isOnFire()), "Test setup: player is not burning");
		server.runCommand("setblock 140 " + FEET_Y + " 0 minecraft:air");

		// Blocken: Side Shield schiebt den Schild zur Seite
		context.getInput().holdKey(options -> options.keyUse);
		context.waitTicks(8);
		check(context.computeOnClient(c -> c.player.isBlocking()), "Test setup: player is not blocking with the shield");
		check(context.computeOnClient(c -> c.player.isOnFire()), "Test setup: player stopped burning before the screenshot");
		context.takeScreenshot("salami-23-visuals-first-person");
		context.getInput().releaseKey(options -> options.keyUse);
		// Vergleich: Feuer ohne Low Fire
		context.runOnClient(c -> modules.setEnabled(modules.get(LowFireModule.class), false));
		context.waitTicks(2);
		context.takeScreenshot("salami-23b-fire-vanilla");
		context.runOnClient(c -> modules.setEnabled(modules.get(LowFireModule.class), true));

		check(context.computeOnClient(c -> c.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)),
			"Hitbox: entity hitboxes not shown");
		check(FullBrightModule.gamma(0.5F) >= 10.0F, "FullBright: gamma not raised");
		check(LowFireModule.offset() > 0.0F, "Low Fire: no offset");

		server.runCommand("setblock 140 " + FEET_Y + " 0 minecraft:air");
		server.runCommand("tp @p 140.5 " + FEET_Y + " 0.5 0 0");
		context.runOnClient(c -> c.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(20);
		check(context.computeOnClient(c -> NameTagsModule.showOwn(c.player)), "NameTags: own name tag not shown in third person");
		context.takeScreenshot("salami-24-visuals-third-person");

		// Vergleich: Hitboxen über das echte F3 + B statt über das Modul
		context.runOnClient(c -> modules.setEnabled(modules.get(HitboxModule.class), false));
		context.getInput().holdKey(GLFW.GLFW_KEY_F3);
		context.getInput().pressKey(GLFW.GLFW_KEY_B);
		context.getInput().releaseKey(GLFW.GLFW_KEY_F3);
		context.waitTicks(5);
		boolean vanillaOn = context.computeOnClient(c -> c.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES));
		context.takeScreenshot("salami-24b-hitbox-vanilla-f3b");
		if (vanillaOn) {
			context.runOnClient(c -> c.debugEntries.toggleStatus(DebugScreenEntries.ENTITY_HITBOXES));
		}
		context.runOnClient(c -> c.options.setCameraType(CameraType.FIRST_PERSON));

		// alles läuft weiter – ein Modul, das wirft, schaltet der ModuleManager ab
		for (Class<? extends Module> type : visuals) {
			if (type == HitboxModule.class) {
				continue;
			}
			check(context.computeOnClient(c -> modules.get(type).isEnabled()), type.getSimpleName() + " crashed and was disabled");
			context.runOnClient(c -> modules.setEnabled(modules.get(type), false));
		}
		check(!context.computeOnClient(c -> c.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)),
			"Hitbox: hitboxes still shown after disabling");
		server.runCommand("kill @e[type=minecraft:zombie,tag=salami_visual]");
		server.runCommand("gamemode creative @p");
		server.runCommand("effect clear @p");
		server.runCommand("clear @a");
	}

	// ------------------------------------------------------------------ Zoom

	private static void testZoom(ClientGameTestContext context, ModuleManager modules) {
		Module zoom = modules.get(ZoomModule.class);
		set(zoom, "animation", false);
		context.runOnClient(c -> modules.setEnabled(zoom, true));
		context.getInput().holdKey(GLFW.GLFW_KEY_C);
		context.waitTicks(3);
		float zoomed = context.computeOnClient(c -> ZoomModule.applyFov(70.0F, 0.0F));
		context.takeScreenshot("salami-25-zoom");
		context.getInput().scroll(2);
		context.waitTick();
		float scrolled = context.computeOnClient(c -> ZoomModule.applyFov(70.0F, 0.0F));
		context.getInput().releaseKey(GLFW.GLFW_KEY_C);
		context.waitTicks(2);
		float released = context.computeOnClient(c -> ZoomModule.applyFov(70.0F, 0.0F));
		context.runOnClient(c -> modules.setEnabled(zoom, false));
		set(zoom, "animation", true);

		check(zoomed < 20.0F, "Zoom: FOV not reduced while holding the key (" + zoomed + ")");
		check(scrolled < zoomed, "Zoom: scrolling did not zoom further (" + scrolled + " vs " + zoomed + ")");
		check(Math.abs(released - 70.0F) < 0.01F, "Zoom: FOV not restored (" + released + ")");
	}

	// ------------------------------------------------------------------ CPS + Reach

	private static void testClicksAndReach(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		server.runCommand("tp @p 150.5 " + FEET_Y + " 0.5 0 0");
		context.waitTicks(10);
		server.runCommand("summon minecraft:zombie 150.5 " + FEET_Y + " 3.0 {NoAI:1b,PersistenceRequired:1b,Tags:[\"salami_reach\"]}");
		context.waitTicks(5);
		check(waitFor(context, 10, c -> c.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Zombie),
			"Test setup: zombie not in crosshair");

		Module reach = modules.get(ReachDisplayModule.class);
		context.runOnClient(c -> modules.setEnabled(reach, true));
		int before = context.computeOnClient(c -> ClickTracker.left());
		for (int i = 0; i < 3; i++) {
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTick();
		}
		int after = context.computeOnClient(c -> ClickTracker.left());
		double measured = context.computeOnClient(c -> ((ReachDisplayModule) reach).lastReach());
		context.runOnClient(c -> modules.setEnabled(reach, false));
		server.runCommand("kill @e[type=minecraft:zombie,tag=salami_reach]");

		check(after >= before + 3, "CPS: clicks not counted (" + before + " -> " + after + ")");
		check(measured > 1.0 && measured < 3.5, "Reach Display: unexpected reach " + measured);
	}

	// ------------------------------------------------------------------ Toggle Sprint

	private static void testToggleSprint(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		server.runCommand("tp @p 160.5 " + FEET_Y + " 0.5 0 0");
		server.runCommand("effect clear @p");
		context.waitTicks(3);
		Module sprint = modules.get(ToggleSprintModule.class);
		set(sprint, "mode", ToggleSprintModule.Mode.ALWAYS);
		context.runOnClient(c -> modules.setEnabled(sprint, true));
		context.getInput().holdKey(options -> options.keyUp);
		boolean sprinting = waitFor(context, 20, c -> c.player.isSprinting());
		context.getInput().releaseKey(options -> options.keyUp);
		context.runOnClient(c -> modules.setEnabled(sprint, false));
		set(sprint, "mode", ToggleSprintModule.Mode.TOGGLE);
		check(sprinting, "Toggle Sprint: player did not sprint while walking forward");
		check(!context.computeOnClient(c -> c.options.keySprint.isDown()), "Toggle Sprint: sprint key still held after disabling");
	}

	// ------------------------------------------------------------------ HUD

	private static void testHud(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		server.runCommand("clear @a");
		server.runCommand("item replace entity @p weapon.offhand with minecraft:shield");
		server.runCommand("effect give @p minecraft:speed 120 1");
		server.runCommand("effect give @p minecraft:strength 8 0");
		List<Class<? extends Module>> hud = List.of(KeystrokesModule.class, CpsModule.class, PotionStatusModule.class,
			ReachDisplayModule.class, ShieldStatusModule.class);
		for (Class<? extends Module> type : hud) {
			context.runOnClient(c -> modules.setEnabled(modules.get(type), true));
		}
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(5);
		context.takeScreenshot("salami-26-hud-mods");
		context.getInput().releaseKey(options -> options.keyUp);
		for (Class<? extends Module> type : hud) {
			check(context.computeOnClient(c -> modules.get(type).isEnabled()), type.getSimpleName() + " crashed and was disabled");
			context.runOnClient(c -> modules.setEnabled(modules.get(type), false));
		}
		server.runCommand("effect clear @p");
		server.runCommand("clear @a");
	}

	// ------------------------------------------------------------------ Helfer

	private static boolean waitFor(ClientGameTestContext context, int ticks, FailableFunction<Minecraft, Boolean, RuntimeException> condition) {
		for (int i = 0; i < ticks; i++) {
			if (context.computeOnClient(condition)) {
				return true;
			}
			context.waitTick();
		}
		return context.computeOnClient(condition);
	}

	@SuppressWarnings("unchecked")
	private static void set(Module module, String settingId, Object value) {
		for (Setting<?> setting : module.settings()) {
			if (setting.id().equals(settingId)) {
				((Setting<Object>) setting).set(value);
				return;
			}
		}
		throw new IllegalArgumentException(module.id() + " has no setting " + settingId);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
