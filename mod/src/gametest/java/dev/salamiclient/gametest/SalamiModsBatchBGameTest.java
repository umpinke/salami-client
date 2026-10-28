package dev.salamiclient.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.client.SafeModeModule;
import dev.salamiclient.modules.hud.ScoreboardModule;
import dev.salamiclient.modules.hud.TitlesModule;
import dev.salamiclient.modules.visual.ArrowTrailModule;
import dev.salamiclient.modules.visual.ClearBackgroundModule;
import dev.salamiclient.modules.visual.ColorSaturationModule;
import dev.salamiclient.modules.visual.FovChangerModule;
import dev.salamiclient.modules.visual.FreeLookModule;
import dev.salamiclient.modules.visual.GlintColorizerModule;
import dev.salamiclient.modules.visual.HealthIndicatorsModule;
import dev.salamiclient.modules.visual.ItemModelModule;
import dev.salamiclient.modules.visual.ItemPhysicsModule;
import dev.salamiclient.modules.visual.LightLevelModule;
import dev.salamiclient.modules.visual.MotionBlurModule;
import dev.salamiclient.modules.visual.NoFogModule;
import dev.salamiclient.modules.visual.OldAnimationsModule;
import dev.salamiclient.modules.visual.ShinyPotsModule;
import dev.salamiclient.modules.visual.TimeChangerModule;
import dev.salamiclient.modules.visual.TntTimerModule;
import dev.salamiclient.modules.visual.WeatherChangerModule;
import dev.salamiclient.render.PostEffects;
import dev.salamiclient.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Tests der Mods aus Batch B (Kamera, Welt-Optik, Items, Vanilla-HUD). */
public final class SalamiModsBatchBGameTest implements FabricClientGameTest {
	private static final int FEET_Y = -60;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!GametestCompat.selected(this)) {
			return;
		}
		ModuleManager modules = SalamiClient.get().modules();
		context.runOnClient(c -> modules.setEnabled(modules.get(SafeModeModule.class), false));

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			GametestCompat.waitForChunksRender(world);
			TestServerContext server = world.getServer();
			server.runCommand("time set day");
			server.runCommand("weather clear");
			server.runCommand("tp @p 200.5 " + FEET_Y + " 0.5 0 10");
			context.waitTicks(20);

			testFreeLook(context, modules);
			testWorldLooks(context, server, modules);
			testItems(context, server, modules);
			testVanillaHud(context, server, modules);
		}
	}

	// ------------------------------------------------------------------ Free Look

	private static void testFreeLook(ClientGameTestContext context, ModuleManager modules) {
		Module look = modules.get(FreeLookModule.class);
		context.runOnClient(c -> modules.setEnabled(look, true));
		float yawBefore = context.computeOnClient(c -> c.player.getYRot());
		context.getInput().holdKey(InputConstants.KEY_LALT);
		context.waitTicks(3);
		check(context.computeOnClient(c -> c.options.getCameraType() == CameraType.THIRD_PERSON_BACK), "Free Look: no third person");
		// Im Testfenster ist die Maus nicht eingefangen – Mausbewegung direkt in Free Look geben
		// (der Weg MouseHandler -> Free Look ist per Mixin abgedeckt, das Laden prüft ihn).
		context.runOnClient(c -> FreeLookModule.turn(400, 0));
		context.waitTicks(3);
		float yawWhile = context.computeOnClient(c -> c.player.getYRot());
		//? if >=1.21.11 {
		boolean lookedAround = context.computeOnClient(c -> Math.abs(c.gameRenderer.getMainCamera().yRot() - yawBefore) > 10);
		//?} else
		/*boolean lookedAround = context.computeOnClient(c -> Math.abs(c.gameRenderer.getMainCamera().getYRot() - yawBefore) > 10);*/
		context.takeScreenshot("salami-30-free-look");
		context.getInput().releaseKey(InputConstants.KEY_LALT);
		context.waitTicks(3);
		boolean restored = context.computeOnClient(c -> c.options.getCameraType() == CameraType.FIRST_PERSON);
		context.runOnClient(c -> modules.setEnabled(look, false));

		check(Math.abs(yawWhile - yawBefore) < 0.01, "Free Look: player turned while looking around");
		check(lookedAround, "Free Look: camera did not turn");
		check(restored, "Free Look: camera not restored to first person");
	}

	// ------------------------------------------------------------------ Welt-Optik

	private static void testWorldLooks(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		Module time = modules.get(TimeChangerModule.class);
		Module weather = modules.get(WeatherChangerModule.class);
		set(time, "time", 18000.0);
		set(weather, "weather", WeatherChangerModule.Weather.RAIN);
		List<Class<? extends Module>> looks = List.of(FovChangerModule.class, NoFogModule.class, TimeChangerModule.class,
			WeatherChangerModule.class, ColorSaturationModule.class, MotionBlurModule.class, LightLevelModule.class);
		for (Class<? extends Module> type : looks) {
			context.runOnClient(c -> modules.setEnabled(modules.get(type), true));
		}
		context.waitTicks(20);
		check(context.computeOnClient(c -> c.level.getDayTime() % 24000) == 18000, "Time Changer: client time not changed");
		check(context.computeOnClient(c -> c.level.getRainLevel(1.0F)) > 0.99F, "Weather Changer: no rain on the client");
		check(server.computeOnServer(s -> s.overworld().getRainLevel(1.0F)) < 0.01F, "Weather Changer: changed the server's weather");
		context.takeScreenshot("salami-31-world-night-rain-saturation");
		check(PostEffects.failures().isEmpty(), "Post effects failed to load: " + PostEffects.failures());

		for (Class<? extends Module> type : looks) {
			check(context.computeOnClient(c -> modules.get(type).isEnabled()), type.getSimpleName() + " crashed and was disabled");
			context.runOnClient(c -> modules.setEnabled(modules.get(type), false));
		}
		context.waitTicks(2);
		check(context.computeOnClient(c -> c.level.getRainLevel(1.0F)) < 0.01F, "Weather Changer: rain stayed after disabling");
	}

	// ------------------------------------------------------------------ Items

	private static void testItems(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		server.runCommand("clear @a");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:diamond_sword[minecraft:enchantments={\"minecraft:sharpness\":5}]");
		server.runCommand("item replace entity @p hotbar.1 with minecraft:potion");
		server.runCommand("tp @p 210.5 " + FEET_Y + " 0.5 0 30");
		context.waitTicks(10);
		server.runCommand("summon minecraft:item 210.5 " + FEET_Y + " 2.5 {Item:{id:\"minecraft:golden_apple\",count:1},PickupDelay:32767}");
		server.runCommand("summon minecraft:item 211.5 " + FEET_Y + " 2.5 {Item:{id:\"minecraft:stone\",count:1},PickupDelay:32767}");
		server.runCommand("summon minecraft:tnt 209.0 " + FEET_Y + " 3.5 {fuse:200}");
		server.runCommand("summon minecraft:arrow 212.5 " + (FEET_Y + 2) + " 1.5 {Motion:[0.0,0.2,0.6]}");

		List<Class<? extends Module>> items = List.of(ItemPhysicsModule.class, GlintColorizerModule.class, ShinyPotsModule.class,
			ArrowTrailModule.class, TntTimerModule.class, ItemModelModule.class, HealthIndicatorsModule.class,
			OldAnimationsModule.class, ClearBackgroundModule.class);
		set(modules.get(ArrowTrailModule.class), "only_own", false);
		for (Class<? extends Module> type : items) {
			context.runOnClient(c -> modules.setEnabled(modules.get(type), true));
		}
		context.waitTicks(10);
		check(context.computeOnClient(c -> ShinyPotsModule.shines(new ItemStack(Items.POTION)) && new ItemStack(Items.POTION).hasFoil()),
			"Shiny Pots: potion has no glint");
		check(context.computeOnClient(c -> c.level.getEntitiesOfClass(PrimedTnt.class, new AABB(200, -70, -10, 220, -50, 10))
			.stream().anyMatch(t -> t.getCustomName() != null && t.getCustomName().getString().endsWith("s"))), "TNT Timer: no countdown");
		check(context.computeOnClient(c -> HealthIndicatorsModule.decorate(Component.literal("P"), c.player).getString().contains("❤")),
			"Health Indicators: no hearts behind the name");
		context.takeScreenshot("salami-32-items");

		context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		context.waitTicks(3);
		check(context.computeOnClient(c -> c.getTextureManager().getTexture(ItemRenderer.ENCHANTED_GLINT_ITEM)
			.getClass().getSimpleName().equals("TintedTexture")), "Glint Colorizer: glint texture not replaced");
		context.takeScreenshot("salami-33-clear-inventory");
		// Vergleich: Vanilla-Glanz
		context.runOnClient(c -> modules.setEnabled(modules.get(GlintColorizerModule.class), false));
		context.waitTicks(3);
		check(!context.computeOnClient(c -> c.getTextureManager().getTexture(ItemRenderer.ENCHANTED_GLINT_ITEM)
			.getClass().getSimpleName().equals("TintedTexture")), "Glint Colorizer: original glint not restored");
		context.takeScreenshot("salami-33b-glint-vanilla");
		context.runOnClient(c -> modules.setEnabled(modules.get(GlintColorizerModule.class), true));
		context.waitTicks(2);
		context.setScreen(() -> null);

		for (Class<? extends Module> type : items) {
			check(context.computeOnClient(c -> modules.get(type).isEnabled()), type.getSimpleName() + " crashed and was disabled");
			context.runOnClient(c -> modules.setEnabled(modules.get(type), false));
		}
		check(context.computeOnClient(c -> c.level.getEntitiesOfClass(PrimedTnt.class, new AABB(200, -70, -10, 220, -50, 10))
			.stream().allMatch(t -> t.getCustomName() == null)), "TNT Timer: countdown stayed after disabling");
		set(modules.get(ArrowTrailModule.class), "only_own", true);
		server.runCommand("kill @e[type=!minecraft:player]");
		server.runCommand("clear @a");
	}

	// ------------------------------------------------------------------ Vanilla-HUD

	private static void testVanillaHud(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		server.runCommand("scoreboard objectives add salami dummy \"Salami Test\"");
		server.runCommand("scoreboard objectives setdisplay sidebar salami");
		server.runCommand("scoreboard players set Kills salami 12");
		server.runCommand("scoreboard players set Tode salami 3");
		Module scoreboard = modules.get(ScoreboardModule.class);
		Module titles = modules.get(TitlesModule.class);
		set(scoreboard, "size", 0.8);
		context.runOnClient(c -> modules.setEnabled(scoreboard, true));
		context.runOnClient(c -> modules.setEnabled(titles, true));
		server.runCommand("title @a title \"Salami Titel\"");
		server.runCommand("title @a actionbar \"Actionbar Text\"");
		context.waitTicks(15);
		context.takeScreenshot("salami-34-scoreboard-titles");
		check(context.computeOnClient(c -> scoreboard.isEnabled() && titles.isEnabled()), "Scoreboard/Titles crashed and were disabled");
		context.runOnClient(c -> modules.setEnabled(scoreboard, false));
		context.runOnClient(c -> modules.setEnabled(titles, false));
		set(scoreboard, "size", 1.0);
		server.runCommand("scoreboard objectives remove salami");
		server.runCommand("title @a clear");
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
