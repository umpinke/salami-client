package dev.salamiclient.cosmetics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Kleine Figuren, die ihrem Spieler hinterherlaufen (Mini Me und Buddys am Platz „Folgt dir“): eine
 * pro Spieler, die wie ein Haustier stehen bleibt, solange man in der Nähe ist, und erst losläuft,
 * wenn man sich entfernt. Sie hat Schwerkraft und stößt an Blöcke (geht Stufen hoch, springt auf
 * Blöcke) und taucht neben dem Spieler auf, wenn sie zu weit weg ist oder feststeckt. Wohin man
 * schaut, ist egal. Nur im Client simuliert – jeder sieht sie bei sich, ohne Server.
 *
 * <p>Hier steckt nur die Bewegung; wer welche Figur hat und wie sie aussieht, wissen die Aufrufer
 * ({@link dev.salamiclient.minime.MiniMeFollowers}, {@link BuddyFollowers}).
 */
public final class FollowerSim {
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

	/** Eine Figur: Füße bei (x, y, z), dazu der Stand des vorigen Ticks zum Überblenden. */
	public static final class Follower {
		public double x;
		public double y;
		public double z;
		public double prevX;
		public double prevY;
		public double prevZ;
		public float yaw;
		public float prevYaw;
		/** Laufphase, wächst mit der zurückgelegten Strecke. */
		public float walk;
		public float prevWalk;
		/** Zurückgelegte Strecke in Blöcken (waagerecht), z. B. für den Takt der Hüpfer eines Buddys. */
		public double distance;
		public double prevDistance;
		/** Wie stark gerade gelaufen wird, 0 = steht. Ändert sich pro Tick – zum Zeichnen {@link #amount(float)}. */
		public float amount;
		public float prevAmount;

		/** Laufstärke zwischen zwei Ticks: ohne Überblenden springt sie 20-mal pro Sekunde. */
		public float amount(float partial) {
			return prevAmount + (amount - prevAmount) * partial;
		}

		/** Laufphase zwischen zwei Ticks. */
		public float walk(float partial) {
			return prevWalk + (walk - prevWalk) * partial;
		}

		/** Strecke zwischen zwei Ticks. */
		public double distance(float partial) {
			return prevDistance + (distance - prevDistance) * partial;
		}
		/** Was die Figur ist – der Skin-Name eines Mini Me oder die id eines Buddys. */
		public String what = "";
		double fall;
		boolean onGround;
		boolean following;
		int stuck;

		void place(Vec3 at) {
			x = prevX = at.x;
			y = prevY = at.y;
			z = prevZ = at.z;
			fall = 0;
			onGround = false;
			following = false;
			stuck = 0;
			amount = prevAmount = 0;
		}
	}

	private final double halfWidth;
	private final double height;
	private final float scale;
	private final double[][] spots;
	private final Map<UUID, Follower> followers = new HashMap<>();

	/**
	 * @param halfWidth halbe Breite der Figur in Blöcken, für die Kollision
	 * @param height    Höhe der Figur in Blöcken
	 * @param scale     Größe gegenüber einem Spieler – kleine Figuren machen schnellere Schritte
	 * @param spots     Plätze um den Spieler zum Auftauchen: {vorne, rechts} in Blöcken, der erste freie gewinnt
	 */
	public FollowerSim(double halfWidth, double height, float scale, double[][] spots) {
		this.halfWidth = halfWidth;
		this.height = height;
		this.scale = scale;
		this.spots = spots;
	}

	public boolean isEmpty() {
		return followers.isEmpty();
	}

	public Map<UUID, Follower> all() {
		return followers;
	}

	public @Nullable Follower get(UUID player) {
		return followers.get(player);
	}

	public void remove(UUID player) {
		followers.remove(player);
	}

	/** Nur die Figuren dieser Spieler behalten. */
	public void retain(Set<UUID> players) {
		followers.keySet().retainAll(players);
	}

	public void clear() {
		followers.clear();
	}

	/** Einen Tick weiter: die Figur dieses Spielers nachziehen (beim ersten Mal taucht sie neben ihm auf). */
	public Follower follow(Level level, Player player) {
		Follower follower = followers.get(player.getUUID());
		if (follower == null) {
			follower = new Follower();
			Vec3 spot = spotNear(level, player);
			follower.place(spot != null ? spot : player.position());
			follower.yaw = follower.prevYaw = player.yBodyRot;
			followers.put(player.getUUID(), follower);
		}
		step(follower, level, player);
		return follower;
	}

	private void step(Follower f, Level level, Player player) {
		f.prevX = f.x;
		f.prevY = f.y;
		f.prevZ = f.z;
		f.prevYaw = f.yaw;
		f.prevWalk = f.walk;
		f.prevDistance = f.distance;
		f.prevAmount = f.amount;
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

		boolean swimming = !level.getFluidState(BlockPos.containing(f.x, f.y + height * 0.5, f.z)).isEmpty();
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
		} else if (dist > PERSONAL) {
			// steht: langsam zum Spieler drehen (steht er fast darauf, springt die Richtung hin und her – dann nicht)
			f.yaw = approachAngle(f.yaw, (float) Math.toDegrees(Math.atan2(-dx, dz)), 8f);
		}
		// kurze Beine: schnellere Schritte als ein großer Spieler auf derselben Strecke
		f.walk += (float) Math.min(2.0, moved * 4 / scale);
		f.distance += moved;
		float wanted = (float) Math.min(1, moved * 6);
		f.amount += (wanted - f.amount) * 0.4f;
	}

	/** Kollisionsbox der Figur, Füße bei {@code y}. */
	private AABB box(double x, double y, double z) {
		return new AABB(x - halfWidth, y, z - halfWidth, x + halfWidth, y + height, z + halfWidth);
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
	private void move(Follower f, Level level, double mx, double mz, boolean canStep) {
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
	private boolean canJumpOver(Level level, Follower f, double dirX, double dirZ) {
		AABB high = box(f.x, f.y + 1.05, f.z);
		return level.noBlockCollision(null, high) && level.noBlockCollision(null, high.move(dirX * 0.4, 0, dirZ * 0.4));
	}

	/** Freier Platz mit Boden nahe beim Spieler oder {@code null}. */
	private @Nullable Vec3 spotNear(Level level, Player player) {
		double yaw = Math.toRadians(player.yBodyRot);
		// vorne = (-sin, cos), rechts = (-cos, -sin)
		double forwardX = -Math.sin(yaw);
		double forwardZ = Math.cos(yaw);
		for (double[] spot : spots) {
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
	private @Nullable Double groundBelow(Level level, double x, double y, double z) {
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
}
