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

	/** Blickwinkel (Yaw, Pitch) vom Auge zu einem Punkt. */
	public static float[] rotationTo(Vec3 eye, Vec3 point) {
		Vec3 d = point.subtract(eye);
		double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
		float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
		float pitch = (float) -(Mth.atan2(d.y, horizontal) * Mth.RAD_TO_DEG);
		return new float[] {yaw, pitch};
	}
}
