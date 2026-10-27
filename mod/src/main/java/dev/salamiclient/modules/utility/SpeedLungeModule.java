package dev.salamiclient.modules.utility;

import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.InventoryUtil;
import dev.salamiclient.util.WeaponUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
//? if >=1.21.11
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Speed Lunge: Mit einem Speer mit Ausfall (Lunge) wird automatisch zugestoßen,
 * sobald die Aufladung es erlaubt – jeder Stoß schiebt dich nach vorn.
 * Genau wie ein Linksklick (gleicher Client-Pfad); kostet Haltbarkeit und Hunger,
 * deshalb stoppt das Modul unter einer Hunger-Grenze. Wirkt nicht im Wasser,
 * beim Reiten oder Gleiten (Spielregel der Verzauberung).
 */
public final class SpeedLungeModule extends Module {
	private final BoolSetting onlyForward = add(new BoolSetting("only_forward", true));
	private final NumberSetting minFood = add(new NumberSetting("min_food", 6, 0, 20, 1));
	private final BoolSetting autoSwitch = add(new BoolSetting("auto_switch", false));

	public SpeedLungeModule() {
		super("speed_lunge", Category.UTILITY);
	}

	@Override
	public void onTick() {
		LocalPlayer player = mc().player;
		if (mc().screen != null || mc().gameMode == null) {
			return;
		}
		if (onlyForward.isOn() && !mc().options.keyUp.isDown()) {
			return;
		}
		if (player.getFoodData().getFoodLevel() <= minFood.get()
			|| player.isPassenger() || player.isFallFlying() || player.isInWater() || player.isHandsBusy()) {
			return;
		}
		ItemStack stack = player.getMainHandItem();
		if (!isLungeSpear(stack)) {
			if (!autoSwitch.isOn()) {
				return;
			}
			int slot = InventoryUtil.findHotbar(this::isLungeSpear);
			if (slot == InventoryUtil.NOT_FOUND) {
				return;
			}
			InventoryUtil.selectHotbar(slot);
			return; // ab dem nächsten Tick mit dem Speer
		}
		// Speere gibt es erst ab 1.21.11 (davor ist das Modul nicht registriert)
		//? if >=1.21.11 {
		if (player.cannotAttackWithItem(stack, 0)) {
			return;
		}
		PiercingWeapon piercing = stack.get(DataComponents.PIERCING_WEAPON);
		//? if >=26.3 {
		/*mc().gameMode.piercingAttack(stack.getAttackAnimation(), piercing); // schwingt selbst
		*///?} else {
		mc().gameMode.piercingAttack(piercing);
		player.swing(InteractionHand.MAIN_HAND);
		//?}
		//?}
	}

	private boolean isLungeSpear(ItemStack stack) {
		//? if >=1.21.11 {
		return WeaponUtil.isSpear(stack) && WeaponUtil.enchantLevel(stack, Enchantments.LUNGE) > 0;
		//?} else
		/*return false;*/
	}
}
