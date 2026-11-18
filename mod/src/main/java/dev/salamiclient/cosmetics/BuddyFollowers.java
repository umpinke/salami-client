package dev.salamiclient.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.modules.visual.BuddyModule;
import dev.salamiclient.render.BoxRender;
import dev.salamiclient.render.WorldContext;
import dev.salamiclient.render.WorldModels;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Buddys, die ihrem Spieler hinterherlaufen (Platz „Folgt dir“). Wie sie laufen, steht in
 * {@link FollowerSim} – genauso wie ein Mini Me. Ein Buddy hat keine Beine: er hüpft beim Laufen
 * und wackelt dabei ein wenig. Gezeichnet in der Welt, damit man seinen eigenen auch in der
 * Ich-Perspektive sieht.
 */
public final class BuddyFollowers {
	/** So viel größer als über der Schulter – am Boden wäre er sonst kaum zu sehen. */
	public static final float SCALE = 1.6f;
	/** Halbe Breite und Höhe in Blöcken, für die Kollision. */
	private static final double HALF_WIDTH = 0.2;
	private static final double HEIGHT = 0.7;
	/** Plätze zum Auftauchen: {vorne, rechts} in Blöcken – auf der anderen Seite als ein Mini Me. */
	private static final double[][] SPOTS = {
		{-1.3, -0.7}, {-1.3, 0.7}, {-1.8, 0}, {0, -1.2}, {0, 1.2}, {-2.5, 0}, {1.5, 0}, {0, 0},
	};
	/** So hoch (in Blöcken) hüpft ein Buddy bei jedem Schritt. */
	private static final float HOP = 0.14f;

	private static final FollowerSim SIM = new FollowerSim(HALF_WIDTH, HEIGHT, 0.4f, SPOTS);

	private BuddyFollowers() {}

	/** Füße des hinterherlaufenden Buddys dieses Spielers (für Tests) oder {@code null}. */
	public static @Nullable Vec3 positionOf(UUID player) {
		FollowerSim.Follower f = SIM.get(player);
		return f == null ? null : new Vec3(f.x, f.y, f.z);
	}

	public static void clear() {
		SIM.clear();
	}

	/** Jeden Client-Tick: Buddys nachziehen, verschwundene Spieler vergessen. */
	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			clear();
			return;
		}
		Set<UUID> present = new HashSet<>();
		for (Player player : mc.level.players()) {
			BuddyModule.Wish wish = BuddyModule.of(player);
			if (wish == null || wish.place() != BuddyModule.Place.FOLLOW) {
				SIM.remove(player.getUUID());
				continue;
			}
			present.add(player.getUUID());
			SIM.follow(mc.level, player).what = wish.buddy().id();
		}
		SIM.retain(present);
	}

	/** Alle hinterherlaufenden Buddys zeichnen. */
	public static void render(WorldContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (SIM.isEmpty() || mc.level == null) {
			return;
		}
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 cam = WorldModels.camera();
		PoseStack poseStack = context.matrices();
		for (Map.Entry<UUID, FollowerSim.Follower> entry : SIM.all().entrySet()) {
			FollowerSim.Follower f = entry.getValue();
			Hat buddy = BuddyCatalog.get(f.what);
			if (buddy == null) {
				continue;
			}
			double x = f.prevX + (f.x - f.prevX) * partial;
			double y = f.prevY + (f.y - f.prevY) * partial;
			double z = f.prevZ + (f.z - f.prevZ) * partial;
			float yaw = f.prevYaw + (((f.yaw - f.prevYaw) % 360f + 540f) % 360f - 180f) * partial;
			float walk = f.prevWalk + (f.walk - f.prevWalk) * partial;
			// jeder Schritt ein Hüpfer, dazu ein leichtes Wackeln von Seite zu Seite
			float step = walk * 0.6662f;
			float hop = (float) Math.abs(Math.sin(step)) * f.amount * HOP;
			float wobble = (float) Math.sin(step) * f.amount * 0.14f;

			int light = buddy.glow() ? BoxRender.FULL_BRIGHT : BoxRender.lightAt(mc.level, x, y + 0.3, z);
			float scale = buddy.scale() * SCALE / 16f;
			poseStack.pushPose();
			poseStack.translate(x - cam.x, y - cam.y + hop, z - cam.z);
			// wie bei Minecrafts Wesen: drehen und umklappen – im Modell ist oben negativ und vorn bei -z
			poseStack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(180f - yaw)));
			poseStack.mulPose(new Quaternionf().rotationZ(wobble));
			poseStack.scale(-scale, -scale, scale);
			BoxRender.world(context, buddy.model(), light);
			poseStack.popPose();
		}
	}
}
