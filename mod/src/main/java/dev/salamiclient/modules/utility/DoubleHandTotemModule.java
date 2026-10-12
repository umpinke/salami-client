package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.InventoryUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;

/**
 * Double Hand Totem: hält einen zweiten Totem in einem festen Hotbar-Slot bereit
 * und wechselt bei niedrigem Leben dorthin – dann schützt ein Totem in jeder Hand.
 * Für den Offhand-Totem sorgt Auto Totem.
 */
public final class DoubleHandTotemModule extends Module {
	private final NumberSetting slot = add(new NumberSetting("slot", 9, 1, 9, 1));
	private final BoolSetting switchOnLowHealth = add(new BoolSetting("switch_low", true));
	private final NumberSetting health = add(new NumberSetting("health", 8, 1, 20, 1));
	private final NumberSetting delay = add(new NumberSetting("delay", 1, 0, 10, 1));

	private int cooldown;

	public DoubleHandTotemModule() {
		super("double_hand_totem", Category.UTILITY);
	}

	@Override
	public void onTick() {
		Inventory inventory = mc().player.getInventory();
		int hotbarIndex = slot.intValue() - 1;

		if (cooldown > 0) {
			cooldown--;
		} else if (!inventory.getItem(hotbarIndex).is(Items.TOTEM_OF_UNDYING) && InventoryUtil.canClickPlayerInventory()) {
			int source = InventoryUtil.find(Items.TOTEM_OF_UNDYING, 9, 35);
			if (source != InventoryUtil.NOT_FOUND) {
				InventoryUtil.swapWithHotbar(source, hotbarIndex);
				cooldown = delay.intValue();
			}
		}

		if (switchOnLowHealth.isOn()
			&& mc().screen == null
			&& InventoryUtil.totalHealth() <= health.get()
			&& inventory.getItem(hotbarIndex).is(Items.TOTEM_OF_UNDYING)
			&& InventoryUtil.selectedHotbar() != hotbarIndex) {
			InventoryUtil.selectHotbar(hotbarIndex);
		}
	}
}
