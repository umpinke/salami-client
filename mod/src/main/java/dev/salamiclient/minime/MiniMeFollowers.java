package dev.salamiclient.minime;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.salamiclient.modules.visual.MiniMeModule;
import dev.salamiclient.render.WorldContext;
import dev.salamiclient.render.WorldModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mini Mes, die ihrem Spieler hinterherlaufen (Platz „Folgt dir“): eine kleine Figur pro
 * Spieler, die wie ein Haustier stehen bleibt, solange man in der Nähe ist, und erst losläuft,
 * wenn man sich entfernt. Sie hat Schwerkraft und stößt an Blöcke (geht Stufen hoch, springt
 * auf Blöcke) und taucht neben dem Spieler auf, wenn sie zu weit weg ist oder feststeckt. Wohin
 * man schaut, ist egal. Nur im Client simuliert – jeder sieht sie bei sich, ohne Server.
 * Gezeichnet in der Welt (nicht am Spielermodell), damit man sein eigenes auch in der
 * Ich-Perspektive sieht. Hält außerdem die Modelle für die anderen Plätze bereit.
 */
public final class MiniMeFollowers {
	/** Größe gegenüber einem Spieler. */
	private static final float SCALE = 0.4f;
	/** Halbe Breite (mit Armen) und Höhe der Figur in Blöcken, für die Kollision. */
	private static final double HALF_WIDTH = 0.2;
	private static final double HEIGHT = 0.75;
	/** Weiter weg als das: loslaufen; näher als das: stehen bleiben. Dazwischen bleibt es, wie es ist. */
	private static final double START = 3.0;
	private static final double STOP = 1.8;
	/** Näher als das: einen Schritt zur Seite, damit es nicht im Spieler steht. */
	private static final double PERSONAL = 0.7;
	private static final double TELEPORT = 12;
	private static final double MIN_SPEED = 0.12;
	private static final double MAX_SPEED = 0.45;
	/** Kanten bis zu dieser Höhe (Stufen, Platten) hochgehen; ganze Blöcke werden hochgesprungen. */
	private static final double STEP = 0.6;
	private static final double JUMP = 0.42;
	private static final double GRAVITY = 0.08;
	/** So viele Ticks ohne voranzukommen, dann taucht es neben dem Spieler auf. */
	private static final int STUCK_TICKS = 60;
	/** Plätze um den Spieler zum Auftauchen: {vorne, rechts} in Blöcken, der erste freie gewinnt. */
	private static final double[][] SPOTS = {
		{-1.3, 0.7}, {-1.3, -0.7}, {-1.5, 0}, {0, 1.2}, {0, -1.2}, {-2.5, 0}, {1.5, 0}, {0, 0},
	};

	private static final class Follower {
		double x;
		double y;
		double z;
		double prevX;
		double prevY;
		double prevZ;
		double fall;
		boolean onGround;
		boolean following;
		int stuck;
		float yaw;
		float prevYaw;
		float walk;
		float prevWalk;
		float amount;
		String name = "";

		void place(Vec3 at) {
			x = prevX = at.x;
			y = prevY = at.y;
			z = prevZ = at.z;
			fall = 0;
			onGround = false;
			following = false;
			stuck = 0;
			amount = 0;
		}
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

	/** Füße des hinterherlaufenden Mini Mes dieses Spielers (für Tests) oder {@code null}. */
	public static @Nullable Vec3 positionOf(UUID player) {
		Follower f = FOLLOWERS.get(player);
		return f == null ? null : new Vec3(f.x, f.y, f.z);
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
			if (follower == null) {
				follower = new Follower();
				Vec3 spot = spotNear(mc.level, player);
				follower.place(spot != null ? spot : player.position());
				follower.yaw = follower.prevYaw = player.yBodyRot;
				FOLLOWERS.put(uuid, follower);
			}
			follower.name = wish.name();
			step(follower, mc.level, player);
		}
		FOLLOWERS.keySet().retainAll(present);
		MODELS.keySet().retainAll(present);
		MODEL_SLIM.keySet().retainAll(present);
	}

	private static void step(Follower f, Level level, Player player) {
		f.prevX = f.x;
		f.prevY = f.y;
		f.prevZ = f.z;
		f.prevYaw = f.yaw;
		f.prevWalk = f.walk;
		double dx = player.getX() - f.x;
		double dz = player.getZ() - f.z;
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist > TELEPORT || Math.abs(player.getY() - f.y) > TELEPORT || f.stuck > STUCK_TICKS) {
			// zu weit weg (Teleport, Elytra …) oder steckt fest: neben dem Spieler auftauchen,
			// sobald dort Boden ist (fliegt er, läuft es unten weiter hinterher)
			Vec3 spot = spotNear(level, player);
			f.stuck = 0;
			if (spot != null) {
				f.place(spot);
				return;
			}
		}
		AABB here = box(f.x, f.y, f.z).deflate(1.0e-3);
		if (!level.noBlockCollision(null, here)) {
			// steckt in einem Block (dort wurde etwas hingesetzt): obendrauf stellen, sonst zum Spieler
			double top = f.y;
			for (VoxelShape shape : collisions(level, here)) {
				top = Math.max(top, shape.max(Direction.Axis.Y));
			}
			if (top - f.y <= 1.0 && level.noBlockCollision(null, box(f.x, top, f.z).deflate(1.0e-3))) {
				f.y = top;
				f.fall = 0;
				return;
			}
			Vec3 spot = spotNear(level, player);
			if (spot != null) {
				f.place(spot);
				return;
			}
		}

		if (dist > START) {
			f.following = true;
		} else if (dist < STOP) {
			f.following = false;
		}
		double mx = 0;
		double mz = 0;
		if (f.following) {
			// je weiter weg, desto schneller, damit es auch beim Rennen dranbleibt
			double speed = Math.min(MAX_SPEED, MIN_SPEED + (dist - STOP) * 0.08);
			mx = dx / dist * speed;
			mz = dz / dist * speed;
		} else if (dist < PERSONAL && dist > 1.0e-4) {
			mx = -dx / dist * 0.06;
			mz = -dz / dist * 0.06;
		}

		boolean swimming = !level.getFluidState(BlockPos.containing(f.x, f.y + HEIGHT * 0.5, f.z)).isEmpty();
		if (swimming) {
			mx *= 0.6;
			mz *= 0.6;
		}
		move(f, level, mx, mz, f.onGround || swimming);
		// wie bei Minecrafts Wesen erst bewegen, dann Schwerkraft (im Wasser: treibt an die Oberfläche)
		f.fall = swimming ? f.fall * 0.6 + 0.025 : (f.fall - GRAVITY) * 0.98;

		double movedX = f.x - f.prevX;
		double movedZ = f.z - f.prevZ;
		double moved = Math.sqrt(movedX * movedX + movedZ * movedZ);
		double wantedMove = Math.sqrt(mx * mx + mz * mz);
		if (f.following && (f.onGround || swimming) && moved < wantedMove * 0.5
			&& canJumpOver(level, f, mx / wantedMove, mz / wantedMove)) {
			f.fall = JUMP; // ein ganzer Block im Weg (oder das Ufer): hochspringen
		}
		f.stuck = f.following && moved < 0.03 ? f.stuck + 1 : 0;

		if (f.following && moved > 0.02) {
			f.yaw = approachAngle(f.yaw, (float) Math.toDegrees(Math.atan2(-movedX, movedZ)), 25f);
		} else {
			// steht: langsam zum Spieler drehen
			f.yaw = approachAngle(f.yaw, (float) Math.toDegrees(Math.atan2(-dx, dz)), 8f);
		}
		// kurze Beine: schnellere Schritte als ein großer Spieler auf derselben Strecke
		f.walk += (float) Math.min(2.0, moved * 4 / SCALE);
		float wanted = (float) Math.min(1, moved * 6);
		f.amount += (wanted - f.amount) * 0.4f;
	}

	/** Kollisionsbox der Figur, Füße bei {@code y}. */
	private static AABB box(double x, double y, double z) {
		return new AABB(x - HALF_WIDTH, y, z - HALF_WIDTH, x + HALF_WIDTH, y + HEIGHT, z + HALF_WIDTH);
	}

	private static List<VoxelShape> collisions(Level level, AABB area) {
		List<VoxelShape> shapes = new ArrayList<>();
		for (VoxelShape shape : level.getBlockCollisions(null, area)) {
			shapes.add(shape);
		}
		return shapes;
	}

	/**
	 * Bewegen wie Minecrafts Wesen: erst senkrecht, dann waagerecht, an Blöcken anhalten.
	 * {@code canStep}: steht (oder schwimmt) und darf Stufen hochgehen.
	 */
	private static void move(Follower f, Level level, double mx, double mz, boolean canStep) {
		AABB start = box(f.x, f.y, f.z);
		double my = f.fall;
		List<VoxelShape> shapes = collisions(level, start.expandTowards(mx, my, mz).expandTowards(0, STEP, 0));
		double ry = Shapes.collide(Direction.Axis.Y, start, shapes, my);
		AABB box = start.move(0, ry, 0);
		double rx = Shapes.collide(Direction.Axis.X, box, shapes, mx);
		box = box.move(rx, 0, 0);
		double rz = Shapes.collide(Direction.Axis.Z, box, shapes, mz);
		box = box.move(0, 0, rz);
		boolean landed = my < 0 && ry > my;
		boolean blocked = Math.abs(rx - mx) > 1.0e-6 || Math.abs(rz - mz) > 1.0e-6;
		if (blocked && (canStep || landed)) {
			// Stufe oder Platte: hoch, waagerecht, wieder runter – wenn man so weiter kommt
			double up = Shapes.collide(Direction.Axis.Y, start, shapes, STEP);
			AABB stepped = start.move(0, up, 0);
			double sx = Shapes.collide(Direction.Axis.X, stepped, shapes, mx);
			stepped = stepped.move(sx, 0, 0);
			double sz = Shapes.collide(Direction.Axis.Z, stepped, shapes, mz);
			stepped = stepped.move(0, 0, sz);
			stepped = stepped.move(0, Shapes.collide(Direction.Axis.Y, stepped, shapes, -up), 0);
			if (sx * sx + sz * sz > rx * rx + rz * rz + 1.0e-7) {
				box = stepped;
				landed = true;
			}
		}
		f.x = (box.minX + box.maxX) * 0.5;
		f.y = box.minY;
		f.z = (box.minZ + box.maxZ) * 0.5;
		f.onGround = landed;
		if (landed || ry != my) {
			f.fall = 0; // Boden oder Decke
		}
	}

	/** Lohnt ein Sprung? Ein Block hoch und ein Stück in Laufrichtung muss frei sein. */
	private static boolean canJumpOver(Level level, Follower f, double dirX, double dirZ) {
		AABB high = box(f.x, f.y + 1.05, f.z);
		return level.noBlockCollision(null, high) && level.noBlockCollision(null, high.move(dirX * 0.4, 0, dirZ * 0.4));
	}

	/** Freier Platz mit Boden nahe beim Spieler (bevorzugt schräg hinter ihm) oder {@code null}. */
	private static @Nullable Vec3 spotNear(Level level, Player player) {
		double yaw = Math.toRadians(player.yBodyRot);
		// vorne = (-sin, cos), rechts = (-cos, -sin)
		double forwardX = -Math.sin(yaw);
		double forwardZ = Math.cos(yaw);
		for (double[] spot : SPOTS) {
			double x = player.getX() + forwardX * spot[0] - forwardZ * spot[1];
			double z = player.getZ() + forwardZ * spot[0] + forwardX * spot[1];
			Double ground = groundBelow(level, x, player.getY(), z);
			if (ground != null) {
				return new Vec3(x, ground, z);
			}
		}
		return null;
	}

	/** Boden unter (x, z), gesucht von etwas über {@code y} bis 4 Blöcke darunter; {@code null}, wenn keiner da ist. */
	private static @Nullable Double groundBelow(Level level, double x, double y, double z) {
		for (double above : new double[] {1.0, 0.5, 0.0}) {
			AABB box = box(x, y + above, z);
			if (!level.noBlockCollision(null, box)) {
				continue;
			}
			double reach = above + 4.0;
			double down = Shapes.collide(Direction.Axis.Y, box, collisions(level, box.expandTowards(0, -reach, 0)), -reach);
			return down > -reach + 1.0e-6 ? y + above + down : null;
		}
		return null;
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
