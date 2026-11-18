package dev.salamiclient.emotes;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.modules.social.EmotesModule;
import dev.salamiclient.render.BoxRender;
import dev.salamiclient.render.WorldContext;
import dev.salamiclient.render.WorldModels;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Was Emotes außer der Bewegung zeigen und nicht am Spielermodell hängt: geworfene Dinge (ein
 * Schneeball fliegt los und zerplatzt am nächsten Block), Geräusche und Verwandlungen (der Spieler
 * wird zu einer Figur aus Würfeln). Alles nur Optik, nur im Client und bei jedem für sich: ein Wurf
 * trifft niemanden, der Server erfährt nichts davon – er gibt nur weiter, welches Emote läuft.
 *
 * <p>Alles auf dem Client-/Render-Thread.
 */
public final class EmoteEffects {
	/** Länger fliegt nichts (Ticks). */
	private static final int MAX_AGE = 100;
	private static final int MAX_FLYING = 64;
	/** Luftwiderstand je Tick, wie bei Minecrafts Schneeball. */
	private static final double DRAG = 0.99;

	private static final class Flying {
		final Emote.Throw spec;
		double x;
		double y;
		double z;
		double prevX;
		double prevY;
		double prevZ;
		double vx;
		double vy;
		double vz;
		float spin;
		float prevSpin;
		final float yaw;
		int age;

		Flying(Emote.Throw spec, Vec3 at, Vec3 velocity) {
			this.spec = spec;
			x = prevX = at.x;
			y = prevY = at.y;
			z = prevZ = at.z;
			vx = velocity.x;
			vy = velocity.y;
			vz = velocity.z;
			yaw = (float) Math.atan2(-velocity.x, velocity.z);
		}
	}

	private static final List<Flying> FLYING = new ArrayList<>();
	/** Wie oft schon etwas geworfen wurde und wie viel gerade fliegt (für Gametests). */
	private static long launched;

	private EmoteEffects() {}

	public static long launchedCount() {
		return launched;
	}

	public static int flyingCount() {
		return FLYING.size();
	}

	static void clear() {
		FLYING.clear();
	}

	/** Der Spieler wirft: das Modell startet neben seiner rechten Hand in Blickrichtung. */
	static void launch(Player player, Emote.Throw spec) {
		if (FLYING.size() >= MAX_FLYING) {
			return;
		}
		Vec3 look = player.getViewVector(1f);
		double yaw = Math.toRadians(player.yBodyRot);
		// rechts vom Spieler = (-cos, -sin)
		Vec3 start = player.getEyePosition(1f)
			.add(-Math.cos(yaw) * 0.35, -0.15, -Math.sin(yaw) * 0.35)
			.add(look.scale(0.4));
		FLYING.add(new Flying(spec, start, look.scale(spec.speed()).add(0, spec.up(), 0)));
		launched++;
	}

	/** Geräusch eines Emotes am Ort des Spielers (abschaltbar im Modul „Emotes“). */
	static void sound(Minecraft mc, Player player, String sound) {
		EmotesModule module = EmotesModule.instance();
		if (mc.level == null || module == null || !module.playsSounds()) {
			return;
		}
		float vary = ThreadLocalRandom.current().nextFloat();
		SoundEvent event;
		float volume;
		float pitch;
		switch (sound) {
			case "snowball" -> {
				event = SoundEvents.SNOWBALL_THROW;
				volume = 0.5f;
				pitch = 0.4f / (vary * 0.4f + 0.8f);
			}
			case "snow" -> {
				event = SoundEvents.SNOW_BREAK;
				volume = 0.6f;
				pitch = 1.1f + vary * 0.2f;
			}
			case "bell" -> {
				event = SoundEvents.NOTE_BLOCK_BELL.value();
				volume = 0.7f;
				pitch = 1.5f + vary * 0.1f;
			}
			case "pop" -> {
				event = SoundEvents.ITEM_PICKUP;
				volume = 0.4f;
				pitch = 1.2f + vary * 0.4f;
			}
			default -> {
				return;
			}
		}
		mc.level.playLocalSound(player.getX(), player.getY() + 1.0, player.getZ(), event, SoundSource.PLAYERS, volume, pitch, false);
	}

	/** Rauchwölkchen um den Spieler: er verwandelt sich (oder zurück). */
	static void poof(Minecraft mc, Player player) {
		if (mc.level == null) {
			return;
		}
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int i = 0; i < 16; i++) {
			mc.level.addParticle(ParticleTypes.POOF,
				player.getX() + random.nextDouble(-0.5, 0.5), player.getY() + random.nextDouble(0.1, 1.8), player.getZ() + random.nextDouble(-0.5, 0.5),
				random.nextDouble(-0.03, 0.03), random.nextDouble(0.0, 0.06), random.nextDouble(-0.03, 0.03));
		}
	}

	/** Jeden Client-Tick: alles Geworfene weiterfliegen lassen; am ersten Block zerplatzt es. */
	static void tick(Minecraft mc) {
		if (FLYING.isEmpty()) {
			return;
		}
		if (mc.level == null) {
			FLYING.clear();
			return;
		}
		for (Iterator<Flying> it = FLYING.iterator(); it.hasNext(); ) {
			Flying f = it.next();
			f.prevX = f.x;
			f.prevY = f.y;
			f.prevZ = f.z;
			f.prevSpin = f.spin;
			Vec3 from = new Vec3(f.x, f.y, f.z);
			Vec3 to = from.add(f.vx, f.vy, f.vz);
			// nur Blöcke: Spieler und Tiere fliegt es einfach durch
			BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
			if (hit.getType() != HitResult.Type.MISS) {
				impact(mc, f.spec.impact(), hit.getLocation());
				it.remove();
				continue;
			}
			if (++f.age > MAX_AGE) {
				it.remove();
				continue;
			}
			f.x = to.x;
			f.y = to.y;
			f.z = to.z;
			f.vx *= DRAG;
			f.vy = f.vy * DRAG - f.spec.gravity();
			f.vz *= DRAG;
			f.spin += f.spec.spin();
		}
	}

	private static void impact(Minecraft mc, String kind, Vec3 at) {
		SimpleParticleType particle = switch (kind) {
			case "snow" -> ParticleTypes.ITEM_SNOWBALL;
			case "poof" -> ParticleTypes.POOF;
			default -> null;
		};
		if (particle == null || mc.level == null) {
			return;
		}
		for (int i = 0; i < 8; i++) {
			mc.level.addParticle(particle, at.x, at.y, at.z, 0.0, 0.0, 0.0);
		}
		EmotesModule module = EmotesModule.instance();
		if (kind.equals("snow") && module != null && module.playsSounds()) {
			mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.SNOW_BREAK, SoundSource.PLAYERS, 0.5f, 1.3f, false);
		}
	}

	// ------------------------------------------------------------------ Zeichnen

	/** In der Welt: alles, was fliegt, und alle verwandelten Spieler. */
	public static void render(WorldContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 cam = WorldModels.camera();
		PoseStack poseStack = context.matrices();
		for (Flying f : FLYING) {
			double x = Mth.lerp(partial, f.prevX, f.x);
			double y = Mth.lerp(partial, f.prevY, f.y);
			double z = Mth.lerp(partial, f.prevZ, f.z);
			float scale = f.spec.scale() / 16f;
			poseStack.pushPose();
			poseStack.translate(x - cam.x, y - cam.y, z - cam.z);
			poseStack.mulPose(new Quaternionf().rotationY(Mth.PI - f.yaw));
			poseStack.mulPose(new Quaternionf().rotationX(Mth.lerp(partial, f.prevSpin, f.spin)));
			poseStack.scale(-scale, -scale, scale);
			BoxRender.world(context, f.spec.model(), BoxRender.lightAt(mc.level, x, y, z));
			poseStack.popPose();
		}
		if (!Emotes.anyPlaying()) {
			return;
		}
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();
		for (Player player : mc.level.players()) {
			Emotes.Active active = Emotes.activeOf(player);
			Emote.Costume costume = active == null ? null : active.emote().costume();
			if (costume == null || player.isInvisible() || player.isSpectator() || (player == mc.player && firstPerson)) {
				continue;
			}
			costume(context, mc, player, costume, active, partial, cam);
		}
	}

	/** Die Figur anstelle des Spielers: steht, wo er steht, schaut, wohin er schaut. */
	private static void costume(WorldContext context, Minecraft mc, Player player, Emote.Costume costume, Emotes.Active active,
			float partial, Vec3 cam) {
		Vec3 at = player.getPosition(partial);
		float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		float headYaw = Mth.wrapDegrees(Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot) - bodyYaw);
		float headPitch = player.getXRot(partial);
		float t = active.seconds();
		// wächst beim Verwandeln aus dem Rauch und schrumpft am Ende wieder hinein
		float scale = costume.scale() * player.getScale() * (0.35f + 0.65f * active.weight()) / 16f;
		float hop = costume.hop() <= 0f ? 0f : (float) Math.abs(Math.sin(Math.PI * t / costume.hopCycle())) * costume.hop() * active.weight();
		int light = BoxRender.lightAt(mc.level, at.x, at.y + 1.0, at.z);

		PoseStack poseStack = context.matrices();
		poseStack.pushPose();
		poseStack.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		// wie bei Minecrafts Wesen: drehen, umklappen, auf die Füße stellen (der Boden liegt im Modell bei y 24)
		poseStack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(180f - bodyYaw)));
		poseStack.scale(-scale, -scale, scale);
		poseStack.translate(0f, -24.016f - hop, 0f);
		if (costume.body() != null) {
			BoxRender.world(context, costume.body(), light);
		}
		for (Emote.Piece piece : costume.pieces()) {
			float x = 0f;
			float y = 0f;
			float z = 0f;
			Emote.Track track = costume.track(piece.follow());
			if (track != null) {
				track.sample(t, ROTATION);
				x = ROTATION[0] * active.weight();
				y = ROTATION[1] * active.weight();
				z = ROTATION[2] * active.weight();
			}
			if (piece.follow() == Emote.Part.HEAD) {
				x += (float) Math.toRadians(headPitch);
				y += (float) Math.toRadians(headYaw);
			}
			poseStack.pushPose();
			poseStack.translate(piece.at()[0], piece.at()[1], piece.at()[2]);
			// wie ein Modellteil in Minecraft: um z, y, x drehen – erst die Bewegung, dann die eigene Grundhaltung
			poseStack.mulPose(new Quaternionf().rotationZYX(z, y, x));
			poseStack.mulPose(new Quaternionf().rotationZYX(piece.rot()[2], piece.rot()[1], piece.rot()[0]));
			BoxRender.world(context, piece.model(), light);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	private static final float[] ROTATION = new float[3];
}
