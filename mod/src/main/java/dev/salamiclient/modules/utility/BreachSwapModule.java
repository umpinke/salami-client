package dev.salamiclient.modules.utility;

import dev.salamiclient.module.AttackListener;
import dev.salamiclient.module.Category;
import dev.salamiclient.module.Module;
import dev.salamiclient.setting.BoolSetting;
import dev.salamiclient.setting.NumberSetting;
import dev.salamiclient.util.InventoryUtil;
import dev.salamiclient.util.SlotSwapper;
import dev.salamiclient.util.WeaponUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Breach Swap: Beim Schlag wird im selben Tick auf einen Streitkolben mit
 * Bresche (Breach) gewechselt – der Treffer zählt mit dessen Verzauberung
 * gegen die Rüstung des Ziels. Danach geht es zurück zur vorherigen Waffe.
 */
public final class BreachSwapModule extends Module implements AttackListener {
	private final BoolSetting onlyArmored = add(new BoolSetting("only_armored", true));
	private final NumberSetting swapBack = add(new NumberSetting("swap_back", 2, 0, 10, 1));

	private final SlotSwapper swapper = new SlotSwapper();

	public BreachSwapModule() {
		super("breach_swap", Category.UTILITY);
	}

	@Override
	protected void onDisable() {
		swapper.reset();
	}

	@Override
	public void onTick() {
		swapper.tick();
	}

	@Override
	public void beforeAttack(Entity target) {
		if (!(target instanceof LivingEntity living) || mc().player.getMainHandItem().is(Items.MACE)) {
			return;
		}
		if (onlyArmored.isOn() && !WeaponUtil.wearsArmor(living)) {
			return;
		}
		int slot = WeaponUtil.bestHotbarSlot(
			stack -> stack.is(Items.MACE) && WeaponUtil.enchantLevel(stack, Enchantments.BREACH) > 0,
			Enchantments.BREACH);
		if (slot != InventoryUtil.NOT_FOUND) {
			swapper.swapTo(slot, swapBack.intValue());
		}
	}
}
