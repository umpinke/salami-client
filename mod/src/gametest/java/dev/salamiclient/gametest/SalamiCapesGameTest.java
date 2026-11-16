package dev.salamiclient.gametest;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.capes.AnimatedCapes;
import dev.salamiclient.capes.art.CapeDesign;
import dev.salamiclient.capes.art.CapeDesigns;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.visual.CapesModule;
import dev.salamiclient.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.InactivityFpsLimit;

import java.util.Locale;

/**
 * Gemalte, animierte Capes: jedes Motiv wird im Spiel getragen, in der gewählten Auflösung
 * gezeigt und läuft (zwei Zeitpunkte je Cape als Screenshot). Dazu ein Leistungsvergleich
 * Standbild / HD / 2K / 4K: Bildrate, Kosten des Hochladens, Arbeitsspeicher.
 *
 * <pre>./gradlew :1.21.11:runClientGameTest -PsalamiTests=Capes</pre>
 */
public final class SalamiCapesGameTest implements FabricClientGameTest {
	private static final int FEET_Y = -60;
	private static final int BENCH_SECONDS = 10;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!GametestCompat.selected(this)) {
			return;
		}
		ModuleManager modules = SalamiClient.get().modules();
		Module capes = modules.get(CapesModule.class);

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			GametestCompat.waitForChunksRender(world);
			TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("tp @p 700.5 " + FEET_Y + " 0.5 180 7");
			System.clearProperty("salami.cape");
			int[] window = context.computeOnClient(c -> new int[] {c.getWindow().getScreenWidth(), c.getWindow().getScreenHeight()});
			int oldFps = context.computeOnClient(c -> c.options.framerateLimit().get());
			boolean oldVsync = context.computeOnClient(c -> c.options.enableVsync().get());
			context.runOnClient(c -> {
				modules.setEnabled(capes, true);
				// großes Fenster und nah dran, damit man auf den Screenshots die Schärfe beurteilen kann
				c.getWindow().setWindowed(1600, 900);
				c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				c.options.fov().set(42);
				c.options.framerateLimit().set(260);
				c.options.enableVsync().set(false);
				// sonst bremst Minecraft nach einer Minute ohne Eingabe auf 30 FPS
				c.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
				GametestCompat.hideHud(c, true);
			});
			set(capes, "animated", true);
			set(capes, "quality", CapesModule.Quality.QHD);
			context.waitTicks(40);
			// Körper gerade zur Kamera drehen (nach dem Teleport steht er noch schräg)
			context.runOnClient(c -> {
				c.player.setYBodyRot(c.player.getYRot());
				c.player.yBodyRotO = c.player.getYRot();
			});
			context.waitTicks(5);

			try {
				for (CapeDesign design : CapeDesigns.all()) {
					wear(context, capes, design, AnimatedCapes.SCALE_2K);
					check(context.computeOnClient(c -> c.player.getSkin().cape() != null
							&& c.player.getSkin().cape().texturePath().getPath().startsWith("cape/" + design.name + "/")),
						"Capes: " + design.name + " is not worn");
					int first = design.frames / 3;
					int second = design.frames * 5 / 6;
					show(context, design, first);
					context.takeScreenshot("salami-cape-" + design.name + "-1");
					show(context, design, second);
					context.takeScreenshot("salami-cape-" + design.name + "-2");
				}

				// läuft von allein weiter, wenn die Zeit nicht angehalten ist
				AnimatedCapes.freeze(-1);
				int before = context.computeOnClient(c -> AnimatedCapes.shownFrame("dragon"));
				context.waitTicks(8);
				int after = context.computeOnClient(c -> AnimatedCapes.shownFrame("dragon"));
				check(before != after, "Capes: animation does not advance (frame " + before + ")");

				// Elytra trägt dasselbe Motiv
				server.runCommand("item replace entity @p armor.chest with minecraft:elytra");
				for (String name : new String[] {"aurora", "salami"}) {
					CapeDesign design = CapeDesigns.byName(name);
					wear(context, capes, design, AnimatedCapes.SCALE_2K);
					show(context, design, design.frames / 3);
					context.takeScreenshot("salami-cape-" + name + "-elytra");
				}
				server.runCommand("item replace entity @p armor.chest with minecraft:air");
				AnimatedCapes.freeze(-1);

				benchmark(context, capes);
			} finally {
				AnimatedCapes.freeze(-1);
				set(capes, "quality", CapesModule.Quality.QHD);
				set(capes, "animated", true);
				set(capes, "cape", CapesModule.Cape.LAUNCHER);
				context.runOnClient(c -> {
					c.options.setCameraType(CameraType.FIRST_PERSON);
					c.options.fov().set(70);
					c.options.framerateLimit().set(oldFps);
					c.options.enableVsync().set(oldVsync);
					c.getWindow().setWindowed(window[0], window[1]);
					GametestCompat.hideHud(c, false);
				});
			}
		}
	}

	/** Cape anziehen und warten, bis es in der gewünschten Auflösung gemalt und sichtbar ist. */
	private static void wear(ClientGameTestContext context, Module capes, CapeDesign design, int scale) {
		set(capes, "cape", CapesModule.Cape.valueOf(design.name.toUpperCase(Locale.ROOT)));
		for (int i = 0; i < 1200; i++) {
			context.waitTick();
			if (context.computeOnClient(c -> AnimatedCapes.shownScale(design.name)) == scale) {
				return;
			}
		}
		throw new AssertionError("Capes: " + design.name + " never reached " + 64 * scale + " px");
	}

	private static void show(ClientGameTestContext context, CapeDesign design, int frame) {
		AnimatedCapes.freeze(frame);
		for (int i = 0; i < 200; i++) {
			context.waitTick();
			if (context.computeOnClient(c -> AnimatedCapes.shownFrame(design.name)) == frame) {
				context.waitTick();
				return;
			}
		}
		throw new AssertionError("Capes: " + design.name + " never showed frame " + frame);
	}

	/** Galaxie (die meisten Frames) als Standbild und animiert in HD, 2K und 4K. */
	private static void benchmark(ClientGameTestContext context, Module capes) {
		CapeDesign design = CapeDesigns.byName("galaxy");
		set(capes, "animated", false);
		wear(context, capes, design, AnimatedCapes.SCALE_2K);
		double still = fps(context);
		SalamiClient.LOG.info(String.format(Locale.ROOT, "[Capes] Standbild 2K:   %.0f FPS", still));
		set(capes, "animated", true);
		for (CapesModule.Quality quality : CapesModule.Quality.values()) {
			set(capes, "quality", quality);
			int scale = quality == CapesModule.Quality.HD ? AnimatedCapes.SCALE_HD
				: quality == CapesModule.Quality.QHD ? AnimatedCapes.SCALE_2K : AnimatedCapes.SCALE_4K;
			// von vorn malen, damit die Malzeit zur Auflösung gehört
			wear(context, capes, design, scale);
			for (int i = 0; i < 60; i++) {
				context.waitTick();
			}
			context.runOnClient(c -> AnimatedCapes.takeStats());
			double fps = fps(context);
			double[] stats = context.computeOnClient(c -> AnimatedCapes.takeStats());
			long bytes = context.computeOnClient(c -> AnimatedCapes.frameBytes());
			SalamiClient.LOG.info(String.format(Locale.ROOT,
				"[Capes] animiert %-4s: %.0f FPS (Standbild %.0f) | Upload %.2f ms x %.0f | Frame schreiben %.2f ms (Hintergrund) | gemalt in %.0f ms | %d MB Frames | Textur %d x %d",
				quality, fps, still, stats[1], stats[0], stats[2], stats[3], bytes >> 20, 64 * scale, 32 * scale));
			if (quality == CapesModule.Quality.UHD) {
				context.runOnClient(c -> AnimatedCapes.freeze(design.frames / 3));
				show(context, design, design.frames / 3);
				context.takeScreenshot("salami-cape-galaxy-4k");
				AnimatedCapes.freeze(-1);
			}
		}
	}

	/** Mittlere Bildrate über gut zehn Sekunden (Bildrate unbegrenzt, VSync aus). */
	private static double fps(ClientGameTestContext context) {
		context.waitTicks(40);
		double sum = 0;
		for (int i = 0; i < BENCH_SECONDS; i++) {
			context.waitTicks(20);
			sum += context.computeOnClient(c -> c.getFps());
		}
		return sum / BENCH_SECONDS;
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
