package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.KeybindSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.InteractUtil;
import dev.salamiclient.util.InventoryUtil;
import dev.salamiclient.util.KeyTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Cobweb Placer: Auf Tastendruck setzt das Makro ein Spinnennetz an deine Füße – Gegner,
 * die nachrücken, bleiben darin hängen. Danach geht es zurück zum vorherigen (oder einem
 * festen) Hotbar-Slot.
 */
public final class CobwebPlacerModule extends Module {
	private final KeybindSetting triggerKey = add(new KeybindSetting("trigger_key", KeybindSetting.NONE));
	private final NumberSetting delay = add(new NumberSetting("delay", 4, 0, 20, 1));
	/** 0 = zurück zum vorherigen Slot, sonst fester Hotbar-Slot 1–9. */
	private final NumberSetting returnSlot = add(new NumberSetting("return_slot", 0, 0, 9, 1));

	private final KeyTrigger trigger = new KeyTrigger(triggerKey);
	private int cooldown;

	public CobwebPlacerModule() {
		super("cobweb_placer", Category.UTILITY);
	}

	@Override
	public void onTick() {
		if (cooldown > 0) {
			cooldown--;
		}
		if (!trigger.pressed() || cooldown > 0) {
			return;
		}
		BlockPos feet = mc().player.blockPosition();
		if (!mc().level.getBlockState(feet).canBeReplaced()) {
			return;
		}
		int web = InventoryUtil.findHotbar(stack -> stack.is(Items.COBWEB));
		if (web == InventoryUtil.NOT_FOUND) {
			return;
		}
		int previous = InventoryUtil.selectedHotbar();
		InventoryUtil.selectHotbar(web);
		// Klick auf die Oberseite des Blocks unter dir: das Netz entsteht an deinen Füßen
		BlockPos below = feet.below();
		InteractUtil.useOn(new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false));
		int back = returnSlot.intValue();
		InventoryUtil.selectHotbar(back > 0 ? back - 1 : previous);
		cooldown = delay.intValue();
	}
}
