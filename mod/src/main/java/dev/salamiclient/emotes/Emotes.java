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
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Laufende Emotes: dein eigenes und die anderer Salami-Spieler (live über den Cape-Server).
 *
 * <ul>
 *   <li>Dein Emote endet nach seiner Dauer oder sobald du schlägst – endlose (Tanzen, T-Pose)
 *       spätestens nach 15 Sekunden. Laufen, Springen, Schleichen und Benutzen beenden es nicht: die
 *       Haltung liegt über der normalen Bewegung, Beine ohne eigene Spur laufen einfach weiter.</li>
 *   <li>Aus der Ich-Sicht schaltet die Kamera für das Emote nach vorn und danach zurück
 *       (abschaltbar im Modul „Emotes“).</li>
 *   <li>Emotes anderer enden nach ihrer Dauer oder sobald der Spieler mit der Haupthand ausholt –
 *       so sieht ein Schlag von außen aus (einen eigenen „Stopp“ schickt der Cape-Server nicht).</li>
 * </ul>
 *
 * Gezeichnet wird über {@code PlayerModelMixin} → {@link #pose}. Was ein Emote sonst mitbringt –
 * Requisiten, einen Wurf, Geräusche, eine Verwandlung –, lösen {@link #tick} und {@link EmoteEffects}
 * aus. Alles auf dem Client-/Render-Thread.
 */
public final class Emotes {
	/** Weiches Ein- und Ausblenden. */
	private static final float BLEND_IN_S = 0.18f;
	private static final long BLEND_OUT_MS = 220L;

	/** Ein laufendes Emote; {@code stopAt} = Beginn des Ausblendens oder -1. */
	private static final class Playing {
		final Emote emote;
		final long startAt;
		long stopAt = -1;
		/** Arm eines anderen Spielers beim letzten Tick: schon einmal angesehen, holte aus, wie weit. */
		boolean watched;
		boolean swinging;
		float swingProgress;
		/** Schon geworfen, schon abgespielte Geräusche (ein Bit je Eintrag), schon verwandelt und zurück. */
		boolean thrown;
		int soundsPlayed;
		boolean poofedIn;
		boolean poofedOut;

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

	/** Ein laufendes Emote von außen gesehen: seit {@code seconds} dabei, {@code weight} 0..1 blendet ein und aus. */
	public record Active(Emote emote, float seconds, float weight) {}

	/** Läuft überhaupt irgendein Emote? */
	public static boolean anyPlaying() {
		return own != null || !others.isEmpty();
	}

	/** Das Emote, das dieser Spieler gerade zeigt (auch während es ausblendet), oder {@code null}. */
	public static @Nullable Active activeOf(Player player) {
		if (own == null && others.isEmpty()) {
			return null;
		}
		Playing playing = player == Minecraft.getInstance().player ? own : others.get(player.getUUID());
		if (playing == null) {
			return null;
		}
		long now = System.currentTimeMillis();
		float weight = playing.weight(now);
		return weight <= 0f ? null : new Active(playing.emote, (now - playing.startAt) / 1000f, weight);
	}

	/**
	 * Ist dieser Spieler gerade verwandelt (sein Emote hat eine Figur)? Dann zeichnet Minecraft weder
	 * sein Modell noch Rüstung, Cape und was er hält – an seiner Stelle steht die Figur.
	 */
	public static boolean transformed(LivingEntityRenderState state) {
		if ((own == null && others.isEmpty()) || !(state instanceof AvatarRenderState avatar)) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		Entity entity = mc.level == null ? null : mc.level.getEntity(avatar.id);
		if (!(entity instanceof Player player)) {
			return false;
		}
		Active active = activeOf(player);
		return active != null && active.emote().costume() != null;
	}

	/** Wurf, Geräusche und Rauch eines laufenden Emotes auslösen, sobald ihre Zeit gekommen ist. */
	private static void effects(Minecraft mc, Player player, Playing playing, long now) {
		Emote emote = playing.emote;
		boolean ending = playing.stopAt >= 0 || now >= playing.startAt + emote.durationMs();
		if (emote.costume() != null) {
			if (!playing.poofedIn) {
				playing.poofedIn = true;
				EmoteEffects.poof(mc, player);
			} else if (ending && !playing.poofedOut) {
				playing.poofedOut = true;
				EmoteEffects.poof(mc, player);
			}
		}
		if (ending) {
			return;
		}
		float t = (now - playing.startAt) / 1000f;
		Emote.Throw thrown = emote.thrown();
		if (thrown != null && !playing.thrown && t >= thrown.t()) {
			playing.thrown = true;
			EmoteEffects.launch(player, thrown);
		}
		for (int i = 0; i < emote.sounds().size(); i++) {
			Emote.Sound sound = emote.sounds().get(i);
			if ((playing.soundsPlayed & 1 << i) == 0 && t >= sound.t()) {
				playing.soundsPlayed |= 1 << i;
				EmoteEffects.sound(mc, player, sound.sound());
			}
		}
	}

	/** Für Gametests und den Befehl: alles beenden, Kamera zurück. */
	public static void clear() {
		own = null;
		others.clear();
		EmoteEffects.clear();
		restoreCamera(Minecraft.getInstance());
		EmoteSync.reset();
	}

	public static void tick(Minecraft mc) {
		long now = System.currentTimeMillis();
		EmoteEffects.tick(mc);
		Playing playing = own;
		if (playing != null) {
			if (mc.player == null || mc.player.isRemoved() || playing.finished(now)) {
				own = null;
			} else {
				if (playing.stopAt < 0 && mc.options.keyAttack.isDown()) {
					playing.stop(now);
				}
				effects(mc, mc.player, playing, now);
			}
		}
		if (own == null || own.stopAt >= 0 || now >= own.startAt + own.emote.durationMs()) {
			restoreCamera(mc);
		}

		for (Iterator<Map.Entry<UUID, Playing>> it = others.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Playing> entry = it.next();
			Playing other = entry.getValue();
			Player player = mc.level == null ? null : mc.level.getPlayerByUUID(entry.getKey());
			if (other.finished(now) || (player == null && now - other.startAt > 5_000L)) {
				it.remove();
				continue;
			}
			if (player != null && other.stopAt < 0 && swung(other, player)) {
				other.stop(now);
			}
			if (player != null) {
				effects(mc, player, other, now);
			}
		}
	}

	/**
	 * Hat dieser Spieler seit dem letzten Tick neu mit der Haupthand ausgeholt? Ein Schwung, der beim
	 * Start des Emotes schon lief, zählt nicht – sonst endete ein Emote direkt nach einem Schlag sofort.
	 */
	private static boolean swung(Playing playing, Player player) {
		// ab 26.3 hält Minecraft den Schwung in einem eigenen Zustand statt in offenen Feldern
		//? if >=26.3 {
		/*var swing = player.getCurrentSwing();
		boolean swinging = swing != null && swing.hand() == InteractionHand.MAIN_HAND;
		float progress = player.getSwingAnimation(1f);
		*///?} else {
		boolean swinging = player.swinging && player.swingingArm == InteractionHand.MAIN_HAND;
		float progress = player.swingTime;
		//?}
		// neu: vorher kein Schwung, oder er springt zurück (noch im alten Schwung wieder ausgeholt)
		boolean started = playing.watched && swinging && (!playing.swinging || progress < playing.swingProgress);
		playing.watched = true;
		playing.swinging = swinging;
		playing.swingProgress = progress;
		return started;
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
