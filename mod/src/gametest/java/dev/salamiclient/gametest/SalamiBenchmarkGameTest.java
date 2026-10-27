package dev.salamiclient.gametest;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.client.SafeModeModule;
import dev.salamiclient.modules.client.BorderlessFullscreenModule;
import dev.salamiclient.modules.misc.AutoTextModule;
import dev.salamiclient.modules.misc.KitsModule;
import dev.salamiclient.modules.visual.ColorSaturationModule;
import dev.salamiclient.modules.visual.FreeLookModule;
import dev.salamiclient.modules.visual.MotionBlurModule;
import dev.salamiclient.modules.visual.ZoomModule;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Leistungstest: misst, wie viel Zeit die Salami-Module pro Frame (HUD, Welt) und pro Tick
 * kosten – erst ohne, dann mit allen Anzeige- und Optik-Modulen in einer Szene mit vielen Mobs.
 * Grenzen sind großzügig (Testrechner schwanken), fangen aber echte Ausreißer ab.
 */
public final class SalamiBenchmarkGameTest implements FabricClientGameTest {
	private static final int FEET_Y = -60;
	private static final int FRAMES = 200;

	/** Max. Zeit pro Frame bzw. Tick für alle Module zusammen. */
	private static final double MAX_HUD_MS = 2.0;
	private static final double MAX_WORLD_MS = 3.0;
	private static final double MAX_TICK_MS = 2.0;

	/** Nicht im Benchmark: Kampfmodule, Tasten-/Fenster-Module und bewusst teure Nachbearbeitung. */
	private static final Set<Class<?>> SKIP = Set.of(SafeModeModule.class, BorderlessFullscreenModule.class, AutoTextModule.class,
		KitsModule.class, FreeLookModule.class, ZoomModule.class, MotionBlurModule.class, ColorSaturationModule.class);

	@Override
	public void runTest(ClientGameTestContext context) {
		ModuleManager modules = SalamiClient.get().modules();
		context.runOnClient(c -> modules.setEnabled(modules.get(SafeModeModule.class), false));

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			GametestCompat.waitForChunksRender(world);
			TestServerContext server = world.getServer();
			server.runCommand("tp @p 400.5 " + FEET_Y + " 0.5 0 10");
			context.waitTicks(10);
			for (int i = 0; i < 40; i++) {
				server.runCommand(String.format(Locale.ROOT, "summon minecraft:zombie %.1f %d %.1f {NoAI:1b,PersistenceRequired:1b}",
					396.5 + (i % 8), FEET_Y, 6.5 + i / 8));
			}
			context.waitTicks(20);

			double[] baseline = measure(context, modules);

			List<Module> enabled = new ArrayList<>();
			context.runOnClient(c -> {
				for (Module module : modules.all()) {
					if (module.category() == Category.UTILITY || SKIP.contains(module.getClass()) || module.isEnabled()) {
						continue;
					}
					modules.setEnabled(module, true);
					enabled.add(module);
				}
			});
			context.waitTicks(20);
			double[] loaded = measure(context, modules);
			context.takeScreenshot("salami-50-benchmark-all-modules");
			context.runOnClient(c -> enabled.forEach(m -> modules.setEnabled(m, false)));

			SalamiClient.LOG.info(String.format(Locale.ROOT,
				"[Benchmark] %d modules on: HUD %.3f ms/frame (base %.3f), world %.3f ms/frame (base %.3f), tick %.3f ms (base %.3f)",
				enabled.size(), loaded[0], baseline[0], loaded[1], baseline[1], loaded[2], baseline[2]));
			check(loaded[0] < MAX_HUD_MS, String.format(Locale.ROOT, "HUD too slow: %.3f ms/frame", loaded[0]));
			check(loaded[1] < MAX_WORLD_MS, String.format(Locale.ROOT, "World rendering too slow: %.3f ms/frame", loaded[1]));
			check(loaded[2] < MAX_TICK_MS, String.format(Locale.ROOT, "Ticks too slow: %.3f ms/tick", loaded[2]));
			for (Module module : enabled) {
				check(!context.computeOnClient(c -> module.isEnabled()), module.id() + " still enabled after the benchmark");
			}
			server.runCommand("kill @e[type=minecraft:zombie]");
		}
	}

	/** {HUD ms/Frame, Welt ms/Frame, ms/Tick} über {@link #FRAMES} Ticks. */
	private static double[] measure(ClientGameTestContext context, ModuleManager modules) {
		context.runOnClient(c -> modules.resetTimings());
		context.waitTicks(FRAMES);
		return context.computeOnClient(c -> new double[] {modules.averageHudMillis(), modules.averageWorldMillis(), modules.averageTickMillis()});
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
