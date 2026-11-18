package dev.salamiclient.emotes;

import dev.salamiclient.capes.EmoteSync;
import dev.salamiclient.modules.social.EmotesModule;
//? if >=1.21.11 {
import net.minecraft.client.model.player.PlayerModel;
//?} else {
/*import net.minecraft.client.model.PlayerModel;
*///?}
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Laufende Emotes: dein eigenes und die anderer Salami-Spieler (live über den Cape-Server).
 *
 * <ul>
 *   <li>Dein Emote endet nach seiner Dauer, sobald du dich bewegst, springst, schleichst, schlägst
 *       oder etwas benutzt – endlose (Tanzen, T-Pose) spätestens nach 15 Sekunden.</li>
 *   <li>Aus der Ich-Sicht schaltet die Kamera für das Emote nach vorn und danach zurück
 *       (abschaltbar im Modul „Emotes“).</li>
 *   <li>Emotes anderer enden nach ihrer Dauer oder sobald sich der Spieler bewegt.</li>
 * </ul>
 *
 * Gezeichnet wird über {@code PlayerModelMixin} → {@link #pose}. Alles auf dem Client-/Render-Thread.
 */
public final class Emotes {
	/** Weiches Ein- und Ausblenden. */
	private static final float BLEND_IN_S = 0.18f;
	private static final long BLEND_OUT_MS = 220L;
	/** So weit (Blöcke, waagerecht) darf sich ein anderer Spieler bewegen, ohne dass sein Emote endet. */
	private static final double REMOTE_MOVE = 0.3;

	/** Ein laufendes Emote; {@code stopAt} = Beginn des Ausblendens oder -1. */
	private static final class Playing {
		final Emote emote;
		final long startAt;
		long stopAt = -1;
		@Nullable Vec3 origin;

		Playing(Emote emote, long startAt) {
			this.emote = emote;
			this.startAt = startAt;
		}

		/** Ende inkl. Ausblenden erreicht? */
		boolean finished(long now) {
			long end = stopAt >= 0 ? stopAt : startAt + emote.durationMs();
			return now >= end + BLEND_OUT_MS;
		}

		void stop(long now) {
			if (stopAt < 0 || stopAt > now) {
				stopAt = now;
			}
		}

		float weight(long now) {
			float in = Math.min(1f, (now - startAt) / 1000f / BLEND_IN_S);
			long end = stopAt >= 0 ? stopAt : startAt + emote.durationMs();
			float out = now <= end ? 1f : Math.max(0f, 1f - (now - end) / (float) BLEND_OUT_MS);
			return Math.min(in, out);
		}
	}

	private static @Nullable Playing own;
	private static final Map<UUID, Playing> others = new HashMap<>();
	/** Kamera vor dem Emote, wenn sie dafür umgeschaltet wurde. */
	private static @Nullable CameraType cameraBefore;

	private Emotes() {}

	/** Eigenes Emote starten (ersetzt ein laufendes) und – wenn eingeschaltet – mit anderen teilen. */
	public static void play(Emote emote) {
		Minecraft mc = Minecraft.getInstance();
		EmotesModule module = EmotesModule.instance();
		if (mc.player == null || module == null || !module.isEnabled()) {
			return;
		}
		own = new Playing(emote, System.currentTimeMillis());
		if (module.switchesCamera() && cameraBefore == null && mc.options.getCameraType().isFirstPerson()) {
			cameraBefore = mc.options.getCameraType();
			mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		}
		if (module.shares()) {
			EmoteSync.send(emote);
		}
	}

	/** Eigenes Emote (oder {@code null}). */
	public static @Nullable Emote current() {
		Playing playing = own;
		return playing == null || playing.stopAt >= 0 ? null : playing.emote;
	}

	/** Emote eines anderen Spielers kam über den Cape-Server. */
	public static void onRemote(UUID player, String id) {
		Minecraft mc = Minecraft.getInstance();
		Emote emote = EmoteCatalog.get(id);
		EmotesModule module = EmotesModule.instance();
		if (emote == null) {
			// vielleicht ein ganz neues Emote: Katalog neu laden, beim nächsten Mal ist es da
			EmoteCatalog.refreshSoon();
			return;
		}
		if (module == null || !module.isEnabled() || !module.showsOthers()
			|| (mc.player != null && mc.player.getUUID().equals(player))) {
			return;
		}
		others.put(player, new Playing(emote, System.currentTimeMillis()));
	}

	/** Für Gametests und den Befehl: alles beenden, Kamera zurück. */
	public static void clear() {
		own = null;
		others.clear();
		restoreCamera(Minecraft.getInstance());
		EmoteSync.reset();
	}

	public static void tick(Minecraft mc) {
		EmoteCatalog.tick(mc);
		long now = System.currentTimeMillis();
		Playing playing = own;
		if (playing != null) {
			if (mc.player == null || mc.player.isRemoved() || playing.finished(now)) {
				own = null;
			} else if (playing.stopAt < 0 && (movementInput(mc.options) || mc.player.hurtTime > 0)) {
				playing.stop(now);
			}
		}
		if (own == null || own.stopAt >= 0 || now >= own.startAt + own.emote.durationMs()) {
			restoreCamera(mc);
		}

		if (others.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Playing>> it = others.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Playing> entry = it.next();
			Playing other = entry.getValue();
			Player player = mc.level == null ? null : mc.level.getPlayerByUUID(entry.getKey());
			if (other.finished(now) || (player == null && now - other.startAt > 5_000L)) {
				it.remove();
				continue;
			}
			if (player != null && other.stopAt < 0) {
				Vec3 position = player.position();
				if (other.origin == null) {
					other.origin = position;
				} else if (position.subtract(other.origin).horizontalDistance() > REMOTE_MOVE) {
					other.stop(now);
				}
			}
		}
	}

	private static boolean movementInput(Options options) {
		return options.keyUp.isDown() || options.keyDown.isDown() || options.keyLeft.isDown() || options.keyRight.isDown()
			|| options.keyJump.isDown() || options.keyShift.isDown() || options.keyAttack.isDown() || options.keyUse.isDown();
	}

	private static void restoreCamera(Minecraft mc) {
		if (cameraBefore != null) {
			// nur zurückschalten, wenn niemand die Kamera inzwischen selbst umgestellt hat
			if (mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
				mc.options.setCameraType(cameraBefore);
			}
			cameraBefore = null;
		}
	}

	/** Vom {@code PlayerModelMixin} nach der normalen Animation: Emote-Haltung für dieses Modell. */
	public static void pose(PlayerModel model, AvatarRenderState state) {
		if (own == null && others.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		Entity entity = mc.level.getEntity(state.id);
		if (!(entity instanceof Player player)) {
			return;
		}
		Playing playing = player == mc.player ? own : others.get(player.getUUID());
		if (playing == null) {
			return;
		}
		long now = System.currentTimeMillis();
		float weight = playing.weight(now);
		if (weight <= 0f) {
			return;
		}
		EmotePoses.apply(model, playing.emote, (now - playing.startAt) / 1000f, weight);
		posed++;
	}

	/** Wie oft schon eine Emote-Haltung gezeichnet wurde (für Gametests). */
	private static long posed;

	public static long posedCount() {
		return posed;
	}

	/** Läuft gerade ein Emote dieses anderen Spielers? */
	public static boolean playingFor(UUID player) {
		Playing playing = others.get(player);
		return playing != null && playing.stopAt < 0;
	}
}
