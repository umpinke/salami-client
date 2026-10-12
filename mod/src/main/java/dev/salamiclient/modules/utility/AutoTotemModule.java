package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.ModeSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.InventoryUtil;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Items;

/**
 * Auto Totem: hält einen Totem der Unsterblichkeit in der Offhand.
 *
 * <ul>
 *   <li>{@code ALWAYS} – legt jederzeit nach, sobald die Offhand keinen Totem mehr hat.</li>
 *   <li>{@code INVENTORY} – legt nur nach, während das Inventar offen ist ("Inventory Totem").</li>
 * </ul>
 * Andere Offhand-Items (z. B. Kristalle) werden erst ersetzt, wenn das Leben unter
 * die eingestellte Grenze fällt.
 */
public final class AutoTotemModule extends Module {
	public enum Mode { ALWAYS, INVENTORY }

	private final ModeSetting<Mode> mode = add(new ModeSetting<>("mode", Mode.ALWAYS));
	private final NumberSetting delay = add(new NumberSetting("delay", 1, 0, 10, 1));
	private final NumberSetting health = add(new NumberSetting("health", 20, 1, 20, 1));

	private int cooldown;

	public AutoTotemModule() {
		super("auto_totem", Category.UTILITY);
	}

	@Override
	protected void onEnable() {
		cooldown = 0;
	}

	@Override
	public void onTick() {
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		LocalPlayer player = mc().player;
		if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
			return;
		}
		if (mode.get() == Mode.INVENTORY && !(mc().screen instanceof InventoryScreen)) {
			return;
		}
		if (!InventoryUtil.canClickPlayerInventory()) {
			return;
		}
		if (!player.getOffhandItem().isEmpty() && player.getHealth() > health.get()) {
			return;
		}
		int slot = InventoryUtil.findPreferInventory(Items.TOTEM_OF_UNDYING);
		if (slot == InventoryUtil.NOT_FOUND) {
			return;
		}
		InventoryUtil.swapWithOffhand(slot);
		cooldown = delay.intValue();
	}
}
