package dev.salamiclient.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.emotes.Emote;
import dev.salamiclient.emotes.EmoteCatalog;
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
import java.util.List;
import java.util.UUID;

/**
 * Cosmetics und Emotes: jeder Hut auf dem Kopf (Screenshot von vorn), Cosmetics-Menü mit Hüten und
 * Emotes, Emote-Rad mit Seiten, alle Emotes aus dem Katalog (Haltung wird gezeichnet, Bewegen
 * beendet sie, Kamera kommt zurück), ein Katalog vom Server mit einem neuen Emote (kein Update
 * nötig) und das Emote eines anderen Spielers, wie es vom Cape-Server käme.
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
			testServerCatalog(context);
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
		// ohne Server gilt die Kopie des Katalogs aus der Jar (beim Bauen aus server/capes/emotes.json genommen)
		List<Emote> builtin = context.computeOnClient(c -> EmoteCatalog.all());
		check(builtin.size() >= 8 && EmoteCatalog.get("wave") != null && EmoteCatalog.get("tpose") != null,
			"Emotes: bundled catalog missing or incomplete (" + builtin.size() + ")");
		context.setScreen(() -> new CosmeticsScreen(null, CosmeticsScreen.Tab.HATS));
		context.waitTicks(2);
		context.takeScreenshot("salami-82-cosmetics-hats");
		context.setScreen(() -> new CosmeticsScreen(null, CosmeticsScreen.Tab.EMOTES));
		context.waitTicks(2);
		context.takeScreenshot("salami-83-cosmetics-emotes");
		context.setScreen(() -> new EmoteWheelScreen(SalamiClient.get().emoteKey()));
		context.waitTicks(2);
		context.takeScreenshot("salami-84-emote-wheel");
		if (builtin.size() > 8) {
			// Mausrad blättert zur zweiten Seite
			context.getInput().scroll(-1);
			context.waitTicks(2);
			context.takeScreenshot("salami-84b-emote-wheel-page-2");
			context.getInput().scroll(1);
		}
		context.setScreen(() -> null);
	}

	private static void testOwnEmotes(ClientGameTestContext context, ModuleManager modules) {
		EmotesModule module = modules.get(EmotesModule.class);
		context.runOnClient(c -> modules.setEnabled(module, true));
		for (Emote emote : context.computeOnClient(c -> EmoteCatalog.all())) {
			long before = Emotes.posedCount();
			context.runOnClient(c -> Emotes.play(emote));
			check(context.computeOnClient(c -> c.options.getCameraType()) == CameraType.THIRD_PERSON_FRONT,
				"Emotes: camera did not turn to the front for " + emote.id());
			context.waitTicks(12);
			check(Emotes.posedCount() > before, "Emotes: " + emote.id() + " pose was never drawn");
			context.takeScreenshot("salami-85-emote-" + emote.id());
		}
		// Bewegen beendet das Emote, die Kamera kommt zurück
		context.runOnClient(c -> Emotes.play(EmoteCatalog.get("dance")));
		context.waitTicks(2);
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(3);
		context.getInput().releaseKey(options -> options.keyUp);
		check(context.computeOnClient(c -> Emotes.current()) == null, "Emotes: moving did not stop the emote");
		context.waitTicks(10);
		check(context.computeOnClient(c -> c.options.getCameraType()) == CameraType.FIRST_PERSON, "Emotes: camera did not come back");
	}

	/**
	 * Katalog, wie ihn der Salami-Server liefert: ein Emote, das die Mod nicht kennt, kommt ohne Update
	 * dazu. Kaputte Einträge fallen weg, ein leerer Katalog ändert nichts.
	 */
	private static void testServerCatalog(ClientGameTestContext context) {
		int bundled = context.computeOnClient(c -> EmoteCatalog.all().size());
		JsonObject fromServer = JsonParser.parseString("""
			{"version":"test1","emotes":[
			  {"id":"wave","names":{"en_us":"Wave","de_de":"Winken"},"duration":2.4,
			   "tracks":{"right_arm":{"cycle":0.57,"keys":[{"t":0,"rot":[0,0,129]},{"t":0.285,"rot":[0,0,169]}]}}},
			  {"id":"superhero","names":{"en_us":"Superhero","de_de":"Superheld"},"loop":true,
			   "tracks":{"right_arm":{"keys":[{"t":0,"rot":[-170,0,-8]}]},
			             "left_arm":{"keys":[{"t":0,"rot":[0,0,-35]}]},
			             "head":{"base":"current","keys":[{"t":0,"rot":[-15,0,0]}]}}},
			  {"id":"Kaputt!","names":{"en_us":"x"},"duration":1,"tracks":{"head":{"keys":[{"t":0,"rot":[0,0,0]}]}}},
			  {"id":"zu_lang","names":{"en_us":"x"},"duration":99,"tracks":{"head":{"keys":[{"t":0,"rot":[0,0,0]}]}}},
			  {"id":"ohne_namen","duration":1,"tracks":{"head":{"keys":[{"t":0,"rot":[0,0,0]}]}}},
			  {"id":"falsches_teil","names":{"en_us":"x"},"duration":1,"tracks":{"tail":{"keys":[{"t":0,"rot":[0,0,0]}]}}},
			  {"id":"wave","names":{"en_us":"Twice"},"duration":1,"tracks":{"head":{"keys":[{"t":0,"rot":[0,0,0]}]}}}
			]}""").getAsJsonObject();
		int revision = EmoteCatalog.revision();
		check(context.computeOnClient(c -> EmoteCatalog.applyRemote(fromServer)), "Emotes: server catalog rejected");
		List<Emote> now = context.computeOnClient(c -> EmoteCatalog.all());
		check(now.size() == 2 && now.get(1).id().equals("superhero"), "Emotes: server catalog not applied: " + now.size());
		check(EmoteCatalog.revision() != revision, "Emotes: catalog change not announced to the menus");
		Emote hero = EmoteCatalog.get("superhero");
		check(hero != null && hero.loops() && hero.durationMs() == Emote.MAX_LOOP_MS, "Emotes: new emote read wrong");

		// das neue Emote spielt wie jedes andere, im Menü steht es mit gezeichnetem Symbol
		long before = Emotes.posedCount();
		context.runOnClient(c -> Emotes.play(hero));
		context.waitTicks(12);
		check(Emotes.posedCount() > before, "Emotes: emote from the server was never drawn");
		context.takeScreenshot("salami-87-emote-from-server");
		context.runOnClient(c -> Emotes.clear());
		context.setScreen(() -> new CosmeticsScreen(null, CosmeticsScreen.Tab.EMOTES));
		context.waitTicks(2);
		context.takeScreenshot("salami-87b-cosmetics-server-catalog");
		context.setScreen(() -> null);

		// gleicher Stand noch einmal: nichts wird neu aufgebaut; leerer Katalog: der alte bleibt
		int unchanged = EmoteCatalog.revision();
		context.runOnClient(c -> EmoteCatalog.applyRemote(fromServer));
		check(EmoteCatalog.revision() == unchanged, "Emotes: unchanged catalog rebuilt the menus");
		JsonObject empty = JsonParser.parseString("{\"version\":\"leer\",\"emotes\":[]}").getAsJsonObject();
		check(!context.computeOnClient(c -> EmoteCatalog.applyRemote(empty)), "Emotes: empty catalog accepted");
		check(EmoteCatalog.get("superhero") != null, "Emotes: empty catalog wiped the emotes");

		context.runOnClient(c -> EmoteCatalog.clearRemote());
		check(context.computeOnClient(c -> EmoteCatalog.all().size()) == bundled && EmoteCatalog.get("superhero") == null,
			"Emotes: bundled catalog not restored");
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
			Emotes.play(EmoteCatalog.get("cheer"));
		});
		check(Emotes.playingFor(uuid), "Emotes: remote emote not started");
		context.waitTicks(15);
		context.takeScreenshot("salami-86-emote-remote");
		context.runOnClient(c -> Emotes.onRemote(uuid, "gibt_es_nicht")); // unbekannt: ignorieren, laufendes bleibt
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
