package dev.salamiclient.gametest;

import com.mojang.authlib.GameProfile;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.emotes.Emote;
import dev.salamiclient.emotes.Emotes;
import dev.salamiclient.gui.CosmeticsScreen;
import dev.salamiclient.gui.EmoteWheelScreen;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.social.EmotesModule;
import dev.salamiclient.modules.visual.HatsModule;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Cosmetics und Emotes: jeder Hut auf dem Kopf (Screenshot von vorn), Cosmetics-Menü mit Hüten und
 * Emotes, Emote-Rad, eigene Emotes (Haltung wird gezeichnet, Bewegen beendet sie, Kamera kommt zurück)
 * und das Emote eines anderen Spielers, wie es vom Cape-Server käme.
 */
public final class SalamiCosmeticsGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!GametestCompat.selected(this)) {
			return;
		}
		ModuleManager modules = SalamiClient.get().modules();
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			GametestCompat.waitForChunksRender(world);
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("tp @p 0.5 -60 0.5 0 0");
			context.waitTicks(10);
			testHats(context, world, modules);
			testMenu(context);
			testOwnEmotes(context, modules);
			testRemoteEmote(context, modules);
		}
	}

	private static void testHats(ClientGameTestContext context, TestSingleplayerContext world, ModuleManager modules) {
		HatsModule hats = modules.get(HatsModule.class);
		context.runOnClient(c -> {
			modules.setEnabled(hats, true);
			c.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		});
		for (HatsModule.Hat hat : HatsModule.Hat.values()) {
			if (hat == HatsModule.Hat.NONE) {
				continue;
			}
			context.runOnClient(c -> hats.choose(hat));
			check(context.computeOnClient(c -> HatsModule.hatOf(c.player.getId())) == hat, "Hats: own player does not wear " + hat);
			check(hat.id().equals(HatsModule.sharedHat()), "Hats: " + hat + " is not shared");
			context.waitTicks(3);
			context.takeScreenshot("salami-80-hat-" + hat.id());
		}
		// über einem Helm
		context.runOnClient(c -> hats.choose(HatsModule.Hat.CROWN));
		context.waitTick();
		world.getServer().runCommand("item replace entity @p armor.head with minecraft:diamond_helmet");
		context.waitTicks(5);
		context.takeScreenshot("salami-81-hat-over-helmet");
		context.runOnClient(c -> {
			hats.choose(HatsModule.Hat.NONE);
			c.options.setCameraType(CameraType.FIRST_PERSON);
		});
		world.getServer().runCommand("item replace entity @p armor.head with minecraft:air");
		check(HatsModule.Hat.of("zylinder") == HatsModule.Hat.NONE && HatsModule.Hat.of("top_hat") == HatsModule.Hat.TOP_HAT,
			"Hats: name lookup wrong");
	}

	private static void testMenu(ClientGameTestContext context) {
		context.setScreen(() -> new CosmeticsScreen(null, CosmeticsScreen.Tab.HATS));
		context.waitTicks(2);
		context.takeScreenshot("salami-82-cosmetics-hats");
		context.setScreen(() -> new CosmeticsScreen(null, CosmeticsScreen.Tab.EMOTES));
		context.waitTicks(2);
		context.takeScreenshot("salami-83-cosmetics-emotes");
		context.setScreen(() -> new EmoteWheelScreen(SalamiClient.get().emoteKey()));
		context.waitTicks(2);
		context.takeScreenshot("salami-84-emote-wheel");
		context.setScreen(() -> null);
	}

	private static void testOwnEmotes(ClientGameTestContext context, ModuleManager modules) {
		EmotesModule module = modules.get(EmotesModule.class);
		context.runOnClient(c -> modules.setEnabled(module, true));
		for (Emote emote : Emote.values()) {
			long before = Emotes.posedCount();
			context.runOnClient(c -> Emotes.play(emote));
			check(context.computeOnClient(c -> c.options.getCameraType()) == CameraType.THIRD_PERSON_FRONT,
				"Emotes: camera did not turn to the front for " + emote);
			context.waitTicks(12);
			check(Emotes.posedCount() > before, "Emotes: " + emote + " pose was never drawn");
			context.takeScreenshot("salami-85-emote-" + emote.id());
		}
		// Bewegen beendet das Emote, die Kamera kommt zurück
		context.runOnClient(c -> Emotes.play(Emote.DANCE));
		context.waitTicks(2);
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(3);
		context.getInput().releaseKey(options -> options.keyUp);
		check(context.computeOnClient(c -> Emotes.current()) == null, "Emotes: moving did not stop the emote");
		context.waitTicks(10);
		check(context.computeOnClient(c -> c.options.getCameraType()) == CameraType.FIRST_PERSON, "Emotes: camera did not come back");
	}

	/** Emote eines anderen Spielers, wie es über die Live-Verbindung des Cape-Servers ankäme. */
	private static void testRemoteEmote(ClientGameTestContext context, ModuleManager modules) {
		HatsModule hats = modules.get(HatsModule.class);
		RemotePlayer other = context.computeOnClient(c -> fakePlayer(c, 910_001, "Tanzbein", 2.5, -60, 4.5));
		UUID uuid = other.getUUID();
		context.runOnClient(c -> {
			hats.choose(HatsModule.Hat.PARTY);
			c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			Emotes.onRemote(uuid, "dance");
			Emotes.play(Emote.CHEER);
		});
		check(Emotes.playingFor(uuid), "Emotes: remote emote not started");
		context.waitTicks(15);
		context.takeScreenshot("salami-86-emote-remote");
		context.runOnClient(c -> Emotes.onRemote(uuid, "moonwalk")); // unbekannt: ignorieren, laufendes bleibt
		check(Emotes.playingFor(uuid), "Emotes: unknown emote stopped the running one");
		// bewegt sich der andere Spieler, endet sein Emote
		context.runOnClient(c -> other.snapTo(5.5, -60, 4.5, 180F, 0F));
		context.waitTicks(2);
		check(!Emotes.playingFor(uuid), "Emotes: remote emote did not stop when the player moved");
		context.runOnClient(c -> {
			other.discard();
			Emotes.clear();
			hats.choose(HatsModule.Hat.NONE);
			c.options.setCameraType(CameraType.FIRST_PERSON);
		});
	}

	private static RemotePlayer fakePlayer(Minecraft c, int id, String name, double x, double y, double z) {
		RemotePlayer player = new RemotePlayer(c.level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
		player.setId(id);
		player.snapTo(x, y, z, 180F, 0F); // schaut zur Kamera
		c.level.addEntity(player);
		return player;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
