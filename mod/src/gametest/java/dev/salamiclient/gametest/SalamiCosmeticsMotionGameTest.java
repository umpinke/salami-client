package dev.salamiclient.gametest;

import dev.salamiclient.SalamiClient;
import dev.salamiclient.cosmetics.BuddyFollowers;
import dev.salamiclient.minime.MiniMeFollowers;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.ModuleManager;
import dev.salamiclient.modules.visual.BuddyModule;
import dev.salamiclient.modules.visual.CapesModule;
import dev.salamiclient.modules.visual.HatsModule;
import dev.salamiclient.modules.visual.MiniMeModule;
import dev.salamiclient.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Cosmetics in Bewegung: Hut, Buddy, Mini Me und Cape an jedem Platz, in jeder Ansicht (Ich-Perspektive,
 * von hinten, von vorn) beim Stehen, Gehen, Rennen, Schleichen, schnellen Umschauen und Springen.
 * Je Zustand mehrere Screenshots hintereinander, dazu misst {@link Probe} in jedem einzelnen Frame, wo
 * jedes Cosmetic gezeichnet wird ({@code salami-motion.csv} im Testordner), und prüft, dass es genau am
 * gezeichneten Körperteil hängt und Figuren an Hand und Kopf aufrecht bleiben ({@link #check}).
 *
 * <p>Mit Animations-Mods: {@code -PsalamiExtraMods=…/punchy-….jar;…/punchy-moves-….jar} und
 * {@code -PsalamiExtraRunFiles=<Ordner mit config/ und resourcepacks/>} (Punchy! Moves braucht sein
 * Ressourcenpaket, sonst hält ein Hinweisfenster das Spiel an).
 */
public final class SalamiCosmeticsMotionGameTest implements FabricClientGameTest {
	private static final int FEET_Y = -60;

	/** Was getragen wird: je Platz eins, damit sie sich nicht in die Quere kommen. */
	private record Look(String id, String hat, String buddy, BuddyModule.Place buddyPlace, MiniMeModule.Place miniPlace, CapesModule.Cape cape) {}

	private static final List<Look> LOOKS = List.of(
		new Look("a", "halo", "salami_buddy", BuddyModule.Place.SHOULDER, MiniMeModule.Place.HAND, CapesModule.Cape.SALAMI),
		new Look("b", "crown", "tree_buddy", BuddyModule.Place.HEAD, MiniMeModule.Place.SHOULDER, CapesModule.Cape.AURORA),
		new Look("c", null, "snowman_buddy", BuddyModule.Place.HAND, MiniMeModule.Place.HEAD, CapesModule.Cape.DRAGON),
		new Look("d", "top_hat", "reindeer_buddy", BuddyModule.Place.FOLLOW, MiniMeModule.Place.FOLLOW, CapesModule.Cape.GALAXY));

	private enum State { STAND, WALK, SPRINT, SNEAK, LOOK, JUMP }

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!GametestCompat.selected(this)) {
			return;
		}
		ModuleManager modules = SalamiClient.get().modules();
		HatsModule hats = modules.get(HatsModule.class);
		BuddyModule buddies = modules.get(BuddyModule.class);
		MiniMeModule minis = modules.get(MiniMeModule.class);
		Module capes = modules.get(CapesModule.class);
		StringBuilder csv = new StringBuilder();
		GametestCompat.enableExtraResourcePacks(context);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			GametestCompat.waitForChunksRender(world);
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			home(context, world);
			context.runOnClient(c -> {
				modules.setEnabled(hats, true);
				modules.setEnabled(buddies, true);
				modules.setEnabled(minis, true);
				modules.setEnabled(capes, true);
				minis.setName("Steve");
			});
			set(capes, "animated", true);
			set(capes, "quality", CapesModule.Quality.HD);
			try {
				capeSequence(context, world, capes);
				for (Look look : LOOKS) {
					context.runOnClient(c -> {
						hats.choose(look.hat());
						buddies.choose(look.buddy());
						buddies.setPlace(look.buddyPlace());
						minis.setPlace(look.miniPlace());
					});
					set(capes, "cape", look.cape());
					for (CameraType view : CameraType.values()) {
						for (State state : State.values()) {
							csv.append(run(context, world, look, view, state));
						}
					}
				}
			} finally {
				Probe.stop();
				Probe.turn(0, 0);
				context.getInput().releaseKey(o -> o.keyUp);
				context.getInput().releaseKey(o -> o.keySprint);
				context.getInput().releaseKey(o -> o.keyShift);
				context.runOnClient(c -> {
					hats.choose(null);
					buddies.choose(null);
					buddies.setPlace(BuddyModule.Place.SHOULDER);
					minis.setPlace(MiniMeModule.Place.NONE);
					c.options.setCameraType(CameraType.FIRST_PERSON);
				});
				set(capes, "cape", CapesModule.Cape.LAUNCHER);
				write(csv.toString());
			}
		}
	}

	/**
	 * Das Cape von hinten, groß und Tick für Tick (zum Ansehen, wie es schwingt): eigenes Bild
	 * ({@code config/salami/cape.png}, falls der Testlauf eins mitbringt), sonst das gemalte Salami-Cape
	 * in 2K – beim Gehen, Rennen und Umdrehen.
	 */
	private static void capeSequence(ClientGameTestContext context, TestSingleplayerContext world, Module capes) {
		int[] window = context.computeOnClient(c -> new int[] {c.getWindow().getScreenWidth(), c.getWindow().getScreenHeight()});
		context.runOnClient(c -> {
			c.getWindow().setWindowed(1280, 720);
			c.options.fov().set(50);
			c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		});
		CapesModule.Cape cape = Files.isRegularFile(CapesModule.customCapeFile()) ? CapesModule.Cape.CUSTOM : CapesModule.Cape.SALAMI;
		try {
			set(capes, "quality", CapesModule.Quality.QHD);
			set(capes, "cape", cape);
			for (State state : new State[] {State.WALK, State.SPRINT, State.LOOK}) {
				home(context, world);
				context.waitTicks(30);
				switch (state) {
					case WALK -> context.getInput().holdKey(o -> o.keyUp);
					case SPRINT -> {
						context.getInput().holdKey(o -> o.keySprint);
						context.getInput().holdKey(o -> o.keyUp);
					}
					default -> Probe.turn(240f, 0f);
				}
				context.waitTicks(10);
				for (int i = 0; i < 4; i++) {
					context.takeScreenshot("salami-mc-" + cape.name().toLowerCase(Locale.ROOT) + "-" + state.name().toLowerCase(Locale.ROOT) + "-" + i);
					context.waitTick();
				}
				Probe.turn(0, 0);
				context.getInput().releaseKey(o -> o.keyUp);
				context.getInput().releaseKey(o -> o.keySprint);
			}
		} finally {
			set(capes, "quality", CapesModule.Quality.HD);
			context.runOnClient(c -> {
				c.getWindow().setWindowed(window[0], window[1]);
				c.options.fov().set(70);
			});
		}
	}

	/** Einen Zustand durchspielen: hinstellen, Bewegung starten, einschwingen, drei Bilder hintereinander. */
	private static String run(ClientGameTestContext context, TestSingleplayerContext world, Look look, CameraType view, State state) {
		home(context, world);
		context.runOnClient(c -> {
			c.options.setCameraType(view);
			BuddyFollowers.clear();
			MiniMeFollowers.clear();
		});
		context.waitTicks(12);
		String label = look.id() + "-" + view.name().toLowerCase(Locale.ROOT).replace("third_person_", "") + "-" + state.name().toLowerCase(Locale.ROOT);
		Probe.take();
		Probe.start(label);
		switch (state) {
			case STAND -> {}
			case WALK -> context.getInput().holdKey(o -> o.keyUp);
			case SPRINT -> {
				context.getInput().holdKey(o -> o.keySprint);
				context.getInput().holdKey(o -> o.keyUp);
			}
			case SNEAK -> {
				context.getInput().holdKey(o -> o.keyShift);
				context.getInput().holdKey(o -> o.keyUp);
			}
			case LOOK -> Probe.turn(420f, 25f);
			case JUMP -> context.getInput().pressKey(o -> o.keyJump);
		}
		context.waitTicks(state == State.JUMP ? 3 : 10);
		for (int i = 0; i < 3; i++) {
			// das Screenshot-Bild zeichnet die Test-API mit eigener Zeit – nicht mitmessen
			Probe.stop();
			context.takeScreenshot("salami-m-" + label + "-" + i);
			context.waitTick();
			Probe.start(label);
			context.waitTicks(state == State.JUMP ? 2 : 1);
		}
		Probe.stop();
		Probe.turn(0, 0);
		context.getInput().releaseKey(o -> o.keyUp);
		context.getInput().releaseKey(o -> o.keySprint);
		context.getInput().releaseKey(o -> o.keyShift);
		List<Probe.Sample> samples = Probe.take();
		check(look, view, state, samples);
		String rows = Probe.csv(samples);
		// Kopfzeile nur einmal
		return rows.substring(rows.indexOf('\n') + 1);
	}

	/**
	 * Was in jedem Frame stimmen muss:
	 * <ul>
	 * <li>Jedes Cosmetic hängt genau dort, wo das Spiel im selben Frame das Körperteil zeichnet – auch
	 * wenn Animations-Mods (Punchy! Moves) den Körper bewegen.</li>
	 * <li>Ein Buddy an der Hand baumelt aufrecht (nur sein sanftes Pendeln), statt jeden Armschwung
	 * mitzumachen; auf dem Kopf kippt er beim Umschauen nur ein wenig mit.</li>
	 * </ul>
	 */
	private static void check(Look look, CameraType view, State state, List<Probe.Sample> samples) {
		if (view == CameraType.FIRST_PERSON) {
			return; // das eigene Modell wird nicht gezeichnet
		}
		String where = look.id() + "/" + view + "/" + state;
		java.util.Map<String, Probe.Sample> drawn = new java.util.HashMap<>();
		for (Probe.Sample s : samples) {
			if (s.caller().equals("drawn")) {
				drawn.put(s.frame() + "/" + s.part(), s);
			}
		}
		int compared = 0;
		for (Probe.Sample s : samples) {
			if (s.caller().equals("drawn") || s.part().equals("box") || s.part().equals("world")) {
				continue;
			}
			Probe.Sample part = drawn.get(s.frame() + "/" + s.part());
			if (part == null) {
				continue;
			}
			compared++;
			float off = s.at().distance(part.at()) * 16f;
			float turned = new org.joml.Vector3f(s.down()).sub(s.at()).distance(new org.joml.Vector3f(part.down()).sub(part.at())) * 16f;
			if (off > 0.01f || turned > 0.01f) {
				throw new AssertionError(String.format(Locale.ROOT, "Motion %s: %s hangs %.2f px off the drawn %s (turned %.2f px)",
					where, s.caller(), off, s.part(), turned));
			}
		}
		if (compared == 0 && samples.stream().anyMatch(s -> s.part().equals("box"))) {
			throw new AssertionError("Motion " + where + ": body parts were never drawn – nothing to compare");
		}
		float maxTilt = switch (look.buddyPlace()) {
			case HAND -> 10f; // Pendeln ±7°
			case HEAD -> state == State.LOOK ? 25f : 15f; // nickt nur zu gut einem Drittel mit
			default -> Float.MAX_VALUE;
		};
		for (Probe.Sample s : samples) {
			if (s.caller().equals("BuddyLayer") && s.part().equals("box")) {
				org.joml.Vector3f down = new org.joml.Vector3f(s.down()).sub(s.at()).normalize();
				float tilt = (float) Math.toDegrees(Math.acos(Math.max(-1f, Math.min(1f, -down.y))));
				if (tilt > maxTilt) {
					throw new AssertionError(String.format(Locale.ROOT, "Motion %s: buddy (%s) tilts %.1f° (max %.0f°)",
						where, look.buddyPlace(), tilt, maxTilt));
				}
			}
		}
	}

	private static void home(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("tp @p 0.5 " + FEET_Y + " 0.5 0 10");
		context.waitTick();
		context.runOnClient(c -> {
			c.player.setYRot(0f);
			c.player.setXRot(10f);
			c.player.setYHeadRot(0f);
			c.player.setYBodyRot(0f);
			c.player.yBodyRotO = 0f;
			c.player.setDeltaMovement(0, 0, 0);
		});
	}

	private static void write(String rows) {
		Path file = FabricLoader.getInstance().getGameDir().resolve("salami-motion.csv");
		try {
			Files.writeString(file, Probe.csv(List.of()) + rows, StandardCharsets.UTF_8);
			SalamiClient.LOG.info("[Gametest] Bewegungsmessung: {}", file.toAbsolutePath());
		} catch (IOException e) {
			throw new AssertionError("Motion: could not write " + file, e);
		}
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
}
