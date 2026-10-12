package dev.salamiclient.gametest;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.gui.HudEditorScreen;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.client.SafeModeModule;
import dev.salamiclient.modules.misc.KitsModule;
import dev.salamiclient.modules.utility.AutoElytraModule;
import dev.salamiclient.modules.utility.AutoMaceModule;
import dev.salamiclient.modules.utility.BlockEspModule;
import dev.salamiclient.modules.utility.BreachSwapModule;
import dev.salamiclient.modules.utility.SpeedLungeModule;
import dev.salamiclient.modules.visual.ArmorHudModule;
import dev.salamiclient.modules.visual.BlockOutlineModule;
import dev.salamiclient.modules.visual.CrosshairModule;
import dev.salamiclient.modules.visual.InfoHudModule;
import dev.salamiclient.modules.visual.ItemCounterModule;
import dev.salamiclient.modules.visual.LogoOverlayModule;
import dev.salamiclient.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Items;
import org.apache.commons.lang3.function.FailableFunction;
import org.lwjgl.glfw.GLFW;

/** Funktionstests für Streitkolben/Speer-Module, Kits und Visuals in echten Testszenen. */
public final class SalamiCombatGameTest implements FabricClientGameTest {
	private static final int FEET_Y = -60;

	@Override
	public void runTest(ClientGameTestContext context) {
		ModuleManager modules = SalamiClient.get().modules();
		context.runOnClient(c -> modules.setEnabled(modules.get(SafeModeModule.class), false));

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientWorld().waitForChunksRender();
			TestServerContext server = world.getServer();
			server.runCommand("difficulty easy");
			server.runCommand("gamerule doMobSpawning false");

			testBreachSwap(context, server, modules);
			testAutoMace(context, server, modules);
			testAutoElytra(context, server, modules);
			testSpeedLunge(context, server, modules);
			testKits(context, server, modules);
			testVisuals(context, server, modules);
		}
	}

	private static void summonDummy(TestServerContext server, String pos, boolean armored) {
		String equipment = armored
			? ",equipment:{chest:{id:\"minecraft:diamond_chestplate\",count:1},head:{id:\"minecraft:diamond_helmet\",count:1}}"
			: "";
		server.runCommand("summon minecraft:zombie " + pos + " {NoAI:1b,PersistenceRequired:1b,Silent:1b" + equipment + "}");
	}

	// ------------------------------------------------------------------ Breach Swap

	private static void testBreachSwap(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:diamond_sword");
		server.runCommand("item replace entity @p hotbar.1 with minecraft:mace[enchantments={\"minecraft:breach\":4}]");
		server.runCommand("tp @p 0.5 " + FEET_Y + " 0.5 180 0");
		summonDummy(server, "0.5 " + FEET_Y + " -1.5", true);
		context.waitTicks(10);
		context.runOnClient(c -> c.player.getInventory().setSelectedSlot(0));

		Module breach = modules.get(BreachSwapModule.class);
		context.runOnClient(c -> modules.setEnabled(breach, true));
		context.waitTicks(2);
		check(context.computeOnClient(c -> c.hitResult instanceof net.minecraft.world.phys.EntityHitResult), "Breach Swap: zombie not targeted");

		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		boolean swappedToMace = false;
		for (int i = 0; i < 3; i++) {
			swappedToMace |= context.computeOnClient(c -> c.player.getInventory().getSelectedSlot() == 1);
			context.waitTick();
		}
		check(swappedToMace, "Breach Swap: did not swap to breach mace on attack");
		check(waitFor(context, 10, c -> c.player.getInventory().getSelectedSlot() == 0), "Breach Swap: did not swap back");
		context.runOnClient(c -> modules.setEnabled(breach, false));
		server.runCommand("kill @e[type=minecraft:zombie]");
	}

	// ------------------------------------------------------------------ Auto Mace

	private static void testAutoMace(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		server.runCommand("clear @a");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:diamond_sword");
		server.runCommand("item replace entity @p hotbar.1 with minecraft:mace");
		server.runCommand("effect give @p minecraft:resistance 30 4 true");
		summonDummy(server, "20.5 " + FEET_Y + " 0.5", false);
		// 8 Blöcke über dem Zombie, Blick senkrecht nach unten
		server.runCommand("tp @p 20.5 " + (FEET_Y + 8) + " 0.5 0 90");
		context.runOnClient(c -> c.player.getInventory().setSelectedSlot(0));

		Module mace = modules.get(AutoMaceModule.class);
		context.runOnClient(c -> modules.setEnabled(mace, true));
		boolean hurt = waitFor(context, 60, c -> zombieHurt(c));
		context.runOnClient(c -> modules.setEnabled(mace, false));
		check(hurt, "Auto Mace: zombie was not hit while falling");
		server.runCommand("kill @e[type=minecraft:zombie]");
	}

	private static boolean zombieHurt(Minecraft client) {
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof Zombie zombie && (zombie.getHealth() < zombie.getMaxHealth() || !zombie.isAlive())) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ Auto Elytra

	private static void testAutoElytra(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		server.runCommand("clear @a");
		server.runCommand("item replace entity @p armor.chest with minecraft:diamond_chestplate");
		server.runCommand("item replace entity @p inventory.0 with minecraft:elytra");
		server.runCommand("effect give @p minecraft:resistance 60 4 true");
		server.runCommand("tp @p 40.5 " + (FEET_Y + 60) + " 0.5 0 20");

		Module elytra = modules.get(AutoElytraModule.class);
		context.runOnClient(c -> modules.setEnabled(elytra, true));
		check(waitFor(context, 80, c -> c.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)), "Auto Elytra: elytra not equipped while falling");
		check(waitFor(context, 20, c -> c.player.isFallFlying()), "Auto Elytra: did not start gliding");
		context.takeScreenshot("salami-10-auto-elytra");
		check(waitFor(context, 400, c -> c.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE)), "Auto Elytra: chestplate not restored near ground");
		context.runOnClient(c -> modules.setEnabled(elytra, false));
	}

	// ------------------------------------------------------------------ Speed Lunge

	private static void testSpeedLunge(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		server.runCommand("clear @a");
		server.runCommand("effect clear @a");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:iron_spear[enchantments={\"minecraft:lunge\":3}]");
		server.runCommand("fill 55 " + (FEET_Y - 1) + " -40 65 " + (FEET_Y - 1) + " 10 minecraft:stone");
		server.runCommand("fill 55 " + FEET_Y + " -40 65 " + (FEET_Y + 3) + " 10 minecraft:air");
		server.runCommand("tp @p 60.5 " + FEET_Y + " 5.5 180 0");
		context.waitTicks(10);
		context.runOnClient(c -> c.player.getInventory().setSelectedSlot(0));

		Module lunge = modules.get(SpeedLungeModule.class);
		context.runOnClient(c -> modules.setEnabled(lunge, true));
		context.getInput().holdKey(options -> options.keyUp);
		boolean jabbed = waitFor(context, 60, c -> c.player.getMainHandItem().getDamageValue() > 0);
		context.getInput().releaseKey(options -> options.keyUp);
		context.runOnClient(c -> modules.setEnabled(lunge, false));
		check(jabbed, "Speed Lunge: no lunge jab happened (spear took no damage)");
	}

	// ------------------------------------------------------------------ Kits

	private static void testKits(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		server.runCommand("clear @a");
		server.runCommand("item replace entity @p hotbar.0 with minecraft:totem_of_undying");
		server.runCommand("item replace entity @p hotbar.4 with minecraft:end_crystal 64");
		server.runCommand("item replace entity @p hotbar.6 with minecraft:netherite_sword");
		server.runCommand("item replace entity @p inventory.11 with minecraft:obsidian 64");
		server.runCommand("item replace entity @p inventory.20 with minecraft:respawn_anchor 16");
		context.waitTicks(5);

		KitsModule kits = modules.get(KitsModule.class);
		set(kits, "kit", KitsModule.Kit.CRYSTAL);
		context.runOnClient(c -> modules.setEnabled(kits, true));
		context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		context.runOnClient(c -> kits.apply());
		check(waitFor(context, 60, c -> !kits.isApplying()), "Kits: applying did not finish");
		context.takeScreenshot("salami-11-kit-crystal");
		check(context.computeOnClient(c -> c.player.getInventory().getItem(0).is(ItemTags.SWORDS)), "Kits: sword not in slot 1");
		check(context.computeOnClient(c -> c.player.getInventory().getItem(1).is(Items.END_CRYSTAL)), "Kits: crystals not in slot 2");
		check(context.computeOnClient(c -> c.player.getInventory().getItem(2).is(Items.OBSIDIAN)), "Kits: obsidian not in slot 3");
		check(context.computeOnClient(c -> c.player.getInventory().getItem(3).is(Items.RESPAWN_ANCHOR)), "Kits: anchors not in slot 4");
		check(context.computeOnClient(c -> c.player.getInventory().getItem(8).is(Items.TOTEM_OF_UNDYING)), "Kits: totem not in slot 9");
		context.setScreen(() -> null);
		context.runOnClient(c -> modules.setEnabled(kits, false));
	}

	// ------------------------------------------------------------------ Visuals

	private static void testVisuals(ClientGameTestContext context, TestServerContext server, ModuleManager modules) {
		server.runCommand("gamemode creative @a");
		server.runCommand("item replace entity @p armor.head with minecraft:diamond_helmet");
		server.runCommand("item replace entity @p armor.chest with minecraft:netherite_chestplate");
		server.runCommand("item replace entity @p armor.legs with minecraft:iron_leggings");
		server.runCommand("item replace entity @p armor.feet with minecraft:golden_boots");
		server.runCommand("item replace entity @p weapon.offhand with minecraft:totem_of_undying");
		server.runCommand("give @p minecraft:ender_pearl 16");
		server.runCommand("give @p minecraft:golden_apple 32");
		server.runCommand("fill 75 " + (FEET_Y - 1) + " -10 85 " + (FEET_Y - 1) + " 2 minecraft:stone");
		server.runCommand("fill 75 " + FEET_Y + " -10 85 " + (FEET_Y + 4) + " 2 minecraft:air");
		server.runCommand("setblock 78 " + FEET_Y + " -4 minecraft:respawn_anchor");
		server.runCommand("setblock 82 " + FEET_Y + " -4 minecraft:obsidian");
		server.runCommand("setblock 80 " + FEET_Y + " -3 minecraft:obsidian");
		server.runCommand("tp @p 80.5 " + FEET_Y + " 0.5 180 25");
		context.waitTicks(10);

		for (Class<? extends Module> type : java.util.List.of(CrosshairModule.class, BlockOutlineModule.class, ArmorHudModule.class,
			ItemCounterModule.class, InfoHudModule.class, LogoOverlayModule.class, BlockEspModule.class)) {
			Module module = modules.get(type);
			context.runOnClient(c -> modules.setEnabled(module, true));
		}
		context.waitTicks(25); // ESP scannt einmal pro Sekunde
		context.takeScreenshot("salami-12-visuals");

		context.setScreen(() -> new HudEditorScreen(null));
		context.waitTicks(2);
		context.takeScreenshot("salami-13-hud-editor");
		context.setScreen(() -> null);
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
