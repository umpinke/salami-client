package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.WorldRenderListener;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.TargetUtil;
import dev.salamiclient.util.WeaponUtil;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Aim Assist: dreht den Blick pro Frame ein Stück Richtung des nächsten Ziels im
 * Sichtfeld – wie eine zusätzliche Mausbewegung. Gezielt wird auf den Punkt der Hitbox,
 * der dem aktuellen Blick am nächsten liegt; ist das Ziel schon im Fadenkreuz, passiert nichts.
 */
public final class AimAssistModule extends Module implements WorldRenderListener {
	/** {@code Entity.turn} multipliziert Mauswerte mit diesem Faktor. */
	private static final double TURN_SCALE = 0.15;

	private final BoolSetting players = add(new BoolSetting("players", true));
	private final BoolSetting mobs = add(new BoolSetting("mobs", false));
	private final BoolSetting invisible = add(new BoolSetting("invisible", false));
	private final NumberSetting range = add(new NumberSetting("range", 4.5, 2, 8, 0.5));
	private final NumberSetting fov = add(new NumberSetting("fov", 60, 10, 180, 5));
	/** Grad pro Tick (bei 20 Ticks/s). */
	private final NumberSetting speed = add(new NumberSetting("speed", 4, 0.5, 15, 0.5));
	private final BoolSetting vertical = add(new BoolSetting("vertical", true));
	private final BoolSetting weaponOnly = add(new BoolSetting("weapon_only", false));
	private final BoolSetting clickOnly = add(new BoolSetting("click_only", false));

	private long lastFrame;

	public AimAssistModule() {
		super("aim_assist", Category.UTILITY);
	}

	@Override
	protected void onEnable() {
		lastFrame = 0;
	}

	@Override
	public void renderWorld(WorldRenderContext context) {
		long now = System.nanoTime();
		float ticks = lastFrame == 0 ? 0 : Math.min((now - lastFrame) / 50_000_000f, 3f);
		lastFrame = now;
		LocalPlayer player = mc().player;
		if (ticks <= 0 || player == null || mc().screen != null) {
			return;
		}
		if (clickOnly.isOn() && !mc().options.keyAttack.isDown()) {
			return;
		}
		if (weaponOnly.isOn() && !WeaponUtil.isMeleeWeapon(player.getMainHandItem())) {
			return;
		}
		float partial = mc().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 eye = player.getEyePosition(partial);
		Vec3 look = player.getViewVector(partial);
		Entity target = findTarget(player, eye, look, partial);
		if (target == null || mc().crosshairPickEntity == target) {
			return;
		}

		AABB box = lerpedBox(target, partial);
		// Punkt auf dem Blickstrahl in Zielentfernung, in die Hitbox geklemmt
		double distance = eye.distanceTo(box.getCenter());
		Vec3 aim = TargetUtil.closestPoint(eye.add(look.scale(distance)), box.deflate(0.1));
		float[] rotation = TargetUtil.rotationTo(eye, aim);
		float step = speed.get().floatValue() * ticks;
		float yaw = Mth.clamp(Mth.wrapDegrees(rotation[0] - player.getYRot()), -step, step);
		float pitch = vertical.isOn() ? Mth.clamp(rotation[1] - player.getXRot(), -step, step) : 0f;
		player.turn(yaw / TURN_SCALE, pitch / TURN_SCALE);
	}

	private Entity findTarget(LocalPlayer player, Vec3 eye, Vec3 look, float partial) {
		TargetUtil.Filter filter = new TargetUtil.Filter(players.isOn(), mobs.isOn(), invisible.isOn());
		double maxRange = range.get();
		double maxAngle = fov.get() / 2;
		Entity best = null;
		double bestAngle = Double.MAX_VALUE;
		for (Entity entity : mc().level.getEntities(player, player.getBoundingBox().inflate(maxRange + 2))) {
			if (!filter.accepts(entity)) {
				continue;
			}
			AABB box = lerpedBox(entity, partial);
			if (TargetUtil.closestPoint(eye, box).distanceTo(eye) > maxRange || !player.hasLineOfSight(entity)) {
				continue;
			}
			Vec3 toTarget = box.getCenter().subtract(eye).normalize();
			double angle = Math.toDegrees(Math.acos(Mth.clamp(look.dot(toTarget), -1, 1)));
			if (angle <= maxAngle && angle < bestAngle) {
				best = entity;
				bestAngle = angle;
			}
		}
		return best;
	}

	private static AABB lerpedBox(Entity entity, float partial) {
		return entity.getBoundingBox().move(entity.getPosition(partial).subtract(entity.position()));
	}
}
