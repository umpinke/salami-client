package dev.salamiclient.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import dev.salamiclient.SalamiClient;
import dev.salamiclient.capes.CapeLibrary;
import dev.salamiclient.capes.OfficialCapes;
import dev.salamiclient.cosmetics.BoxModel;
import dev.salamiclient.cosmetics.BuddyCatalog;
import dev.salamiclient.cosmetics.BuddyFollowers;
import dev.salamiclient.cosmetics.Hat;
import dev.salamiclient.cosmetics.HatCatalog;
import dev.salamiclient.emotes.Emote;
import dev.salamiclient.emotes.EmoteCatalog;
import dev.salamiclient.emotes.EmoteEffects;
import dev.salamiclient.emotes.Emotes;
import dev.salamiclient.gui.CosmeticsScreen;
import dev.salamiclient.gui.EmoteWheelScreen;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.social.EmotesModule;
import dev.salamiclient.modules.visual.BuddyModule;
import dev.salamiclient.modules.visual.HatsModule;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Cosmetics und Emotes: jeder Hut aus dem Katalog (Screenshot von vorn), jeder Buddy an jedem Platz
 * (Hand, Schulter, Kopf, läuft hinterher), Cosmetics-Menü mit Hüten, Buddys und Emotes, Emote-Rad mit
 * Seiten, alle Emotes aus dem Katalog (Haltung wird gezeichnet, Bewegen beendet sie, Kamera kommt
 * zurück), was Emotes mitbringen (der Schneeball fliegt los, die Verwandlung zum Rentier), Kataloge
 * vom Server mit einem neuen Emote, einem neuen Hut und einem offiziellen Cape (kein Update nötig)
 * und das Emote eines anderen Spielers, wie es vom Cape-Server käme.
 */
public final class SalamiCosmeticsGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!GametestCompat.selected(this)) {
			return;
		}
		ModuleManager modules = SalamiClient.get().modules();
		GametestCompat.enableExtraResourcePacks(context);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			GametestCompat.waitForChunksRender(world);
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("tp @p 0.5 -60 0.5 0 0");
			context.waitTicks(10);
			testHats(context, world, modules);
			testBuddies(context, world, modules);
			testMenu(context);
			testOwnEmotes(context, modules);
			testEmoteExtras(context, world);
			testServerCatalog(context);
			testHatCatalog(context, modules);
			testOfficialCapes(context);
			testRemoteEmote(context, modules);
		}
	}

	private static void testHats(ClientGameTestContext context, TestSingleplayerContext world, ModuleManager modules) {
		HatsModule hats = modules.get(HatsModule.class);
		context.runOnClient(c -> {
			modules.setEnabled(hats, true);
			c.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		});
		// ohne Server gilt die Kopie des Katalogs aus der Jar (beim Bauen aus server/capes/hats.json genommen)
		List<Hat> all = context.computeOnClient(c -> HatCatalog.all());
		check(all.size() >= 9 && HatCatalog.get("crown") != null && HatCatalog.get("beret") != null,
			"Hats: bundled catalog missing or incomplete (" + all.size() + ")");
		Hat santa = HatCatalog.get("santa");
		check(santa != null && santa.anchor() == Hat.Anchor.SHOULDER && santa.colors().length >= 6, "Hats: santa hat read wrong");
		Hat halo = HatCatalog.get("halo");
		// wie viele Würfel der Heiligenschein hat, steht in hats.json und darf sich ändern – sechs Flächen je Würfel bleiben
		check(halo != null && halo.glow() && halo.anchor() == Hat.Anchor.HEAD
			&& halo.colors().length >= 6 && halo.colors().length % 6 == 0, "Hats: halo read wrong");
		for (Hat hat : all) {
			if (BuddyCatalog.isBuddy(hat.id())) {
				continue; // nur noch für ältere Versionen im Hut-Katalog: siehe testBuddies
			}
			context.runOnClient(c -> hats.choose(hat.id()));
			check(context.computeOnClient(c -> HatsModule.hatOf(c.player.getId())) == hat, "Hats: own player does not wear " + hat.id());
			check(hat.id().equals(HatsModule.sharedHat()), "Hats: " + hat.id() + " is not shared");
			context.waitTicks(3);
			context.takeScreenshot("salami-80-hat-" + hat.id());
		}
		// über einem Helm
		context.runOnClient(c -> hats.choose("crown"));
		context.waitTick();
		world.getServer().runCommand("item replace entity @p armor.head with minecraft:diamond_helmet");
		context.waitTicks(5);
		context.takeScreenshot("salami-81-hat-over-helmet");
		context.runOnClient(c -> {
			hats.choose(null);
			c.options.setCameraType(CameraType.FIRST_PERSON);
		});
		world.getServer().runCommand("item replace entity @p armor.head with minecraft:air");
		check(HatCatalog.get("zylinder") == null && HatCatalog.get("top_hat") != null && HatsModule.sharedHat() == null,
			"Hats: name lookup wrong");
	}

	/**
	 * Buddys: jeder aus dem Katalog über der Schulter, einer an jedem Platz (Hand, Kopf, läuft
	 * hinterher), die Auswahl im Menü – und wer einen Buddy noch als Schulter-Hut trägt (so war es vor
	 * den Buddys), bekommt ihn als Buddy.
	 */
	private static void testBuddies(ClientGameTestContext context, TestSingleplayerContext world, ModuleManager modules) {
		HatsModule hats = modules.get(HatsModule.class);
		BuddyModule buddies = modules.get(BuddyModule.class);
		context.runOnClient(c -> {
			modules.setEnabled(hats, true);
			modules.setEnabled(buddies, true);
			buddies.choose(null);
			c.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		});
		// ohne Server gilt die Kopie des Katalogs aus der Jar (beim Bauen aus server/capes/buddies.json genommen)
		List<Hat> all = context.computeOnClient(c -> BuddyCatalog.all());
		check(all.size() >= 7 && BuddyCatalog.get("salami_buddy") != null && BuddyCatalog.get("tree_buddy") != null,
			"Buddies: bundled catalog missing or incomplete (" + all.size() + ")");
		for (Hat buddy : all) {
			check(HatCatalog.get(buddy.id()) != null, "Buddies: " + buddy.id() + " is missing as a hat for older versions");
		}
		check(BuddyModule.own() == null, "Buddies: a buddy without choosing one");

		// als Hut gewählt (ältere Einstellung): wird zum Buddy über der Schulter
		context.runOnClient(c -> hats.choose("salami_buddy"));
		context.waitTicks(3);
		check(context.computeOnClient(c -> hats.selected()) == null && buddies.selected() == BuddyCatalog.get("salami_buddy")
			&& buddies.place() == BuddyModule.Place.SHOULDER, "Buddies: a buddy worn as a hat did not become a buddy");
		check(context.computeOnClient(c -> HatsModule.hatOf(c.player.getId())) == null, "Buddies: buddy is still drawn as a hat");
		check("salami_buddy".equals(HatsModule.sharedHat()), "Buddies: shoulder buddy is not shared as a hat for older versions");

		for (Hat buddy : all) {
			context.runOnClient(c -> buddies.choose(buddy.id()));
			BuddyModule.Wish wish = context.computeOnClient(c -> BuddyModule.of(c.player));
			check(wish != null && wish.buddy() == buddy && wish.place() == BuddyModule.Place.SHOULDER, "Buddies: own player has no " + buddy.id());
			context.waitTicks(3);
			context.takeScreenshot("salami-90-buddy-" + buddy.id());
		}

		// die anderen Plätze – auf dem Kopf steht er auf dem Hut
		context.runOnClient(c -> {
			buddies.choose("snowman_buddy");
			buddies.setPlace(BuddyModule.Place.HAND);
		});
		context.waitTicks(5);
		check(HatsModule.sharedHat() == null, "Buddies: a buddy that is not on the shoulder is shared as a hat");
		context.takeScreenshot("salami-91-buddy-place-hand");
		context.runOnClient(c -> {
			hats.choose("crown");
			buddies.setPlace(BuddyModule.Place.HEAD);
		});
		context.waitTicks(5);
		context.takeScreenshot("salami-91-buddy-place-head");
		context.runOnClient(c -> {
			hats.choose(null);
			buddies.choose("reindeer_buddy");
			buddies.setPlace(BuddyModule.Place.FOLLOW);
		});
		context.waitTicks(10);
		UUID me = context.computeOnClient(c -> c.player.getUUID());
		check(context.computeOnClient(c -> BuddyFollowers.positionOf(me)) != null, "Buddies: nothing follows the player");
		context.takeScreenshot("salami-91-buddy-place-follow");
		// läuft hinterher, wenn der Spieler sich entfernt
		world.getServer().runCommand("tp @p 8.5 -60 0.5 0 0");
		context.waitTicks(50);
		double distance = context.computeOnClient(c -> BuddyFollowers.positionOf(me).distanceTo(c.player.position()));
		check(distance < 3.2, "Buddies: the follower did not come along (" + distance + " blocks away)");
		context.takeScreenshot("salami-91-buddy-followed");
		world.getServer().runCommand("tp @p 0.5 -60 0.5 0 0");
		context.waitTicks(5);

		// Menü: Platz oben, Buddys darunter
		CosmeticsScreen screen = context.computeOnClient(c -> new CosmeticsScreen(null, CosmeticsScreen.Tab.BUDDIES));
		context.setScreen(() -> screen);
		context.waitTicks(2);
		context.takeScreenshot("salami-92-cosmetics-buddies");
		context.runOnClient(c -> {
			screen.pick("buddyplace:head");
			screen.pick("buddy:tree_buddy");
		});
		check(buddies.selected() == BuddyCatalog.get("tree_buddy") && buddies.place() == BuddyModule.Place.HEAD, "Buddies: picking in the menu did nothing");
		context.waitTicks(3);
		check(context.computeOnClient(c -> BuddyFollowers.positionOf(me)) == null, "Buddies: follower stays although the buddy is on the head");
		context.runOnClient(c -> screen.pick("buddy:none"));
		check(BuddyModule.own() == null, "Buddies: putting the buddy away did nothing");
		context.setScreen(() -> null);
		context.runOnClient(c -> {
			buddies.setPlace(BuddyModule.Place.SHOULDER);
			c.options.setCameraType(CameraType.FIRST_PERSON);
		});
	}

	/**
	 * Was Emotes mitbringen: der Schneeball liegt erst in der Hand und fliegt dann wirklich los (nur
	 * Optik), die Glocke hängt in der Hand, und beim Rentier steht eine Figur anstelle des Spielers.
	 */
	private static void testEmoteExtras(ClientGameTestContext context, TestSingleplayerContext world) {
		Emote snowball = EmoteCatalog.get("snowball");
		Emote bell = EmoteCatalog.get("bell");
		Emote gift = EmoteCatalog.get("gift");
		Emote reindeer = EmoteCatalog.get("reindeer");
		check(snowball != null && snowball.thrown() != null && snowball.props().size() == 1 && snowball.sounds().size() == 1,
			"Emote extras: snowball has no throw");
		check(bell != null && bell.props().size() == 1 && bell.sounds().size() == 8, "Emote extras: bell has no bell");
		check(gift != null && gift.props().size() == 1, "Emote extras: gift has no present");
		check(reindeer != null && reindeer.costume() != null && reindeer.costume().body() != null
			&& reindeer.costume().pieces().size() == 5 && reindeer.costume().tracks().size() == 5, "Emote extras: reindeer has no costume");
		check(EmoteCatalog.get("wave").props().isEmpty() && EmoteCatalog.get("wave").thrown() == null
			&& EmoteCatalog.get("wave").costume() == null, "Emote extras: wave has extras");

		// kaputte Angaben: das ganze Emote fällt weg
		String base = "{\"id\":\"x\",\"names\":{\"en_us\":\"x\"},\"duration\":1,\"tracks\":{\"head\":{\"keys\":[{\"t\":0,\"rot\":[0,0,0]}]}},";
		String ball = "\"parts\":[{\"color\":\"#ffffff\",\"boxes\":[[-1,-1,-1,2,2,2]]}]";
		for (String broken : List.of(
			"\"props\":[{\"on\":\"tail\"," + ball + "}]}",
			"\"props\":[{\"on\":\"head\",\"from\":1,\"to\":0.5," + ball + "}]}",
			"\"throw\":{" + ball + "}}",
			"\"throw\":{\"t\":0.5,\"impact\":\"explosion\"," + ball + "}}",
			"\"sounds\":[{\"t\":0,\"sound\":\"creeper\"}]}",
			"\"costume\":{\"parts\":[]}}",
			"\"costume\":{\"parts\":[{\"follow\":\"tail\",\"color\":\"#ffffff\",\"boxes\":[[0,0,0,1,1,1]]}]}}")) {
			boolean rejected;
			try {
				Emote.parse(JsonParser.parseString(base + broken).getAsJsonObject());
				rejected = false;
			} catch (IllegalArgumentException expected) {
				rejected = true;
			}
			check(rejected, "Emote extras: accepted " + broken);
		}
		Emote parsed = Emote.parse(JsonParser.parseString(base + "\"props\":[{\"on\":\"body\"," + ball + "}],\"throw\":{\"t\":0.2," + ball + "}}").getAsJsonObject());
		check(parsed.props().get(0).on() == Emote.Limb.BODY && parsed.thrown().impact().equals("none"), "Emote extras: read wrong");

		// von hinten gesehen: der Schneeball fliegt vom Spieler weg
		long launched = EmoteEffects.launchedCount();
		context.runOnClient(c -> {
			c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			Emotes.play(snowball);
		});
		context.waitTicks(6);
		check(EmoteEffects.launchedCount() == launched, "Emote extras: snowball thrown before its time");
		context.takeScreenshot("salami-93-emote-snowball-in-hand");
		context.waitTicks(9);
		check(EmoteEffects.launchedCount() == launched + 1, "Emote extras: snowball was not thrown");
		check(context.computeOnClient(c -> EmoteEffects.flyingCount()) == 1, "Emote extras: nothing is flying");
		context.takeScreenshot("salami-93-emote-snowball-flying");
		context.waitTicks(60);
		check(context.computeOnClient(c -> EmoteEffects.flyingCount()) == 0, "Emote extras: the snowball never landed");
		check(EmoteEffects.launchedCount() == launched + 1, "Emote extras: snowball thrown twice");

		context.runOnClient(c -> Emotes.play(bell));
		context.waitTicks(10);
		context.takeScreenshot("salami-93-emote-bell-in-hand");
		context.runOnClient(c -> Emotes.play(gift));
		context.waitTicks(24);
		context.takeScreenshot("salami-93-emote-gift-in-hands");

		// Verwandlung: der Spieler ist weg, an seiner Stelle steht das Rentier – auch mit Rüstung und etwas in der Hand
		world.getServer().runCommand("item replace entity @p armor.chest with minecraft:diamond_chestplate");
		world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_sword");
		context.runOnClient(c -> Emotes.play(reindeer));
		context.waitTicks(14);
		Emotes.Active active = context.computeOnClient(c -> Emotes.activeOf(c.player));
		check(active != null && active.emote() == reindeer && active.weight() > 0.9f, "Emote extras: reindeer is not running");
		context.takeScreenshot("salami-94-emote-reindeer-from-behind");
		context.runOnClient(c -> c.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(4);
		context.takeScreenshot("salami-94-emote-reindeer-from-front");
		context.runOnClient(c -> {
			Emotes.clear();
			c.options.setCameraType(CameraType.FIRST_PERSON);
		});
		world.getServer().runCommand("item replace entity @p armor.chest with minecraft:air");
		world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:air");
		context.waitTicks(3);
		check(context.computeOnClient(c -> Emotes.activeOf(c.player)) == null, "Emote extras: emote still running after clear");
	}

	/**
	 * Hut-Katalog, wie ihn der Salami-Server liefert: ein Hut, den die Mod nicht kennt, kommt ohne
	 * Update dazu – mit Modell und Symbol aus den Daten. Kaputte Einträge fallen weg.
	 */
	private static void testHatCatalog(ClientGameTestContext context, ModuleManager modules) {
		HatsModule hats = modules.get(HatsModule.class);
		int bundled = context.computeOnClient(c -> HatCatalog.all().size());
		JsonObject fromServer = JsonParser.parseString("""
			{"version":"hats1","hats":[
			  {"id":"crown","names":{"en_us":"Crown","de_de":"Krone"},
			   "parts":[{"color":"#ecbe3a","boxes":[[-4.5,-9.5,-4.5,9,2,9]]}]},
			  {"id":"wizard","names":{"en_us":"Wizard hat","de_de":"Zauberhut"},
			   "parts":[
			     {"color":"#4b2a9e","boxes":[[-6,-8.75,-6,12,0.75,12]]},
			     {"color":"#5a35b8","at":[0,-8.75,0],"rot":[-6,0,5],"boxes":[[-3.5,-4,-3.5,7,4,7]],
			      "parts":[
			        {"color":"#f2c94c","boxes":[[-3.6,-1.5,-3.6,7.2,1,7.2]]},
			        {"color":"#5a35b8","at":[0,-4,0],"rot":[-10,0,8],"boxes":[[-2.5,-4,-2.5,5,4,5]],
			         "parts":[{"color":"#4b2a9e","at":[0,-4,0],"rot":[-14,0,10],"boxes":[[-1.25,-3.5,-1.25,2.5,3.5,2.5]]}]}
			      ]}
			   ]},
			  {"id":"ghost","names":{"en_us":"Ghost","de_de":"Geist"},"anchor":"shoulder","scale":0.5,"glow":true,"bob":1,"sway":10,
			   "parts":[
			     {"color":"#f4f6ff","boxes":[[-2.5,-6,-2.5,5,6,5]]},
			     {"color":"#20222c","boxes":[[-1.5,-4.5,-2.7,1,1.2,0.3],[0.5,-4.5,-2.7,1,1.2,0.3]]}
			   ]},
			  {"id":"ohne_farbe","names":{"en_us":"x"},"parts":[{"boxes":[[0,0,0,1,1,1]]}]},
			  {"id":"zu_tief","names":{"en_us":"x"},"parts":[{"parts":[{"parts":[{"parts":[{"color":"#ffffff","boxes":[[0,0,0,1,1,1]]}]}]}]}]},
			  {"id":"none","names":{"en_us":"x"},"parts":[{"color":"#ffffff","boxes":[[0,0,0,1,1,1]]}]},
			  {"id":"leer","names":{"en_us":"x"},"parts":[]},
			  {"id":"crown","names":{"en_us":"Twice"},"parts":[{"color":"#ffffff","boxes":[[0,0,0,1,1,1]]}]}
			]}""").getAsJsonObject();
		int revision = HatCatalog.revision();
		check(context.computeOnClient(c -> HatCatalog.applyRemote(fromServer)), "Hats: server catalog rejected");
		List<Hat> now = context.computeOnClient(c -> HatCatalog.all());
		check(now.size() == 3 && now.get(1).id().equals("wizard") && now.get(2).id().equals("ghost"),
			"Hats: server catalog not applied: " + now.size());
		check(HatCatalog.revision() != revision, "Hats: catalog change not announced to the menu");
		Hat wizard = HatCatalog.get("wizard");
		check(wizard != null && wizard.colors().length == 5 * 6 && wizard.quads().length == 5 * 6 * BoxModel.QUAD, "Hats: new hat read wrong");

		// die neuen Hüte lassen sich tragen wie jeder andere, im Menü stehen sie mit gezeichnetem Symbol
		context.runOnClient(c -> {
			c.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			hats.choose("wizard");
		});
		check(context.computeOnClient(c -> HatsModule.hatOf(c.player.getId())) == wizard && "wizard".equals(HatsModule.sharedHat()),
			"Hats: hat from the server is not worn");
		context.waitTicks(3);
		context.takeScreenshot("salami-88-hat-from-server");
		context.runOnClient(c -> hats.choose("ghost"));
		context.waitTicks(3);
		context.takeScreenshot("salami-88b-shoulder-hat-from-server");
		context.runOnClient(c -> c.options.setCameraType(CameraType.FIRST_PERSON));
		context.setScreen(() -> new CosmeticsScreen(null, CosmeticsScreen.Tab.HATS));
		context.waitTicks(2);
		context.takeScreenshot("salami-88c-cosmetics-hats-server-catalog");
		context.setScreen(() -> null);

		// leerer Katalog: der alte bleibt
		JsonObject empty = JsonParser.parseString("{\"version\":\"leer\",\"hats\":[]}").getAsJsonObject();
		check(!context.computeOnClient(c -> HatCatalog.applyRemote(empty)), "Hats: empty catalog accepted");
		check(HatCatalog.get("wizard") != null, "Hats: empty catalog wiped the hats");

		// der Hut verschwindet vom Server: er ist einfach weg, die Auswahl bleibt ohne Wirkung stehen
		context.runOnClient(c -> {
			hats.choose("wizard");
			HatCatalog.clearRemote();
		});
		check(context.computeOnClient(c -> HatCatalog.all().size()) == bundled && HatCatalog.get("wizard") == null,
			"Hats: bundled catalog not restored");
		check(context.computeOnClient(c -> HatsModule.hatOf(c.player.getId())) == null && HatsModule.sharedHat() == null,
			"Hats: a hat that no longer exists is still worn");
		context.runOnClient(c -> hats.choose(null));
	}

	/**
	 * Offizielle Capes, wie sie der Salami-Server liefert: sie stehen ohne Update im Cosmetics-Menü.
	 * Anlegen holt das Bild zu den eigenen Capes – jedes nur einmal.
	 */
	private static void testOfficialCapes(ClientGameTestContext context) {
		check(context.computeOnClient(c -> OfficialCapes.all().isEmpty()), "Capes: official capes without a server");
		String imageId = "ab".repeat(32);
		JsonObject fromServer = JsonParser.parseString("""
			{"version":"capes1","capes":[
			  {"id":"frost","names":{"en_us":"Frost","de_de":"Frost"},"cape":"%s","width":128,"height":64},
			  {"id":"ember","names":{"en_us":"Ember","de_de":"Glut"},"cape":"%s","width":64,"height":32},
			  {"id":"ohne_bild","names":{"en_us":"x"},"width":64,"height":32},
			  {"id":"schief","names":{"en_us":"x"},"cape":"%s","width":64,"height":64},
			  {"id":"frost","names":{"en_us":"Twice"},"cape":"%s","width":64,"height":32}
			]}""".formatted(imageId, "cd".repeat(32), imageId, imageId)).getAsJsonObject();
		int revision = OfficialCapes.revision();
		check(context.computeOnClient(c -> OfficialCapes.applyRemote(fromServer)), "Capes: server catalog rejected");
		List<OfficialCapes.Cape> capes = context.computeOnClient(c -> OfficialCapes.all());
		check(capes.size() == 2 && capes.get(0).id().equals("frost") && capes.get(1).width() == 64,
			"Capes: server catalog not applied: " + capes.size());
		check(OfficialCapes.revision() != revision, "Capes: catalog change not announced to the menu");
		OfficialCapes.Cape frost = OfficialCapes.get("frost");
		check(frost != null && frost.capeId().equals(imageId) && OfficialCapes.byOwnId(OfficialCapes.ownId(frost)) == frost,
			"Capes: official cape read wrong");
		check(OfficialCapes.byOwnId("official-" + "0".repeat(32)) == null && OfficialCapes.byOwnId("irgendwas") == null,
			"Capes: unknown own cape matched an official one");

		// im Menü stehen sie bei den Capes (ohne Server lädt die Vorschau nicht)
		context.setScreen(() -> new CosmeticsScreen(null, CosmeticsScreen.Tab.CAPES));
		context.waitTicks(2);
		// sie stehen hinter den mitgelieferten Capes: zwei Reihen weiter unten
		context.getInput().scroll(-1);
		context.getInput().scroll(-1);
		context.waitTicks(2);
		context.takeScreenshot("salami-89-cosmetics-official-capes");
		context.setScreen(() -> null);

		// anlegen: das Bild landet bei den eigenen Capes, auch beim zweiten Mal nur einmal
		List<CapeLibrary.OwnCape> own = context.computeOnClient(c -> {
			try {
				Path dir = Files.createTempDirectory("salami-official-capes");
				Path source = dir.resolve("source.png");
				try (NativeImage image = new NativeImage(64, 32, true)) {
					for (int y = 0; y < 32; y++) {
						for (int x = 0; x < 64; x++) {
							image.setPixel(x, y, 0xFF2060C0 + x);
						}
					}
					image.writeToFile(source);
				}
				byte[] png = Files.readAllBytes(source);
				Files.delete(source);
				CapeLibrary.adoptOfficial(dir, OfficialCapes.ownId(frost), frost.displayName(), png);
				CapeLibrary.adoptOfficial(dir, OfficialCapes.ownId(frost), frost.displayName(), png);
				return CapeLibrary.ownCapes(dir);
			} catch (IOException | CapeLibrary.InvalidCapeException e) {
				throw new AssertionError("Capes: official cape could not be adopted", e);
			}
		});
		check(own.size() == 1 && own.get(0).id().equals(OfficialCapes.ownId(frost)) && own.get(0).name().equals("Frost"),
			"Capes: adopted official cape missing or doubled: " + own.size());

		// ein leerer Katalog gilt hier: der Server hat dann einfach keine offiziellen Capes mehr
		JsonObject empty = JsonParser.parseString("{\"version\":\"leer\",\"capes\":[]}").getAsJsonObject();
		check(context.computeOnClient(c -> OfficialCapes.applyRemote(empty)) && OfficialCapes.all().isEmpty(),
			"Capes: empty catalog not applied");
		context.runOnClient(c -> OfficialCapes.clearRemote());
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
			// bei einer Verwandlung wird das Spielermodell gar nicht gezeichnet – dort steht die Figur
			check(emote.costume() != null ? context.computeOnClient(c -> Emotes.activeOf(c.player)) != null : Emotes.posedCount() > before,
				"Emotes: " + emote.id() + " pose was never drawn");
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
			hats.choose("party");
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
		// der andere wirft einen Schneeball und wird zum Rentier – so, wie es jeder Mitspieler sieht
		long launched = EmoteEffects.launchedCount();
		context.runOnClient(c -> {
			// er schaut zur Seite, damit der Schneeball quer durchs Bild fliegt
			other.snapTo(5.5, -60, 4.5, 90F, 0F);
			other.setYHeadRot(90F);
			other.yBodyRot = 90F;
			Emotes.onRemote(uuid, "snowball");
		});
		context.waitTicks(15);
		check(EmoteEffects.launchedCount() == launched + 1, "Emotes: remote snowball was not thrown");
		check(context.computeOnClient(c -> EmoteEffects.flyingCount()) == 1, "Emotes: remote snowball is not flying");
		context.takeScreenshot("salami-86c-emote-remote-snowball");
		context.runOnClient(c -> Emotes.onRemote(uuid, "reindeer"));
		context.waitTicks(14);
		Emotes.Active theirs = context.computeOnClient(c -> Emotes.activeOf(other));
		check(theirs != null && theirs.emote().costume() != null, "Emotes: remote player did not turn into the reindeer");
		context.takeScreenshot("salami-86b-emote-remote-reindeer");
		context.runOnClient(c -> {
			other.discard();
			Emotes.clear();
			hats.choose(null);
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
