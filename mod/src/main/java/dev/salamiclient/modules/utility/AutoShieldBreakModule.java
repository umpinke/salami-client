package dev.salamiclient.modules.utility;

import dev.salamiclient.module.AttackListener;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.InteractUtil;
import dev.salamiclient.util.InventoryUtil;
import dev.salamiclient.util.TargetUtil;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Auto Shield Break: Blockt das Ziel mit einem Schild, wechselt das Modul für den Schlag
 * auf eine Axt (die den Schild für einige Sekunden ausschaltet) und danach zurück.
 * Optional schlägt es dabei selbst zu, sobald ein blockender Spieler im Fadenkreuz ist,
 * und setzt nach dem Zurückwechseln mit der eigenen Waffe nach (der Schild ist dann aus).
 */
public final class AutoShieldBreakModule extends Module implements AttackListener {
	/** Mindestabstand zwischen automatischen Schlägen. */
	private static final int AUTO_HIT_INTERVAL = 4;

	private final BoolSetting autoHit = add(new BoolSetting("auto_hit", true));
	/** 0 = auf der Axt bleiben. */
	private final NumberSetting swapBack = add(new NumberSetting("swap_back", 2, 0, 10, 1));
	/** Nur mit Zurückwechseln: einen Tick danach mit der eigenen Waffe nachschlagen. */
	private final BoolSetting followUp = add(new BoolSetting("follow_up", false));

	private int previousSlot = InventoryUtil.NOT_FOUND;
	private int swapBackIn;
	private int hitCooldown;
	private Player followUpTarget;
	private int followUpIn;

	public AutoShieldBreakModule() {
		super("auto_shield_break", Category.UTILITY);
	}

	@Override
	protected void onDisable() {
		restoreSlot();
		followUpTarget = null;
		followUpIn = 0;
	}

	@Override
	public void beforeAttack(Entity target) {
		if (target instanceof Player player && player.isBlocking()) {
			holdAxe();
			if (swapBackIn > 0) {
				followUpTarget = player;
			}
		}
	}

	@Override
	public void onTick() {
		if (hitCooldown > 0) {
			hitCooldown--;
		}
		if (followUpIn > 0 && --followUpIn == 0) {
			followUp();
		}
		if (swapBackIn > 0 && --swapBackIn == 0) {
			restoreSlot();
			if (followUp.isOn() && followUpTarget != null) {
				followUpIn = 1;
			} else {
				followUpTarget = null;
			}
		}
		if (!autoHit.isOn() || mc().screen != null || hitCooldown > 0) {
			return;
		}
		if (mc().hitResult instanceof EntityHitResult hit
			&& hit.getEntity() instanceof Player player
			&& player.isBlocking()
			&& new TargetUtil.Filter(true, false, true).accepts(player)
			&& InventoryUtil.findHotbar(stack -> stack.is(ItemTags.AXES)) != InventoryUtil.NOT_FOUND) {
			InteractUtil.attack(player); // beforeAttack wechselt vorher auf die Axt
			hitCooldown = AUTO_HIT_INTERVAL;
		}
	}

	private void holdAxe() {
		if (mc().player.getMainHandItem().is(ItemTags.AXES)) {
			return;
		}
		int axe = InventoryUtil.findHotbar(stack -> stack.is(ItemTags.AXES));
		if (axe == InventoryUtil.NOT_FOUND) {
			return;
		}
		if (previousSlot == InventoryUtil.NOT_FOUND) {
			previousSlot = InventoryUtil.selectedHotbar();
		}
		InventoryUtil.selectHotbar(axe);
		swapBackIn = swapBack.intValue();
		if (swapBackIn == 0) {
			previousSlot = InventoryUtil.NOT_FOUND;
		}
	}

	/** Folgeschlag mit der eigenen Waffe auf das Ziel, dessen Schild gerade ausgeschaltet wurde. */
	private void followUp() {
		Player target = followUpTarget;
		followUpTarget = null;
		if (target != null && target.isAlive() && mc().screen == null && mc().player.isWithinEntityInteractionRange(target, 0)) {
			InteractUtil.attack(target);
			hitCooldown = AUTO_HIT_INTERVAL;
		}
	}

	private void restoreSlot() {
		if (previousSlot != InventoryUtil.NOT_FOUND && mc().player != null) {
			InventoryUtil.selectHotbar(previousSlot);
		}
		previousSlot = InventoryUtil.NOT_FOUND;
		swapBackIn = 0;
	}
}
