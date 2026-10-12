package dev.salamiclient.modules.utility;

import dev.salamiclient.mixin.AbstractContainerScreenAccessor;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.InventoryUtil;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

/**
 * Hover Totem: Im offenen Inventar wandert ein Totem unter der Maus automatisch in
 * die Offhand – und optional zusätzlich in einen Hotbar-Slot.
 */
public final class HoverTotemModule extends Module {
	private final NumberSetting delay = add(new NumberSetting("delay", 2, 0, 10, 1));
	private final BoolSetting hotbar = add(new BoolSetting("hotbar", false));
	private final NumberSetting hotbarSlot = add(new NumberSetting("hotbar_slot", 9, 1, 9, 1));

	private int cooldown;

	public HoverTotemModule() {
		super("hover_totem", Category.UTILITY);
	}

	@Override
	public void onTick() {
		if (!(mc().screen instanceof InventoryScreen screen)) {
			cooldown = 0;
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		Slot hovered = ((AbstractContainerScreenAccessor) screen).salami$getHoveredSlot();
		if (hovered == null || !(hovered.container instanceof Inventory) || !hovered.getItem().is(Items.TOTEM_OF_UNDYING)) {
			return;
		}
		LocalPlayer player = mc().player;
		if (!player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
			InventoryUtil.swapMenuSlotWithOffhand(hovered.index);
			cooldown = delay.intValue();
			return;
		}
		int target = hotbarSlot.intValue() - 1;
		if (hotbar.isOn()
			&& hovered.index != InventoryUtil.toMenuSlot(target)
			&& !player.getInventory().getItem(target).is(Items.TOTEM_OF_UNDYING)) {
			InventoryUtil.swapMenuSlotWithHotbar(hovered.index, target);
			cooldown = delay.intValue();
		}
	}
}
