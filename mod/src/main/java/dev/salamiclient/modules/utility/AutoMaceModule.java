package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.ModeSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.InteractUtil;
import dev.salamiclient.util.InventoryUtil;
import dev.salamiclient.util.SlotSwapper;
import dev.salamiclient.util.TargetUtil;
import dev.salamiclient.util.WeaponUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Auto Mace: Fällst du auf ein anvisiertes Ziel, wird automatisch zum Streitkolben
 * gewechselt und im Fall zugeschlagen (Smash-Angriff, Schaden wächst mit der Fallhöhe).
 * Ein Schlag pro Fall; danach zurück zur vorherigen Waffe.
 *
 * <p>Timing: {@link Timing#INSTANT} schlägt zu, sobald das Ziel in Reichweite ist;
 * {@link Timing#MAX_DAMAGE} wartet bis zum letzten Tick vor der Landung bzw. bevor das Ziel
 * aus Reichweite oder Fadenkreuz fällt – die Fallhöhe und damit der Schaden ist dann am größten.
 * Ziele, die gerade mit dem Schild blocken, werden auf Wunsch ausgelassen (der Schlag würde
 * abgewehrt).
 */
public final class AutoMaceModule extends Module {
	public enum Prefer { DENSITY, BREACH, ANY }
	public enum Timing { INSTANT, MAX_DAMAGE }

	private final NumberSetting minFall = add(new NumberSetting("min_fall", 2.0, 1.5, 20, 0.5));
	private final ModeSetting<Timing> timing = add(new ModeSetting<>("timing", Timing.INSTANT));
	/** Höchstens so weit (Auge bis Hitbox); mehr als die Spiel-Reichweite geht nie. */
	private final NumberSetting range = add(new NumberSetting("range", 3.0, 1.0, 6.0, 0.1));
	private final BoolSetting players = add(new BoolSetting("players", true));
	private final BoolSetting mobs = add(new BoolSetting("mobs", true));
	private final BoolSetting skipBlocking = add(new BoolSetting("skip_blocking", true));
	private final ModeSetting<Prefer> prefer = add(new ModeSetting<>("prefer", Prefer.DENSITY));
	private final NumberSetting swapBack = add(new NumberSetting("swap_back", 3, 0, 10, 1));

	private final SlotSwapper swapper = new SlotSwapper();
	private boolean struckThisFall;

	public AutoMaceModule() {
		super("auto_mace", Category.UTILITY);
	}

	@Override
	protected void onDisable() {
		swapper.reset();
		struckThisFall = false;
	}

	@Override
	public void onTick() {
		swapper.tick();
		LocalPlayer player = mc().player;
		if (player.onGround()) {
			struckThisFall = false;
			return;
		}
		if (struckThisFall || mc().screen != null || player.isFallFlying() || player.fallDistance < minFall.get()) {
			return;
		}
		if (!(mc().hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity target)) {
			return;
		}
		if (!new TargetUtil.Filter(players.isOn(), mobs.isOn(), true).accepts(target)) {
			return;
		}
		if (skipBlocking.isOn() && target.isBlocking()) {
			return;
		}
		double reach = Math.min(range.get(), player.entityInteractionRange());
		Vec3 eye = player.getEyePosition();
		if (eye.distanceTo(TargetUtil.closestPoint(eye, target.getBoundingBox())) > reach) {
			return;
		}
		if (timing.get() == Timing.MAX_DAMAGE && !lastChance(player, target, eye, reach)) {
			return;
		}
		ResourceKey<Enchantment> preferred = switch (prefer.get()) {
			case DENSITY -> Enchantments.DENSITY;
			case BREACH -> Enchantments.BREACH;
			case ANY -> null;
		};
		int slot = WeaponUtil.bestHotbarSlot(stack -> stack.is(Items.MACE), preferred);
		if (slot == InventoryUtil.NOT_FOUND) {
			return;
		}
		swapper.swapTo(slot, swapBack.intValue());
		InteractUtil.attack(target);
		struckThisFall = true;
	}

	/**
	 * Ist das der letzte Tick für den Schlag? Ja, wenn du im nächsten Tick landest (dann wäre
	 * die Fallhöhe weg) oder das Ziel dann nicht mehr in Reichweite bzw. im Fadenkreuz ist.
	 */
	private boolean lastChance(LocalPlayer player, LivingEntity target, Vec3 eye, double reach) {
		Vec3 motion = player.getDeltaMovement();
		if (!mc().level.noCollision(player, player.getBoundingBox().move(0, motion.y, 0))) {
			return true;
		}
		Vec3 nextEye = eye.add(motion);
		AABB nextBox = target.getBoundingBox().move(target.getDeltaMovement()).inflate(target.getPickRadius());
		Vec3 lookEnd = nextEye.add(player.getViewVector(1).scale(reach));
		return nextEye.distanceTo(TargetUtil.closestPoint(nextEye, nextBox)) > reach
			|| nextBox.clip(nextEye, lookEnd).isEmpty() && !nextBox.contains(nextEye);
	}
}
