package dev.salamiclient.gametest;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.gui.WaypointsScreen;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.client.SafeModeModule;
import dev.salamiclient.modules.hud.ClockModule;
import dev.salamiclient.modules.hud.DayCounterModule;
import dev.salamiclient.modules.hud.JumpResetModule;
import dev.salamiclient.modules.hud.ResourcePackDisplayModule;
import dev.salamiclient.modules.hud.SpeedometerModule;
import dev.salamiclient.modules.hud.TpsModule;
import dev.salamiclient.modules.hud.UptimeModule;
import dev.salamiclient.modules.misc.AutoReconnectModule;
import dev.salamiclient.modules.misc.AutoTextModule;
import dev.salamiclient.modules.misc.ChatTweaksModule;
import dev.salamiclient.modules.misc.ConfirmDisconnectModule;
import dev.salamiclient.modules.misc.NoAdvancementModule;
import dev.salamiclient.modules.misc.StreamerModeModule;
import dev.salamiclient.modules.misc.TabTweaksModule;
import dev.salamiclient.modules.misc.WaypointsModule;
import dev.salamiclient.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Tests der Mods aus Batch C (HUD-Infos, Chat, Verbindung, Wegpunkte). */
public final class SalamiModsBatchCGameTest implements FabricClientGameTest {
	private static final int FEET_Y = -60;

	@Override
	public void runTest(ClientGameTestContext context) {
		ModuleManager modules = SalamiClient.get().modules();
		context.runOnClient(c -> modules.setEnabled(modules.get(SafeModeModule.class), false));

		// Cheats an: AutoText wird über einen Befehl geprüft, den der Spieler selbst ausführen darf
		try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(settings -> settings.setAllowCommands(true)).create()) {
			world.getClientWorld().waitForChunksRender();
			TestServerContext server = world.getServer();
			server.runCommand("time set day");
			server.runCommand("tp @p 300.5 " + FEET_Y + " 0.5 0 10");
			context.waitTicks(20);

			testHudInfos(context, modules);
			testChat(context, server, modules);
			testWaypoints(context, modules);
			testConnectionScreens(context, modules);
		}
	}

	// ------------------------------------------------------------------ HUD-Infos

	private static void testHudInfos(ClientGameTestContext context, ModuleManager modules) {
		List<Class<? extends Module>> infos = List.of(ClockModule.class, SpeedometerModule.class, UptimeModule.class,
			DayCounterModule.class, TpsModule.class, ResourcePackDisplayModule.class, JumpResetModule.class, TabTweaksModule.class);
		for (Class<? extends Module> type : infos) {
			context.runOnClient(c -> modules.setEnabled(modules.get(type), true));
		}
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(70); // Zeit-Pakete kommen jede Sekunde – genug für eine TPS-Messung
		context.getInput().holdKey(options -> options.keyPlayerList);
		context.waitTicks(3);
		context.takeScreenshot("salami-40-hud-infos-tab");
		context.getInput().releaseKey(options -> options.keyPlayerList);
		context.getInput().releaseKey(options -> options.keyUp);
		for (Class<? extends Module> type : infos) {
			check(context.computeOnClient(c -> modules.get(type).isEnabled()), type.getSimpleName() + " crashed and was disabled");
			context.runOnClient(c -> modules.setEnabled(modules.get(type), false));
		}
	}

	// ------------------------------------------------------------------ Chat

	private static void testChat(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		Module autoText = modules.get(AutoTextModule.class);
		set(autoText, "key_1", GLFW.GLFW_KEY_K);
		// Befehl statt Chat: lässt sich auf dem Server sicher nachprüfen
		set(autoText, "text_1", "/tag @s add salami_autotext");
		List<Class<? extends Module>> chat = List.of(AutoTextModule.class, StreamerModeModule.class, ChatTweaksModule.class, NoAdvancementModule.class);
		for (Class<? extends Module> type : chat) {
			context.runOnClient(c -> modules.setEnabled(modules.get(type), true));
		}
		context.getInput().holdKeyFor(GLFW.GLFW_KEY_K, 3);
		context.waitTicks(5);
		check(server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getTags().contains("salami_autotext")),
			"AutoText: command was not sent");

		String own = context.computeOnClient(c -> c.getUser().getName());
		String hidden = context.computeOnClient(c -> StreamerModeModule.hideName(Component.literal("<" + own + "> hallo")).getString());
		check(!hidden.contains(own) && hidden.contains("Salami"), "Streamer Mode: own name still visible: " + hidden);
		String stamped = context.computeOnClient(c -> ChatTweaksModule.decorate(Component.literal("x")).getString());
		check(stamped.matches("\\[\\d\\d:\\d\\d] x"), "Chat Tweaks: no timestamp: " + stamped);
		context.takeScreenshot("salami-41-chat");

		for (Class<? extends Module> type : chat) {
			check(context.computeOnClient(c -> modules.get(type).isEnabled()), type.getSimpleName() + " crashed and was disabled");
			context.runOnClient(c -> modules.setEnabled(modules.get(type), false));
		}
		set(autoText, "key_1", -1);
	}

	// ------------------------------------------------------------------ Wegpunkte

	private static void testWaypoints(ClientGameTestContext context, ModuleManager modules) {
		WaypointsModule waypoints = modules.get(WaypointsModule.class);
		context.runOnClient(c -> modules.setEnabled(waypoints, true));
		int before = context.computeOnClient(c -> waypoints.store().current().size());
		context.getInput().holdKeyFor(GLFW.GLFW_KEY_B, 3);
		context.waitTicks(3);
		check(context.computeOnClient(c -> waypoints.store().current().size()) == before + 1, "Waypoints: key B did not add a waypoint");
		context.runOnClient(c -> c.player.setPos(c.player.getX(), c.player.getY(), c.player.getZ() - 12));
		context.waitTicks(5);
		context.takeScreenshot("salami-42-waypoint-world");
		context.setScreen(() -> new WaypointsScreen(waypoints, null));
		context.waitTicks(2);
		context.takeScreenshot("salami-43-waypoints-screen");
		context.setScreen(() -> null);
		context.runOnClient(c -> {
			var list = waypoints.store().current();
			while (list.size() > before) {
				waypoints.store().remove(list.get(list.size() - 1));
			}
		});
		check(context.computeOnClient(c -> waypoints.isEnabled()), "Waypoints crashed and was disabled");
		context.runOnClient(c -> modules.setEnabled(waypoints, false));
	}

	// ------------------------------------------------------------------ Verbindung

	private static void testConnectionScreens(ClientGameTestContext context, ModuleManager modules) {
		// Confirm Disconnect: Trennen-Knopf öffnet zuerst eine Rückfrage
		context.runOnClient(c -> modules.setEnabled(modules.get(ConfirmDisconnectModule.class), true));
		context.setScreen(() -> new PauseScreen(true));
		context.waitTicks(2);
		context.runOnClient(c -> {
			for (var child : c.screen.children()) {
				if (child instanceof Button button && button.getMessage() == CommonComponents.disconnectButtonLabel(c.isLocalServer())) {
					button.onPress(null);
					return;
				}
			}
			throw new AssertionError("Confirm Disconnect: no disconnect button in the pause menu");
		});
		context.waitTick();
		check(context.computeOnClient(c -> c.screen instanceof ConfirmScreen), "Confirm Disconnect: no confirmation screen");
		context.takeScreenshot("salami-44-confirm-disconnect");
		context.setScreen(() -> null);

		// Auto Reconnect: Knopf im Verbindungsabbruch-Bildschirm (ohne Countdown, damit nichts verbindet)
		Module reconnect = modules.get(AutoReconnectModule.class);
		set(reconnect, "auto", false);
		context.runOnClient(c -> modules.setEnabled(reconnect, true));
		context.runOnClient(c -> AutoReconnectModule.rememberServer(new ServerData("Test", "localhost:25565", ServerData.Type.OTHER)));
		context.setScreen(() -> new DisconnectedScreen(new TitleScreen(), Component.literal("Verbindung verloren"), Component.literal("Test")));
		context.waitTicks(2);
		check(context.computeOnClient(c -> c.screen.children().stream().anyMatch(w -> w instanceof Button b
			&& b.getMessage().getString().equals(Component.translatable("salami.module.auto_reconnect.button").getString()))),
			"Auto Reconnect: no reconnect button");
		context.takeScreenshot("salami-45-auto-reconnect");
		context.setScreen(() -> null);
		context.runOnClient(c -> modules.setEnabled(reconnect, false));
		set(reconnect, "auto", true);
	}

	// ------------------------------------------------------------------ Helfer

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
