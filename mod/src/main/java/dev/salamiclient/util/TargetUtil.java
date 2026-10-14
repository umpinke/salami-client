package dev.salamiclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Zielauswahl für Kampfmodule (Trigger Bot, Aim Assist). */
public final class TargetUtil {
	private TargetUtil() {}

	/** Welche Entities als Ziel zählen. */
	public record Filter(boolean players, boolean mobs, boolean invisible) {
		public boolean accepts(Entity entity) {
			LocalPlayer self = Minecraft.getInstance().player;
			if (!(entity instanceof LivingEntity living) || entity == self || !living.isAlive() || living.isDeadOrDying()) {
				return false;
			}
			if (!invisible && entity.isInvisible()) {
				return false;
			}
			if (self != null && self.isAlliedTo(entity)) {
				return false; // gleiches Team
			}
			if (entity instanceof Player player) {
				return players && !player.isSpectator() && !player.isCreative();
			}
			return mobs && entity instanceof Enemy;
		}
	}

	/** Punkt der Hitbox, der dem Auge am nächsten liegt – dorthin zu zielen wirkt am wenigsten ruckartig. */
	public static Vec3 closestPoint(Vec3 eye, AABB box) {
		return new Vec3(
			Mth.clamp(eye.x, box.minX, box.maxX),
			Mth.clamp(eye.y, box.minY, box.maxY),
			Mth.clamp(eye.z, box.minZ, box.maxZ));
	}

	/** {@code Entity.turn} multipliziert Mauswerte mit diesem Faktor. */
	private static final double TURN_SCALE = 0.15;

	/**
	 * Dreht den Blick wie eine Mausbewegung, höchstens {@code maxStep} Grad je Achse,
	 * Richtung (yaw, pitch).
	 */
	public static void turnTowards(LocalPlayer player, float yaw, float pitch, float maxStep, boolean vertical) {
		float dYaw = Mth.clamp(Mth.wrapDegrees(yaw - player.getYRot()), -maxStep, maxStep);
		float dPitch = vertical ? Mth.clamp(pitch - player.getXRot(), -maxStep, maxStep) : 0f;
		player.turn(dYaw / TURN_SCALE, dPitch / TURN_SCALE);
	}

	/** Hitbox an der interpolierten Position (für flüssiges Zielen zwischen Ticks). */
	public static AABB lerpedBox(Entity entity, float partial) {
		return entity.getBoundingBox().move(entity.getPosition(partial).subtract(entity.position()));
	}

	/**
	 * Ziel mit dem kleinsten Winkel zum Blick: innerhalb von {@code range} (Abstand zur Hitbox),
	 * im Sichtfeld {@code fov} (Grad) und mit Sichtlinie.
	 */
	public static Entity bestInView(LocalPlayer player, Filter filter, Vec3 eye, Vec3 look, double range, double fov, float partial) {
		double maxAngle = fov / 2;
		Entity best = null;
		double bestAngle = Double.MAX_VALUE;
		for (Entity entity : Minecraft.getInstance().level.getEntities(player, player.getBoundingBox().inflate(range + 2))) {
			if (!filter.accepts(entity)) {
				continue;
			}
			AABB box = lerpedBox(entity, partial);
			if (closestPoint(eye, box).distanceTo(eye) > range) {
				continue;
			}
			Vec3 toTarget = box.getCenter().subtract(eye).normalize();
			double angle = Math.toDegrees(Math.acos(Mth.clamp(look.dot(toTarget), -1, 1)));
			// Raycast (teuer) erst, wenn das Ziel überhaupt besser wäre
			if (angle <= maxAngle && angle < bestAngle && player.hasLineOfSight(entity)) {
				best = entity;
				bestAngle = angle;
			}
		}
		return best;
	}

	/** Blickwinkel (Yaw, Pitch) vom Auge zu einem Punkt. */
	public static float[] rotationTo(Vec3 eye, Vec3 point) {
		Vec3 d = point.subtract(eye);
		double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
		float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
		float pitch = (float) -(Mth.atan2(d.y, horizontal) * Mth.RAD_TO_DEG);
		return new float[] {yaw, pitch};
	}
}
