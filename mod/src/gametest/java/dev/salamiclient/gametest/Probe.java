package dev.salamiclient.gametest;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.render.WorldModels;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Messfühler für die Cosmetics-Bewegungstests: Gametest-Mixins melden hier, wo Hüte, Buddys, Mini Mes
 * und Requisiten in jedem einzelnen Frame gezeichnet werden (Matrix relativ zur Kamera). Daneben dreht
 * er den Spieler pro Frame wie eine Maus, damit „schnell umschauen“ so läuft wie im Spiel.
 * Alles nur im Render-Thread.
 */
public final class Probe {
	/** Ein gezeichnetes Cosmetic in einem Frame. */
	public record Sample(int frame, long nanos, String label, String caller, String part, float partial,
		Vec3 camera, Vec3 player, float bodyYaw, float headYaw, float viewYaw,
		Vector3f at, Vector3f down, Vector3f base) {}

	private static volatile boolean recording;
	private static String label = "";
	private static int frame;
	private static long lastFrameNanos;
	private static final List<Sample> SAMPLES = new ArrayList<>();
	/** Drehung pro Sekunde wie mit der Maus (Grad), dazu Nicken hin und her. */
	private static float turnYaw;
	private static float turnPitch;
	private static float pitchPhase;
	private static Matrix4f anchorBase = new Matrix4f();

	private Probe() {}

	public static void start(String newLabel) {
		label = newLabel;
		recording = true;
	}

	public static void stop() {
		recording = false;
	}

	public static boolean recording() {
		return recording;
	}

	public static List<Sample> take() {
		List<Sample> copy = new ArrayList<>(SAMPLES);
		SAMPLES.clear();
		return copy;
	}

	/** Spieler pro Frame drehen ({@code 0, 0} = aus). */
	public static void turn(float yawPerSecond, float pitchAmplitude) {
		turnYaw = yawPerSecond;
		turnPitch = pitchAmplitude;
		pitchPhase = 0;
	}

	/** Einmal pro Frame (Weltrendering von Salami). */
	public static void frame(Minecraft mc) {
		long now = System.nanoTime();
		float dt = lastFrameNanos == 0 ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1e9f);
		lastFrameNanos = now;
		frame++;
		if ((turnYaw != 0f || turnPitch != 0f) && mc.player != null && dt > 0f) {
			float yaw = turnYaw * dt;
			float pitch = 0f;
			if (turnPitch != 0f) {
				float before = (float) Math.sin(pitchPhase) * turnPitch;
				pitchPhase += dt * 4f;
				pitch = (float) Math.sin(pitchPhase) * turnPitch - before;
			}
			// wie MouseHandler: Player.turn rechnet mit 0,15 Grad pro Einheit
			mc.player.turn(yaw / 0.15f, pitch / 0.15f);
		}
	}

	/** Vor {@code ModelAnchor.to}: Matrix des Spielermodells. */
	public static void anchorStart(PoseStack poseStack) {
		inAnchor = true;
		if (recording) {
			anchorBase = new Matrix4f(poseStack.last().pose());
		}
	}

	/** Nach {@code ModelAnchor.to}: Matrix am Körperteil. */
	public static void anchorEnd(PoseStack poseStack, Object part, String name) {
		inAnchor = false;
		if (recording) {
			TRACKED.put(part, name);
			record(caller(), name, poseStack.last().pose(), anchorBase);
		}
	}

	/**
	 * Nach jedem {@code ModelPart.translateAndRotate} außerhalb von {@code ModelAnchor}: so zeichnet das
	 * Spiel das Körperteil wirklich. Gemessen nur für Teile, an denen gerade ein Cosmetic hängt.
	 */
	public static void partDrawn(Object part, PoseStack poseStack) {
		if (recording && !inAnchor) {
			String name = TRACKED.get(part);
			if (name != null) {
				record("drawn", name, poseStack.last().pose(), null);
			}
		}
	}

	/** Ein Modellteil beim Zeichnen mitmessen (z. B. das Cape von Minecraft). */
	public static void track(Object part, String name) {
		if (recording) {
			TRACKED.put(part, name);
		}
	}

	private static boolean inAnchor;
	private static final java.util.Map<Object, String> TRACKED = new java.util.IdentityHashMap<>();

	/** Am Spielermodell gezeichnetes Würfel-Modell (Hut, Buddy, Requisite): wo es wirklich landet. */
	public static void box(PoseStack poseStack) {
		if (recording) {
			record(caller(), "box", poseStack.last().pose(), null);
		}
	}

	/** In der Welt gezeichnet (hinterherlaufende Figuren, Würfe, Verwandlungen). */
	public static void world(PoseStack poseStack) {
		if (recording) {
			record(caller(), "world", poseStack.last().pose(), null);
		}
	}

	private static void record(String caller, String part, Matrix4f pose, Matrix4f base) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null) {
			return;
		}
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = WorldModels.camera();
		Vec3 at = player.getPosition(partial);
		float body = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		float head = Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot);
		Vector3f translation = pose.getTranslation(new Vector3f());
		// eine Stelle 0,5 Blöcke „unter“ dem Anker (im Modell ist unten +y): zeigt Drehungen
		Vector3f down = pose.transformPosition(new Vector3f(0f, 0.5f, 0f));
		Vector3f baseAt = base == null ? new Vector3f() : base.getTranslation(new Vector3f());
		SAMPLES.add(new Sample(frame, System.nanoTime(), label, caller, part, partial, camera, at, body, head,
			player.getYRot(), translation, down, baseAt));
		if (SAMPLES.size() > 200_000) {
			SAMPLES.subList(0, 100_000).clear();
		}
	}

	/** Welche Salami-Klasse zeichnet gerade (HatLayer, BuddyFollowers …)? */
	private static String caller() {
		return StackWalker.getInstance().walk(frames -> frames
			.map(f -> f.getClassName())
			.filter(name -> name.startsWith("dev.salamiclient.") && !name.startsWith("dev.salamiclient.gametest")
				&& !name.endsWith("ModelAnchor") && !name.endsWith("BoxRender") && !name.endsWith("WorldModels"))
			.findFirst()
			.map(name -> name.substring(name.lastIndexOf('.') + 1))
			.orElse("?"));
	}

	/** Alle Proben als CSV (für die Auswertung außerhalb). */
	public static String csv(List<Sample> samples) {
		StringBuilder out = new StringBuilder("label,frame,ms,caller,part,partial,camX,camY,camZ,plX,plY,plZ,body,head,view,x,y,z,dx,dy,dz,bx,by,bz\n");
		long t0 = samples.isEmpty() ? 0 : samples.get(0).nanos();
		for (Sample s : samples) {
			out.append(String.format(Locale.ROOT, "%s,%d,%.3f,%s,%s,%.4f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.3f,%.3f,%.3f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f%n",
				s.label(), s.frame(), (s.nanos() - t0) / 1e6, s.caller(), s.part(), s.partial(),
				s.camera().x, s.camera().y, s.camera().z, s.player().x, s.player().y, s.player().z,
				s.bodyYaw(), s.headYaw(), s.viewYaw(), s.at().x, s.at().y, s.at().z,
				s.down().x, s.down().y, s.down().z, s.base().x, s.base().y, s.base().z));
		}
		return out.toString();
	}
}
