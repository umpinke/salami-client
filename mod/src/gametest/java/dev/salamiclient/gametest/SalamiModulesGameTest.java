package dev.salamiclient.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.capes.SharedCapes;
import dev.salamiclient.capes.CapeLibrary;
import dev.salamiclient.gui.CosmeticsScreen;
import dev.salamiclient.gui.clickgui.ClickGuiScreen;
import dev.salamiclient.module.HudModule;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.client.GuiScaleModule;
import dev.salamiclient.modules.visual.CapesModule;
import dev.salamiclient.modules.visual.HatsModule;
import dev.salamiclient.modules.visual.MiniMeModule;
import dev.salamiclient.minime.MiniMeSkins;
import dev.salamiclient.modules.visual.InfoHudModule;
import dev.salamiclient.modules.visual.LiveTrackerModule;
import dev.salamiclient.modules.visual.ParticlesModule;
import dev.salamiclient.setting.Setting;
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
import net.minecraft.resources.Identifier;
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
 * Funktionstests der Visual-Module in einer echten Welt.
 * Szenen werden per Befehl aufgebaut; geprüft wird der Zustand auf dem Client.
 */
public final class SalamiModulesGameTest implements FabricClientGameTest {
	/** Standhöhe in der flachen Testwelt (Gras auf y=-61). */
	private static final int FEET_Y = -60;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!GametestCompat.selected(this)) {
			return;
		}
		ModuleManager modules = SalamiClient.get().modules();

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			GametestCompat.waitForChunksRender(world);
			TestServerContext server = world.getServer();

			testLiveTracker(context, server, modules);
			testParticles(context, server, modules);
			testCapes(context, server, modules);
			testHats(context, modules);
			testMiniMe(context, modules);
			testSharedCapes(context, modules);
			testLauncherLiveCapes(context);
			testGuiScale(context, modules);
		}
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

	/** Salami-Cape: gemalt und animiert, die Textur heißt {@code cape/salami/…}. */
	private static boolean salamiCape(Identifier texture) {
		return texture.getNamespace().equals("salami") && texture.getPath().startsWith("cape/salami/");
	}

	private static void testCapes(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		Module capes = modules.get(CapesModule.class);
		set(capes, "cape", CapesModule.Cape.SALAMI);
		context.runOnClient(c -> modules.setEnabled(capes, true));
		// das Cape wird erst gemalt – das dauert ein paar Zehntelsekunden
		check(waitFor(context, 200, c -> c.player.getSkin().cape() != null
				&& salamiCape(c.player.getSkin().cape().texturePath())),
			"Capes: own player has no salami cape");
		context.runOnClient(c -> c.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(5);
		context.takeScreenshot("salami-10-cape");

		// Elytra mit Cape-Design: normale Elytra-Form, nur andere Textur
		server.runCommand("item replace entity @p armor.chest with minecraft:elytra");
		context.waitTicks(5);
		check(context.computeOnClient(c -> c.player.getSkin().elytra() != null
				&& salamiCape(c.player.getSkin().elytra().texturePath())),
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
			// Ohne Freigabe (hier gar kein Cape-Server) trägt man ein eigenes Bild nicht – auch nicht selbst
			System.setProperty(SharedCapes.REQUIRE_APPROVAL_PROPERTY, "true");
			try {
				check(waitFor(context, 20, c -> !ownCape(c).getNamespace().equals("salami")),
					"Launcher live: own cape is shown before it was approved (" + context.computeOnClient(SalamiModulesGameTest::ownCape) + ")");
			} finally {
				System.clearProperty(SharedCapes.REQUIRE_APPROVAL_PROPERTY);
			}
			check(waitFor(context, 20, c -> ownCape(c).equals(Identifier.fromNamespaceAndPath("salami", "cape/custom"))),
				"Launcher live: own cape missing again in the development environment");
			writeCapeStore(dir, "null", 4);
			check(waitFor(context, 40, c -> !ownCape(c).getNamespace().equals("salami")),
				"Launcher live: 'no cape' still shows a salami cape");

			// Cosmetics im Spiel: anlegen und hochladen landen in derselben capes.json wie im Launcher
			try {
				context.runOnClient(c -> CapeLibrary.select(dir, "builtin:board"));
				check(waitFor(context, 10, c -> ownCape(c).equals(Identifier.fromNamespaceAndPath("salami", "textures/cape/board.png"))),
					"Cosmetics: equipping a built-in cape from the game has no effect");
				CapeLibrary.OwnCape uploaded = context.computeOnClient(c -> CapeLibrary.add(dir, capeTemplate()));
				check(context.computeOnClient(c -> CapeLibrary.ownCapes(dir).stream().anyMatch(own -> own.id().equals(uploaded.id()))),
					"Cosmetics: uploaded cape missing from the launcher store");
				context.runOnClient(c -> CapeLibrary.select(dir, uploaded.id()));
				check(waitFor(context, 10, c -> ownCape(c).equals(Identifier.fromNamespaceAndPath("salami", "cape/custom"))),
					"Cosmetics: uploaded cape not applied (" + context.computeOnClient(SalamiModulesGameTest::ownCape) + ")");
				check(context.computeOnClient(c -> uploaded.id().equals(CapeLibrary.selected(dir))), "Cosmetics: selection not stored");
				boolean rejected = context.computeOnClient(c -> {
					java.nio.file.Path wrong = dir.resolve("falsch.png");
					Files.write(wrong, new byte[] {1, 2, 3});
					try {
						CapeLibrary.add(dir, wrong);
						return false;
					} catch (CapeLibrary.InvalidCapeException e) {
						return true;
					}
				});
				check(rejected, "Cosmetics: a broken image was accepted");
				context.setScreen(() -> new CosmeticsScreen(null));
				context.waitTicks(20);
				context.takeScreenshot("salami-13-cosmetics");
				// ganz nach unten: die letzte Reihe ist vollständig zu sehen, nichts halb abgeschnitten
				for (int i = 0; i < 5; i++) {
					context.getInput().scroll(-1);
				}
				context.waitTicks(2);
				context.takeScreenshot("salami-13b-cosmetics-bottom");
				context.setScreen(() -> null);
			} catch (Exception e) {
				throw new RuntimeException("Cosmetics test failed", e);
			}
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

	// ------------------------------------------------------------------ Hüte

	/** Weihnachtsmütze auf der Schulter: trägt man sie, wird sie geteilt und von vorne gezeigt. */
	private static void testHats(ClientGameTestContext context, ModuleManager modules) {
		Module hats = modules.get(HatsModule.class);
		set(hats, "hat", HatsModule.Hat.SANTA);
		context.runOnClient(c -> {
			modules.setEnabled(hats, true);
			c.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		});
		context.waitTicks(5);
		check(context.computeOnClient(c -> HatsModule.wearsSanta(c.player.getId())), "Hats: own player does not wear the santa hat");
		check("santa".equals(HatsModule.sharedHat()), "Hats: santa hat is not shared");
		context.takeScreenshot("salami-10d-santa-hat");
		context.runOnClient(c -> c.options.setCameraType(CameraType.FIRST_PERSON));
		set(hats, "hat", HatsModule.Hat.NONE);
		check(HatsModule.sharedHat() == null, "Hats: hat still shared after taking it off");
		check(context.computeOnClient(c -> !HatsModule.wearsSanta(c.player.getId())), "Hats: hat still shown after taking it off");
	}

	// ------------------------------------------------------------------ Mini Me

	/** Mini Me mit dem Skin eines Namens: läuft hinterher, sitzt auf Kopf und Schulter, hängt an der Hand. */
	private static void testMiniMe(ClientGameTestContext context, ModuleManager modules) {
		MiniMeModule mini = modules.get(MiniMeModule.class);
		context.runOnClient(c -> {
			mini.setName("Notch");
			mini.setPlace(MiniMeModule.Place.FOLLOW);
			modules.setEnabled(mini, true);
			c.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		});
		// Skin von Mojang laden (braucht Netz; ohne Netz bleibt es beim Standard-Skin)
		for (int i = 0; i < 300 && context.computeOnClient(c -> MiniMeSkins.state("Notch")) == MiniMeSkins.State.LOADING; i++) {
			context.waitTicks(1);
		}
		MiniMeSkins.State loaded = context.computeOnClient(c -> MiniMeSkins.state("Notch"));
		if (loaded != MiniMeSkins.State.READY) {
			System.out.println("[salami-test] Mini Me: skin of Notch not loaded (" + loaded + "), default skin is shown");
		}
		MiniMeModule.Wish own = MiniMeModule.own();
		check(own != null && own.name().equals("Notch") && own.place() == MiniMeModule.Place.FOLLOW, "Mini Me: own wish missing");
		check(context.computeOnClient(c -> MiniMeModule.of(c.player)) != null, "Mini Me: own player has no Mini Me");
		// von hinten: das Mini Me läuft schräg hinter dem Spieler
		context.runOnClient(c -> {
			c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			c.gui.getChat().clearMessages(false); // Chat und Meldungen verdecken sonst das Mini Me
			c.getToastManager().clear();
		});
		context.waitTicks(20);
		context.takeScreenshot("salami-10e-minime-follow");
		context.runOnClient(c -> c.options.setCameraType(CameraType.THIRD_PERSON_FRONT));

		String[] shots = {"head", "shoulder", "hand"};
		MiniMeModule.Place[] places = {MiniMeModule.Place.HEAD, MiniMeModule.Place.SHOULDER, MiniMeModule.Place.HAND};
		for (int i = 0; i < places.length; i++) {
			MiniMeModule.Place place = places[i];
			context.runOnClient(c -> mini.setPlace(place));
			context.waitTicks(5);
			context.takeScreenshot("salami-10f-minime-" + shots[i]);
		}

		// Cosmetics-Reiter mit Namensfeld und den Plätzen
		context.runOnClient(c -> c.options.setCameraType(CameraType.FIRST_PERSON));
		context.setScreen(() -> new CosmeticsScreen(null, CosmeticsScreen.Tab.MINIME));
		context.waitTicks(10);
		context.takeScreenshot("salami-13c-cosmetics-minime");
		context.setScreen(() -> null);

		// Platz „Kein Mini Me“: nichts mehr zu sehen und nichts geteilt
		context.runOnClient(c -> mini.setPlace(MiniMeModule.Place.NONE));
		check(MiniMeModule.own() == null, "Mini Me: still shared after putting it away");
		context.runOnClient(c -> modules.setEnabled(mini, false));
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
