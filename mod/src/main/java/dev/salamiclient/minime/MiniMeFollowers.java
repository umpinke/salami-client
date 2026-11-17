package dev.salamiclient.minime;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.modules.visual.MiniMeModule;
import dev.salamiclient.render.WorldContext;
import dev.salamiclient.render.WorldModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mini Mes, die ihrem Spieler hinterherlaufen (Platz „Folgt dir“): eine kleine Figur pro
 * Spieler, die schräg hinter ihm bleibt, losläuft, wenn er sich entfernt, und bei großem
 * Abstand nachspringt. Nur im Client simuliert – jeder sieht sie bei sich, ohne Server.
 * Gezeichnet in der Welt (nicht am Spielermodell), damit man sein eigenes auch in der
 * Ich-Perspektive sieht. Hält außerdem die Modelle für die anderen Plätze bereit.
 */
public final class MiniMeFollowers {
	/** Größe gegenüber einem Spieler. */
	private static final float SCALE = 0.4f;
	/** Abstand hinter dem Spieler (Blöcke) und zur Seite. */
	private static final double BEHIND = 1.3;
	private static final double SIDE = 0.7;
	private static final double TELEPORT = 12;
	private static final double MAX_STEP = 0.35;

	private static final class Follower {
		double x;
		double y;
		double z;
		double prevX;
		double prevY;
		double prevZ;
		float yaw;
		float prevYaw;
		float walk;
		float prevWalk;
		float amount;
		String name = "";
	}

	private static final Map<UUID, Follower> FOLLOWERS = new HashMap<>();
	/** Ein Modell pro Spieler und Armform (siehe {@link MiniMeModel}). */
	private static final Map<UUID, MiniMeModel> MODELS = new HashMap<>();
	private static final Map<UUID, Boolean> MODEL_SLIM = new HashMap<>();

	private MiniMeFollowers() {}

	/** Modell für das Mini Me dieses Spielers (wird bei geänderter Armform neu gebaut). */
	public static MiniMeModel model(UUID player, boolean slim) {
		MiniMeModel model = MODELS.get(player);
		if (model == null || MODEL_SLIM.get(player) != slim) {
			model = new MiniMeModel(slim);
			MODELS.put(player, model);
			MODEL_SLIM.put(player, slim);
		}
		return model;
	}

	public static void clear() {
		FOLLOWERS.clear();
		MODELS.clear();
		MODEL_SLIM.clear();
	}

	/** Jeden Client-Tick: Mini Mes nachziehen, verschwundene Spieler vergessen. */
	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			clear();
			return;
		}
		Set<UUID> present = new HashSet<>();
		for (Player player : mc.level.players()) {
			UUID uuid = player.getUUID();
			MiniMeModule.Wish wish = MiniMeModule.of(player);
			if (wish != null) {
				present.add(uuid);
			}
			if (wish == null || wish.place() != MiniMeModule.Place.FOLLOW) {
				FOLLOWERS.remove(uuid);
				continue;
			}
			Follower follower = FOLLOWERS.get(uuid);
			double yaw = Math.toRadians(player.yBodyRot);
			// schräg hinter dem Spieler: vorne = (-sin, cos), rechts = (-cos, -sin)
			double targetX = player.getX() + Math.sin(yaw) * BEHIND - Math.cos(yaw) * SIDE;
			double targetZ = player.getZ() - Math.cos(yaw) * BEHIND - Math.sin(yaw) * SIDE;
			if (follower == null) {
				follower = new Follower();
				follower.x = follower.prevX = targetX;
				follower.y = follower.prevY = player.getY();
				follower.z = follower.prevZ = targetZ;
				follower.yaw = follower.prevYaw = player.yBodyRot;
				FOLLOWERS.put(uuid, follower);
			}
			follower.name = wish.name();
			step(follower, player, targetX, targetZ);
		}
		FOLLOWERS.keySet().retainAll(present);
		MODELS.keySet().retainAll(present);
		MODEL_SLIM.keySet().retainAll(present);
	}

	private static void step(Follower f, Player player, double targetX, double targetZ) {
		f.prevX = f.x;
		f.prevY = f.y;
		f.prevZ = f.z;
		f.prevYaw = f.yaw;
		f.prevWalk = f.walk;
		double dx = targetX - f.x;
		double dz = targetZ - f.z;
		double dist = Math.sqrt(dx * dx + dz * dz);
		double moved = 0;
		if (dist > TELEPORT || Math.abs(player.getY() - f.y) > TELEPORT) {
			// zu weit weg (Teleport, Elytra …): einfach wieder hinter dem Spieler auftauchen
			f.x = f.prevX = targetX;
			f.z = f.prevZ = targetZ;
			f.y = f.prevY = player.getY();
		} else if (dist > 0.25) {
			moved = Math.min(MAX_STEP, dist * 0.2);
			f.x += dx / dist * moved;
			f.z += dz / dist * moved;
			float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			f.yaw = approachAngle(f.yaw, targetYaw, 25f);
		} else {
			// steht: langsam zum Spieler drehen
			float toPlayer = (float) Math.toDegrees(Math.atan2(-(player.getX() - f.x), player.getZ() - f.z));
			f.yaw = approachAngle(f.yaw, toPlayer, 6f);
		}
		f.y += (player.getY() - f.y) * 0.35;
		f.walk += (float) moved * 4f;
		float wanted = (float) Math.min(1, moved * 5);
		f.amount += (wanted - f.amount) * 0.4f;
	}

	private static float approachAngle(float current, float target, float maxStep) {
		float diff = ((target - current) % 360f + 540f) % 360f - 180f;
		return current + Math.max(-maxStep, Math.min(maxStep, diff));
	}

	/** Alle hinterherlaufenden Mini Mes zeichnen. */
	public static void render(WorldContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (FOLLOWERS.isEmpty() || mc.level == null) {
			return;
		}
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 cam = WorldModels.camera();
		PoseStack poseStack = context.matrices();
		for (Map.Entry<UUID, Follower> entry : FOLLOWERS.entrySet()) {
			Follower f = entry.getValue();
			MiniMeSkins.Skin skin = MiniMeSkins.get(f.name);
			Identifier texture = skin != null ? skin.texture() : DefaultPlayerSkin.getDefaultTexture();
			MiniMeModel model = model(entry.getKey(), skin != null && skin.slim());
			double x = f.prevX + (f.x - f.prevX) * partial;
			double y = f.prevY + (f.y - f.prevY) * partial;
			double z = f.prevZ + (f.z - f.prevZ) * partial;
			float yaw = f.prevYaw + (((f.yaw - f.prevYaw) % 360f + 540f) % 360f - 180f) * partial;
			float walk = f.prevWalk + (f.walk - f.prevWalk) * partial;
			model.pose(MiniMeModel.Pose.WALK, walk, f.amount, mc.level.getGameTime() + partial);

			BlockPos at = BlockPos.containing(x, y + 0.5, z);
			int light = mc.level.getBrightness(LightLayer.BLOCK, at) << 4 | mc.level.getBrightness(LightLayer.SKY, at) << 20;
			poseStack.pushPose();
			poseStack.translate(x - cam.x, y - cam.y, z - cam.z);
			// wie bei Minecrafts Wesen: drehen, umklappen, auf die Füße stellen
			poseStack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(180f - yaw)));
			poseStack.scale(-SCALE, -SCALE, SCALE);
			poseStack.translate(0f, -1.501f, 0f);
			WorldModels.model(context, model.root, WorldModels.entityType(texture), light);
			poseStack.popPose();
		}
	}
}
