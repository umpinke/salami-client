package dev.salamiclient.gametest;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.client.SafeModeModule;
import dev.salamiclient.modules.utility.AnchorMacroModule;
import dev.salamiclient.modules.utility.AutoCrystalModule;
import dev.salamiclient.modules.utility.AutoTotemModule;
import dev.salamiclient.modules.utility.DoubleHandTotemModule;
import dev.salamiclient.modules.utility.GhostSpearModule;
import dev.salamiclient.modules.visual.LiveTrackerModule;
import dev.salamiclient.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.apache.commons.lang3.function.FailableFunction;

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
			world.getClientWorld().waitForChunksRender();
			TestServerContext server = world.getServer();

			testAutoTotem(context, server, modules);
			testDoubleHandTotem(context, server, modules);
			testAutoCrystal(context, server, modules);
			testAnchorMacro(context, server, modules);
			testGhostSpear(context, server, modules);
			testLiveTracker(context, server, modules);
		}
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

	private static void testAutoCrystal(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		server.runCommand("gamemode creative @a");
		server.runCommand("clear @a");
		server.runCommand("give @a minecraft:end_crystal 16");
		// Bereich bei x=0: Spieler mittig, Blick nach Norden auf die Obsidian-Oberseite
		server.runCommand("tp @p 0.5 " + FEET_Y + " 0.5 180 39");
		server.runCommand("setblock 0 " + (FEET_Y - 1) + " -2 minecraft:obsidian");
		context.waitTicks(5);

		Module crystal = modules.get(AutoCrystalModule.class);
		set(crystal, "auto_switch", true);
		context.runOnClient(c -> modules.setEnabled(crystal, true));

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
		context.takeScreenshot("salami-07-auto-crystal");
		context.runOnClient(c -> modules.setEnabled(crystal, false));
		check(placed, "Auto Crystal: no crystal was placed");
		check(broken, "Auto Crystal: placed crystal was not broken");
		server.runCommand("kill @e[type=minecraft:end_crystal]");
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
