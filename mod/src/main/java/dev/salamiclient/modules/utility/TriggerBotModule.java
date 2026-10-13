package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.InteractUtil;
import dev.salamiclient.util.TargetUtil;
import dev.salamiclient.util.WeaponUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Trigger Bot: schlägt automatisch zu, sobald ein Ziel im Fadenkreuz ist und der
 * Angriff wieder voll aufgeladen ist.
 */
public final class TriggerBotModule extends Module {
	private final BoolSetting players = add(new BoolSetting("players", true));
	private final BoolSetting mobs = add(new BoolSetting("mobs", false));
	private final BoolSetting invisible = add(new BoolSetting("invisible", false));
	/** Mindest-Aufladung des Angriffs (1.0 = voller Schaden). */
	private final NumberSetting cooldown = add(new NumberSetting("cooldown", 0.95, 0.5, 1.0, 0.05));
	private final BoolSetting weaponOnly = add(new BoolSetting("weapon_only", true));
	/** In der Luft erst zuschlagen, wenn man fällt (kritischer Treffer). Am Boden normal. */
	private final BoolSetting waitForCrit = add(new BoolSetting("wait_for_crit", false));
	private final NumberSetting delay = add(new NumberSetting("delay", 0, 0, 10, 1));

	private Entity aimedAt;
	private int aimedTicks;

	public TriggerBotModule() {
		super("trigger_bot", Category.UTILITY);
	}

	@Override
	protected void onEnable() {
		aimedAt = null;
	}

	@Override
	public void onTick() {
		LocalPlayer player = mc().player;
		if (mc().screen != null || player.isUsingItem()) {
			return;
		}
		if (weaponOnly.isOn() && !WeaponUtil.isMeleeWeapon(player.getMainHandItem())) {
			return;
		}
		if (!(mc().hitResult instanceof EntityHitResult hit)) {
			aimedAt = null;
			return;
		}
		Entity target = hit.getEntity();
		if (!new TargetUtil.Filter(players.isOn(), mobs.isOn(), invisible.isOn()).accepts(target)) {
			aimedAt = null;
			return;
		}
		// Reaktionszeit: Ziel muss so viele Ticks im Fadenkreuz sein
		if (target != aimedAt) {
			aimedAt = target;
			aimedTicks = 0;
		}
		if (aimedTicks++ < delay.intValue()) {
			return;
		}
		if (player.getAttackStrengthScale(0.5f) < cooldown.get()) {
			return;
		}
		if (waitForCrit.isOn() && !player.onGround() && !critReady(player)) {
			return;
		}
		InteractUtil.attack(target);
	}

	/** Bedingungen für einen kritischen Treffer (ohne Sprint-Sonderfälle). */
	private static boolean critReady(LocalPlayer player) {
		return player.fallDistance > 0 && player.getDeltaMovement().y < 0
			&& !player.onClimbable() && !player.isInWater() && !player.isPassenger();
	}
}
