package dev.salamiclient.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.function.Predicate;

/** Waffen- und Verzauberungs-Helfer (Streitkolben, Speer). */
public final class WeaponUtil {
	private WeaponUtil() {}

	/** Stufe einer Verzauberung auf dem Item (0 = nicht vorhanden). */
	public static int enchantLevel(ItemStack stack, ResourceKey<Enchantment> enchantment) {
		for (Object2IntMap.Entry<Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
			if (entry.getKey().is(enchantment)) {
				return entry.getIntValue();
			}
		}
		return 0;
	}

	/**
	 * Hotbar-Slot mit passendem Item; bei mehreren der mit der höchsten Stufe von
	 * {@code preferred} (darf {@code null} sein).
	 */
	public static int bestHotbarSlot(Predicate<ItemStack> matcher, ResourceKey<Enchantment> preferred) {
		Inventory inventory = Minecraft.getInstance().player.getInventory();
		int best = InventoryUtil.NOT_FOUND;
		int bestLevel = -1;
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || !matcher.test(stack)) {
				continue;
			}
			int level = preferred == null ? 0 : enchantLevel(stack, preferred);
			if (level > bestLevel) {
				best = slot;
				bestLevel = level;
			}
		}
		return best;
	}

	public static boolean isSpear(ItemStack stack) {
		return stack.has(DataComponents.PIERCING_WEAPON);
	}

	public static boolean wearsArmor(LivingEntity entity) {
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (!entity.getItemBySlot(slot).isEmpty()) {
				return true;
			}
		}
		return false;
	}
}
