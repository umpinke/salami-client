package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.KeybindSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.InteractUtil;
import dev.salamiclient.util.InventoryUtil;
import dev.salamiclient.util.KeyTrigger;
import net.minecraft.world.item.Items;

/**
 * Pearl Catch (Sattela-Kombo): Auf Tastendruck wird eine Enderperle geworfen und kurz
 * danach eine Windkugel hinterher. Danach geht es zurück zum vorherigen Slot.
 */
public final class PearlCatchModule extends Module {
	private final KeybindSetting triggerKey = add(new KeybindSetting("trigger_key", KeybindSetting.NONE));
	/** Ticks zwischen Perle und Windkugel (0 = im selben Tick). */
	private final NumberSetting delay = add(new NumberSetting("delay", 1, 0, 10, 1));

	private final KeyTrigger trigger = new KeyTrigger(triggerKey);
	private int previousSlot = InventoryUtil.NOT_FOUND;
	/** Ticks bis zur Windkugel; 0 = keine Kombo aktiv. */
	private int windChargeIn;

	public PearlCatchModule() {
		super("pearl_catch", Category.UTILITY);
	}

	@Override
	protected void onDisable() {
		windChargeIn = 0;
		previousSlot = InventoryUtil.NOT_FOUND;
	}

	@Override
	public void onTick() {
		boolean pressed = trigger.pressed();
		if (windChargeIn > 0) {
			if (--windChargeIn == 0) {
				throwWindCharge();
			}
			return;
		}
		if (!pressed) {
			return;
		}
		int pearl = InventoryUtil.findHotbar(stack -> stack.is(Items.ENDER_PEARL));
		if (pearl == InventoryUtil.NOT_FOUND) {
			return;
		}
		previousSlot = InventoryUtil.selectedHotbar();
		InventoryUtil.selectHotbar(pearl);
		InteractUtil.use();
		windChargeIn = delay.intValue();
		if (windChargeIn == 0) {
			throwWindCharge();
		}
	}

	private void throwWindCharge() {
		int windCharge = InventoryUtil.findHotbar(stack -> stack.is(Items.WIND_CHARGE));
		if (windCharge != InventoryUtil.NOT_FOUND) {
			InventoryUtil.selectHotbar(windCharge);
			InteractUtil.use();
		}
		if (previousSlot != InventoryUtil.NOT_FOUND) {
			InventoryUtil.selectHotbar(previousSlot);
			previousSlot = InventoryUtil.NOT_FOUND;
		}
	}
}
