package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.module.WorldRenderListener;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.ArrowBallistics;
import dev.salamiclient.util.TargetUtil;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Bow Aim: Während du den Bogen spannst, zielt das Modul auf das nächste Ziel im
 * Sichtfeld – mit Pfeilflugbahn (Schwerkraft, Luftwiderstand) und auf Wunsch mit
 * Vorhalt für die Bewegung des Ziels.
 */
public final class BowAimModule extends Module implements WorldRenderListener {
	/** Pfeile starten knapp unter Augenhöhe. */
	private static final double ARROW_START_OFFSET = 0.1;
	/** Erst ab dieser Spannung zielen (darunter fliegt der Pfeil kaum). */
	private static final float MIN_POWER = 0.1f;

	private final BoolSetting players = add(new BoolSetting("players", true));
	private final BoolSetting mobs = add(new BoolSetting("mobs", false));
	private final BoolSetting invisible = add(new BoolSetting("invisible", false));
	private final NumberSetting range = add(new NumberSetting("range", 50, 5, 100, 5));
	private final NumberSetting fov = add(new NumberSetting("fov", 60, 10, 180, 5));
	/** Grad pro Tick (bei 20 Ticks/s). */
	private final NumberSetting speed = add(new NumberSetting("speed", 8, 1, 30, 1));
	private final BoolSetting predict = add(new BoolSetting("predict", true));

	private long lastFrame;

	public BowAimModule() {
		super("bow_aim", Category.UTILITY);
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
		if (!player.isUsingItem() || !player.getUseItem().is(Items.BOW)) {
			return;
		}
		float power = BowItem.getPowerForTime(player.getTicksUsingItem());
		if (power < MIN_POWER) {
			return;
		}
		float partial = mc().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 eye = player.getEyePosition(partial);
		TargetUtil.Filter filter = new TargetUtil.Filter(players.isOn(), mobs.isOn(), invisible.isOn());
		Entity target = TargetUtil.bestInView(player, filter, eye, player.getViewVector(partial), range.get(), fov.get(), partial);
		if (target == null) {
			return;
		}

		Vec3 start = eye.subtract(0, ARROW_START_OFFSET, 0);
		double arrowSpeed = power * 3.0;
		Vec3 aimPoint = TargetUtil.lerpedBox(target, partial).getCenter();
		float[] rotation = aimAt(start, aimPoint, arrowSpeed);
		if (rotation == null) {
			return;
		}
		if (predict.isOn()) {
			// Vorhalt: Zielbewegung pro Tick × Flugzeit, dann mit dem neuen Punkt nochmal lösen
			Vec3 motion = new Vec3(target.getX() - target.xo, target.getY() - target.yo, target.getZ() - target.zo);
			Vec3 predicted = aimPoint.add(motion.scale(rotation[2]));
			float[] led = aimAt(start, predicted, arrowSpeed);
			if (led != null) {
				rotation = led;
			}
		}
		TargetUtil.turnTowards(player, rotation[0], rotation[1], speed.get().floatValue() * ticks, true);
	}

	/** {yaw, pitch, Flugzeit in Ticks} für einen Pfeil von {@code start} nach {@code target}. */
	private static float[] aimAt(Vec3 start, Vec3 target, double arrowSpeed) {
		Vec3 delta = target.subtract(start);
		double distance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		Float pitch = ArrowBallistics.solvePitch(arrowSpeed, distance, delta.y);
		if (pitch == null) {
			return null;
		}
		ArrowBallistics.Flight flight = ArrowBallistics.fly(arrowSpeed, pitch, distance);
		float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90f;
		return new float[] {yaw, pitch, flight == null ? 0f : (float) flight.ticks()};
	}
}
