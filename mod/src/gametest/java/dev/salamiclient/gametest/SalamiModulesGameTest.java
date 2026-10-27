package dev.salamiclient.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.gui.clickgui.ClickGuiScreen;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.modules.client.SafeModeModule;
import dev.salamiclient.modules.utility.AimAssistModule;
import dev.salamiclient.modules.utility.AnchorMacroModule;
import dev.salamiclient.modules.utility.CrystalMacroModule;
import dev.salamiclient.modules.utility.AutoDTapModule;
import dev.salamiclient.modules.utility.BowAimModule;
import dev.salamiclient.modules.utility.BowSpamModule;
import dev.salamiclient.modules.utility.AutoTotemModule;
import dev.salamiclient.modules.utility.DoubleHandTotemModule;
import dev.salamiclient.modules.utility.GhostSpearModule;
import dev.salamiclient.modules.utility.HitboxExpandModule;
import dev.salamiclient.modules.utility.PlayerEspModule;
import dev.salamiclient.modules.utility.TriggerBotModule;
import dev.salamiclient.modules.visual.CapesModule;
import dev.salamiclient.modules.visual.InfoHudModule;
import dev.salamiclient.modules.visual.LiveTrackerModule;
import dev.salamiclient.modules.visual.ParticlesModule;
import dev.salamiclient.setting.Setting;
import dev.salamiclient.util.ArrowBallistics;
import dev.salamiclient.util.CrystalUtil;
import dev.salamiclient.util.InteractUtil;
import dev.salamiclient.util.TargetUtil;
import com.mojang.authlib.GameProfile;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import org.apache.commons.lang3.function.FailableFunction;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Funktionstests der Utility- und Visual-Module in einer echten Welt.
 * Szenen werden per Befehl aufgebaut; geprüft wird der Zustand auf dem Client.
 */
public final class SalamiModulesGameTest implements FabricClientGameTest {
	/** Standhöhe in der flachen Testwelt (Gras auf y=-61). */
	private static final int FEET_Y = -60;

	@Override
	public void runTest(ClientGameTestContext context) {
		ModuleManager modules = SalamiClient.get().modules();
		context.runOnClient(c -> modules.setEnabled(modules.get(SafeModeModule.class), false));

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			GametestCompat.waitForChunksRender(world);
			TestServerContext server = world.getServer();

			testAutoTotem(context, server, modules);
			testDoubleHandTotem(context, server, modules);
			testCrystalMacro(context, server, modules);
			testAnchorMacro(context, server, modules);
			// Speere gibt es erst ab 1.21.11
			//? if >=1.21.11
			testGhostSpear(context, server, modules);
			testLiveTracker(context, server, modules);
			testParticles(context, server, modules);
			testCapes(context, server, modules);
			testSharedCapes(context, modules);
			testLauncherLiveCapes(context);
			testPlayerEsp(context, modules);
			testTriggerBot(context, server, modules);
			testAimAssist(context, server, modules);
			testHitboxExpand(context, server, modules);
			testBallistics();
			testBowSpam(context, server, modules);
			testBowAim(context, server, modules);
			testAutoDTap(context, server, modules);
			testGuiScale(context, modules);
		}
	}

	// ------------------------------------------------------------------ Bogen

	/** Rechnung allein: gefundener Winkel muss das Ziel treffen, Unerreichbares liefert null. */
	private static void testBallistics() {
		Float pitch = ArrowBallistics.solvePitch(3.0, 30, 0);
		check(pitch != null, "Ballistics: no pitch for a 30 block shot");
		ArrowBallistics.Flight flight = ArrowBallistics.fly(3.0, pitch, 30);
		check(flight != null && Math.abs(flight.height()) < 0.05, "Ballistics: solved pitch misses (" + flight + ")");
		check(pitch < 0, "Ballistics: a level 30 block shot must aim slightly upwards, got " + pitch);
		check(ArrowBallistics.solvePitch(0.3, 100, 0) == null, "Ballistics: out-of-range target must be null");
	}

	private static void testBowSpam(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		server.runCommand("clear @a");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:bow");
		server.runCommand("item replace entity @p hotbar.1 with minecraft:arrow 64");
		server.runCommand("kill @e[type=minecraft:arrow]");
		server.runCommand("tp @p 110.5 " + FEET_Y + " 0.5 180 -10");
		context.waitTicks(5);
		context.runOnClient(c -> c.player.getInventory().setSelectedSlot(0));
		Module spam = modules.get(BowSpamModule.class);
		set(spam, "charge", 4.0);
		context.runOnClient(c -> modules.setEnabled(spam, true));
		context.getInput().holdKeyFor(options -> options.keyUse, 30);
		context.waitTicks(3);
		int arrows = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(Arrow.class, new AABB(100, FEET_Y - 5, -60, 121, FEET_Y + 30, 5)).size());
		context.runOnClient(c -> modules.setEnabled(spam, false));
		server.runCommand("kill @e[type=minecraft:arrow]");
		check(arrows >= 3, "Bow Spam: only " + arrows + " arrows in 30 ticks");
	}

	private static void testBowAim(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		server.runCommand("summon minecraft:zombie 124.5 " + FEET_Y + " -14.0 {NoAI:1b,PersistenceRequired:1b,Tags:[\"salami_bow\"]}");
		server.runCommand("tp @p 120.5 " + FEET_Y + " 0.5 180 0");
		context.waitTicks(5);
		Module aim = modules.get(BowAimModule.class);
		set(aim, "players", false);
		set(aim, "mobs", true);
		set(aim, "speed", 30.0);
		context.runOnClient(c -> modules.setEnabled(aim, true));
		context.getInput().holdKey(options -> options.keyUse);
		context.waitTicks(25);
		float[] diff = context.computeOnClient(c -> {
			Zombie zombie = c.level.getEntitiesOfClass(Zombie.class, new AABB(122, FEET_Y - 1, -16, 127, FEET_Y + 3, -12)).getFirst();
			float[] direct = TargetUtil.rotationTo(c.player.getEyePosition(), zombie.getBoundingBox().getCenter());
			return new float[] {Mth.wrapDegrees(direct[0] - c.player.getYRot()), c.player.getXRot() - direct[1]};
		});
		context.getInput().releaseKey(options -> options.keyUse);
		context.runOnClient(c -> modules.setEnabled(aim, false));
		server.runCommand("kill @e[type=minecraft:zombie,tag=salami_bow]");
		server.runCommand("kill @e[type=minecraft:arrow]");
		check(Math.abs(diff[0]) < 2f, "Bow Aim: yaw is " + diff[0] + " degrees off the zombie");
		// Pfeile fallen: über das Ziel zielen, aber nicht absurd hoch
		check(diff[1] < 0f && diff[1] > -10f, "Bow Aim: pitch offset for the arrow drop is " + diff[1]);
	}

	// ------------------------------------------------------------------ Auto D-Tap

	private static void testAutoDTap(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		BlockPos base = new BlockPos(130, FEET_Y, -3);
		server.runCommand("clear @a");
		server.runCommand("gamemode survival @a");
		server.runCommand("effect give @p minecraft:resistance 60 4 true");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:stone_sword");
		server.runCommand("item replace entity @p hotbar.1 with minecraft:end_crystal 16");
		server.runCommand("fill 128 " + FEET_Y + " -5 132 " + (FEET_Y + 3) + " 1 minecraft:air");
		server.runCommand("setblock 130 " + FEET_Y + " -3 minecraft:obsidian");
		server.runCommand("summon minecraft:end_crystal 130.5 " + (FEET_Y + 1) + " -2.5 {ShowBottom:0b}");
		server.runCommand("tp @p 130.5 " + FEET_Y + " 0.5 180 0");
		context.waitTicks(5);
		context.runOnClient(c -> c.player.getInventory().setSelectedSlot(0));
		check(context.computeOnClient(c -> CrystalUtil.crystalOn(base) != null), "Test setup: crystal missing");

		Module dtap = modules.get(AutoDTapModule.class);
		context.runOnClient(c -> modules.setEnabled(dtap, true));
		context.runOnClient(c -> InteractUtil.attack(CrystalUtil.crystalOn(base)));
		boolean firstGone = false;
		boolean secondPlaced = false;
		boolean secondGone = false;
		for (int i = 0; i < 40 && !secondGone; i++) {
			context.waitTick();
			boolean present = context.computeOnClient(c -> CrystalUtil.crystalOn(base) != null);
			if (!firstGone) {
				firstGone = !present;
			} else if (!secondPlaced) {
				secondPlaced = present;
			} else {
				secondGone = !present;
			}
		}
		context.runOnClient(c -> modules.setEnabled(dtap, false));
		check(firstGone, "Auto D-Tap: first crystal was not destroyed");
		check(secondPlaced, "Auto D-Tap: no second crystal was placed");
		check(secondGone, "Auto D-Tap: second crystal was not destroyed");
	}

	// ------------------------------------------------------------------ Oberfläche

	private static void testGuiScale(ClientGameTestContext context, ModuleManager modules) {
		Module gui = modules.get(GuiScaleModule.class);
		HudModule hud = modules.get(InfoHudModule.class);
		float before = context.computeOnClient(c -> hud.effectiveScale());
		set(gui, "hud_scale", 2.0);
		float after = context.computeOnClient(c -> hud.effectiveScale());
		check(Math.abs(after - before * 2) < 0.001f, "GUI scale: HUD scale " + before + " -> " + after);
		set(gui, "hud_scale", 1.0);

		set(gui, "clickgui_scale", 1.5);
		context.setScreen(() -> new ClickGuiScreen(modules, SalamiClient.get().config()));
		context.waitTicks(3);
		context.takeScreenshot("salami-14-clickgui-scaled");
		context.setScreen(() -> null);
		set(gui, "clickgui_scale", 1.0);

		// Größe direkt im Menü: Knopf „+“, Strg + Mausrad, Strg + 0, Strg + −
		context.setScreen(() -> new ClickGuiScreen(modules, SalamiClient.get().config()));
		context.waitTicks(2);
		double[] plus = context.computeOnClient(c -> {
			float ui = GuiScaleModule.clickGuiScale();
			int uiWidth = Math.round(c.getWindow().getGuiScaledWidth() / ui);
			int hudLeft = uiWidth - (c.font.width(I18n.get("salami.clickgui.hud_editor")) + 12) - 6;
			double toWindow = c.getWindow().getGuiScale() * ui;
			return new double[] {(hudLeft - 4 - 7) * toWindow, 11 * toWindow};
		});
		context.getInput().setCursorPos(plus[0], plus[1]);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
		checkClickGuiScale(context, 1.1f, "+ button");
		context.runOnClient(c -> c.getToastManager().clear()); // Rezept-Meldung liegt sonst über den Knöpfen
		context.getInput().setCursorPos(plus[0] - 200, plus[1] + 200);
		context.waitTick();
		context.takeScreenshot("salami-15-clickgui-size-buttons");
		context.getInput().setCursorPos(plus[0], plus[1]);

		context.getInput().holdControl();
		context.getInput().scroll(1);
		context.waitTick();
		checkClickGuiScale(context, 1.2f, "Ctrl + scroll");
		context.getInput().pressKey(InputConstants.KEY_0);
		context.waitTick();
		checkClickGuiScale(context, 1.0f, "Ctrl + 0");
		context.getInput().pressKey(InputConstants.KEY_MINUS);
		context.waitTick();
		checkClickGuiScale(context, 0.9f, "Ctrl + -");
		context.getInput().releaseControl();
		context.setScreen(() -> null);
		set(gui, "clickgui_scale", 1.0);
	}

	private static void checkClickGuiScale(ClientGameTestContext context, float expected, String via) {
		float actual = context.computeOnClient(c -> GuiScaleModule.clickGuiScale());
		check(Math.abs(actual - expected) < 0.001f, "GUI scale via " + via + ": expected " + expected + ", got " + actual);
	}

	// ------------------------------------------------------------------ Partikel

	private static void testParticles(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		server.runCommand("tp @p 90.5 " + FEET_Y + " 0.5 180 0");
		context.waitTicks(3);
		Module particles = modules.get(ParticlesModule.class);
		set(particles, "explosions", true);
		String spawn = "particle minecraft:explosion 90.5 " + (FEET_Y + 1) + " -2.5 0.5 0.5 0.5 0 20 force";

		context.runOnClient(c -> modules.setEnabled(particles, true));
		context.runOnClient(c -> c.particleEngine.clearParticles());
		server.runCommand(spawn);
		context.waitTicks(3);
		int hidden = context.computeOnClient(GametestCompat::particleCount);

		context.runOnClient(c -> modules.setEnabled(particles, false));
		context.runOnClient(c -> c.particleEngine.clearParticles());
		server.runCommand(spawn);
		context.waitTicks(3);
		int shown = context.computeOnClient(GametestCompat::particleCount);

		check(shown >= 10, "Particles: test setup spawned only " + shown + " explosion particles");
		check(hidden == 0, "Particles: " + hidden + " explosion particles were not hidden");
	}

	// ------------------------------------------------------------------ Capes

	private static void testCapes(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		Module capes = modules.get(CapesModule.class);
		set(capes, "cape", CapesModule.Cape.SALAMI);
		context.runOnClient(c -> modules.setEnabled(capes, true));
		context.waitTick();
		check(context.computeOnClient(c -> c.player.getSkin().cape() != null
				&& c.player.getSkin().cape().texturePath().equals(Identifier.fromNamespaceAndPath("salami", "textures/cape/salami.png"))),
			"Capes: own player has no salami cape");
		context.runOnClient(c -> c.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(5);
		context.takeScreenshot("salami-10-cape");

		// Elytra mit Cape-Design: normale Elytra-Form, nur andere Textur
		server.runCommand("item replace entity @p armor.chest with minecraft:elytra");
		context.waitTicks(5);
		check(context.computeOnClient(c -> c.player.getSkin().elytra() != null
				&& c.player.getSkin().elytra().texturePath().equals(Identifier.fromNamespaceAndPath("salami", "textures/cape/salami.png"))),
			"Capes: elytra does not use the cape texture");
		context.takeScreenshot("salami-10b-elytra-salami");
		// eigenes HD-Cape (512x256) – hier die Vorlage aus dem Launcher
		java.nio.file.Path template = capeTemplate();
		set(capes, "cape", CapesModule.Cape.LAUNCHER);
		System.setProperty("salami.cape", template.toString());
		context.waitTicks(5);
		context.takeScreenshot("salami-10c-elytra-template");
		System.clearProperty("salami.cape");
		set(capes, "cape", CapesModule.Cape.SALAMI);
		server.runCommand("item replace entity @p armor.chest with minecraft:air");
		context.runOnClient(c -> c.options.setCameraType(CameraType.FIRST_PERSON));

		// Auswahl aus dem Launcher (-Dsalami.cape)
		set(capes, "cape", CapesModule.Cape.LAUNCHER);
		System.setProperty("salami.cape", "builtin:pepperoni");
		check(context.computeOnClient(c -> c.player.getSkin().cape() != null
				&& c.player.getSkin().cape().texturePath().equals(Identifier.fromNamespaceAndPath("salami", "textures/cape/pepperoni.png"))),
			"Capes: launcher selection builtin:pepperoni not applied");
		System.setProperty("salami.cape", "none");
		check(context.computeOnClient(c -> c.player.getSkin().cape() == null
				|| !c.player.getSkin().cape().texturePath().getNamespace().equals("salami")),
			"Capes: launcher selection none still shows a salami cape");

		// Alte Config mit festem Cape: die Launcher-Auswahl muss trotzdem gelten …
		set(capes, "cape", CapesModule.Cape.SALAMI);
		System.setProperty("salami.cape", "builtin:board");
		check(capeIs(context, "board"), "Capes: config cape SALAMI overrides the launcher selection");
		check(context.computeOnClient(c -> capes.settings().stream()
				.anyMatch(s -> s.id().equals("cape") && s.get() == CapesModule.Cape.LAUNCHER)),
			"Capes: mode was not switched to LAUNCHER");
		// … und im Spiel umstellen geht danach weiterhin
		set(capes, "cape", CapesModule.Cape.PEPPERONI);
		check(capeIs(context, "pepperoni"), "Capes: changing the cape in game after launch has no effect");
		set(capes, "cape", CapesModule.Cape.LAUNCHER);
		System.clearProperty("salami.cape");
	}

	/**
	 * Andere Salami-Spieler: ein Mini-Cape-Server liefert für zwei fremde Spieler ein
	 * mitgeliefertes und ein (freigegebenes) eigenes Cape – der Mod muss beide anzeigen und
	 * Wechsel über die Live-Verbindung sofort übernehmen.
	 */
	private static void testSharedCapes(ClientGameTestContext context, ModuleManager modules) {
		UUID builtinPlayer = UUID.fromString("0f1e2d3c-4b5a-4978-8796-a5b4c3d2e1f0");
		UUID customPlayer = UUID.fromString("11111111-2222-4333-8444-555555555555");
		UUID unknownPlayer = UUID.fromString("22222222-3333-4444-8555-666666666666");
		String customId = "ab".repeat(32);
		// Was der Server gerade für jeden Spieler meldet (wie capes.json auf dem echten Server)
		Map<UUID, String> visible = new ConcurrentHashMap<>();
		visible.put(builtinPlayer, "{\"builtin\":\"pepperoni\"}");
		visible.put(customPlayer, "{\"id\":\"" + customId + "\"}");
		BlockingQueue<String> events = new LinkedBlockingQueue<>();
		AtomicBoolean live = new AtomicBoolean();
		AtomicBoolean stopped = new AtomicBoolean();
		ExecutorService handlers = Executors.newCachedThreadPool();
		HttpServer http;
		try {
			byte[] customPng = Files.readAllBytes(capeTemplate());
			http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			http.setExecutor(handlers); // die Live-Verbindung bleibt offen und darf andere Anfragen nicht blockieren
			http.createContext("/v1/capes/lookup", exchange -> {
				StringBuilder json = new StringBuilder("{\"capes\":{");
				visible.forEach((uuid, cape) -> json.append(json.length() > 10 ? "," : "").append('"').append(uuid).append("\":").append(cape));
				respond(exchange, 200, "application/json", json.append("}}").toString().getBytes(StandardCharsets.UTF_8));
			});
			http.createContext("/v1/capes/img/" + customId + ".png", exchange -> respond(exchange, 200, "image/png", customPng));
			http.createContext("/v1/capes/events", exchange -> {
				exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
				exchange.sendResponseHeaders(200, 0);
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(": verbunden\n\n".getBytes(StandardCharsets.UTF_8));
					out.flush();
					live.set(true);
					while (!stopped.get()) {
						String event = events.poll(100, TimeUnit.MILLISECONDS);
						if (event != null) {
							out.write(("data: " + event + "\n\n").getBytes(StandardCharsets.UTF_8));
							out.flush();
						}
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (IOException ignored) {
					// Mod hat die Verbindung geschlossen
				}
			});
			// Teilen des eigenen Capes (Challenge) gibt es hier nicht – kein Mojang-Login im Test
			http.createContext("/", exchange -> respond(exchange, 404, "application/json", "{\"error\":\"nicht gefunden\"}".getBytes(StandardCharsets.UTF_8)));
			http.start();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		System.setProperty("salami.capes.url", "http://127.0.0.1:" + http.getAddress().getPort());
		try {
			Module capes = modules.get(CapesModule.class);
			set(capes, "others", true);
			RemotePlayer[] others = context.computeOnClient(c -> new RemotePlayer[] {
				new RemotePlayer(c.level, new GameProfile(builtinPlayer, "Salamitester")),
				new RemotePlayer(c.level, new GameProfile(customPlayer, "Pepper_99")),
				new RemotePlayer(c.level, new GameProfile(unknownPlayer, "KeinSalami")),
			});
			Identifier pepperoni = Identifier.fromNamespaceAndPath("salami", "textures/cape/pepperoni.png");
			Identifier board = Identifier.fromNamespaceAndPath("salami", "textures/cape/board.png");
			Identifier custom = Identifier.fromNamespaceAndPath("salami", "cape/shared/" + customId);
			check(waitFor(context, 100, c -> capePath(others[0]).equals(pepperoni) && capePath(others[1]).equals(custom)),
				"Shared capes: other players' capes not shown (" + context.computeOnClient(c -> capePath(others[0]) + ", " + capePath(others[1])) + ")");
			check(context.computeOnClient(c -> capePath(others[2]).getNamespace().equals("minecraft")),
				"Shared capes: player without Salami cape got one");

			// Live: Wechsel kommen ohne neue Abfrage innerhalb einer Sekunde an
			check(waitFor(context, 100, c -> live.get()), "Shared capes: no live connection to the cape server");
			context.waitTicks(5);
			visible.put(builtinPlayer, "{\"builtin\":\"board\"}");
			events.add("{\"uuid\":\"" + builtinPlayer + "\",\"cape\":{\"builtin\":\"board\"}}");
			check(waitFor(context, 20, c -> capePath(others[0]).equals(board)),
				"Shared capes: live change to board not applied (" + context.computeOnClient(c -> capePath(others[0])) + ")");
			visible.remove(customPlayer);
			events.add("{\"uuid\":\"" + customPlayer + "\",\"cape\":null}");
			check(waitFor(context, 20, c -> !capePath(others[1]).getNamespace().equals("salami")),
				"Shared capes: live removal not applied");

			set(capes, "others", false);
			check(context.computeOnClient(c -> !capePath(others[0]).getNamespace().equals("salami")),
				"Shared capes: still shown with 'others' off");
			set(capes, "others", true);

			// ohne Cape-Server (Launcher ohne Adresse): keine fremden Salami-Capes, Cache wird geleert
			System.clearProperty("salami.capes.url");
			check(context.computeOnClient(c -> !capePath(others[0]).getNamespace().equals("salami")),
				"Shared capes: still shown without a cape server");
		} finally {
			System.clearProperty("salami.capes.url");
			stopped.set(true);
			http.stop(0);
			handlers.shutdownNow();
		}
	}

	/**
	 * Launcher live: im Launcher ein anderes Cape wählen, während das Spiel läuft – salami-core
	 * liest capes.json aus dem Launcher-Ordner ({@code -Dsalami.capes.dir}) und wechselt sofort.
	 */
	private static void testLauncherLiveCapes(ClientGameTestContext context) {
		java.nio.file.Path dir;
		try {
			dir = Files.createTempDirectory("salami-capes-live");
			java.nio.file.Path template = capeTemplate();
			Files.copy(template, dir.resolve("meins.png"));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		System.setProperty("salami.capes.dir", dir.toString());
		try {
			writeCapeStore(dir, "\"builtin:pepperoni\"", 1);
			check(waitFor(context, 40, c -> ownCape(c).equals(Identifier.fromNamespaceAndPath("salami", "textures/cape/pepperoni.png"))),
				"Launcher live: selection pepperoni not applied");
			writeCapeStore(dir, "\"builtin:board\"", 2);
			check(waitFor(context, 40, c -> ownCape(c).equals(Identifier.fromNamespaceAndPath("salami", "textures/cape/board.png"))),
				"Launcher live: switching to board while playing has no effect");
			writeCapeStore(dir, "\"meins\"", 3);
			check(waitFor(context, 40, c -> ownCape(c).equals(Identifier.fromNamespaceAndPath("salami", "cape/custom"))),
				"Launcher live: own cape from the launcher not applied (" + context.computeOnClient(SalamiModulesGameTest::ownCape) + ")");
			writeCapeStore(dir, "null", 4);
			check(waitFor(context, 40, c -> !ownCape(c).getNamespace().equals("salami")),
				"Launcher live: 'no cape' still shows a salami cape");
		} finally {
			System.clearProperty("salami.capes.dir");
			try (var files = Files.list(dir)) {
				for (java.nio.file.Path file : files.toList()) {
					Files.deleteIfExists(file);
				}
				Files.deleteIfExists(dir);
			} catch (IOException ignored) {
				// Temp-Ordner
			}
		}
	}

	/** capes.json wie der Launcher sie schreibt; Änderungszeit ausdrücklich weiterzählen. */
	// Cape-Vorlage aus dem Launcher; build.gradle.kts übergibt den Repo-Ordner als salami.repoDir.
	private static java.nio.file.Path capeTemplate() {
		return java.nio.file.Path.of(System.getProperty("salami.repoDir"), "launcher/src-tauri/assets/salami_cape_template.png");
	}

	private static void writeCapeStore(java.nio.file.Path dir, String selected, int step) {
		java.nio.file.Path store = dir.resolve("capes.json");
		String json = "{\"selected\":" + selected + ",\"capes\":[{\"id\":\"meins\",\"name\":\"Meins\",\"created\":\"2026-10-25T00:00:00Z\"}]}";
		try {
			Files.writeString(store, json);
			Files.setLastModifiedTime(store, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + step * 10_000L));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Cape-Textur des eigenen Spielers; ohne Cape {@code minecraft:none}. */
	private static Identifier ownCape(Minecraft c) {
		var cape = c.player.getSkin().cape();
		return cape == null ? Identifier.withDefaultNamespace("none") : cape.texturePath();
	}

	/** Cape-Textur eines Spielers; ohne Cape {@code minecraft:none}. */
	private static Identifier capePath(RemotePlayer player) {
		var cape = player.getSkin().cape();
		return cape == null ? Identifier.withDefaultNamespace("none") : cape.texturePath();
	}

	private static void respond(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
		exchange.getResponseHeaders().set("Content-Type", type);
		exchange.sendResponseHeaders(status, body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}
	}

	private static boolean capeIs(ClientGameTestContext context, String name) {
		return context.computeOnClient(c -> c.player.getSkin().cape() != null
			&& c.player.getSkin().cape().texturePath().equals(Identifier.fromNamespaceAndPath("salami", "textures/cape/" + name + ".png")));
	}

	// ------------------------------------------------------------------ Player ESP

	private static void testPlayerEsp(ClientGameTestContext context, ModuleManager modules) {
		Module esp = modules.get(PlayerEspModule.class);
		set(esp, "mode", PlayerEspModule.Mode.BOTH);
		context.runOnClient(c -> modules.setEnabled(esp, true));
		context.waitTicks(5); // Render-Durchgänge mit aktivem Modul dürfen nicht abstürzen
		check(context.computeOnClient(c -> esp.isEnabled()), "Player ESP: module crashed while rendering");
		check(!context.computeOnClient(c -> PlayerEspModule.glows(c.player)), "Player ESP: own player must not glow");
		context.runOnClient(c -> modules.setEnabled(esp, false));
	}

	// ------------------------------------------------------------------ Trigger Bot

	private static void testTriggerBot(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		server.runCommand("difficulty easy");
		server.runCommand("clear @a");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:stone_sword");
		server.runCommand("summon minecraft:zombie 90.5 " + FEET_Y + " -2.0 {NoAI:1b,PersistenceRequired:1b,Tags:[\"salami_trigger\"]}");
		server.runCommand("tp @p 90.5 " + FEET_Y + " 0.5 180 0");
		context.waitTicks(5);
		context.runOnClient(c -> c.player.getInventory().setSelectedSlot(0));
		check(waitFor(context, 10, c -> c.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Zombie),
			"Test setup: zombie not in crosshair");

		Module trigger = modules.get(TriggerBotModule.class);
		set(trigger, "players", false);
		set(trigger, "mobs", true);
		context.runOnClient(c -> modules.setEnabled(trigger, true));
		boolean hit = waitFor(context, 40, c -> c.level.getEntitiesOfClass(Zombie.class, new AABB(88, FEET_Y - 1, -5, 93, FEET_Y + 3, 1))
			.stream().allMatch(z -> z.getHealth() < z.getMaxHealth()));
		context.runOnClient(c -> modules.setEnabled(trigger, false));
		server.runCommand("kill @e[type=minecraft:zombie,tag=salami_trigger]");
		check(hit, "Trigger Bot: zombie in crosshair was not attacked");
	}

	// ------------------------------------------------------------------ Aim Assist

	private static void testAimAssist(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		// Zombie etwa 30° rechts vom Blick
		server.runCommand("summon minecraft:zombie 102.5 " + FEET_Y + " -3.0 {NoAI:1b,PersistenceRequired:1b,Tags:[\"salami_aim\"]}");
		server.runCommand("tp @p 100.5 " + FEET_Y + " 0.5 180 0");
		context.waitTicks(5);
		check(context.computeOnClient(c -> !(c.hitResult instanceof EntityHitResult)), "Test setup: zombie already in crosshair");

		Module aim = modules.get(AimAssistModule.class);
		set(aim, "players", false);
		set(aim, "mobs", true);
		set(aim, "fov", 90.0);
		context.runOnClient(c -> modules.setEnabled(aim, true));
		boolean onTarget = waitFor(context, 40, c -> c.crosshairPickEntity instanceof Zombie);
		context.runOnClient(c -> modules.setEnabled(aim, false));
		server.runCommand("kill @e[type=minecraft:zombie,tag=salami_aim]");
		check(onTarget, "Aim Assist: view was not pulled onto the zombie");
	}

	// ------------------------------------------------------------------ Hitbox Expand

	private static void testHitboxExpand(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		// Blick genau nach Norden; der Zombie steht so, dass der Strahl 0,15 Blöcke an ihm vorbeigeht
		server.runCommand("summon minecraft:zombie 220.95 " + FEET_Y + " -1.5 {NoAI:1b,IsBaby:0b,PersistenceRequired:1b,Tags:[\"salami_hitbox\"]}");
		server.runCommand("tp @p 220.5 " + FEET_Y + " 0.5 180 0");
		context.waitTicks(5);
		check(context.computeOnClient(c -> c.crosshairPickEntity == null), "Test setup: zombie already in crosshair");

		Module hitbox = modules.get(HitboxExpandModule.class);
		set(hitbox, "expand", 0.3);
		context.runOnClient(c -> modules.setEnabled(hitbox, true));
		boolean picked = waitFor(context, 10, c -> c.crosshairPickEntity instanceof Zombie);
		context.takeScreenshot("salami-16-hitbox-expand");
		set(hitbox, "affect_aim", false);
		boolean displayOnly = waitFor(context, 10, c -> c.crosshairPickEntity == null);
		set(hitbox, "affect_aim", true);
		context.runOnClient(c -> modules.setEnabled(hitbox, false));
		server.runCommand("kill @e[type=minecraft:zombie,tag=salami_hitbox]");
		check(picked, "Hitbox Expand: crosshair did not hit the enlarged hitbox");
		check(displayOnly, "Hitbox Expand: display-only mode still changed the crosshair");
	}

	// ------------------------------------------------------------------ Totems

	private static void testAutoTotem(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("give @a minecraft:totem_of_undying 2");
		context.waitTicks(3);

		Module autoTotem = modules.get(AutoTotemModule.class);
		context.runOnClient(c -> modules.setEnabled(autoTotem, true));
		check(waitFor(context, 20, c -> c.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)), "Auto Totem: offhand has no totem");
		context.runOnClient(c -> modules.setEnabled(autoTotem, false));
	}

	private static void testDoubleHandTotem(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		server.runCommand("item replace entity @a container.9 with minecraft:totem_of_undying");
		context.waitTicks(3);

		Module doubleHand = modules.get(DoubleHandTotemModule.class); // Standard: Slot 9
		context.runOnClient(c -> {
			c.player.getInventory().setSelectedSlot(0);
			modules.setEnabled(doubleHand, true);
		});
		check(waitFor(context, 20, c -> c.player.getInventory().getItem(8).is(Items.TOTEM_OF_UNDYING)),
			"Double Hand Totem: hotbar slot 9 was not refilled");

		server.runCommand("damage @p 14 minecraft:generic");
		check(waitFor(context, 20, c -> c.player.getInventory().getSelectedSlot() == 8),
			"Double Hand Totem: did not switch to totem slot on low health");
		context.runOnClient(c -> modules.setEnabled(doubleHand, false));
		server.runCommand("effect give @a minecraft:instant_health 1 5 true");
	}

	// ------------------------------------------------------------------ Kristalle

	private static void testCrystalMacro(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		server.runCommand("gamemode creative @a");
		server.runCommand("clear @a");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:end_crystal 16");
		server.runCommand("item replace entity @p hotbar.1 with minecraft:obsidian 16");
		// Bereich bei x=0: Spieler mittig, Blick nach Norden auf die Obsidian-Oberseite
		server.runCommand("tp @p 0.5 " + FEET_Y + " 0.5 180 39");
		server.runCommand("setblock 0 " + (FEET_Y - 1) + " -2 minecraft:obsidian");
		context.waitTicks(5);
		context.runOnClient(c -> c.player.getInventory().setSelectedSlot(0));

		Module crystal = modules.get(CrystalMacroModule.class);
		context.runOnClient(c -> modules.setEnabled(crystal, true));
		context.waitTicks(10);
		check(context.computeOnClient(SalamiModulesGameTest::crystalCount) == 0, "Crystal Macro: acted without right-click held");

		context.getInput().holdKey(options -> options.keyUse);
		boolean placed = false;
		boolean broken = false;
		for (int i = 0; i < 60 && !(placed && broken); i++) {
			int count = context.computeOnClient(SalamiModulesGameTest::crystalCount);
			if (count > 0) {
				placed = true;
			} else if (placed) {
				broken = true;
			}
			context.waitTick();
		}
		context.getInput().releaseKey(options -> options.keyUse);
		context.takeScreenshot("salami-07-crystal-macro");
		check(placed, "Crystal Macro: no crystal was placed");
		check(broken, "Crystal Macro: placed crystal was not broken");
		server.runCommand("kill @e[type=minecraft:end_crystal]");

		// Obsidian setzen: Blick auf Stein – erst Obsidian aus der Hotbar, dann der Kristall darauf
		set(crystal, "place_obsidian", true);
		server.runCommand("fill -12 " + (FEET_Y - 1) + " -4 -8 " + (FEET_Y - 1) + " 2 minecraft:stone");
		server.runCommand("fill -12 " + FEET_Y + " -4 -8 " + (FEET_Y + 3) + " 2 minecraft:air");
		server.runCommand("tp @p -9.5 " + FEET_Y + " 0.5 180 39");
		context.waitTicks(5);
		context.runOnClient(c -> c.player.getInventory().setSelectedSlot(0));
		BlockPos obsidian = new BlockPos(-10, FEET_Y, -2);
		context.getInput().holdKey(options -> options.keyUse);
		boolean obsidianPlaced = false;
		boolean crystalSeen = false;
		for (int i = 0; i < 60 && !(obsidianPlaced && crystalSeen); i++) {
			obsidianPlaced |= context.computeOnClient(c -> c.level.getBlockState(obsidian).is(Blocks.OBSIDIAN));
			crystalSeen |= context.computeOnClient(SalamiModulesGameTest::crystalCount) > 0;
			context.waitTick();
		}
		context.getInput().releaseKey(options -> options.keyUse);
		context.runOnClient(c -> modules.setEnabled(crystal, false));
		set(crystal, "place_obsidian", false);
		server.runCommand("kill @e[type=minecraft:end_crystal]");
		check(obsidianPlaced, "Crystal Macro: no obsidian was placed");
		check(crystalSeen, "Crystal Macro: no crystal was placed on the new obsidian");
	}

	private static int crystalCount(Minecraft client) {
		int count = 0;
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof EndCrystal && entity.isAlive()) {
				count++;
			}
		}
		return count;
	}

	// ------------------------------------------------------------------ Anker

	private static void testAnchorMacro(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		Module macro = modules.get(AnchorMacroModule.class);

		// Automatisch: Anker nur anvisieren
		set(macro, "trigger", AnchorMacroModule.Trigger.AUTO);
		buildAnchorScene(context, server);
		context.runOnClient(c -> modules.setEnabled(macro, true));
		runAnchorSequence(context, "auto");
		context.runOnClient(c -> modules.setEnabled(macro, false));

		// Rechtsklick: ein kurzer Klick muss reichen, das Makro läuft bis zur Explosion weiter
		set(macro, "trigger", AnchorMacroModule.Trigger.HOLD_USE);
		buildAnchorScene(context, server);
		context.runOnClient(c -> modules.setEnabled(macro, true));
		context.getInput().holdKeyFor(options -> options.keyUse, 2);
		runAnchorSequence(context, "click");
		context.takeScreenshot("salami-08-anchor");
		context.runOnClient(c -> modules.setEnabled(macro, false));
		check(waitFor(context, 5, c -> c.player.getInventory().getSelectedSlot() == 0),
			"Anchor Macro: did not switch back to the sword slot");
	}

	private static final BlockPos ANCHOR = new BlockPos(30, FEET_Y, -2);

	/**
	 * Anker auf dem Boden, Spieler 3 Blöcke davor mit Blick auf die Seitenfläche –
	 * wie im Kampf. Der Glowstone-Schutz landet genau im Fadenkreuz.
	 */
	private static void buildAnchorScene(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("clear @a");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:stone_sword");
		server.runCommand("item replace entity @p hotbar.1 with minecraft:totem_of_undying");
		server.runCommand("item replace entity @p hotbar.2 with minecraft:glowstone 16");
		server.runCommand("effect give @p minecraft:resistance 30 4 true");
		// Bereich bei x=30 (weit weg von der Kristall-Explosion)
		server.runCommand("fill 28 " + (FEET_Y - 1) + " -4 32 " + (FEET_Y - 1) + " 2 minecraft:stone");
		server.runCommand("fill 28 " + FEET_Y + " -4 32 " + (FEET_Y + 2) + " 2 minecraft:air");
		server.runCommand("setblock 30 " + FEET_Y + " -2 minecraft:respawn_anchor");
		server.runCommand("tp @p 30.5 " + FEET_Y + " 1.5 180 24");
		context.waitTicks(5);
		context.runOnClient(c -> c.player.getInventory().setSelectedSlot(0));
		context.waitTicks(2);
		check(context.computeOnClient(c -> c.level.getBlockState(ANCHOR).is(Blocks.RESPAWN_ANCHOR)), "Test setup: anchor missing");
		check(context.computeOnClient(c -> c.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit && hit.getBlockPos().equals(ANCHOR)),
			"Test setup: crosshair is not on the anchor side");
	}

	private static void runAnchorSequence(ClientGameTestContext context, String variant) {
		BlockPos shield = ANCHOR.south();
		boolean shieldSeen = false;
		boolean exploded = false;
		for (int i = 0; i < 60 && !exploded; i++) {
			shieldSeen |= context.computeOnClient(c -> c.level.getBlockState(shield).is(Blocks.GLOWSTONE));
			exploded = context.computeOnClient(c -> !c.level.getBlockState(ANCHOR).is(Blocks.RESPAWN_ANCHOR));
			context.waitTick();
		}
		check(shieldSeen, "Safe Anchor (" + variant + "): no glowstone shield was placed");
		check(exploded, "Anchor Macro (" + variant + "): anchor was not detonated");
	}

	// ------------------------------------------------------------------ Ghost Spear

	private static void testGhostSpear(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		server.runCommand("clear @a");
		server.runCommand("give @a minecraft:iron_spear 1");
		server.runCommand("give @a minecraft:totem_of_undying 1");
		context.waitTicks(3);
		context.runOnClient(c -> c.player.getInventory().setSelectedSlot(0));
		context.waitTicks(2);

		Module ghost = modules.get(GhostSpearModule.class);
		context.runOnClient(c -> modules.setEnabled(ghost, true));
		check(waitFor(context, 10, c -> !ghost.isEnabled()), "Ghost Spear: macro did not finish");
		check(context.computeOnClient(c -> c.player.getInventory().getSelectedSlot() == 1),
			"Ghost Spear: slot was not switched to the next slot");
	}

	// ------------------------------------------------------------------ Live Tracker

	private static void testLiveTracker(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		server.runCommand("tp @p 60.5 " + FEET_Y + " 0.5 180 10");
		server.runCommand("summon minecraft:armor_stand 60.5 " + FEET_Y + " -3.5 {equipment:{"
			+ "head:{id:\"minecraft:diamond_helmet\",count:1},chest:{id:\"minecraft:netherite_chestplate\",count:1},"
			+ "legs:{id:\"minecraft:iron_leggings\",count:1},feet:{id:\"minecraft:golden_boots\",count:1},"
			+ "mainhand:{id:\"minecraft:diamond_sword\",count:1}}}");
		context.waitTicks(5);

		Module tracker = modules.get(LiveTrackerModule.class);
		set(tracker, "mobs", true);
		context.runOnClient(c -> modules.setEnabled(tracker, true));
		context.waitTicks(5);
		context.takeScreenshot("salami-09-live-tracker");
		context.runOnClient(c -> modules.setEnabled(tracker, false));
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
